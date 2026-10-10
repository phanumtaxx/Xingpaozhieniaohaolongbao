package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerEntity.class)
public abstract class MaceMovementMixin {
    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
    private void xing$hold(CallbackInfo ci) {
        var app = XingClient.INSTANCE;
        if (app != null && app.maceKill != null && app.maceKill.frozen()) {
            app.maceKill.freezeMovement(); ci.cancel();
        }
    }
    @Inject(method = "sendMovementPackets", at = @At("HEAD"), cancellable = true)
    private void xing$heartbeat(CallbackInfo ci) {
        var app = XingClient.INSTANCE;
        if (app != null && app.maceKill != null && app.maceKill.frozen()) {
            app.maceKill.sendFrozenMovement(); ci.cancel();
        }
    }
}
