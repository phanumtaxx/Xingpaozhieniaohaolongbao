package dev.xingclient.mixin;

import dev.xingclient.module.NativeNoRenderModule;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class NoRenderActivationMixin {
    @Inject(method = "renderFloatingItem", at = @At("HEAD"), cancellable = true)
    private void xing$hideActivation(CallbackInfo ci) {
        if (NativeNoRenderModule.hides(NativeNoRenderModule.Effect.ITEM_ACTIVATION)) ci.cancel();
    }
}
