package dev.xingclient.manager;

import dev.xingclient.ClientSettings;
import dev.xingclient.XingClient;
import dev.xingclient.nativebridge.XingNativeBridge;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerPosition;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.LoggerFactory;

/** Java adapter for native velocity, correction, and phase state. Author: uint32. */
public final class MovementStateManager {
    public record Result(int flags, Vec3d movement, float yaw, float pitch, int blockX, int blockY, int blockZ) {
        public boolean cancel() { return (flags & 1) != 0; }
        public boolean apply() { return (flags & 2) != 0; }
        public boolean confirm() { return (flags & 4) != 0; }
        public boolean positionOnly() { return (flags & 8) != 0; }
        public boolean cancelPush() { return (flags & 16) != 0; }
    }
    public record Correction(long sequence, int teleportId, Vec3d position, boolean expected) {}
    private final ByteBuffer input = XingNativeBridge.allocate(320);
    private final ByteBuffer output = XingNativeBridge.allocate(112);
    private final MovementWorldAccess worldAccess = new MovementWorldAccess();
    private ClientWorld world;
    private long worldEpoch;
    private boolean velocityResetPending;

    public void resetVelocity() {
        velocityResetPending = true;
        if (MinecraftClient.getInstance().isOnThread()) applyPendingVelocityReset();
    }

    private void applyPendingVelocityReset() {
        if (!velocityResetPending) return;
        velocityResetPending = false;
        call(0, Vec3d.ZERO, ignored -> {});
    }
    public Result tickVelocity() { return call(1, Vec3d.ZERO, ignored -> {}); }
    public Result receive(Packet<?> packet) {
        return call(2, packet instanceof EntityVelocityUpdateS2CPacket motion
                ? new Vec3d(motion.getVelocityX(), motion.getVelocityY(), motion.getVelocityZ()) : Vec3d.ZERO, request -> {
            var player = MinecraftClient.getInstance().player;
            if (packet instanceof EntityVelocityUpdateS2CPacket motion) {
                request.putInt(128, 1);
                request.putInt(132, player != null && motion.getEntityId() == player.getId() ? 1 : 0);
            } else if (packet instanceof PlayerPositionLookS2CPacket correction) {
                request.putInt(128, 2); request.putInt(216, correction.teleportId());
                var baseline = new PlayerPosition(player == null ? Vec3d.ZERO : player.getPos(), Vec3d.ZERO, 0, 0);
                putVector(request, 224, PlayerPosition.apply(baseline, correction.change(), correction.relatives()).position());
            }
        });
    }
    public Vec3d explosion(Vec3d knockback) { return call(3, knockback, ignored -> {}).movement(); }
    public boolean cancelBlockPush() { return call(4, Vec3d.ZERO, ignored -> {}).cancelPush(); }
    public void markPhasePearl() { call(5, Vec3d.ZERO, ignored -> {}); }
    public void expectTeleport(long timeoutMillis) {
        call(6, Vec3d.ZERO, request -> request.putLong(280, Math.max(0, timeoutMillis)));
    }
    public void markRecentPhase() {
        call(7, Vec3d.ZERO, request -> { request.putInt(280, 40); request.putInt(284, 0); });
    }
    public void markRecentPhase(int ticks) {
        call(7, Vec3d.ZERO, request -> { request.putInt(280, Math.max(0, ticks)); request.putInt(284, 1); });
    }
    public void requestPhaseAssist(ActionOwner owner, int ticks, boolean stopOnSetback, long setbackPauseMillis) {
        call(8, Vec3d.ZERO, request -> {
            request.putLong(272, owner.id()); request.putInt(280, ticks);
            request.putInt(284, stopOnSetback ? 1 : 0); request.putLong(288, Math.max(0, setbackPauseMillis));
        });
    }
    public void clearPhaseAssist(ActionOwner owner) {
        call(9, Vec3d.ZERO, request -> request.putLong(272, owner.id()));
    }
    public Correction latestCorrection() {
        call(10, Vec3d.ZERO, ignored -> {});
        return new Correction(output.getLong(80), output.getInt(76), readVector(output, 88), output.getInt(72) != 0);
    }
    public boolean confirmationCircuitOpen() { return output.getInt(64) != 0; }

    private Result call(int event, Vec3d incoming, Consumer<ByteBuffer> fields) {
        CombatActionScheduler.requireClientThread();
        applyPendingVelocityReset();
        snapshot(incoming);
        fields.accept(input);
        int status = XingNativeBridge.velocity(event, input, output, worldAccess);
        if (status != 0) throw new IllegalStateException("Native Velocity error " + status);
        if (output.getInt(0) != 1) throw new IllegalStateException("Unsupported native Velocity version");
        if ((output.getInt(4) & 32) != 0) logDecision();
        return new Result(output.getInt(4), readVector(output, 24), output.getFloat(16), output.getFloat(20),
                output.getInt(48), output.getInt(52), output.getInt(56));
    }

