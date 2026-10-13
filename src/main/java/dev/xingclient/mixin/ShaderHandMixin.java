package dev.xingclient.mixin;

import dev.xingclient.module.NativeShadersModule;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class ShaderHandMixin {
    @ModifyVariable(method = "renderFirstPersonItem", at = @At("HEAD"), argsOnly = true)
    private VertexConsumerProvider xing$captureHands(VertexConsumerProvider original) {
        var module = NativeShadersModule.active();
        return module == null ? original : module.captureHands(original);
    }
    @Inject(method = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V", at = @At("TAIL"))
    private void xing$flushHands(CallbackInfo info) {
        var module = NativeShadersModule.active();
        if (module != null) module.flush();
    }
}
