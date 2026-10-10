package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class VelocityBlockPushMixin {
    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void xing$blockPush(double x, double z, CallbackInfo ci) {
        var app = XingClient.INSTANCE;
        if (app != null && app.velocity != null && app.velocity.isEnabled() && app.managers.movementState.cancelBlockPush()) ci.cancel();
    }
}
