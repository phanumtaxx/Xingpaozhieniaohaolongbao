package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ServerInventoryMixin {
    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
    private void xingclient$inventorySlotUpdated(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        if (XingClient.INSTANCE == null) return;
        XingClient.INSTANCE.managers.synchronizeWorld(MinecraftClient.getInstance());
        XingClient.INSTANCE.managers.serverState.handleSlotUpdate(packet);
    }

    @Inject(method = "onInventory", at = @At("TAIL"))
    private void xingclient$inventoryUpdated(InventoryS2CPacket packet, CallbackInfo ci) {
        if (XingClient.INSTANCE == null) return;
        XingClient.INSTANCE.managers.synchronizeWorld(MinecraftClient.getInstance());
        XingClient.INSTANCE.managers.serverState.handleInventory(packet);
    }
}
