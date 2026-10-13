package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.EventBus;
import dev.xingclient.manager.FlightGameAccess;
import dev.xingclient.manager.ActionOwner;
import dev.xingclient.mixin.FireworkRocketEntityAccessor;
import dev.xingclient.nativebridge.XingNativeBridge;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;

/** Lifecycle, snapshots, and Minecraft adapters for native Flight. Author: uint32. */
public final class NativeFlightModule extends Module {
    private static final int RESET = 0, TICK = 1, MOVE = 2, PACKET = 3, VISUAL = 4, DISABLE = 5;
    private static final int EXTERNAL_REQUEST = 6, EXTERNAL_CLEAR = 7;
    private static final int ENABLE_RESET = 8, CORRECTION = 9;
    private final EventBus events;
    private final ByteBuffer input = XingNativeBridge.allocate(65536);
    private final ByteBuffer output = XingNativeBridge.allocate(56);
    private final ByteBuffer queryInput = XingNativeBridge.allocate(65536);
    private final ByteBuffer queryOutput = XingNativeBridge.allocate(56);
    private EventBus.Subscription subscription;
    private FlightGameAccess game;
    private FlightDiagnostics diagnostics;
    private ClientPlayerEntity player;
    private ClientWorld world;
    private int loggedStatus = -1;
    private boolean loggedGliding;
    private long lastDiagnostic;

    public NativeFlightModule(EventBus events) {
        super("flight", "Flight", ModuleCategory.MOVEMENT);
        this.events = events;
    }

    @Override
    protected void onEnable() {
        game = new FlightGameAccess(XingClient.INSTANCE.managers, actionOwner);
        call(hasCurrentWorld() ? ENABLE_RESET : RESET, input, output, false, Vec3d.ZERO, null);
        player = MinecraftClient.getInstance().player;
        world = MinecraftClient.getInstance().world;
        diagnostics = new FlightDiagnostics(events, actionOwner);
        subscription = events.subscribe(ClientTickEvent.class, this::tick);
    }

    @Override
    protected void onDisable() {
        if (subscription != null) { subscription.close(); subscription = null; }
        if (diagnostics != null) { diagnostics.close(); diagnostics = null; }
        var client = MinecraftClient.getInstance();
        if (client.player == player && client.world == world) {
            if (client.player != null) client.player.getAbilities().flying = false;
            call(DISABLE, input, output, false, Vec3d.ZERO, game);
            if (output.getInt(48) > 0 && output.getInt(48) < 8)
                org.slf4j.LoggerFactory.getLogger("xingclient-flight").warn(
                        "Equipment cleanup blocked: nativeStatus={}; inspect chest and inventory", output.getInt(48));
        } else call(RESET, input, output, false, Vec3d.ZERO, null);
    }

    private void tick(ClientTickEvent event) {
        if (event.phase() != ClientTickEvent.Phase.END) return;
        var client = MinecraftClient.getInstance();
        if (client.player != player || client.world != world) {
            if (player != null || world != null) {
                setEnabled(false);
                XingClient.INSTANCE.settings.modules.get(id()).enabled = false;
                XingClient.INSTANCE.changed();
                return;
            }
            call(RESET, input, output, false, Vec3d.ZERO, null);
            XingClient.INSTANCE.managers.rotations.release(actionOwner);
            player = client.player;
            world = client.world;
        }
        call(TICK, input, output, true, Vec3d.ZERO, game);
        diagnostics.tick();
        int state = output.getInt(48);
        boolean gliding = client.player != null && client.player.isGliding();
        long now = System.nanoTime();
        if (state != loggedStatus || gliding != loggedGliding || now - lastDiagnostic >= 5_000_000_000L) {
            org.slf4j.LoggerFactory.getLogger("xingclient-flight").info(
                "mode={}, path=hotbar-swap-rocket-climb, status={}, gliding={}, grounded={}, safeInventory={}, velocity={}, height={}",
                XingClient.INSTANCE.settings.flight.mode, status(), gliding,
                client.player != null && client.player.isOnGround(), FlightGameAccess.safeInventoryState(client),
                client.player == null ? Vec3d.ZERO : client.player.getVelocity(),
                client.player == null ? 0 : client.player.getY());
            loggedStatus = state;
            loggedGliding = gliding;
            lastDiagnostic = now;
        }
    }

