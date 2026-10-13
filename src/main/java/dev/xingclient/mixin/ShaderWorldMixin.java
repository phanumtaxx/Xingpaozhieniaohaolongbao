package dev.xingclient.mixin;

import dev.xingclient.module.NativeShadersModule;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.render.entity.EntityRenderDispatcher;

@Mixin(WorldRenderer.class)
public abstract class ShaderWorldMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void xing$beginMask(CallbackInfo info) {
        var module = NativeShadersModule.active();
        if (module != null) module.begin();
    }
    @Redirect(method = "renderEntity", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/render/entity/EntityRenderDispatcher;render(Lnet/minecraft/entity/Entity;DDDFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void xing$captureEntity(EntityRenderDispatcher dispatcher, Entity entity, double x, double y, double z,
        float tickDelta, MatrixStack matrices, VertexConsumerProvider vertices, int light) {
        var module = NativeShadersModule.active();
        dispatcher.render(entity, x, y, z, tickDelta, matrices, module == null ? vertices : module.capture(entity, vertices), light);
    }
    @Inject(method = "renderEntities", at = @At("TAIL"))
    private void xing$flushEntities(MatrixStack matrices, VertexConsumerProvider.Immediate vertices, Camera camera,
        RenderTickCounter ticks, List<Entity> entities, CallbackInfo info) {
        var module = NativeShadersModule.active();
        if (module != null) module.flush();
    }
}
