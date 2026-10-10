package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public abstract class PacketCommitMixin {
    @Inject(method = "sendImmediately(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/PacketCallbacks;Z)V",
            at = @At("RETURN"))
    private void xingclient$committed(Packet<?> packet, PacketCallbacks callbacks, boolean flush, CallbackInfo ci) {
        if (XingClient.INSTANCE != null) {
            XingClient.INSTANCE.managers.network.committed((ClientConnection) (Object) this, packet);
        }
    }
}
