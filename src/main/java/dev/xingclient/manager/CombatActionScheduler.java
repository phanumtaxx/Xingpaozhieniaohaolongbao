package dev.xingclient.manager;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;

/** Executes Java game calls when the native scheduler grants an action. */
public final class CombatActionScheduler {
    public enum Status {
        READY, QUEUED, SUCCESS, RATE_LIMITED, RESOURCE_DENIED, ACTION_FAILED,
        ACTION_FAILED_COMMITTED, INVALIDATED, CANCELLED, EMPTY
    }
    public record Result(Status status, ActionResource deniedResource) {
        public boolean success() { return status == Status.SUCCESS; }
        public boolean accepted() { return success() || status == Status.QUEUED; }
    }
    public record Submission(long id, Result result) {}
    public record IntentOptions(ActionKey key, List<ActionKey> after, List<ActionKey> requiresSuccess,
            BooleanSupplier validWhen) {
        public static final IntentOptions NONE = new IntentOptions(null, List.of(), List.of(), null);
        public IntentOptions {
            after = List.copyOf(after);
            requiresSuccess = List.copyOf(requiresSuccess);
        }
        public static IntentOptions keyed(ActionKey key) {
            return new IntentOptions(Objects.requireNonNull(key), List.of(), List.of(), null);
        }
        public IntentOptions after(ActionKey... keys) { return new IntentOptions(key, List.of(keys), requiresSuccess, validWhen); }
        public IntentOptions requiring(ActionKey... keys) { return new IntentOptions(key, after, List.of(keys), validWhen); }
        public IntentOptions onlyWhen(BooleanSupplier condition) { return new IntentOptions(key, after, requiresSuccess, Objects.requireNonNull(condition)); }
    }

    private record Pending(ActionOwner owner, BooleanSupplier action, Consumer<Result> completion, IntentOptions options) {}
    private record Execution(ActionOwner owner, long id) {}
    private final NativeCombatBridge bridge;
    private final Map<Long, Pending> pending = new LinkedHashMap<>();
    private final ArrayDeque<Execution> executing = new ArrayDeque<>();
    private boolean collecting;
    private boolean resolving;

    CombatActionScheduler(NativeCombatBridge bridge) { this.bridge = bridge; }

    public Result execute(ActionOwner owner, int priority, int holdTicks, CombatRateLimiter.Channel channel,
            BooleanSupplier action, ActionResource... resources) {
        return execute(owner, priority, holdTicks, channel, action, null, resources);
    }

    public Result execute(ActionOwner owner, int priority, int holdTicks, CombatRateLimiter.Channel channel,
            BooleanSupplier action, Consumer<Result> completion, ActionResource... resources) {
        requireClientThread();
        Objects.requireNonNull(action);
        var request = request(owner, priority, holdTicks, channel, IntentOptions.NONE, resources);
        return run(bridge.call(NativeCombatBridge.Event.BEGIN, request),
                new Pending(owner, action, completion, IntentOptions.NONE));
    }

    public Submission submit(ActionOwner owner, int priority, int holdTicks, CombatRateLimiter.Channel channel,
            BooleanSupplier action, Consumer<Result> completion, IntentOptions options, ActionResource... resources) {
        requireClientThread();
        Objects.requireNonNull(action);
        Objects.requireNonNull(options);
        var request = request(owner, priority, holdTicks, channel, options, resources);
        if (!collecting || resolving) {
            var reply = bridge.call(NativeCombatBridge.Event.BEGIN, request);
            return new Submission(reply.id(), run(reply, new Pending(owner, action, completion, options)));
        }
        var reply = bridge.call(NativeCombatBridge.Event.ENQUEUE, request);
        if (reply.status() == Status.QUEUED) pending.put(reply.id(), new Pending(owner, action, completion, options));
        else complete(completion, reply.result());
        return new Submission(reply.id(), reply.result());
    }

    public Result cancel(long id) {
        requireClientThread();
        var reply = bridge.call(NativeCombatBridge.Event.CANCEL, NativeCombatBridge.Request.empty(null).withId(id));
        var callback = pending.remove(id);
        if (callback != null) complete(callback.completion(), reply.result());
        return reply.result();
    }

    public void cancelOwner(ActionOwner owner) {
        requireClientThread();
        Objects.requireNonNull(owner);
        if (bridge.used()) bridge.call(NativeCombatBridge.Event.CANCEL_OWNER, owner);
        var cancelled = pending.entrySet().stream().filter(entry -> entry.getValue().owner() == owner).toList();
        for (var entry : cancelled) pending.remove(entry.getKey());
        completeCancelled(cancelled.stream().map(Map.Entry::getValue).toList());
    }

    boolean reserve(ActionOwner owner, int priority, int holdTicks, ActionResource... resources) {
        requireClientThread();
        return bridge.call(NativeCombatBridge.Event.RESERVE,
                request(owner, priority, holdTicks, null, IntentOptions.NONE, resources)).status() == Status.SUCCESS;
    }

