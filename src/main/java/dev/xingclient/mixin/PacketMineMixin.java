package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class PacketMineMixin {
    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    private void xing$startMining(BlockPos position, Direction face, CallbackInfoReturnable<Boolean> callback) {
        var app = XingClient.INSTANCE;
        if (app != null && app.speedMine != null && app.speedMine.attack(position, face)) callback.setReturnValue(true);
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void xing$continueMining(BlockPos position, Direction face, CallbackInfoReturnable<Boolean> callback) {
        var app = XingClient.INSTANCE;
        if (app != null && app.speedMine != null && app.speedMine.ownsTarget()) callback.setReturnValue(true);
    }

    @Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void xing$keepMining(CallbackInfo callback) {
        var app = XingClient.INSTANCE;
        if (app != null && app.speedMine != null && app.speedMine.ownsTarget()) callback.cancel();
    }
}
