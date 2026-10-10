package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class VelocityExplosionMixin {
    @Redirect(method = "onExplosion", at = @At(value = "INVOKE", target = "Ljava/util/Optional;ifPresent(Ljava/util/function/Consumer;)V"))
    private void xing$explosionVelocity(Optional<Vec3d> knockback, Consumer<? super Vec3d> consumer) {
        knockback.ifPresent(movement -> {
            var app = XingClient.INSTANCE;
            consumer.accept(app != null && app.settings != null ? app.managers.movementState.explosion(movement) : movement);
        });
    }
}
