package dev.xingclient.mixin;

import dev.xingclient.XingClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPlayerEntity.class)
public abstract class CrashoutMovementMixin {
    @ModifyVariable(method = "move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3d xing$flightMovement(Vec3d movement) {
        var app = XingClient.INSTANCE;
        return app != null && app.crashout != null ? app.crashout.applyMovement(movement) : movement;
    }
}
