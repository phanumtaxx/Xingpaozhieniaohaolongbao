package dev.xingclient.mixin;
import dev.xingclient.ui.GuiDraw;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replace only the title panorama, preserving vanilla widgets, logo and fade. */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin {
    @Shadow private float backgroundAlpha;
    @Inject(method="renderPanoramaBackground",at=@At("HEAD"),cancellable=true)
    private void xingclient$background(DrawContext ctx,float delta,CallbackInfo ci) {
        GuiDraw.titleBackground(ctx,ctx.getScaledWindowWidth(),ctx.getScaledWindowHeight(),backgroundAlpha);
        ci.cancel();
    }
}
