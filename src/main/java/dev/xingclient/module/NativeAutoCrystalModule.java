package dev.xingclient.module;

import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.CrystalSpawnEvent;
import dev.xingclient.event.EventBus;
import dev.xingclient.event.WorldBlockUpdateEvent;
import dev.xingclient.XingClient;
import dev.xingclient.manager.InventoryManager;
import dev.xingclient.manager.InteractionManager;
import dev.xingclient.nativebridge.NativeCyclePlan;
import dev.xingclient.nativebridge.NativeCycleSnapshot;
import dev.xingclient.nativebridge.XingNativeBridge;
import dev.xingclient.mixin.ClientPlayerInteractionManagerAccessor;
import java.nio.ByteBuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/** Java snapshot and game-action adapter for the native placement cycle. */
public final class NativeAutoCrystalModule extends Module {
    private static final int OUTPUT_CAPACITY = XingNativeBridge.CYCLE_OUTPUT_HEADER_BYTES
            + 64 * XingNativeBridge.CYCLE_OUTPUT_PLACEMENT_BYTES;
    private static final int BREAK_ROTATION_PRIORITY = 70;
    private static final int PLACE_ROTATION_PRIORITY = 65;
    private static final int PLACE_SLOT_PRIORITY = 75;
    private static final int BREAK_ACTION_PRIORITY = 80;
    private static final int ROTATION_HOLD_TICKS = 2;

    private final EventBus events;
    private EventBus.Subscription tickSubscription;
    private EventBus.Subscription spawnSubscription;
    private EventBus.Subscription worldUpdateSubscription;
    private String cycleStatus = "Idle";
    private BlockPos pendingBase;
    private int pendingTicks;
    private int pendingBreakEntityId = -1;
    private int pendingBreakTicks;
    private long lastPacketReactionTick = Long.MIN_VALUE;
    private long lastCycleTick = Long.MIN_VALUE;
    private long lastBreakTick = Long.MIN_VALUE;
    private int lastBrokenEntityId = -1;

    public NativeAutoCrystalModule(EventBus events) {
        super("auto_crystal", "Auto Crystal", ModuleCategory.COMBAT);
        this.events = events;
    }

    @Override
    protected void onEnable() {
        tickSubscription = events.subscribe(ClientTickEvent.class, this::onTick);
        spawnSubscription = events.subscribe(CrystalSpawnEvent.class, this::onCrystalSpawn);
        worldUpdateSubscription = events.subscribe(WorldBlockUpdateEvent.class, this::onWorldBlockUpdate);
    }

    @Override
    protected void onDisable() {
        if (tickSubscription != null) {
            tickSubscription.close();
            tickSubscription = null;
        }
        if (spawnSubscription != null) {
            spawnSubscription.close();
            spawnSubscription = null;
        }
        if (worldUpdateSubscription != null) {
            worldUpdateSubscription.close();
            worldUpdateSubscription = null;
        }
        cycleStatus = "Disabled";
        pendingBase = null;
        pendingTicks = 0;
        pendingBreakEntityId = -1;
        pendingBreakTicks = 0;
        lastPacketReactionTick = Long.MIN_VALUE;
        lastCycleTick = Long.MIN_VALUE;
        lastBreakTick = Long.MIN_VALUE;
        lastBrokenEntityId = -1;
        dev.xingclient.XingClient.INSTANCE.miningSync.clear();
    }

    @Override
    public String status() {
        return isEnabled() ? cycleStatus : "Disabled";
    }

    private void onTick(ClientTickEvent event) {
        if (event.phase() != ClientTickEvent.Phase.END) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null || client.interactionManager == null) {
            cycleStatus = "Waiting for world";
            return;
        }

        ClientPlayerInteractionManagerAccessor mining =
                (ClientPlayerInteractionManagerAccessor) client.interactionManager;
        if (mining.xing$isBreakingBlock()) {
            dev.xingclient.XingClient.INSTANCE.miningSync.update(
                    mining.xing$getCurrentBreakingPos(), mining.xing$getCurrentBreakingProgress(), true, false);
        } else {
            dev.xingclient.XingClient.INSTANCE.miningSync.clear();
        }

        if (pendingBase != null) {
            if (NativeCycleSnapshotFactory.hasCrystalAt(client, player, pendingBase) || pendingTicks >= 2) {
                pendingBase = null;
                pendingTicks = 0;
            } else {
                pendingTicks++;
            }
        }

        if (pendingBreakEntityId != -1) {
            Entity pendingCrystal = client.world.getEntityById(pendingBreakEntityId);
            if (!(pendingCrystal instanceof EndCrystalEntity) || pendingBreakTicks >= 1) {
                pendingBreakEntityId = -1;
                pendingBreakTicks = 0;
            } else {
                pendingBreakTicks++;
            }
        }

