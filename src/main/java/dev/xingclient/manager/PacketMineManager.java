package dev.xingclient.manager;

import dev.xingclient.ClientSettings;
import dev.xingclient.XingClient;
import dev.xingclient.event.EventBus;
import dev.xingclient.event.PacketCommitEvent;
import dev.xingclient.mixin.ClientPlayerInteractionManagerAccessor;
import dev.xingclient.nativebridge.XingNativeBridge;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.GameMode;
import net.minecraft.world.RaycastContext;

/** Supplies game snapshots and executes native mining decisions. Author: uint32. */
public final class PacketMineManager {
    public record Target(BlockPos position, float progress) {}
    private static final int PRIORITY = 40;
    private static final String[] STAGES = {"Starting", "Sending start", "Mining", "Sending stop",
            "Waiting for block update", "Retrying", "Finished", "Failed"};
    private final ClientManagers managers;
    private long handle;
    private ActionOwner owner;
    private BlockPos position;
    private Direction face;
    private Block block;
    private ClientSettings.SpeedMineSettings settings;
    private ItemStack tool;
    private int toolSlot = -1, expectedSequence = -1, expectedCommand;
    private float progress;
    private String status = "Idle";

    PacketMineManager(ClientManagers managers, EventBus events) {
        this.managers = managers;
        events.subscribe(PacketCommitEvent.class, this::committed);
    }

    public boolean isOwnedBy(ActionOwner candidate) { return owner == candidate && position != null; }
    public String status() { return status; }
    public List<Target> targets() { return position == null ? List.of() : List.of(new Target(position, progress)); }

