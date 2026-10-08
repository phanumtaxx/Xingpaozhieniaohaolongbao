package dev.xingclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.MusicTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicTracker.class)
public abstract class MenuMusicMixin {
    @Shadow public abstract void stop();
    private boolean xingclient$wasMenu;
    @Inject(method="tick",at=@At("HEAD"),cancellable=true)
    private void xingclient$menuMusic(CallbackInfo ci){
        if(MinecraftClient.getInstance().currentScreen!=null){
            if(!xingclient$wasMenu)stop();
            xingclient$wasMenu=true;ci.cancel();
        }else xingclient$wasMenu=false;
    }
}
