package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.nativebridge.XingNativeBridge;
import dev.xingclient.render.hud.HudManager;
import dev.xingclient.render.web.WebOverlay;

/** Basic native-backed widget for the HTML HUD system. Author: uint32. */
public final class NativeWatermarkModule extends Module {
    private record Display(String text) {}

    private WebOverlay.Registration widget;

    public NativeWatermarkModule() {
        super("watermark", "Watermark", ModuleCategory.HUD);
    }

    public void registerEditor(HudManager hud) {
        hud.define(id(), name(), () -> {
            var settings = XingClient.INSTANCE.settings.watermark;
            return new HudManager.Style(settings.anchor, settings.x, settings.y, settings.fontSize);
        }, style -> {
            var settings = XingClient.INSTANCE.settings.watermark;
            settings.anchor = style.anchor();
            settings.x = style.x();
            settings.y = style.y();
            settings.fontSize = style.fontSize();
            settings.normalize();
        }, new HudManager.Style(HudManager.Anchor.TOP_LEFT, 8, 8, 11));
    }

    @Override
    protected void onEnable() {
        XingNativeBridge.loadLibrary();
        var app = XingClient.INSTANCE;
        app.webModule.setEnabled(true);
        app.webOverlay.installMarkup("watermark", "watermark.html");
        Display display = new Display(nativeText());
        widget = app.hud.register(id(), "watermark", () -> {
            var settings = app.settings.watermark;
            settings.normalize();
            return new HudManager.Style(settings.anchor, settings.x, settings.y, settings.fontSize);
        }, () -> display);
    }

    @Override
    protected void onDisable() {
        if (widget != null) {
            widget.close();
            widget = null;
        }
    }

    @Override
    public String status() {
        return "Web HUD / uint32";
    }

    private static native String nativeText();
}