    public boolean request(ActionOwner candidate, BlockPos target, Direction direction,
            ClientSettings.SpeedMineSettings configuration) {
        CombatActionScheduler.requireClientThread();
        var client = MinecraftClient.getInstance();
        if (candidate == null || target == null || direction == null || configuration == null
                || client.world == null || client.player == null || client.interactionManager == null) return false;
        configuration.normalize();
        var mode = client.interactionManager.getCurrentGameMode();
        var state = client.world.getBlockState(target);
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || state.isAir()
                || state.getHardness(client.world, target) < 0
                || client.player.isBlockBreakingRestricted(client.world, target, mode)
                || client.player.getEyePos().squaredDistanceTo(target.toCenterPos()) > configuration.range * configuration.range
                || !client.player.canInteractWithBlockAt(target, 0)) return false;
        if (configuration.strictDirection && !visibleFace(client, target, direction)) return false;
        if (owner != null && owner != candidate) return false;
        if (isOwnedBy(candidate) && position.equals(target)) return true;
        release(candidate);
        client.interactionManager.cancelBlockBreaking();
        if (handle == 0) { XingNativeBridge.loadLibrary(); handle = create(); }
        resetNative(handle);
        owner = candidate;
        position = target.toImmutable();
        face = direction;
        block = state.getBlock();
        settings = configuration;
        status = "Starting";
        return true;
    }

    public void tick(MinecraftClient client) {
        if (position != null) tickTarget(client);
        synchronizeProgress(client);
    }

    private void tickTarget(MinecraftClient client) {
        if (client.world == null || client.player == null || client.interactionManager == null) { reset(); return; }
        settings.normalize();
        var player = client.player;
        var state = client.world.getBlockState(position);
        boolean valid = player.isAlive() && state.getBlock() == block
                && player.getEyePos().squaredDistanceTo(position.toCenterPos()) <= settings.range * settings.range
                && player.canInteractWithBlockAt(position, 0)
                && !player.isBlockBreakingRestricted(client.world, position, client.interactionManager.getCurrentGameMode())
                && client.interactionManager.getCurrentGameMode() != GameMode.CREATIVE
                && client.interactionManager.getCurrentGameMode() != GameMode.SPECTATOR;
        boolean paused = !settings.multitask && player.isUsingItem();
        if (!state.isAir() && tool != null && !ItemStack.areItemsAndComponentsEqual(tool, player.getInventory().getStack(toolSlot))) {
            status = "Tool changed"; release(owner); return;
        }
        boolean leased = toolSlot >= 0 && !paused && managers.inventory.beginToolLease(owner, PRIORITY,
                toolSlot, settings.swapMode, 3);
        var efficiency = client.world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.EFFICIENCY);
        double[] tools = new double[27];
        int selected = player.getInventory().getSelectedSlot();
        for (int slot = 0; slot < 9; slot++) {
            var stack = player.getInventory().getStack(slot);
            tools[slot * 3] = settings.swapMode == InventoryManager.SwapMode.NONE && slot != selected
                    ? 0 : stack.getMiningSpeedMultiplier(state);
            tools[slot * 3 + 1] = EnchantmentHelper.getLevel(efficiency, stack);
            tools[slot * 3 + 2] = stack.isSuitableFor(state) ? 1 : 0;
        }
        var fatigue = player.getStatusEffect(StatusEffects.MINING_FATIGUE);
        double[] frame = {state.getHardness(client.world, position), state.isToolRequired() ? 1 : 0,
                StatusEffectUtil.hasHaste(player) ? StatusEffectUtil.getHasteAmplifier(player) : -1,
                fatigue == null ? -1 : fatigue.getAmplifier(), player.getAttributeValue(EntityAttributes.BLOCK_BREAK_SPEED),
                player.getAttributeValue(EntityAttributes.SUBMERGED_MINING_SPEED), player.isOnGround() ? 1 : 0,
                player.isSubmergedIn(FluidTags.WATER) ? 1 : 0, selected,
                leased ? managers.serverState.selectedServerSlot() : -1, paused ? 1 : 0,
                1, settings.threshold, settings.confirmationTicks, settings.retries, valid ? 1 : 0, state.isAir() ? 1 : 0};
        double[] result = tickNative(handle, frame, tools);
        if (result == null || result.length != 5) throw new IllegalStateException("Invalid native mining response");
        progress = (float) result[3];
        status = STAGES[(int) result[1]];
        int command = (int) result[0];
        if (command == 4 || command == 5) { finish(command == 5); return; }
        if (command == 0) return;
        int slot = (int) result[2];
        if (command == 1 && toolSlot != slot) {
            toolSlot = slot;
            tool = player.getInventory().getStack(slot).copy();
        }
        // Commits may arrive during send, so publish the pending stage first.
        if (command != 3) sentNative(handle, command, true);
        boolean success = false;
        try { success = send(command); }
        finally {
            if (command == 3 || !success) sentNative(handle, command, success);
        }
        if (success && command == 3) { expectedSequence = -1; toolSlot = -1; tool = null; }
    }

    private boolean send(int command) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.getNetworkHandler() == null) return false;
        return managers.scheduler.execute(owner, PRIORITY, 1, null, () -> {
            if (command != 3 && (!managers.inventory.beginToolLease(owner, PRIORITY, toolSlot, settings.swapMode, 3)
                    || managers.serverState.selectedServerSlot() != toolSlot)) return false;
            if (command != 3 && settings.strictDirection && !visibleFace(client, position, face)) return false;
            java.util.function.BooleanSupplier action = () -> {
                var packetAction = command == 1 ? PlayerActionC2SPacket.Action.START_DESTROY_BLOCK
                        : command == 2 ? PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK
                        : PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK;
                boolean sent = managers.network.sendSequenced(owner, sequence -> {
                    expectedSequence = sequence; expectedCommand = command;
                    return new PlayerActionC2SPacket(packetAction, position, face, sequence);
                });
                if (sent) managers.combatActions.markPacketMineAction(1);
                if (sent && settings.swing && command != 3) client.player.swingHand(Hand.MAIN_HAND);
                return sent;
            };
            return settings.rotate && command != 3
                    ? managers.rotations.withRotation(owner, PRIORITY, facePoint(position, face), 1, action)
                    : action.getAsBoolean();
        }, ActionResource.MINE_PACKET, ActionResource.MAIN_HAND, ActionResource.INVENTORY).success();
    }

    private void committed(PacketCommitEvent event) {
        if (owner == null || event.owner() != owner || !(event.packet() instanceof PlayerActionC2SPacket packet)
                || !packet.getPos().equals(position) || packet.getSequence() != expectedSequence) return;
        int command = packet.getAction() == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK ? 1
                : packet.getAction() == PlayerActionC2SPacket.Action.STOP_DESTROY_BLOCK ? 2 : 0;
        if (command == expectedCommand && command != 0) {
            committedNative(handle, command); expectedSequence = -1;
        }
    }

    public void release(ActionOwner candidate) {
        if (!isOwnedBy(candidate)) return;
        finish(true);
    }

    private void finish(boolean abort) {
        ActionOwner previous = owner;
        try {
            if (abort) send(3);
        } finally {
            reset();
            try { managers.inventory.release(previous); }
            finally { managers.rotations.release(previous); }
        }
    }

    public void reset() {
        if (handle != 0) resetNative(handle);
        owner = null; position = null; block = null; settings = null; tool = null;
        toolSlot = -1; expectedSequence = -1; expectedCommand = 0; progress = 0;
        if (XingClient.INSTANCE != null) XingClient.INSTANCE.miningSync.clear();
    }

    private void synchronizeProgress(MinecraftClient client) {
        var app = XingClient.INSTANCE;
        if (app == null) return;
        if (position != null) { app.miningSync.update(position, progress, true, false); return; }
        if (client.interactionManager != null && client.world != null) {
            var vanilla = (ClientPlayerInteractionManagerAccessor) client.interactionManager;
            if (vanilla.xing$isBreakingBlock()) {
                app.miningSync.update(vanilla.xing$getCurrentBreakingPos(), vanilla.xing$getCurrentBreakingProgress(), true, false);
                return;
            }
        }
        app.miningSync.clear();
    }

    private static net.minecraft.util.math.Vec3d facePoint(BlockPos target, Direction direction) {
        return target.toCenterPos().add(direction.getOffsetX() * .499, direction.getOffsetY() * .499, direction.getOffsetZ() * .499);
    }

    private static boolean visibleFace(MinecraftClient client, BlockPos target, Direction direction) {
        var hit = client.world.raycast(new RaycastContext(client.player.getEyePos(), facePoint(target, direction),
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target) && hit.getSide() == direction;
    }

    private static native long create();
    private static native void resetNative(long handle);
    private static native double[] tickNative(long handle, double[] frame, double[] tools);
    private static native void sentNative(long handle, int command, boolean success);
    private static native void committedNative(long handle, int command);
}
