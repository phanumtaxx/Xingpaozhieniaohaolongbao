package dev.xingclient.manager;

import dev.xingclient.nativebridge.XingNativeBridge;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

final class NativeCombatBridge {
    private static final int VERSION = 1;
    enum Event {
        RESET, TICK, LIMIT, COOLDOWN, PROFILE, BEGIN, FINISH, ENQUEUE, NEXT,
        CANCEL_OWNER, RESERVE, RELEASE, PACKET_COMMIT, LATEST_COMMIT, CANCEL, INSPECT_RATE, END_FRAME, ACTIVATE, INVALIDATE,
        CAN_USE, CONSUME, TRY_CONSUME, RESET_RATES
    }

    record Request(long id, ActionKey key, ActionOwner owner, int priority, int holdTicks,
            CombatRateLimiter.Channel channel, int resources, List<ActionKey> after, List<ActionKey> requiresSuccess) {
        static Request empty(ActionOwner owner) {
            return new Request(0, null, owner, 0, 1, null, 0, List.of(), List.of());
        }
        Request withId(long id) {
            return new Request(id, key, owner, priority, holdTicks, channel, resources, after, requiresSuccess);
        }
    }

    record Reply(CombatActionScheduler.Status status, ActionResource deniedResource, long id,
            long ownerId, long commitId, int sequence, boolean nested, int used, int limit, int cooldown, long epoch) {
        CombatActionScheduler.Result result() { return new CombatActionScheduler.Result(status, deniedResource); }
    }

    private static final class Buffers {
        ByteBuffer input = XingNativeBridge.allocate(80);
        final ByteBuffer output = XingNativeBridge.allocate(64);
    }

    private final ThreadLocal<Buffers> buffers = ThreadLocal.withInitial(Buffers::new);
    private volatile boolean used;
    private final AtomicLong epoch = new AtomicLong();

    Reply call(Event event, Request request, int value, int extra, int sequence, long packetEpoch) {
        Buffers local = buffers.get();
        int capacity = Math.addExact(80, Math.multiplyExact(8,
                Math.addExact(request.after().size(), request.requiresSuccess().size())));
        if (local.input.capacity() < capacity) local.input = XingNativeBridge.allocate(capacity);
        ByteBuffer input = local.input.slice(0, capacity).order(ByteOrder.LITTLE_ENDIAN);
        input.putInt(0, VERSION);
        input.putLong(8, request.id());
        input.putLong(16, request.key() == null ? 0 : request.key().id());
        input.putLong(24, request.owner() == null ? 0 : request.owner().id());
        input.putInt(32, request.priority());
        input.putInt(36, request.holdTicks());
        input.putInt(40, request.channel() == null ? -1 : request.channel().ordinal());
        input.putInt(44, request.resources());
        input.putInt(48, value);
        input.putInt(52, request.after().size());
        input.putInt(56, request.requiresSuccess().size());
        input.putInt(60, extra);
        input.putInt(64, sequence);
        input.putLong(72, packetEpoch);
        input.position(80);
        for (ActionKey key : request.after()) input.putLong(key.id());
        for (ActionKey key : request.requiresSuccess()) input.putLong(key.id());
        int status = XingNativeBridge.dispatch(8, event.ordinal(), input, local.output);
        if (status != 0) throw new IllegalStateException("Native combat scheduler error " + status);
        ByteBuffer output = local.output;
        if (output.getInt(52) != VERSION) throw new IllegalStateException("Native combat bridge version mismatch");
        int resource = output.getInt(4);
        Reply reply = new Reply(CombatActionScheduler.Status.values()[output.getInt(0)],
                resource < 0 ? null : ActionResource.values()[resource], output.getLong(8),
                output.getLong(16), output.getLong(24), output.getInt(32), output.getInt(36) != 0,
                output.getInt(40), output.getInt(44), output.getInt(48), output.getLong(56));
        epoch.accumulateAndGet(reply.epoch(), Math::max);
        used = true;
        return reply;
    }

    Reply call(Event event, Request request) { return call(event, request, 0, 0, -1, 0); }
    Reply call(Event event, ActionOwner owner) { return call(event, Request.empty(owner)); }
    boolean used() { return used; }
    long epoch() { return epoch.get(); }
}