    public void onServerCorrection() {
        if (!isEnabled() || !hasCurrentWorld()) return;
        call(CORRECTION, queryInput, queryOutput, false, Vec3d.ZERO, null);
    }

    public Vec3d applyMovement(Vec3d movement) {
        if (!hasCurrentWorld()) return movement;
        call(MOVE, queryInput, queryOutput, false, movement, null);
        return new Vec3d(queryOutput.getDouble(24), queryOutput.getDouble(32), queryOutput.getDouble(40));
    }

    public void requestExternalMovement(ActionOwner owner, Vec3d movement) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (player != client.player || world != client.world) {
            call(RESET, input, output, false, Vec3d.ZERO, null);
            player = client.player;
            world = client.world;
        }
        snapshot(queryInput, false, movement);
        queryInput.putLong(160, java.util.Objects.requireNonNull(owner).id());
        dispatch(EXTERNAL_REQUEST, queryInput, queryOutput, null);
    }

    public void clearExternalMovement(ActionOwner owner) {
        if (!hasCurrentWorld()) return;
        snapshot(queryInput, false, Vec3d.ZERO);
        queryInput.putLong(160, java.util.Objects.requireNonNull(owner).id());
        dispatch(EXTERNAL_CLEAR, queryInput, queryOutput, null);
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
            case 4 -> "Waiting for inventory / click budget";
            case 5 -> "Chest swap failed";
            default -> XingClient.INSTANCE.settings.flight.mode + " / uint32";
        };
    }

    private boolean hasCurrentWorld() {
        var client = MinecraftClient.getInstance();
        return player != null && client.player == player && client.world == world;
    }

    private void call(int event, ByteBuffer request, ByteBuffer reply, boolean items, Vec3d movement, FlightGameAccess access) {
        if (!MinecraftClient.getInstance().isOnThread()) throw new IllegalStateException("Flight must run on the client thread");
        snapshot(request, items, movement, event == TICK);
        if (event == DISABLE && dev.xingclient.manager.InventoryManager.safeEquipmentState(MinecraftClient.getInstance()))
            request.putInt(4, request.getInt(4) | 32);
        dispatch(event, request, reply, access);
    }

    private static void dispatch(int event, ByteBuffer request, ByteBuffer reply, FlightGameAccess access) {
        if (!MinecraftClient.getInstance().isOnThread()) throw new IllegalStateException("Flight must run on the client thread");
        int status = XingNativeBridge.flight(event, request, reply, access);
        if (status != 0) throw new IllegalStateException("Native Flight error " + status);
        if (reply.getInt(0) != 7) throw new IllegalStateException("Unsupported native Flight version");
    }

    private static void snapshot(ByteBuffer buffer, boolean items, Vec3d movement) {
        snapshot(buffer, items, movement, false);
    }

    private static void snapshot(ByteBuffer buffer, boolean items, Vec3d movement, boolean trackRockets) {
        var client = MinecraftClient.getInstance();
        var player = client.player;
        var settings = XingClient.INSTANCE.settings.flight;
        settings.normalize();
        buffer.clear();
        for (int offset = 0; offset < 256; offset += 8) buffer.putLong(offset, 0);
        buffer.putInt(0, 7);
        var rotation = XingClient.INSTANCE.managers.rotations.serverRotation();
        int flags = client.world != null && player != null && client.interactionManager != null && client.getNetworkHandler() != null ? 1 : 0;
        if (player != null) {
            if (player.isAlive()) flags |= 2;
            if (player.isOnGround()) flags |= 4;
            if (player.isGliding()) flags |= 8;
            if (player.getEquippedStack(EquipmentSlot.CHEST).contains(DataComponentTypes.GLIDER)) flags |= 16;
            if (FlightGameAccess.safeInventoryState(client)) flags |= 32;
            if (player.input.getMovementInput().lengthSquared() != 0) flags |= 128;
            var camera = settings.mode == dev.xingclient.ClientSettings.FlightSettings.Mode.CRASHOUT
                    ? XingClient.INSTANCE.managers.rotations.cameraRotation(player) : null;
            buffer.putFloat(24, camera == null ? player.getYaw() : camera.yaw());
            buffer.putFloat(28, camera == null ? player.getPitch() : camera.pitch());
            var movementInput = player.input.getMovementInput();
            buffer.putFloat(136, movementInput.x); buffer.putFloat(140, movementInput.y);
            buffer.putInt(144, player.age);
            buffer.putInt(172, player.getInventory().getSelectedSlot());
            var velocity = player.getVelocity();
            buffer.putDouble(184, velocity.x); buffer.putDouble(192, velocity.y); buffer.putDouble(200, velocity.z);
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
        buffer.putInt(100, settings.mode.ordinal());
        buffer.putInt(108, (settings.antiKick ? 1 : 0) | (settings.pitch ? 2 : 0)
                | (settings.firework ? 4 : 0) | (settings.idleFlipFlop ? 8 : 0));
        buffer.putInt(112, settings.flipFlopTicks);
        buffer.putDouble(120, settings.horizontal); buffer.putDouble(128, settings.vertical);
        buffer.putInt(168, XingClient.INSTANCE.flight.isEnabled() ? 1 : 0);
        buffer.putDouble(176, settings.crashoutHorizontal);
        buffer.position(256);
        if (items && player != null) {
            for (int slot = 0; slot < 36; slot++) writeItem(buffer, player.getInventory().getStack(slot));
            writeItem(buffer, player.getEquippedStack(EquipmentSlot.CHEST));
        }
        int rocketCount = 0;
        if (trackRockets && client.world != null && player != null
                && settings.mode == dev.xingclient.ClientSettings.FlightSettings.Mode.GRIM && settings.firework) {
            for (var entity : client.world.getEntities()) {
                if (!(entity instanceof FireworkRocketEntity rocket) || !rocket.isAlive()
                        || ((FireworkRocketEntityAccessor) rocket).xing$getShooter() != player) continue;
                var fireworks = rocket.getStack().get(DataComponentTypes.FIREWORKS);
                if (buffer.remaining() < 8) throw new IllegalStateException("Too many attached fireworks for Flight snapshot");
                buffer.putInt(((FireworkRocketEntityAccessor) rocket).xing$getLife());
                buffer.putInt(fireworks == null ? 1 : fireworks.flightDuration());
                ++rocketCount;
            }
        }
        buffer.putInt(156, rocketCount);
    }

    private static void writeItem(ByteBuffer buffer, ItemStack stack) {
        var equipment = stack.get(DataComponentTypes.EQUIPPABLE);
        var fireworks = stack.get(DataComponentTypes.FIREWORKS);
        byte[] path = Registries.ITEM.getId(stack.getItem()).getPath().getBytes(StandardCharsets.UTF_8);
        int flags = (stack.isEmpty() ? 1 : 0) | (stack.isOf(Items.ELYTRA) ? 2 : 0) | (stack.isOf(Items.FIREWORK_ROCKET) ? 4 : 0)
                | (stack.isDamageable() ? 8 : 0) | (equipment != null && equipment.slot() == EquipmentSlot.CHEST ? 16 : 0)
                | (stack.contains(DataComponentTypes.GLIDER) ? 32 : 0);
        buffer.putInt(flags).putInt(stack.getDamage()).putInt(stack.getMaxDamage())
                .putInt(fireworks == null ? 1 : fireworks.flightDuration()).putInt(path.length).put(path);
    }
}
