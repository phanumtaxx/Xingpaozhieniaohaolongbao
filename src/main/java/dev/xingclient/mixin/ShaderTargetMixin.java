package dev.xingclient.mixin;

import dev.xingclient.module.NativeShadersModule;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.render.RenderPhase$Target")
public abstract class ShaderTargetMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true)
    private void xing$routeMask(CallbackInfoReturnable<Framebuffer> info) {
        var app = dev.xingclient.XingClient.INSTANCE;
        Framebuffer itemTarget = app == null ? null : app.webOverlay.routedItemTarget();
        if (itemTarget != null) { info.setReturnValue(itemTarget); return; }
        var module = NativeShadersModule.active();
        Framebuffer target = module == null ? null : module.routedTarget();
        if (target != null) info.setReturnValue(target);
    }
}
