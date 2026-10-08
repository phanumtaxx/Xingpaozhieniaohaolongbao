package dev.xingclient.mixin;
import dev.xingclient.HazelTarget;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ClientPlayNetworkHandler.class)
public abstract class HazelCommandMixin {
    @Inject(method="sendChatCommand",at=@At("HEAD"),cancellable=true)
    private void xing$hazel(String command,CallbackInfo ci){if(HazelTarget.command(command))ci.cancel();}
}
