package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import dev.xingclient.event.WorldBlockUpdateEvent;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class WorldBlockUpdateMixin {
    @Inject(method = "onBlockUpdate", at = @At("TAIL"))
    private void xingclient$blockUpdated(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        if (XingClient.INSTANCE != null) {
            XingClient.INSTANCE.managers.synchronizeWorld(MinecraftClient.getInstance());
            XingClient.INSTANCE.managers.predictions.reconcileLatestBlock(packet.getPos(), packet.getState());
        }
        postWorldUpdate();
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("TAIL"))
    private void xingclient$blocksUpdated(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        if (XingClient.INSTANCE != null) {
            XingClient.INSTANCE.managers.synchronizeWorld(MinecraftClient.getInstance());
            packet.visitUpdates((position, state) -> XingClient.INSTANCE.managers.predictions.reconcileLatestBlock(position, state));
        }
        postWorldUpdate();
    }

    private static void postWorldUpdate() {
        if (XingClient.INSTANCE != null) {
            XingClient.INSTANCE.events.post(new WorldBlockUpdateEvent());
        }
    }
}
