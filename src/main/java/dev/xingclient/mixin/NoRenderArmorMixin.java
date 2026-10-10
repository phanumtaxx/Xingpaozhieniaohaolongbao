package dev.xingclient.mixin;

import dev.xingclient.module.NativeNoRenderModule;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArmorFeatureRenderer.class)
public abstract class NoRenderArmorMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V",
            at = @At("HEAD"), cancellable = true)
    private void xing$hideArmor(MatrixStack matrices, VertexConsumerProvider vertices, int light,
            BipedEntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (!(state instanceof PlayerEntityRenderState playerState)) return;
        var player = MinecraftClient.getInstance().player;
        var effect = player != null && playerState.id == player.getId()
                ? NativeNoRenderModule.Effect.ARMOR_SELF : NativeNoRenderModule.Effect.ARMOR_OTHERS;
        if (NativeNoRenderModule.hides(effect)) ci.cancel();
    }
}
