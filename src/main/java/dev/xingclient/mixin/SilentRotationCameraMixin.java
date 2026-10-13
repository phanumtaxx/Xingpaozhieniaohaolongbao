package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Displays camera rotation independently of launch physics. Author: uint32. */
@Mixin(Camera.class)
public abstract class SilentRotationCameraMixin {
    @ModifyArgs(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 1))
    private void xing$normalCameraRotation(Args args) {
        xing$cameraRotation(args);
    }

    @ModifyArgs(method = "update", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0))
    private void xing$cameraRotation(Args args) {
        var app = XingClient.INSTANCE;
        if (app == null || app.managers == null) return;
        var rotation = app.managers.rotations.cameraRotation(((Camera) (Object) this).getFocusedEntity());
        if (rotation == null) return;
        args.set(0, rotation.yaw());
        args.set(1, rotation.pitch());
    }
}
