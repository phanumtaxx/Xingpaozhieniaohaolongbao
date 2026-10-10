package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import dev.xingclient.event.CrystalSpawnEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class CrystalSpawnMixin {
    @Inject(method = "onEntitySpawn", at = @At("TAIL"))
    private void xingclient$crystalSpawned(EntitySpawnS2CPacket packet, CallbackInfo ci) {
        if (XingClient.INSTANCE == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        Entity entity = client.world.getEntityById(packet.getEntityId());
        if (entity instanceof EndCrystalEntity crystal) {
            XingClient.INSTANCE.managers.synchronizeWorld(client);
            XingClient.INSTANCE.managers.serverState.handleCrystalSpawn(packet.getEntityId(), crystal.getPos());
            XingClient.INSTANCE.events.post(new CrystalSpawnEvent(crystal));
        }
    }
}