    private void snapshot(Vec3d incoming) {
        var client = MinecraftClient.getInstance();
        var app = XingClient.INSTANCE;
        var player = client.player;
        var settings = app.settings.velocity;
        settings.normalize();
        if (world != client.world) { world = client.world; ++worldEpoch; }
        for (int offset = 0; offset < input.capacity(); offset += 8) input.putLong(offset, 0);
        input.putInt(0, 1);
        int flags = player != null && client.world != null ? 1 : 0;
        if (app.velocity != null && app.velocity.isEnabled()) flags |= 64;
        if (client.getNetworkHandler() != null) flags |= 16;
        if (player != null) {
            if (player.isTouchingWater()) flags |= 2;
            if (player.isInLava()) flags |= 4;
            if (player.isGliding()) flags |= 8;
            if (player.input != null) {
                flags |= 32;
                var movement = player.input.getMovementInput();
                input.putFloat(40, movement.x); input.putFloat(44, movement.y);
                var keys = player.input.playerInput;
                input.putInt(56, (keys.forward() ? 1 : 0) | (keys.backward() ? 2 : 0) | (keys.left() ? 4 : 0)
                        | (keys.right() ? 8 : 0) | (keys.jump() ? 16 : 0));
            }
            input.putFloat(48, player.getYaw()); input.putFloat(52, player.getPitch());
            var box = player.getBoundingBox();
            input.putDouble(136, box.minX); input.putDouble(144, box.minY); input.putDouble(152, box.minZ);
            input.putDouble(160, box.maxX); input.putDouble(168, box.maxY); input.putDouble(176, box.maxZ);
            putVector(input, 184, player.getPos()); putVector(input, 248, player.getVelocity());
        }
        input.putInt(4, flags); input.putInt(8, settings.mode.ordinal()); input.putInt(12, options(settings));
        input.putLong(16, world == null ? Long.MIN_VALUE : world.getTime());
        input.putLong(24, System.currentTimeMillis()); input.putLong(32, worldEpoch);
        input.putDouble(64, settings.horizontal); input.putDouble(72, settings.vertical); input.putDouble(80, settings.lagPauseMillis);
        input.putInt(88, settings.clippedGraceTicks); input.putInt(92, settings.motionMode.ordinal()); input.putDouble(96, settings.nearDistance);
        putVector(input, 104, incoming);
        var rotation = app.managers.rotations.serverRotation();
        input.putFloat(208, rotation.known() ? rotation.yaw() : player == null ? 0 : player.getYaw());
        input.putFloat(212, rotation.known() ? rotation.pitch() : player == null ? 0 : player.getPitch());
    }
    private static int options(ClientSettings.VelocitySettings settings) {
        return (settings.cancelAll ? 1 : 0) | (settings.redirect ? 2 : 0) | (settings.walls ? 4 : 0)
                | (settings.noRotation ? 8 : 0) | (settings.whileLiquid ? 16 : 0) | (settings.whileElytra ? 32 : 0)
                | (settings.explosions ? 64 : 0) | (settings.phaseLock ? 128 : 0) | (settings.blockPush ? 256 : 0)
                | (settings.onlyIntersecting ? 512 : 0) | (settings.lenient ? 1024 : 0)
                | (settings.requireAssist ? 2048 : 0) | (settings.requireRecent ? 4096 : 0)
                | (settings.pushDebug ? 8192 : 0) | (settings.debug ? 16384 : 0);
    }
    private void logDecision() {
        String message = "[Velocity] " + switch (output.getInt(8)) {
            case 1 -> "NCP motion scaled";
            case 2 -> "Grim V3 entity motion canceled";
            case 3 -> "Post-correction motion drain";
            case 4 -> "Explosion motion scaled";
            case 6 -> "Setback pause";
            case 7 -> "Liquid: explosion allowed";
            case 8 -> "Elytra: explosion allowed";
            case 9 -> "Velocity confirmation sent";
            case 10 -> "Correction feedback: confirmation circuit opened";
            case 11 -> "Phase block push canceled";
            default -> "Motion allowed";
        };
        LoggerFactory.getLogger("xingclient-velocity").info("{} motion={} circuit={} drain={}",
                message, readVector(output, 24), output.getInt(64) != 0, output.getInt(68));
        var player = MinecraftClient.getInstance().player;
        if (player != null) player.sendMessage(Text.literal(message), false);
    }
    private static void putVector(ByteBuffer buffer, int offset, Vec3d vector) {
        buffer.putDouble(offset, vector.x); buffer.putDouble(offset + 8, vector.y); buffer.putDouble(offset + 16, vector.z);
    }
    private static Vec3d readVector(ByteBuffer buffer, int offset) {
        return new Vec3d(buffer.getDouble(offset), buffer.getDouble(offset + 8), buffer.getDouble(offset + 16));
    }
}
