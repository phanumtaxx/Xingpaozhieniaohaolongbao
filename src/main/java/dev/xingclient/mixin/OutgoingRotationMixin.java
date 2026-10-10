package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientConnection.class)
public abstract class OutgoingRotationMixin {
    @ModifyVariable(method = "send(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V",
            at = @At("HEAD"), argsOnly = true)
    private Packet<?> xingclient$rotate(Packet<?> packet) {
        if (XingClient.INSTANCE == null || !MinecraftClient.getInstance().isOnThread()) return packet;
        Packet<?> outgoing = XingClient.INSTANCE.managers.rotations.apply(packet);
        XingClient.INSTANCE.managers.rotations.observe(outgoing);
        return XingClient.INSTANCE.managers.network.trackOutgoing((ClientConnection) (Object) this, packet, outgoing);
    }
}
