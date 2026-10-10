package dev.xingclient.manager;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.util.math.Vec3d;

/** Minecraft adapter for native rotation ownership and hold requests. */
public final class RotationManager {
    private final NativeManagerBridge bridge;
    private final NetworkManager network;
    private final CombatActionScheduler scheduler;
    private final ActionOwner restoreOwner = new ActionOwner("Rotation restore");
    private boolean active;
    private float yaw;
    private float pitch;

    RotationManager(NativeManagerBridge bridge, NetworkManager network, CombatActionScheduler scheduler) {
        this.bridge = bridge;
        this.network = network;
        this.scheduler = scheduler;
    }

    public boolean withRotation(ActionOwner owner, int priority, Vec3d target, int holdTicks,
            BooleanSupplier action) {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(target);
        Objects.requireNonNull(action);
        var player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        if (!scheduler.reserve(owner, priority, holdTicks, ActionResource.ROTATION)) return false;
        Vec3d direction = target.subtract(player.getEyePos());
        var result = bridge.requestRotation(owner, priority, direction, holdTicks);
        update(result);
        if (!result.accepted()) {
            scheduler.release(owner, ActionResource.ROTATION);
            return false;
        }
        if (!network.send(owner, new PlayerMoveC2SPacket.LookAndOnGround(
                yaw, pitch, player.isOnGround(), player.horizontalCollision))) {
            release(owner);
            return false;
        }
        return action.getAsBoolean();
    }

    public Packet<?> apply(Packet<?> packet) {
        if (!active || !(packet instanceof PlayerMoveC2SPacket move)) return packet;
        if (move.changesPosition()) {
            return new PlayerMoveC2SPacket.Full(move.getX(0), move.getY(0), move.getZ(0),
                    yaw, pitch, move.isOnGround(), move.horizontalCollision());
        }
        return new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch,
                move.isOnGround(), move.horizontalCollision());
    }

    public void release(ActionOwner owner) {
        Objects.requireNonNull(owner);
        scheduler.release(owner, ActionResource.ROTATION);
        boolean wasActive = active;
        if (bridge.used()) update(bridge.call(NativeManagerBridge.ROTATION_RELEASE, owner, -1));
        if (wasActive && !active) restoreCameraRotation();
    }

    public boolean isOwnedBy(ActionOwner owner) {
        Objects.requireNonNull(owner);
        return bridge.used() && bridge.call(NativeManagerBridge.ROTATION_OWNERSHIP, owner, -1).accepted();
    }

    void tick() {
        boolean wasActive = active;
        if (bridge.used()) update(bridge.call(NativeManagerBridge.ROTATION_TICK, null, -1));
        if (wasActive && !active) restoreCameraRotation();
    }

    void corrected() {
        if (bridge.used()) update(bridge.call(NativeManagerBridge.ROTATION_RELEASE, null, -1));
        active = false;
    }

    private void restoreCameraRotation() {
        var player = MinecraftClient.getInstance().player;
        if (player != null) network.send(restoreOwner, new PlayerMoveC2SPacket.LookAndOnGround(
                player.getYaw(), player.getPitch(), player.isOnGround(), player.horizontalCollision));
    }

    void reset() { active = false; }
    private void update(NativeManagerBridge.Result result) {
        active = result.rotationActive();
        yaw = result.yaw();
        pitch = result.pitch();
    }
}
