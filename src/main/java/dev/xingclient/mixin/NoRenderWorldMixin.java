package dev.xingclient.mixin;

import dev.xingclient.module.NativeNoRenderModule;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class NoRenderWorldMixin {
    @Inject(method = "renderClouds", at = @At("HEAD"), cancellable = true)
    private void xing$hideClouds(CallbackInfo ci) {
        if (NativeNoRenderModule.hides(NativeNoRenderModule.Effect.CLOUDS)) ci.cancel();
    }

    @Inject(method = "renderWeather", at = @At("HEAD"), cancellable = true)
    private void xing$hideWeather(CallbackInfo ci) {
        if (NativeNoRenderModule.hides(NativeNoRenderModule.Effect.WEATHER)) ci.cancel();
    }

    @Inject(method = "addWeatherParticlesAndSound", at = @At("HEAD"), cancellable = true)
    private void xing$hideWeatherParticles(CallbackInfo ci) {
        if (NativeNoRenderModule.hides(NativeNoRenderModule.Effect.WEATHER_PARTICLES)) ci.cancel();
    }
}
