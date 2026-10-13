package dev.xingclient.mixin;

import dev.xingclient.module.NativeViewModelModule;
import dev.xingclient.module.NativeShadersModule;
import dev.xingclient.render.HiddenVertexConsumer;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemDisplayContext;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeldItemRenderer.class)
public abstract class ViewModelMixin {
    @Inject(method="renderFirstPersonItem", at=@At(value="INVOKE", target="Lnet/minecraft/client/util/math/MatrixStack;push()V", shift=At.Shift.AFTER))
    private void xing$transform(AbstractClientPlayerEntity player, float delta, float pitch, Hand hand, float swing,
            ItemStack item, float equip, MatrixStack matrices, VertexConsumerProvider consumers, int light, CallbackInfo info) {
        var module = NativeViewModelModule.active();
        if (module != null) module.apply(matrices, hand, hand == Hand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite());
    }
    @Inject(method={"renderArm", "renderArmHoldingItem"}, at=@At("HEAD"), cancellable=true)
    private void xing$hideArms(CallbackInfo info) {
        if (!NativeViewModelModule.renderArms()) info.cancel();
    }
    @ModifyVariable(method="renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at=@At("HEAD"), argsOnly=true)
    private VertexConsumerProvider xing$hideTexturedItems(VertexConsumerProvider original, LivingEntity entity,
            ItemStack item, ItemDisplayContext context, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (!context.isFirstPerson() || NativeViewModelModule.renderVanillaItems()) return original;
        var shaders = NativeShadersModule.active();
        return shaders == null ? layer -> HiddenVertexConsumer.INSTANCE : shaders.captureHandsOnly(original);
    }
}
