package dev.xingclient.mixin;

import dev.xingclient.module.NativeNoRenderModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ParticleManager.class)
public abstract class NoRenderParticleMixin {
    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void xing$hideParticle(Particle particle, CallbackInfo ci) {
        if (NativeNoRenderModule.hides(NativeNoRenderModule.Effect.PARTICLES)) ci.cancel();
    }
}
