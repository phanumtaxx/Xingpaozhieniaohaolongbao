package dev.xingclient.manager;

import java.nio.ByteBuffer;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;

/** Minecraft value conversion for the native prediction ledger. Author: uint32. */
public final class WorldPredictionLedger {
    public enum Kind { BLOCK_PLACE, BLOCK_BREAK, CRYSTAL }
    public enum Status { PENDING, CONFIRMED, MISMATCH }
    public enum ExpectedType { ANY, BLOCK, ITEM, UNSUPPORTED, BLOCK_STATE }
    public record Expected(ExpectedType type, int registryId, int stateId) {}
    public record Prediction(long generation, Kind kind, BlockPos position, Expected expected,
            long ownerId, long createdMillis, long deadlineMillis, Status status) {}

    private final NativeWorldStateBridge bridge;
    private final NetworkManager network;

    WorldPredictionLedger(NativeWorldStateBridge bridge, NetworkManager network) {
        this.bridge = bridge;
        this.network = network;
    }

    public Prediction predictBlock(BlockPos position, Object expected, ActionOwner owner, Kind kind) {
        if (position == null) return null;
        Expected value = encodeExpected(expected);
        return decode(bridge.call(NativeWorldStateBridge.Event.PREDICT, input -> {
            input.putInt(4, network.latencyMillis());
            input.putLong(48, position.asLong());
            input.putLong(56, owner == null ? 0 : owner.id());
            input.putLong(64, System.currentTimeMillis());
            input.putInt(80, kind == null ? Kind.BLOCK_PLACE.ordinal() : kind.ordinal());
            input.putInt(84, value.type().ordinal());
            input.putInt(88, value.registryId());
            input.putInt(96, value.stateId());
        }));
    }

    public Prediction get(BlockPos position) {
        if (position == null) return null;
        return decode(read(position));
    }

    public boolean isPending(BlockPos position) { return position != null && read(position).getInt(0) == 2; }

    public boolean reconcileBlock(BlockPos position, BlockState actual, long generation) {
        if (position == null) return false;
        if (generation <= 0) return false;
        return reconcile(position, actual, generation);
    }

    public boolean reconcileLatestBlock(BlockPos position, BlockState actual) {
        return position != null && reconcile(position, actual, 0);
    }

    public void clearOwner(ActionOwner owner) {
        if (owner != null) bridge.call(NativeWorldStateBridge.Event.CLEAR_OWNER, input -> input.putLong(56, owner.id()));
    }

    public void clearPosition(BlockPos position) {
        if (position != null) bridge.call(NativeWorldStateBridge.Event.CLEAR_POSITION, input -> input.putLong(48, position.asLong()));
    }

    public void clear() { bridge.call(NativeWorldStateBridge.Event.CLEAR_PREDICTIONS, ignored -> {}); }

    private ByteBuffer read(BlockPos position) {
        return bridge.call(NativeWorldStateBridge.Event.GET_PREDICTION, input -> {
            input.putLong(48, position.asLong());
            input.putLong(64, System.currentTimeMillis());
        });
    }

    private boolean reconcile(BlockPos position, BlockState actual, long generation) {
        return bridge.call(NativeWorldStateBridge.Event.RECONCILE, input -> {
            input.putLong(40, generation);
            input.putLong(48, position.asLong());
            input.putInt(80, actual == null ? 1 : 0);
            input.putInt(84, actual != null && actual.isAir() ? 1 : 0);
            input.putInt(88, actual == null ? -1 : Registries.BLOCK.getRawId(actual.getBlock()));
            input.putInt(92, actual != null && actual.isReplaceable() ? 1 : 0);
            input.putInt(96, actual == null ? -1 : Registries.ITEM.getRawId(actual.getBlock().asItem()));
        }).getInt(0) != 0;
    }

    private static Expected encodeExpected(Object value) {
        if (value == null) return new Expected(ExpectedType.ANY, -1, -1);
        if (value instanceof Expected expected) return expected;
        if (value instanceof BlockState state)
            return new Expected(ExpectedType.BLOCK_STATE, Registries.BLOCK.getRawId(state.getBlock()), Block.getRawIdFromState(state));
        if (value instanceof Block block) return new Expected(ExpectedType.BLOCK, Registries.BLOCK.getRawId(block), -1);
        if (value instanceof ItemStack stack) value = stack.getItem();
        if (value instanceof Item item) return new Expected(ExpectedType.ITEM, Registries.ITEM.getRawId(item), -1);
        return new Expected(ExpectedType.UNSUPPORTED, -1, -1);
    }

    private static Prediction decode(ByteBuffer output) {
        if (output.getInt(0) == 0) return null;
        return new Prediction(output.getLong(40), Kind.values()[output.getInt(80)], BlockPos.fromLong(output.getLong(48)),
                new Expected(ExpectedType.values()[output.getInt(84)], output.getInt(88), output.getInt(96)),
                output.getLong(56), output.getLong(64), output.getLong(72), Status.values()[output.getInt(92)]);
    }
}
