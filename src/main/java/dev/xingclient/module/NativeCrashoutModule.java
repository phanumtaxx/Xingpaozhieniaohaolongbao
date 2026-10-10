package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.EventBus;
import dev.xingclient.manager.FlightGameAccess;
import dev.xingclient.nativebridge.XingNativeBridge;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

/** Lifecycle, snapshots, and Minecraft adapters for native Crashout. Author: uint32. */
public final class NativeCrashoutModule extends Module {
    private static final int RESET = 0, TICK = 1, MOVE = 2, PACKET = 3, VISUAL = 4, DISABLE = 5;
    private final EventBus events;
    private final ByteBuffer input = XingNativeBridge.allocate(65536);
    private final ByteBuffer output = XingNativeBridge.allocate(64);
    private final ByteBuffer queryInput = XingNativeBridge.allocate(65536);
    private final ByteBuffer queryOutput = XingNativeBridge.allocate(64);
    private EventBus.Subscription subscription;
    private FlightGameAccess game;
    private ClientPlayerEntity player;
    private ClientWorld world;

    public NativeCrashoutModule(EventBus events) {
        super("crashout", "Crashout", ModuleCategory.MOVEMENT);
        this.events = events;
    }

    @Override
    protected void onEnable() {
        game = new FlightGameAccess(XingClient.INSTANCE.managers, actionOwner);
        call(RESET, input, output, false, Vec3d.ZERO, null);
        player = MinecraftClient.getInstance().player;
        world = MinecraftClient.getInstance().world;
        subscription = events.subscribe(ClientTickEvent.class, this::tick);
    }

    @Override
    protected void onDisable() {
        if (subscription != null) { subscription.close(); subscription = null; }
        var client = MinecraftClient.getInstance();
        if (client.player == player && client.world == world) {
            if (client.player != null) client.player.getAbilities().flying = false;
            call(DISABLE, input, output, false, Vec3d.ZERO, game);
        } else call(RESET, input, output, false, Vec3d.ZERO, null);
        player = null;
        world = null;
    }

    private void tick(ClientTickEvent event) {
        if (event.phase() != ClientTickEvent.Phase.END) return;
        var client = MinecraftClient.getInstance();
        if (client.player != player || client.world != world) {
            call(RESET, input, output, false, Vec3d.ZERO, null);
            XingClient.INSTANCE.managers.rotations.release(actionOwner);
            player = client.player;
            world = client.world;
        }
        call(TICK, input, output, true, Vec3d.ZERO, game);
    }

    public Vec3d applyMovement(Vec3d movement) {
        if (!isEnabled() || !hasCurrentWorld()) return movement;
        call(MOVE, queryInput, queryOutput, false, movement, null);
        return new Vec3d(queryOutput.getDouble(24), queryOutput.getDouble(32), queryOutput.getDouble(40));
    }

    public boolean allowMovementPacket() {
        if (!isEnabled() || !hasCurrentWorld()) return true;
        call(PACKET, queryInput, queryOutput, false, Vec3d.ZERO, null);
        return queryOutput.getInt(4) != 0;
    }

    public boolean hidePose() {
        if (!isEnabled() || !hasCurrentWorld()) return false;
        call(VISUAL, queryInput, queryOutput, false, Vec3d.ZERO, null);
        return queryOutput.getInt(8) != 0;
    }

    public ItemStack visualChestplate() {
        if (!isEnabled() || !hasCurrentWorld()) return ItemStack.EMPTY;
        call(VISUAL, queryInput, queryOutput, true, Vec3d.ZERO, null);
        int slot = queryOutput.getInt(16);
        return queryOutput.getInt(12) != 0 && slot >= 0 && MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.getInventory().getStack(slot) : ItemStack.EMPTY;
    }

    @Override
    public String status() {
        if (!isEnabled()) return "Disabled / uint32";
        return switch (output.getInt(48)) {
            case 1 -> "No player / world";
            case 2 -> "Needs a usable Elytra";
            case 3 -> "Close the inventory / GUI";
            case 4 -> "Inventory busy";
            case 5 -> "Chest swap failed";
            default -> "Native flight / uint32";
        };
    }

