package dev.xingclient.module;

/** Flight stages; HELD schedules the native pause at the end of the tick. */
public final class MaceSequence {
    public enum Phase { IDLE, PREPARE, CHARGING, ASCENDING, FALLING, HELD }
    private Phase phase = Phase.IDLE;
    private int ticks, shots, impulses, lastShot = -1000, lastImpulse = -1000;
    private double peak, fallen;
    private boolean stallCompleted;
    private String message = "Disabled";

    public Phase phase() { return phase; }
    public int shots() { return shots; }
    public int impulses() { return impulses; }
    public double fallen() { return fallen; }
    public String message() { return message; }
    @Deprecated public boolean frozen() { return false; }
    public boolean stallCompleted() { return stallCompleted; }
    public void start() { phase = Phase.PREPARE; ticks = shots = impulses = 0; fallen = 0; stallCompleted = false; lastShot = lastImpulse = -1000; message = "Close GUI to begin"; }
    public void charge(double y) { phase = Phase.CHARGING; ticks = 0; peak = y; message = "Charging"; }
    public void tick() { ticks++; }
    public int ticks() { return ticks; }
    public boolean shotDue(int count, int interval) { return phase == Phase.CHARGING && shots < count && shots == impulses && ticks - lastShot >= interval; }
    public void shot() { shots++; lastShot = ticks; }
    public boolean waitingForImpulse() { return phase == Phase.CHARGING && shots > impulses && ticks - lastShot <= 40; }
    public boolean impulse() { if (!waitingForImpulse()) return false; impulses++; lastImpulse = ticks; return true; }
    public boolean readyToLaunch(int count) { return phase == Phase.CHARGING && shots == count && impulses == shots && ticks - lastImpulse >= 4; }
    public void observeHeight(double y) { peak = Math.max(peak, y); }
    public void resetFlightHeight(double y) { peak = y; fallen = 0; }
    public void launch(double y) { phase = Phase.ASCENDING; ticks = 0; observeHeight(y); fallen = 0; message = "Tracking flight"; }
    public void flight(double y, double velocityY, double groundGap, double holdHeight, double minFall) {
        if (phase != Phase.ASCENDING && phase != Phase.FALLING) return;
        peak = Math.max(peak, y);
        fallen = Math.max(fallen, peak - y);
        if (velocityY < -0.01) { phase = Phase.FALLING; message = "Falling"; }
        if (!stallCompleted && phase == Phase.FALLING && groundGap >= minFall
                && Double.isFinite(groundGap)) {
            phase = Phase.HELD; ticks = 0; message = "Airborne pause pending";
        }
    }
    public void resumeFlight() {
        stallCompleted = true;
        phase = Phase.FALLING;
        ticks = 0;
        message = "Resumed; attack while falling";
    }
    @Deprecated
    public boolean heartbeatDue(int interval) { return frozen() && ticks % Math.max(1, interval) == 0; }
    public void stop(String reason) { phase = Phase.IDLE; message = reason; }
}
