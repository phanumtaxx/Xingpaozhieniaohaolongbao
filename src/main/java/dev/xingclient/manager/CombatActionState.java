package dev.xingclient.manager;

/** Minecraft adapter for shared native combat activity windows. Author: uint32. */
public final class CombatActionState {
    public enum Activity { CRYSTAL, PACKET_MINE, GENERIC }
    public record State(int crystalTicks, int packetMineTicks, int genericTicks) {
        public boolean active() { return crystalTicks > 0 || packetMineTicks > 0 || genericTicks > 0; }
    }

    private final NativeManagerBridge bridge;

    CombatActionState(NativeManagerBridge bridge) { this.bridge = bridge; }

    public void markCrystalAction(int ticks) { mark(Activity.CRYSTAL, ticks); }
    public void markPacketMineAction(int ticks) { mark(Activity.PACKET_MINE, ticks); }
    public void markCombatAction(int ticks) { mark(Activity.GENERIC, ticks); }
    public boolean isCrystalActionActive() { return state().crystalTicks() > 0; }
    public boolean isPacketMineActionActive() { return state().packetMineTicks() > 0; }
    public boolean isCombatActionActive() { return state().active(); }

    public State state() { return bridge.call(NativeManagerBridge.ACTIVITY_READ, null, -1).activity(); }
    public void clearAll() { bridge.call(NativeManagerBridge.ACTIVITY_CLEAR, null, -1); }
    void tick() { if (bridge.used()) bridge.call(NativeManagerBridge.ACTIVITY_TICK, null, -1); }

    private void mark(Activity activity, int ticks) { bridge.markActivity(activity.ordinal(), ticks); }
}
