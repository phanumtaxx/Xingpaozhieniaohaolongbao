package dev.xingclient.mixin;

import dev.xingclient.module.NativeShadersModule;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class ShaderCompositeMixin {
    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void xing$composite(CallbackInfo info) {
        var module = NativeShadersModule.active();
        if (module != null) module.finish();
    }
    @Inject(method = "close", at = @At("HEAD"))
    private void xing$releaseShaders(CallbackInfo info) {
        var module = NativeShadersModule.active();
        if (module != null) module.setEnabled(false);
    }
}
