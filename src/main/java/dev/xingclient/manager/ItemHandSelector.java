package dev.xingclient.manager;

import dev.xingclient.nativebridge.XingNativeBridge;
import java.nio.ByteBuffer;
import net.minecraft.util.Hand;

/** Adapts item availability to the native hand-selection policy. Author: uint32. */
final class ItemHandSelector {
    private static final int MAIN_HAND_AVAILABLE = 1 << 3;
    private static final int OFF_HAND_AVAILABLE = 1 << 4;
    private static final int HOTBAR_AVAILABLE = 1 << 5;
    private static final int ELIGIBLE = 1 << 7;
    private final ByteBuffer input = XingNativeBridge.allocate(64);
    private final ByteBuffer output = XingNativeBridge.allocate(32);

    Hand select(InteractionManager.HandMode mode, InventoryManager.SwapMode swap,
            boolean mainHand, boolean offHand, int hotbarSlot) {
        int flags = ELIGIBLE | (mainHand ? MAIN_HAND_AVAILABLE : 0)
                | (offHand ? OFF_HAND_AVAILABLE : 0) | (hotbarSlot >= 0 ? HOTBAR_AVAILABLE : 0);
        input.putInt(16, 0);
        input.putInt(40, flags);
        input.putInt(44, mode.ordinal());
        input.putInt(48, swap.ordinal());
        input.putInt(52, hotbarSlot);
        int result = XingNativeBridge.dispatch(XingNativeBridge.AUTOCRYSTAL_EXECUTION_POLICY, 0, input, output);
        if (result != 0) throw new IllegalStateException("Native hand selector failed: " + result);
        return switch (output.getInt(8)) {
            case 0 -> Hand.MAIN_HAND;
            case 1 -> Hand.OFF_HAND;
            default -> null;
        };
    }
}
