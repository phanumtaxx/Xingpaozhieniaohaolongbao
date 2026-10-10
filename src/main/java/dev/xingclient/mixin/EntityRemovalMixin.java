package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import dev.xingclient.event.EntitiesRemovedEvent;
import net.minecraft.network.packet.s2c.play.EntitiesDestroyS2CPacket;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class EntityRemovalMixin {
    @Inject(method = "onEntitiesDestroy", at = @At("TAIL"))
    private void xingclient$entitiesRemoved(EntitiesDestroyS2CPacket packet, CallbackInfo ci) {
        if (XingClient.INSTANCE != null) {
            XingClient.INSTANCE.events.post(new EntitiesRemovedEvent(packet.getEntityIds()));
        }
    }
}
