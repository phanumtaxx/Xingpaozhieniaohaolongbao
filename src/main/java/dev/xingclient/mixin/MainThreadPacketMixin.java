package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NetworkThreadUtils.class)
public abstract class MainThreadPacketMixin {
    @Redirect(method = "method_11072", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/Packet;apply(Lnet/minecraft/network/listener/PacketListener;)V"))
    private static <T extends PacketListener> void xing$apply(Packet<T> packet, T listener) {
        var app = XingClient.INSTANCE;
        var client = MinecraftClient.getInstance();
        if (client.isOnThread() && listener == client.getNetworkHandler() && app != null && app.receivePacket(packet)) return;
        packet.apply(listener);
    }
}
