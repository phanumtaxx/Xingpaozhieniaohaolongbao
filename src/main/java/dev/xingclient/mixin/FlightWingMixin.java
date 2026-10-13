package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.ElytraFlightController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ElytraFlightController.class)
public abstract class FlightWingMixin {
    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;isGliding()Z"))
    private boolean xing$flightWings(LivingEntity entity) {
        var app = XingClient.INSTANCE;
        if (entity == MinecraftClient.getInstance().player && app != null && app.flight != null && app.flight.hidePose()) return false;
        return entity.isGliding();
    }
}