    void release(ActionOwner owner, ActionResource... resources) {
        requireClientThread();
        if (bridge.used()) bridge.call(NativeCombatBridge.Event.RELEASE,
                request(owner, 0, 1, null, IntentOptions.NONE, resources));
    }

    void beginTick(boolean ready) {
        requireClientThread();
        if (!pending.isEmpty()) reset();
        if (bridge.used()) bridge.call(NativeCombatBridge.Event.TICK, (ActionOwner) null);
        collecting = ready;
    }

    public void resolve() {
        requireClientThread();
        if (resolving || !collecting) return;
        resolving = true;
        try {
            while (!pending.isEmpty()) {
                var reply = bridge.call(NativeCombatBridge.Event.NEXT, (ActionOwner) null);
                if (reply.status() == Status.EMPTY) throw new IllegalStateException("Native scheduler lost a queued action");
                var callback = pending.remove(reply.id());
                if (callback == null) throw new IllegalStateException("Missing callback for native action " + reply.id());
                if (reply.status() == Status.READY) {
                    var request = NativeCombatBridge.Request.empty(null).withId(reply.id());
                    boolean valid;
                    try {
                        valid = callback.options().validWhen() == null || callback.options().validWhen().getAsBoolean();
                    } catch (RuntimeException | Error error) {
                        var invalid = bridge.call(NativeCombatBridge.Event.INVALIDATE, request).result();
                        try { complete(callback.completion(), invalid); }
                        catch (RuntimeException | Error secondary) { error.addSuppressed(secondary); }
                        throw error;
                    }
                    reply = bridge.call(valid ? NativeCombatBridge.Event.ACTIVATE : NativeCombatBridge.Event.INVALIDATE, request);
                }
                run(reply, new Pending(callback.owner(), callback.action(), callback.completion(), IntentOptions.NONE));
            }
        } catch (RuntimeException | Error error) {
            try { reset(); }
            catch (RuntimeException | Error secondary) { error.addSuppressed(secondary); }
            throw error;
        } finally {
            resolving = false;
            collecting = false;
            if (bridge.used()) bridge.call(NativeCombatBridge.Event.END_FRAME, (ActionOwner) null);
        }
    }

    void reset() {
        requireClientThread();
        if (bridge.used()) bridge.call(NativeCombatBridge.Event.RESET, (ActionOwner) null);
        var cancelled = List.copyOf(pending.values());
        pending.clear();
        collecting = false;
        completeCancelled(cancelled);
    }

    ActionOwner currentOwner() { return executing.isEmpty() ? null : executing.peek().owner(); }
    long currentActionId(ActionOwner owner) {
        return !executing.isEmpty() && executing.peek().owner() == owner ? executing.peek().id() : 0;
    }

    private Result run(NativeCombatBridge.Reply reply, Pending callback) {
        if (reply.status() != Status.READY) {
            complete(callback.completion(), reply.result());
            return reply.result();
        }
        Result result;
        executing.push(new Execution(callback.owner(), reply.id()));
        try {
            boolean valid = callback.options().validWhen() == null || callback.options().validWhen().getAsBoolean();
            Status status = Status.INVALIDATED;
            if (valid) status = callback.action().getAsBoolean() ? Status.SUCCESS : Status.ACTION_FAILED;
            result = finish(reply, status);
        } catch (RuntimeException | Error error) {
            try { complete(callback.completion(), finish(reply, Status.ACTION_FAILED)); }
            catch (RuntimeException | Error secondary) { error.addSuppressed(secondary); }
            throw error;
        } finally {
            executing.pop();
        }
        complete(callback.completion(), result);
        return result;
    }

    private Result finish(NativeCombatBridge.Reply reply, Status status) {
        if (reply.nested()) return new Result(status, null);
        return bridge.call(NativeCombatBridge.Event.FINISH, NativeCombatBridge.Request.empty(null).withId(reply.id()),
                status.ordinal(), 0, -1, 0).result();
    }
    private static void complete(Consumer<Result> completion, Result result) {
        if (completion != null) completion.accept(result);
    }
    private static void completeCancelled(List<Pending> cancelled) {
        Throwable failure = null;
        for (Pending callback : cancelled) {
            try { complete(callback.completion(), new Result(Status.CANCELLED, null)); }
            catch (RuntimeException | Error error) {
                if (failure == null) failure = error;
                else failure.addSuppressed(error);
            }
        }
        if (failure instanceof RuntimeException error) throw error;
        if (failure instanceof Error error) throw error;
    }
    private static NativeCombatBridge.Request request(ActionOwner owner, int priority, int holdTicks,
            CombatRateLimiter.Channel channel, IntentOptions options, ActionResource... resources) {
        return new NativeCombatBridge.Request(0, options.key(), Objects.requireNonNull(owner), priority,
                holdTicks, channel, ActionResource.mask(resources), options.after(), options.requiresSuccess());
    }
    static void requireClientThread() {
        if (!MinecraftClient.getInstance().isOnThread()) throw new IllegalStateException("Combat actions must run on the Minecraft client thread");
    }
}
