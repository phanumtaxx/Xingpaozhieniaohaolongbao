package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class CrashoutPoseMixin {
    @Inject(method = "getExpectedPose", at = @At("RETURN"), cancellable = true)
    private void xing$flightPose(CallbackInfoReturnable<EntityPose> cir) {
        var app = XingClient.INSTANCE;
        if ((Object) this == MinecraftClient.getInstance().player && cir.getReturnValue() == EntityPose.GLIDING
                && app != null && app.crashout != null && app.crashout.hidePose()) cir.setReturnValue(EntityPose.STANDING);
    }
}