    private boolean hasCurrentWorld() {
        var client = MinecraftClient.getInstance();
        return player != null && client.player == player && client.world == world;
    }

    private void call(int event, ByteBuffer request, ByteBuffer reply, boolean items, Vec3d movement, FlightGameAccess access) {
        if (!MinecraftClient.getInstance().isOnThread()) throw new IllegalStateException("Flight must run on the client thread");
        snapshot(request, items, movement);
        int status = XingNativeBridge.crashout(event, request, reply, access);
        if (status != 0) throw new IllegalStateException("Native Crashout error " + status);
        if (reply.getInt(0) != 1) throw new IllegalStateException("Unsupported native Crashout version");
    }

    private static void snapshot(ByteBuffer buffer, boolean items, Vec3d movement) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        var settings = XingClient.INSTANCE.settings.crashout;
        settings.normalize();
        buffer.clear();
        for (int offset = 0; offset < 128; offset += 8) buffer.putLong(offset, 0);
        buffer.putInt(0, 1);
        var rotation = XingClient.INSTANCE.managers.rotations.serverRotation();
        int flags = client.world != null && player != null && client.interactionManager != null && client.getNetworkHandler() != null ? 1 : 0;
        if (player != null) {
            if (player.isAlive()) flags |= 2;
            if (player.isOnGround()) flags |= 4;
            if (player.isGliding()) flags |= 8;
            if (player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER)) flags |= 16;
            if (FlightGameAccess.safeInventoryState(client)) flags |= 32;
            if (player.input.getMovementInput().lengthSquared() != 0) flags |= 128;
            buffer.putFloat(24, player.getYaw()); buffer.putFloat(28, player.getPitch());
        }
        if (rotation.known()) flags |= 64;
        buffer.putInt(4, flags);
        buffer.putInt(8, (client.options.forwardKey.isPressed() ? 1 : 0) | (client.options.backKey.isPressed() ? 2 : 0)
                | (client.options.leftKey.isPressed() ? 4 : 0) | (client.options.rightKey.isPressed() ? 8 : 0)
                | (client.options.jumpKey.isPressed() ? 16 : 0) | (client.options.sneakKey.isPressed() ? 32 : 0));
        buffer.putInt(12, settings.flipFlop.ordinal()); buffer.putLong(16, System.currentTimeMillis());
        buffer.putFloat(32, rotation.yaw()); buffer.putFloat(36, rotation.pitch());
        buffer.putFloat(40, (float) settings.turnSpeed); buffer.putInt(44, settings.packetGap);
        buffer.putDouble(48, settings.safetyMargin); buffer.putInt(56, settings.inventoryFireworks ? 1 : 0);
        buffer.putInt(60, settings.hideFlyPose ? 1 : 0); buffer.putInt(64, settings.spoofChestplate ? 1 : 0);
        buffer.putDouble(72, movement.x); buffer.putDouble(80, movement.y); buffer.putDouble(88, movement.z);
        buffer.putInt(96, items && player != null ? 37 : 0);
        buffer.position(128);
        if (items && player != null) {
            for (int slot = 0; slot < 36; slot++) writeItem(buffer, player.getInventory().getStack(slot));
            writeItem(buffer, player.getEquippedStack(EquipmentSlot.CHEST));
        }
    }

    private static void writeItem(ByteBuffer buffer, ItemStack stack) {
        var equipment = stack.get(DataComponentTypes.EQUIPPABLE);
        var fireworks = stack.get(DataComponentTypes.FIREWORKS);
        byte[] path = Registries.ITEM.getId(stack.getItem()).getPath().getBytes(StandardCharsets.UTF_8);
        int flags = (stack.isEmpty() ? 1 : 0) | (stack.isOf(Items.ELYTRA) ? 2 : 0) | (stack.isOf(Items.FIREWORK_ROCKET) ? 4 : 0)
                | (stack.isDamageable() ? 8 : 0) | (equipment != null && equipment.slot() == EquipmentSlot.CHEST ? 16 : 0);
        buffer.putInt(flags).putInt(stack.getDamage()).putInt(stack.getMaxDamage())
                .putInt(fireworks == null ? 1 : fireworks.flightDuration()).putInt(path.length).put(path);
    }
}
