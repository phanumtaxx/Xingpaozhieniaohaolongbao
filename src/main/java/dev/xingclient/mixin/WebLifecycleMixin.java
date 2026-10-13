package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.LoggerFactory;

@Mixin(GameRenderer.class)
public abstract class WebLifecycleMixin {
    @Inject(method="close",at=@At("HEAD"))
    private void xing$closeWebEngine(CallbackInfo info) {
        var app=XingClient.INSTANCE;
        if(app!=null) try {app.webOverlay.shutdown();}
        catch(RuntimeException|LinkageError error) {LoggerFactory.getLogger("xingclient-web").warn("Web engine shutdown failed",error);}
    }
}
