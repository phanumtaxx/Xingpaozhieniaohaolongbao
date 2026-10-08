package dev.xingclient.mixin;

import dev.xingclient.ui.PlayerSpectateScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class SpectateCameraMixin {
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    // Set the orbit before vanilla clips the third-person camera against terrain.
    // This changes only the camera; neither player's rotation is touched.
    @Inject(method="update",at=@At(value="INVOKE",target="Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private void xing$orbit(BlockView area,Entity target,boolean thirdPerson,boolean inverseView,float delta,CallbackInfo ci){
        if(MinecraftClient.getInstance().currentScreen instanceof PlayerSpectateScreen screen&&screen.ownsCamera(target))setRotation(screen.cameraYaw(),screen.cameraPitch());
    }
}
