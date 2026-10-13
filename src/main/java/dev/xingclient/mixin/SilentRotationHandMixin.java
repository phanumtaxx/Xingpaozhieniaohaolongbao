package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps first-person hands aligned with the displayed camera. Author: uint32. */
@Mixin(HeldItemRenderer.class)
public abstract class SilentRotationHandMixin {
    private static final String RENDER = "renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V";

    private static Float cameraAngle(ClientPlayerEntity player, boolean pitch) {
        var app = XingClient.INSTANCE;
        if (app == null || app.managers == null) return null;
        var rotation = app.managers.rotations.cameraRotation(player);
        return rotation == null ? null : pitch ? rotation.pitch() : rotation.yaw();
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getLerpedPitch(F)F"))
    private float xing$getLerpedPitch(ClientPlayerEntity player, float delta) {
        var angle = cameraAngle(player, true);
        return angle == null ? player.getLerpedPitch(delta) : angle;
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getPitch(F)F"))
    private float xing$getPitch(ClientPlayerEntity player, float delta) {
        var angle = cameraAngle(player, true);
        return angle == null ? player.getPitch(delta) : angle;
    }

    @Redirect(method = RENDER, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;getYaw(F)F"))
    private float xing$getYaw(ClientPlayerEntity player, float delta) {
        var angle = cameraAngle(player, false);
        return angle == null ? player.getYaw(delta) : angle;
    }

    @Redirect(method = RENDER, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;lastRenderPitch:F"))
    private float xing$lastRenderPitch(ClientPlayerEntity player) {
        var angle = cameraAngle(player, true);
        return angle == null ? player.lastRenderPitch : angle;
    }

    @Redirect(method = RENDER, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;renderPitch:F"))
    private float xing$renderPitch(ClientPlayerEntity player) {
        var angle = cameraAngle(player, true);
        return angle == null ? player.renderPitch : angle;
    }

    @Redirect(method = RENDER, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;lastRenderYaw:F"))
    private float xing$lastRenderYaw(ClientPlayerEntity player) {
        var angle = cameraAngle(player, false);
        return angle == null ? player.lastRenderYaw : angle;
    }

    @Redirect(method = RENDER, at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;renderYaw:F"))
    private float xing$renderYaw(ClientPlayerEntity player) {
        var angle = cameraAngle(player, false);
        return angle == null ? player.renderYaw : angle;
    }
}
