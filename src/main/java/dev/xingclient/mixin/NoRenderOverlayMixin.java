package dev.xingclient.mixin;

import dev.xingclient.module.NativeNoRenderModule;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
public abstract class NoRenderOverlayMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void xing$hideFire(CallbackInfo ci) {
        if (NativeNoRenderModule.hides(NativeNoRenderModule.Effect.FIRE_OVERLAY)) ci.cancel();
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"), cancellable = true)
    private static void xing$hideWater(CallbackInfo ci) {
        if (NativeNoRenderModule.hides(NativeNoRenderModule.Effect.WATER_OVERLAY)) ci.cancel();
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"), cancellable = true)
    private static void xing$hideBlock(CallbackInfo ci) {
        if (NativeNoRenderModule.hides(NativeNoRenderModule.Effect.BLOCK_OVERLAY)) ci.cancel();
    }
}
