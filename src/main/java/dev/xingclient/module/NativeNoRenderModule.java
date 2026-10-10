package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.nativebridge.XingNativeBridge;

/** Render hooks delegate effect decisions to the native module. Author: uint32. */
public final class NativeNoRenderModule extends Module {
    public enum Effect {
        PARTICLES, WEATHER, WEATHER_PARTICLES, CLOUDS, FIRE_OVERLAY, WATER_OVERLAY,
        BLOCK_OVERLAY, ITEM_ACTIVATION, ARMOR_SELF, ARMOR_OTHERS
    }

    public NativeNoRenderModule() {
        super("no_render", "NoRender", ModuleCategory.VISUALS);
    }

    @Override
    protected void onEnable() { XingNativeBridge.loadLibrary(); }

    public static boolean hides(Effect effect) {
        var app = XingClient.INSTANCE;
        if (app == null || app.noRender == null || !app.noRender.isEnabled()) return false;
        return nativeHides(true, app.settings.noRender.effectMask(), effect.ordinal());
    }

    private static native boolean nativeHides(boolean enabled, int settings, int effect);
}
