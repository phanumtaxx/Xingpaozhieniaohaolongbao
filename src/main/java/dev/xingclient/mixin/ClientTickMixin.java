package dev.xingclient.mixin;
import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class ClientTickMixin {
    @Inject(method="tick",at=@At("TAIL"))
    private void xingclient$tick(CallbackInfo ci) { if(XingClient.INSTANCE!=null)XingClient.INSTANCE.tick((MinecraftClient)(Object)this); }
}
