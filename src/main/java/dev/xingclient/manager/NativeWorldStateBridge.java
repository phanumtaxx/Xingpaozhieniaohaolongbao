package dev.xingclient.manager;

import dev.xingclient.nativebridge.XingNativeBridge;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.Consumer;

final class NativeWorldStateBridge {
    static final int HEADER_BYTES = 160;
    enum Event {
        RESET, TICK, COMMIT, CONTAINER_SLOT, CONTAINER_CONTENT, REFRESH_HANDS, CRYSTAL_SPAWN,
        READ, READ_HAND, READ_CRYSTAL, PREDICT, GET_PREDICTION, RECONCILE,
        CLEAR_OWNER, CLEAR_POSITION, CLEAR_PREDICTIONS
    }

    private ByteBuffer input = XingNativeBridge.allocate(HEADER_BYTES);
    private ByteBuffer output = XingNativeBridge.allocate(HEADER_BYTES);
    private boolean used;

    ByteBuffer call(Event event, Consumer<ByteBuffer> fields) {
        return call(event, fields, new byte[0], new byte[0]);
    }

    ByteBuffer call(Event event, Consumer<ByteBuffer> fields, byte[] first, byte[] second) {
        CombatActionScheduler.requireClientThread();
        int size = Math.addExact(HEADER_BYTES, Math.addExact(first.length, second.length));
        if (input.capacity() < size) input = XingNativeBridge.allocate(size);
        ByteBuffer request = input.slice(0, size).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset = 0; offset < HEADER_BYTES; offset += Long.BYTES) request.putLong(offset, 0);
        request.putInt(0, 1);
        request.putInt(24, first.length);
        request.putInt(28, second.length);
        fields.accept(request);
        request.position(HEADER_BYTES).put(first).put(second);
        int status = XingNativeBridge.dispatch(XingNativeBridge.WORLD_STATE, event.ordinal(), request, output);
        if (status == -3 && event == Event.READ_HAND) {
            int required = output.getInt(36);
            if (required <= output.capacity()) throw new IllegalStateException("Invalid native item snapshot size");
            output = XingNativeBridge.allocate(required);
            status = XingNativeBridge.dispatch(XingNativeBridge.WORLD_STATE, event.ordinal(), request, output);
        }
        if (status != 0) throw new IllegalStateException("Native world state error " + status);
        if (output.getInt(156) != 1) throw new IllegalStateException("Unsupported native world state version");
        used = true;
        return output;
    }

    void reset() { if (used) call(Event.RESET, ignored -> {}); }
}
