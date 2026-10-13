package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class WebHudMixin {
    @Inject(method="render",at=@At("TAIL"))
    private void xing$renderWebOverlay(DrawContext context,RenderTickCounter ticks,CallbackInfo info) {
        var app=XingClient.INSTANCE;
        if(app==null||app.webModule==null||!app.webModule.isEnabled()
            || net.minecraft.client.MinecraftClient.getInstance().currentScreen instanceof dev.xingclient.render.hud.HudEditorScreen) return;
        try {app.webOverlay.render(context,app.settings.webOverlay.fps);}
        catch(RuntimeException|LinkageError error) {app.webModule.failed(error);}
    }
}
