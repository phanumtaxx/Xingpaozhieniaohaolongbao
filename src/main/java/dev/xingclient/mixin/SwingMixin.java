package dev.xingclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.xingclient.module.NativeSwingModule;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Hand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(LivingEntity.class)
public abstract class SwingMixin {
    @Shadow public float handSwingProgress;
    @ModifyVariable(method="swingHand(Lnet/minecraft/util/Hand;Z)V", at=@At("HEAD"), argsOnly=true)
    private Hand xing$swingHand(Hand original) {
        var module = NativeSwingModule.active();
        return (Object)this instanceof ClientPlayerEntity && module != null ? module.hand(original) : original;
    }
    @ModifyReturnValue(method="getHandSwingDuration", at=@At("RETURN"))
    private int xing$swingDuration(int original) {
        var module = NativeSwingModule.active();
        return (Object)this instanceof ClientPlayerEntity && module != null ? module.duration(original) : original;
    }
    @ModifyReturnValue(method="getHandSwingProgress", at=@At("RETURN"))
    private float xing$swingProgress(float original) {
        var module = NativeSwingModule.active();
        return (Object)this instanceof ClientPlayerEntity && module != null ? module.progress(original, handSwingProgress) : original;
    }
}
