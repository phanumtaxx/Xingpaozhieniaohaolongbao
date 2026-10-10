package dev.xingclient.manager;

import java.util.List;
import java.util.Objects;

/** Configuration and diagnostics for the native rate limiter. */
public final class CombatRateLimiter {
    public enum Channel { BLOCK_PLACE, ITEM_USE, ATTACK, MINE, GENERIC_COMBAT }
    public record State(int used, int limit, int cooldownTicks, long lastOwnerId) {}

    private final NativeCombatBridge bridge;
    CombatRateLimiter(NativeCombatBridge bridge) { this.bridge = bridge; }

    public void setPerTickLimit(Channel channel, int limit) {
        configure(NativeCombatBridge.Event.LIMIT, channel, limit);
    }
    public void setCooldownTicks(Channel channel, int ticks) {
        configure(NativeCombatBridge.Event.COOLDOWN, channel, ticks);
    }
    public boolean canUse(Channel channel) {
        CombatActionScheduler.requireClientThread();
        return bridge.call(NativeCombatBridge.Event.CAN_USE, request(channel)).status() == CombatActionScheduler.Status.SUCCESS;
    }
    public boolean tryConsume(Channel channel, ActionOwner owner) {
        CombatActionScheduler.requireClientThread();
        return bridge.call(NativeCombatBridge.Event.TRY_CONSUME, request(channel, owner)).status() == CombatActionScheduler.Status.SUCCESS;
    }
    public void consume(Channel channel, ActionOwner owner) {
        CombatActionScheduler.requireClientThread();
        bridge.call(NativeCombatBridge.Event.CONSUME, request(channel, owner));
    }
    public void reset() {
        CombatActionScheduler.requireClientThread();
        bridge.call(NativeCombatBridge.Event.RESET_RATES, (ActionOwner) null);
    }
    public void setProfileLimits(int blockPlacements, int itemUses) {
        CombatActionScheduler.requireClientThread();
        bridge.call(NativeCombatBridge.Event.PROFILE, NativeCombatBridge.Request.empty(null),
                blockPlacements, itemUses, -1, 0);
    }
    public State state(Channel channel) {
        CombatActionScheduler.requireClientThread();
        var reply = bridge.call(NativeCombatBridge.Event.INSPECT_RATE, request(channel));
        return new State(reply.used(), reply.limit(), reply.cooldown(), reply.ownerId());
    }
    private void configure(NativeCombatBridge.Event event, Channel channel, int value) {
        CombatActionScheduler.requireClientThread();
        bridge.call(event, request(channel), value, 0, -1, 0);
    }
    private static NativeCombatBridge.Request request(Channel channel) {
        return new NativeCombatBridge.Request(0, null, null, 0, 1,
                Objects.requireNonNull(channel), 0, List.of(), List.of());
    }
    private static NativeCombatBridge.Request request(Channel channel, ActionOwner owner) {
        return new NativeCombatBridge.Request(0, null, Objects.requireNonNull(owner), 0, 1,
                Objects.requireNonNull(channel), 0, List.of(), List.of());
    }
}
