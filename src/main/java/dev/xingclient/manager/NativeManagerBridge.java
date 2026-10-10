package dev.xingclient.manager;

import dev.xingclient.nativebridge.XingNativeBridge;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;

final class NativeManagerBridge {
    private static final InventoryManager.TransactionState[] TRANSACTION_STATES = InventoryManager.TransactionState.values();
    static final int SLOT_BEGIN = 0, SLOT_FINISH = 1, SLOT_TICK = 2, SLOT_RELEASE = 3;
    static final int ROTATION_REQUEST = 4, ROTATION_TICK = 5, ROTATION_RELEASE = 6;
    static final int RESET = 8, ROTATION_OWNERSHIP = 9;
    static final int SLOT_ACQUIRE = 10, SLOT_BEGIN_SILENT = 11, SLOT_SCHEDULE_RESTORE = 12;
    static final int SLOT_RESTORE_SILENT = 13, SLOT_KEEP_ALIVE = 14, SLOT_INSPECT = 15;
    static final int SLOT_COMMIT = 16, SLOT_FAILED = 17;
    static final int ACTIVITY_TICK = 18, ACTIVITY_MARK = 19, ACTIVITY_READ = 20, ACTIVITY_CLEAR = 21;
    static final int ROTATION_REQUEST_ANGLES = 22, ROTATION_OBSERVE = 23, ROTATION_SERVER_READ = 24;
    static final int CRYSTAL_TICK = 25, CRYSTAL_ATTACK = 26, CRYSTAL_ATTACK_COOLING_DOWN = 27, CRYSTAL_RESET = 28;
    static final int CRYSTAL_PLACED = 29, CRYSTAL_SPAWNED = 30, CRYSTAL_BROKEN = 31, CRYSTAL_CLEAR_PENDING = 32, CRYSTAL_READ = 33;
    static final int CRYSTAL_PACKET_REACTION = 34;
    static final int CRYSTAL_REMOVED = 35;
    static final int CRYSTAL_REPLACEMENT_READY = 36;

    private final ByteBuffer input = XingNativeBridge.allocate(96);
    private final ByteBuffer output = XingNativeBridge.allocate(176);
    private boolean used;

    private Result dispatch(int event, ActionOwner owner, Consumer<ByteBuffer> fields) {
        requireClientThread();
        for (int offset = 0; offset < input.capacity(); offset += Long.BYTES) input.putLong(offset, 0);
        input.putLong(0, owner == null ? 0 : owner.id());
        input.putInt(12, -1);
        input.putInt(16, -1);
        input.putInt(28, -1);
        fields.accept(input);
        int status = XingNativeBridge.dispatch(7, event, input, output);
        if (status != 0) throw new IllegalStateException("Native manager error " + status);
        if (output.getInt(140) != 2) throw new IllegalStateException("Unsupported native manager version");
        used = true;
        var slot = new InventoryManager.SlotState(output.getLong(32), output.getInt(40), output.getInt(44),
                output.getLong(48), output.getLong(56), output.getInt(64), output.getInt(68),
                TRANSACTION_STATES[output.getInt(72)], TRANSACTION_STATES[output.getInt(76)],
                output.getLong(80), output.getLong(88), output.getLong(96), output.getInt(120), output.getInt(124));
        return new Result(output.getInt(0) != 0, output.getInt(4), output.getInt(8) != 0,
                output.getInt(12), output.getInt(16) != 0, output.getFloat(20), output.getFloat(24),
                output.getInt(28) != 0, slot, output.getLong(104), output.getLong(112),
                new CombatActionState.State(output.getInt(128), output.getInt(132), output.getInt(136)),
                new CrystalActionState.State(output.getInt(144) == 0 ? null
                        : new BlockPos(output.getInt(148), output.getInt(152), output.getInt(156)),
                        output.getInt(160), output.getInt(164), output.getInt(168), output.getInt(172)));
    }

