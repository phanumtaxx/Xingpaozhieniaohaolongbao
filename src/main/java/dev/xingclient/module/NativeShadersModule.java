package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.nativebridge.XingNativeBridge;
import dev.xingclient.render.NativeShaderEffects;
import dev.xingclient.render.ShaderRenderer;
import dev.xingclient.render.ShaderSettings;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import org.slf4j.LoggerFactory;

/** Minecraft adapter for Xing's native visual effects. Author: uint32. */
public final class NativeShadersModule extends Module {
    private ShaderRenderer renderer;
    private String failure;
    public NativeShadersModule() { super("shaders", "Shaders", ModuleCategory.VISUALS); }
    public static NativeShadersModule active() {
        var app = XingClient.INSTANCE;
        return app != null && app.shaders != null && app.shaders.isEnabled() ? app.shaders : null;
    }
    @Override protected void onEnable() {
        XingNativeBridge.loadLibrary(); failure = null; renderer = new ShaderRenderer();
    }
    @Override protected void onDisable() {
        ShaderRenderer previous = renderer;
        renderer = null;
        if (previous != null) previous.close();
    }
    @Override public String status() { return failure == null ? "Native effects / uint32" : "Renderer error: see latest.log"; }
    private ShaderSettings settings() { return XingClient.INSTANCE.settings.shaders; }

    public void begin() { guarded(renderer::begin); }
    public void flush() { guarded(renderer::flush); }
    public void finish() { guarded(() -> renderer.finish(settings())); }
    public Framebuffer routedTarget() { return renderer == null ? null : renderer.routedTarget(); }
    public boolean rendersSky() { return settings().sky; }
    public boolean vanillaCelestials() { return settings().vanillaCelestials; }
    public boolean vanillaSun() { return settings().vanillaSun; }
    public boolean vanillaMoon() { return settings().vanillaMoon; }
    public boolean sky() {
        if (!rendersSky()) return false;
        return guarded(() -> renderer.sky(settings()));
    }
    public VertexConsumerProvider capture(Entity entity, VertexConsumerProvider original) {
        var client = MinecraftClient.getInstance();
        if (client.player == null || entity.isSpectator()) return original;
        int category = entity == client.player ? 1 : entity instanceof PlayerEntity ? 0
            : entity instanceof EndCrystalEntity ? 2 : entity instanceof HostileEntity ? 4
            : entity instanceof PassiveEntity ? 5 : -1;
        boolean friend = entity instanceof PlayerEntity && entity != client.player
            && XingClient.INSTANCE.friends.isFriend(entity.getName().getString());
        var s = settings();
        return NativeShaderEffects.captures(category, s.targetMask(), client.player.squaredDistanceTo(entity), s.range,
            entity.isInvisible(), s.invisible, friend, s.friends) ? renderer.capture(original, category) : original;
    }
    public VertexConsumerProvider captureHands(VertexConsumerProvider original) {
        return settings().hands ? renderer.capture(original, 3) : original;
    }
    public VertexConsumerProvider captureHandsOnly(VertexConsumerProvider original) {
        return settings().hands ? renderer.captureOnly(original, 3) : layer -> dev.xingclient.render.HiddenVertexConsumer.INSTANCE;
    }
    private boolean guarded(Runnable action) {
        try { action.run(); return true; }
        catch (RuntimeException | LinkageError error) {
            failure = error.getMessage();
            LoggerFactory.getLogger("xingclient-shaders").error("Native effects renderer failed", error);
            try { setEnabled(false); }
            catch (RuntimeException cleanupError) {
                LoggerFactory.getLogger("xingclient-shaders").warn("Shader resource cleanup failed", cleanupError);
            }
            var app = XingClient.INSTANCE;
            app.settings.modules.get(id()).enabled = false; app.changed();
            return false;
        }
    }
}
