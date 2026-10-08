package dev.xingclient.mixin;

import dev.xingclient.ui.XingScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GameRenderer.class)
public abstract class ClickGuiBlurMixin {
    @ModifyVariable(method="renderBlur",at=@At("STORE"),ordinal=0)
    private float xingclient$subtleBackgroundBlur(float vanillaRadius){
        // Keep this screen's subtle blur independent of vanilla's menu-blur slider.
        return MinecraftClient.getInstance().currentScreen instanceof XingScreen?4.0f:vanillaRadius;
    }
}