    Result requestRotation(ActionOwner owner, int priority, Vec3d direction, int holdTicks) {
        return dispatch(ROTATION_REQUEST, owner, request -> {
            request.putInt(8, priority);
            request.putInt(36, holdTicks);
            request.putDouble(40, direction.x);
            request.putDouble(48, direction.y);
            request.putDouble(56, direction.z);
        });
    }

    Result rotationAngles(int event, ActionOwner owner, int priority, float yaw, float pitch, int holdTicks) {
        return dispatch(event, owner, request -> {
            request.putInt(8, priority); request.putInt(36, holdTicks);
            request.putFloat(40, yaw); request.putFloat(44, pitch);
        });
    }

    Result call(int event, ActionOwner owner, int selectedSlot) {
        return dispatch(event, owner, request -> request.putInt(16, selectedSlot));
    }

    Result slotRequest(int event, ActionOwner owner, int priority, int slot, int selectedSlot,
            int serverSlot, int holdTicks, boolean silent) {
        return dispatch(event, owner, request -> {
            request.putInt(8, priority);
            request.putInt(12, slot);
            request.putInt(16, selectedSlot);
            request.putInt(20, silent ? 1 : 0);
            request.putInt(28, serverSlot);
            request.putInt(36, holdTicks);
        });
    }

    Result finishSlot(ActionOwner owner, int restoreDelay, boolean success, int selectedSlot) {
        return dispatch(SLOT_FINISH, owner, request -> {
            request.putInt(16, selectedSlot);
            request.putInt(24, restoreDelay);
            request.putInt(40, success ? 1 : 0);
        });
    }

    Result scheduleRestore(ActionOwner owner, int delay) {
        return dispatch(SLOT_SCHEDULE_RESTORE, owner, request -> request.putInt(24, delay));
    }

    Result slotCommitted(ActionOwner owner, long transaction, InventoryManager.PacketPhase phase,
            int packetSlot, int serverSlot, long commitId) {
        return dispatch(SLOT_COMMIT, owner, request -> {
            request.putInt(12, packetSlot);
            request.putInt(20, phase.ordinal());
            request.putInt(28, serverSlot);
            request.putLong(64, transaction);
            request.putLong(72, commitId);
        });
    }

    Result slotFailed(ActionOwner owner, long transaction) {
        return dispatch(SLOT_FAILED, owner, request -> request.putLong(64, transaction));
    }

    void markActivity(int activity, int ticks) {
        dispatch(ACTIVITY_MARK, null, request -> { request.putInt(12, activity); request.putInt(36, ticks); });
    }

    Result crystalAttack(int event, int entityId, int ticks) {
        return dispatch(event, null, request -> { request.putInt(12, entityId); request.putInt(36, ticks); });
    }

    Result crystalPosition(int event, BlockPos base, int entityId) {
        return crystalPosition(event, base, entityId, 5);
    }

    Result crystalPosition(int event, BlockPos base, int entityId, int timeoutTicks) {
        return dispatch(event, null, request -> {
            request.putInt(12, entityId);
            request.putInt(36, timeoutTicks);
            request.putInt(40, base.getX()); request.putInt(44, base.getY()); request.putInt(48, base.getZ());
        });
    }

    boolean crystalPacketReaction(long tick) {
        return dispatch(CRYSTAL_PACKET_REACTION, null, request -> request.putLong(56, tick)).accepted();
    }

    private static void requireClientThread() {
        if (!MinecraftClient.getInstance().isOnThread())
            throw new IllegalStateException("Client managers must run on the Minecraft client thread");
    }

    void reset() { if (used) call(RESET, null, -1); }
    boolean used() { return used; }

    record Result(boolean accepted, int restoreSlot, boolean restoreClient, int selectSlot,
            boolean selectClient, float yaw, float pitch, boolean rotationActive,
            InventoryManager.SlotState slot, long restoreOwnerId, long restoreTransaction,
            CombatActionState.State activity, CrystalActionState.State crystal) {}
}
