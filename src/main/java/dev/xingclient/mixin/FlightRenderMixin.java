package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class FlightRenderMixin {
    @Inject(method = "updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",
            at = @At("TAIL"))
    private void xing$flightRender(AbstractClientPlayerEntity player, PlayerEntityRenderState state, float tickDelta, CallbackInfo ci) {
        var app = XingClient.INSTANCE;
        if (player != MinecraftClient.getInstance().player || app == null || app.flight == null || !app.flight.isEnabled()) return;
        if (app.flight.hidePose()) {
            state.isGliding = false;
            state.limbAmplitudeInverse = 1.0f;
            state.glidingTicks = 0.0f;
            state.applyFlyingRotation = false;
            state.flyingRotation = 0.0f;
        }
        var chestplate = app.flight.visualChestplate();
        if (!chestplate.isEmpty()) state.equippedChestStack = chestplate;
    }
}
