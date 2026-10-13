package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class NametagsMixin {
    @Inject(method="updateRenderState(Lnet/minecraft/client/network/AbstractClientPlayerEntity;Lnet/minecraft/client/render/entity/state/PlayerEntityRenderState;F)V",at=@At("TAIL"))
    private void xing$replacePlayerLabel(AbstractClientPlayerEntity player,PlayerEntityRenderState state,float delta,CallbackInfo info) {
        var app=XingClient.INSTANCE;
        if(app!=null&&app.nametags!=null&&app.nametags.replaces(player.getUuid())) state.displayName=null;
    }
}
