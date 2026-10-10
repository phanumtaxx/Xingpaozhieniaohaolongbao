package dev.xingclient.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Items;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Passive comparison of manual and automated pauses. Author: uint32. */
final class MaceDiagnostics {
    private static final Logger LOGGER = LoggerFactory.getLogger("xingclient-mace");
    private record Snapshot(Vec3d position, Vec3d velocity, boolean onGround, double localFallDistance) {
        static Snapshot capture(ClientPlayerEntity player) {
            return new Snapshot(player.getPos(), player.getVelocity(), player.isOnGround(), player.fallDistance);
        }
    }

    private ClientPlayerEntity observedPlayer;
    private ClientWorld observedWorld;
    private long lastTickMillis;
    private Snapshot lastTick;
    private Snapshot beforeCorrection;
    private ClientPlayerEntity correctionPlayer;

    void tick(boolean automated) {
        var mc = MinecraftClient.getInstance();
        if (!observing(mc.player, automated) || mc.world == null) {
            observedPlayer = null;
            observedWorld = null;
            lastTick = null;
            return;
        }
        long now = Util.getMeasuringTimeMs();
        Snapshot current = Snapshot.capture(mc.player);
        if (lastTick != null && observedPlayer == mc.player && observedWorld == mc.world
                && now - lastTickMillis >= 250) {
            LOGGER.info("Observed client tick gap: automated={}, durationMs={}, before={}, after={}",
                    automated, now - lastTickMillis, lastTick, current);
        }
        observedPlayer = mc.player;
        observedWorld = mc.world;
        lastTickMillis = now;
        lastTick = current;
    }

    void beforeCorrection(boolean automated) {
        var mc = MinecraftClient.getInstance();
        if (!mc.isOnThread()) return;
        beforeCorrection = null;
        correctionPlayer = null;
        if (!automated && observing(mc.player, false)) {
            beforeCorrection = Snapshot.capture(mc.player);
            correctionPlayer = mc.player;
        }
    }

    void correctionApplied() {
        var player = MinecraftClient.getInstance().player;
        if (beforeCorrection != null && player == correctionPlayer) {
            LOGGER.info("Manual server correction: before={}, after={}",
                    beforeCorrection, Snapshot.capture(player));
        }
        beforeCorrection = null;
        correctionPlayer = null;
    }

    void velocityApplied(boolean automated) {
        var player = MinecraftClient.getInstance().player;
        if (!automated && observing(player, false))
            LOGGER.info("Manual server velocity applied: {}", Snapshot.capture(player));
    }

    private static boolean observing(ClientPlayerEntity player, boolean automated) {
        return player != null && (automated || player.getMainHandStack().isOf(Items.MACE)
                || player.getOffHandStack().isOf(Items.MACE));
    }
}
