package dev.xingclient.manager;

import net.minecraft.util.math.BlockPos;

/** Java adapter for native crystal placement, spawn, and retry state. Author: uint32. */
public final class CrystalActionState {
    public record State(BlockPos pendingBase, int pendingTargetId, int pendingAge, int spawnedCrystalId, int deadAge) {
        public boolean hasPendingPlacement() { return pendingBase != null; }
        public boolean isDeadOnTick() { return deadAge == 0; }
    }
    private final NativeManagerBridge bridge;

    CrystalActionState(NativeManagerBridge bridge) { this.bridge = bridge; }

    public void markAttack(int entityId, int retryTicks) {
        bridge.crystalAttack(NativeManagerBridge.CRYSTAL_ATTACK, entityId, retryTicks);
    }

    public boolean attackCoolingDown(int entityId) {
        return bridge.crystalAttack(NativeManagerBridge.CRYSTAL_ATTACK_COOLING_DOWN, entityId, 0).accepted();
    }

    public State state() { return bridge.call(NativeManagerBridge.CRYSTAL_READ, null, -1).crystal(); }

    public void markPlaced(BlockPos base, int targetId, int timeoutTicks) {
        bridge.crystalPosition(NativeManagerBridge.CRYSTAL_PLACED, base, targetId, timeoutTicks);
    }

    public void markSpawned(BlockPos base, int entityId) {
        bridge.crystalPosition(NativeManagerBridge.CRYSTAL_SPAWNED, base, entityId);
    }

    public void markBroken() { bridge.call(NativeManagerBridge.CRYSTAL_BROKEN, null, -1); }

    public void clearPending() { bridge.call(NativeManagerBridge.CRYSTAL_CLEAR_PENDING, null, -1); }

    public boolean beginPacketReaction(long tick) { return bridge.crystalPacketReaction(tick); }

    public boolean markRemoved(int entityId) {
        return bridge.crystalAttack(NativeManagerBridge.CRYSTAL_REMOVED, entityId, 0).accepted();
    }

    public int replacementCrystalId(boolean sameTickBreakPlace) {
        var result = bridge.crystalAttack(NativeManagerBridge.CRYSTAL_REPLACEMENT_READY,
                -1, sameTickBreakPlace ? 1 : 0);
        return result.accepted() ? result.crystal().spawnedCrystalId() : -1;
    }

    public void reset() {
        if (bridge.used()) bridge.call(NativeManagerBridge.CRYSTAL_RESET, null, -1);
    }

    void tick() {
        if (bridge.used()) bridge.call(NativeManagerBridge.CRYSTAL_TICK, null, -1);
    }
}