        if (client.world.getTime() == lastCycleTick) return;
        runPlanner(client, player);
    }

    private void runPlanner(MinecraftClient client, ClientPlayerEntity player) {
        runPlanner(client, player, -1);
    }

    private void runPlanner(MinecraftClient client, ClientPlayerEntity player, int onlyCrystalEntityId) {
        if (client.world == null || client.interactionManager == null) return;
        lastCycleTick = client.world.getTime();
        NativeCycleSnapshot snapshot = NativeCycleSnapshotFactory.capture(
                client, player, pendingBase != null, pendingBreakEntityId, onlyCrystalEntityId,
                ignoredPlacementEntityId(client));
        if (snapshot == null) {
            cycleStatus = "No target";
            return;
        }
        ByteBuffer output = XingNativeBridge.cycleOutputBuffer(OUTPUT_CAPACITY);
        int result;
        try {
            result = XingNativeBridge.planCycle(snapshot.encode(), output);
        } catch (LinkageError error) {
            cycleStatus = "Native library unavailable";
            setEnabled(false);
            return;
        }
        if (result != 0) {
            cycleStatus = "Native planner error " + result;
            return;
        }

        NativeCyclePlan plan = NativeCycleSnapshot.decode(output);
        cycleStatus = "Plan " + plan.status() + ", " + plan.placements().size()
                + " placements, break: " + plan.breakReasonName();
        boolean breakSent = false;
        if (plan.breakAction() == 2) {
            Entity entity = client.world.getEntityById(plan.breakCrystalEntityId());
            if (entity instanceof EndCrystalEntity crystal && crystal.isAlive()) {
                breakSent = XingClient.INSTANCE.managers.rotations.withRotation(actionOwner, BREAK_ROTATION_PRIORITY,
                        crystal.getPos().add(0.0, crystal.getHeight() * 0.5, 0.0), ROTATION_HOLD_TICKS,
                        () -> XingClient.INSTANCE.managers.interactions.attackImmediate(actionOwner, BREAK_ACTION_PRIORITY, crystal,
                                InteractionManager.Transport.PACKET, true));
                if (breakSent) {
                    XingClient.INSTANCE.managers.combatActions.markCrystalAction(2);
                    pendingBreakEntityId = crystal.getId();
                    pendingBreakTicks = 0;
                    lastBrokenEntityId = crystal.getId();
                    lastBreakTick = client.world.getTime();
                }
            }
        }
        if (plan.placementRequiresSuccessfulBreak() && !breakSent) return;
        if (plan.action() != 2 || plan.placements().isEmpty()) return;

        for (NativeCyclePlan.Placement placement : plan.placements()) {
            BlockPos base = new BlockPos(placement.base().x(), placement.base().y(), placement.base().z());
            if (!NativeCycleSnapshotFactory.canPlaceNow(client, player, base,
                    ignoredPlacementEntityId(client))) continue;
            BlockHitResult hit = NativeCycleSnapshotFactory.placementHitResult(client.world, player, base);
            if (hit == null) continue;
            boolean placed = XingClient.INSTANCE.managers.rotations.withRotation(actionOwner, PLACE_ROTATION_PRIORITY,
                    hit.getPos(), ROTATION_HOLD_TICKS, () -> XingClient.INSTANCE.managers.interactions.useBlock(
                            actionOwner, PLACE_SLOT_PRIORITY, hit, stack -> stack.isOf(Items.END_CRYSTAL),
                            InventoryManager.SwapMode.CLIENT, 0, InteractionManager.Transport.PACKET, true));
            if (placed) {
                XingClient.INSTANCE.managers.combatActions.markCrystalAction(2);
                pendingBase = base;
                pendingTicks = 0;
                cycleStatus = "Placement sent at " + base.toShortString();
                return;
            }
        }
    }

    private void onWorldBlockUpdate(WorldBlockUpdateEvent event) {
        if (!isEnabled()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null || client.interactionManager == null) return;
        long worldTick = client.world.getTime();
        if (lastPacketReactionTick == worldTick) return;
        lastPacketReactionTick = worldTick;
        client.execute(() -> {
            if (!isEnabled() || client.player == null || client.world == null
                    || client.interactionManager == null || client.world.getTime() != worldTick) {
                return;
            }
            runPlanner(client, client.player);
        });
    }

    private void onCrystalSpawn(CrystalSpawnEvent event) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        EndCrystalEntity crystal = event.crystal();
        if (!isEnabled() || pendingBase == null || player == null || client.world == null
                || client.interactionManager == null || !crystal.isAlive()
                || !crystal.getBlockPos().down().equals(pendingBase)) {
            return;
        }

        pendingBase = null;
        pendingTicks = 0;
        runPlanner(client, player, crystal.getId());
    }

    private int ignoredPlacementEntityId(MinecraftClient client) {
        return XingClient.INSTANCE.settings.autoCrystal.sameTickBreakPlace
                && client.world != null && client.world.getTime() == lastBreakTick
                ? lastBrokenEntityId : -1;
    }

    static boolean hasCrystal(ClientPlayerEntity player) {
        if (player.getOffHandStack().isOf(Items.END_CRYSTAL)
                || player.getMainHandStack().isOf(Items.END_CRYSTAL)) return true;
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(Items.END_CRYSTAL)) return true;
        }
        return false;
    }
}
