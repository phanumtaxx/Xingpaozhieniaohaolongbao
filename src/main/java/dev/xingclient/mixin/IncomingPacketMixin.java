package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientConnection.class)
public abstract class IncomingPacketMixin {
    @Redirect(method = "handlePacket", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V"))
    private static <T extends PacketListener> void xing$receive(Packet<T> packet, T listener) {
        var client = MinecraftClient.getInstance();
        var app = XingClient.INSTANCE;
        if (app == null || app.settings == null || listener != client.getNetworkHandler()) {
            packet.apply(listener);
            return;
        }
        if (client.isOnThread()) {
            if (!app.receivePacket(packet)) packet.apply(listener);
            return;
        }
        // A packet handed off by vanilla throws here; its replay is handled by MainThreadPacketMixin.
        packet.apply(listener);
        client.execute(() -> {
            if (listener == client.getNetworkHandler()) app.managers.movementState.receive(packet);
        });
    }
}
