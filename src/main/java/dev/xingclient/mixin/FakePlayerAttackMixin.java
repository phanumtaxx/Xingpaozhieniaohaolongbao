package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import dev.xingclient.module.FakePlayerModule;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public abstract class FakePlayerAttackMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void xingclient$attackFakePlayer(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (XingClient.INSTANCE == null) return;
        XingClient.INSTANCE.modules.find("fake_player")
                .filter(FakePlayerModule.class::isInstance)
                .map(FakePlayerModule.class::cast)
                .ifPresent(module -> {
                    if (player instanceof ClientPlayerEntity clientPlayer
                            && module.handlesAttack(target, clientPlayer)) {
                        ci.cancel();
                    }
                });
    }
}
