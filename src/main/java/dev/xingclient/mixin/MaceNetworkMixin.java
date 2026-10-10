package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ExplosionS2CPacket;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class MaceNetworkMixin {
    // TAIL runs after vanilla's forceMainThread and after the server's movement is applied.
    @Inject(method = "onExplosion", at = @At("TAIL"))
    private void xing$explosion(ExplosionS2CPacket packet, CallbackInfo ci) {
        var app = XingClient.INSTANCE;
        if (app != null && app.maceKill != null) packet.playerKnockback().ifPresent(v -> app.maceKill.explosion(packet.center(), v));
    }
    @Inject(method = "onEntityVelocityUpdate", at = @At("TAIL"))
    private void xing$velocity(EntityVelocityUpdateS2CPacket packet, CallbackInfo ci) {
        var app = XingClient.INSTANCE; var player = MinecraftClient.getInstance().player;
        if (app != null && app.maceKill != null && player != null && packet.getEntityId() == player.getId()) app.maceKill.velocityUpdate();
    }
    @Inject(method = "onPlayerPositionLook", at = @At("TAIL"))
    private void xing$correction(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        var app = XingClient.INSTANCE;
        if (app != null && app.maceKill != null) app.maceKill.corrected();
    }
}
