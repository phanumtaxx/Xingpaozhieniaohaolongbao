package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.ObjectAllocator;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WebWorldMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void xing$captureWebFrame(ObjectAllocator allocator,RenderTickCounter ticks,boolean outlines,Camera camera,
            GameRenderer renderer,Matrix4f position,Matrix4f projection,CallbackInfo info) {
        var app=XingClient.INSTANCE;
        if(app!=null) {
            if(app.nametags!=null) app.nametags.capture(ticks.getTickProgress(false));
            app.webOverlay.capture(camera,position,projection);
        }
    }
}
