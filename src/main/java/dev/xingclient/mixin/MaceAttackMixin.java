package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class MaceAttackMixin {
    @Inject(method = "attackEntity", at = @At("RETURN"))
    private void xing$consume(PlayerEntity player, Entity target, CallbackInfo ci) {
        var app = XingClient.INSTANCE;
        if (app != null && app.maceKill != null) app.maceKill.attacked(target);
    }
}
