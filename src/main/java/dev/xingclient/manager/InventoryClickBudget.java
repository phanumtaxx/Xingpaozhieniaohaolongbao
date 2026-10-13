package dev.xingclient.manager;

import dev.xingclient.nativebridge.XingNativeBridge;

/** Reserves complete automated click sequences against a shared native budget. Author: uint32. */
public final class InventoryClickBudget {
    private InventoryClickBudget() { }
    public static boolean reserve(int clicks) {
        CombatActionScheduler.requireClientThread();
        XingNativeBridge.loadLibrary();
        return nativeReserve(clicks);
    }
    public static Reservation hold(int clicks) {
        CombatActionScheduler.requireClientThread();
        XingNativeBridge.loadLibrary();
        return nativeHold(clicks) ? new Reservation(clicks) : null;
    }

    public static final class Reservation implements AutoCloseable {
        private int remaining;
        private Reservation(int clicks) { remaining = clicks; }
        public boolean consume(int clicks) {
            CombatActionScheduler.requireClientThread();
            if (clicks <= 0 || clicks > remaining) return false;
            nativeConsume(clicks);
            remaining -= clicks;
            return true;
        }
        @Override public void close() {
            CombatActionScheduler.requireClientThread();
            if (remaining > 0) nativeRelease(remaining);
            remaining = 0;
        }
    }

    private static native boolean nativeReserve(int clicks);
    private static native boolean nativeHold(int clicks);
    private static native void nativeConsume(int clicks);
    private static native void nativeRelease(int clicks);
}
