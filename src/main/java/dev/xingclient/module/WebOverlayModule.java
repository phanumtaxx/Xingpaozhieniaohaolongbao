package dev.xingclient.module;

import dev.xingclient.XingClient;
import org.slf4j.LoggerFactory;

/** Lifecycle control for the reusable HTML/CSS overlay engine. Author: uint32. */
public final class WebOverlayModule extends Module {
    public WebOverlayModule() {super("web_overlay","Web UI",ModuleCategory.VISUALS);}
    @Override protected void onEnable() {
        try {XingClient.INSTANCE.webOverlay.start(XingClient.INSTANCE.settings.webOverlay.fps);}
        catch(RuntimeException|LinkageError error) {XingClient.INSTANCE.webOverlay.close();throw error;}
    }
    @Override protected void onDisable() {XingClient.INSTANCE.webOverlay.close();}
    @Override public String status() {return "HTML / CSS engine / uint32";}
    public void failed(Throwable error) {
        LoggerFactory.getLogger("xingclient-web").error("Web overlay failed",error);
        setEnabled(false);
        var app=XingClient.INSTANCE;app.settings.modules.get(id()).enabled=false;app.changed();
    }
}
