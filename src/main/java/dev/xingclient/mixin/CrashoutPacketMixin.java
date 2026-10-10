package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ClientConnection.class, priority = 1500)
public abstract class CrashoutPacketMixin {
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V",
            at = @At("HEAD"), cancellable = true)
    private void xing$flightPacket(Packet<?> packet, PacketCallbacks callbacks, boolean flush, CallbackInfo ci) {
        var client = MinecraftClient.getInstance();
        var app = XingClient.INSTANCE;
        if (client.isOnThread() && client.getNetworkHandler() != null
                && client.getNetworkHandler().getConnection() == (Object) this && packet instanceof PlayerMoveC2SPacket
                && app != null && !app.managers.network.isQuietSend() && app.crashout != null && !app.crashout.allowMovementPacket()) ci.cancel();
    }
}
