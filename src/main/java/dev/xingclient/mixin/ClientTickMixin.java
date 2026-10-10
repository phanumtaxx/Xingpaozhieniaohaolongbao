package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import dev.xingclient.event.ClientTickEvent;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class ClientTickMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void xingclient$tickStart(CallbackInfo ci) {
        if (XingClient.INSTANCE != null) {
            XingClient.INSTANCE.postTickEvent(ClientTickEvent.Phase.START);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void xingclient$tick(CallbackInfo ci) {
        if (XingClient.INSTANCE != null) {
            XingClient.INSTANCE.postTickEvent(ClientTickEvent.Phase.END);
            XingClient.INSTANCE.tick((MinecraftClient) (Object) this);
            if (XingClient.INSTANCE.maceKill != null) {
                XingClient.INSTANCE.maceKill.pauseAtTickEnd();
            }
        }
    }
}
