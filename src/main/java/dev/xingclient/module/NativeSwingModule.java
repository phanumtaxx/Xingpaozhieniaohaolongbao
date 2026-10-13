package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.nativebridge.XingNativeBridge;
import net.minecraft.util.Hand;

/** Local animation hooks delegate swing policy to C++. Author: uint32. */
public final class NativeSwingModule extends Module {
    public NativeSwingModule() { super("swing", "Swing", ModuleCategory.VISUALS); }
    @Override protected void onEnable() { XingNativeBridge.loadLibrary(); }
    public static NativeSwingModule active() {
        var app = XingClient.INSTANCE;
        return app != null && app.settings != null && app.swing != null && app.swing.isEnabled() ? app.swing : null;
    }
    public Hand hand(Hand original) {
        var s = XingClient.INSTANCE.settings.swing;
        return Hand.values()[nativeHand(true, s.overrideHand, s.hand.ordinal(), original.ordinal())];
    }
    public int duration(int original) {
        var s = XingClient.INSTANCE.settings.swing;
        return nativeDuration(true, s.overrideSpeed, s.speed, original);
    }
    public float progress(float original, float current) {
        return nativeProgress(true, XingClient.INSTANCE.settings.swing.oldAnimation, original, current);
    }
    private static native int nativeHand(boolean enabled, boolean overrideHand, int configured, int original);
    private static native int nativeDuration(boolean enabled, boolean overrideSpeed, int speed, int original);
    private static native float nativeProgress(boolean enabled, boolean oldAnimation, float original, float current);
}
