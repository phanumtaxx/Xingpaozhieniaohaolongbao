package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Routes mouse look to the displayed camera during launch. Author: uint32. */
@Mixin(Entity.class)
public abstract class SilentRotationLookMixin {
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void xing$cameraLook(double deltaX, double deltaY, CallbackInfo ci) {
        var app = XingClient.INSTANCE;
        if (app != null && app.managers != null
                && app.managers.rotations.rotateCamera((Entity) (Object) this, deltaX, deltaY)) ci.cancel();
    }
}
