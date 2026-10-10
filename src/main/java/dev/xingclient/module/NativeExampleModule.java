package dev.xingclient.module;

import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.EventBus;
import dev.xingclient.nativebridge.XingNativeBridge;

/** Java lifecycle and event adapter for the native tick counter example. */
public final class NativeExampleModule extends Module {
    private final EventBus events;
    private EventBus.Subscription tickSubscription;

    public NativeExampleModule(EventBus events) {
        super("native_tick_counter", "Native Tick Counter", ModuleCategory.CORE);
        this.events = events;
    }

    @Override
    protected void onEnable() {
        nativeSetEnabled(true);
        tickSubscription = events.subscribe(ClientTickEvent.class, this::onClientTick);
    }

    @Override
    protected void onDisable() {
        nativeSetEnabled(false);
        if (tickSubscription != null) {
            tickSubscription.close();
            tickSubscription = null;
        }
    }

    public long tickCount() {
        return nativeTickCount();
    }

    @Override
    public String status() {
        return isEnabled() ? "Client ticks: " + tickCount() : "Disabled";
    }

    private void onClientTick(ClientTickEvent event) {
        if (event.phase() == ClientTickEvent.Phase.END) {
            nativeOnTick();
        }
    }

    private static void nativeSetEnabled(boolean enabled) {
        XingNativeBridge.loadLibrary();
        setNativeEnabled(enabled);
    }

    private static void nativeOnTick() {
        XingNativeBridge.loadLibrary();
        recordNativeTick();
    }

    private static long nativeTickCount() {
        XingNativeBridge.loadLibrary();
        return getNativeTickCount();
    }

    private static native void setNativeEnabled(boolean enabled);

    private static native void recordNativeTick();

    private static native long getNativeTickCount();
}
