package dev.xingclient.mixin;

import dev.xingclient.module.NativeShadersModule;
import net.minecraft.client.render.SkyRendering;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SkyRendering.class)
public abstract class ShaderSkyMixin {
    @Inject(method = {"renderTopSky", "renderEndSky"}, at = @At("HEAD"), cancellable = true)
    private void xing$drawSky(CallbackInfo info) {
        var module = NativeShadersModule.active();
        if (module != null && module.sky()) info.cancel();
    }
    @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true)
    private void xing$hideDarkSky(CallbackInfo info) {
        var module = NativeShadersModule.active();
        if (module != null && module.rendersSky()) info.cancel();
    }
    @Inject(method = {"renderStars", "renderGlowingSky"}, at = @At("HEAD"), cancellable = true)
    private void xing$hideStarsAndGlow(CallbackInfo info) {
        var module = NativeShadersModule.active();
        if (module != null && module.rendersSky() && !module.vanillaCelestials()) info.cancel();
    }
    @Inject(method = "renderSun", at = @At("HEAD"), cancellable = true)
    private void xing$hideSun(CallbackInfo info) {
        var module = NativeShadersModule.active();
        if (module != null && !module.vanillaSun()) info.cancel();
    }
    @Inject(method = "renderMoon", at = @At("HEAD"), cancellable = true)
    private void xing$hideMoon(CallbackInfo info) {
        var module = NativeShadersModule.active();
        if (module != null && !module.vanillaMoon()) info.cancel();
    }
}
