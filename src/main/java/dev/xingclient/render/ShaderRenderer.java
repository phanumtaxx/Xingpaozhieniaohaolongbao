package dev.xingclient.render;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.render.VertexConsumerProvider;

/** Owns capture and GPU resources for one enabled Shaders module. */
public final class ShaderRenderer implements AutoCloseable {
    private final ShaderMaskCapture mask = new ShaderMaskCapture();
    private ShaderGpuProgram entities, sky, blur;
    private Framebuffer blurScratch, glow;
    private long started = System.nanoTime();
    private boolean frameStarted;

    public void begin() {
        var framebuffer = MinecraftClient.getInstance().getFramebuffer();
        mask.begin(framebuffer.textureWidth, framebuffer.textureHeight);
        frameStarted = true;
    }
    public VertexConsumerProvider capture(VertexConsumerProvider original, int category) {
        return frameStarted ? mask.wrap(original, category) : original;
    }
    public VertexConsumerProvider captureOnly(VertexConsumerProvider original, int category) {
        return frameStarted ? mask.wrap(original, category, false) : layer -> HiddenVertexConsumer.INSTANCE;
    }
    public Framebuffer routedTarget() { return mask.routedTarget(); }
    public void flush() { if (frameStarted) mask.flush(); }

    public void finish(ShaderSettings settings) {
        if (!frameStarted) return;
        try {
            flush();
            if (!mask.hasGeometry()) return;
            var destination = MinecraftClient.getInstance().getFramebuffer();
            ensureBloom(destination.textureWidth, destination.textureHeight);
            if (entities == null) entities = new ShaderGpuProgram(1);
            if (blur == null) blur = new ShaderGpuProgram(3);
            blur.draw(blurScratch, mask.target(), null, settings, time(), null, true);
            blur.draw(glow, blurScratch, null, settings, time(), null, false);
            entities.draw(destination, mask.target(), glow, settings, time(), camera(), false);
        } finally { frameStarted = false; }
    }
    public void sky(ShaderSettings settings) {
        if (sky == null) sky = new ShaderGpuProgram(2);
        sky.draw(MinecraftClient.getInstance().getFramebuffer(), null, null, settings, time(), camera(), false);
    }
    private void ensureBloom(int width, int height) {
        int w = Math.max(1, width / 2), h = Math.max(1, height / 2);
        if (blurScratch == null) {
            blurScratch = new SimpleFramebuffer("Xing bloom scratch", w, h, false);
            blurScratch.setFilter(FilterMode.LINEAR);
        }
        else if (blurScratch.textureWidth != w || blurScratch.textureHeight != h) blurScratch.resize(w, h);
        if (glow == null) {
            glow = new SimpleFramebuffer("Xing bloom", w, h, false);
            glow.setFilter(FilterMode.LINEAR);
        }
        else if (glow.textureWidth != w || glow.textureHeight != h) glow.resize(w, h);
    }
    private float time() { return (System.nanoTime() - started) / 1_000_000_000f; }
    private float[] camera() {
        var client = MinecraftClient.getInstance();
        var camera = client.gameRenderer.getCamera();
        return new float[] {(float)Math.toRadians(-camera.getYaw()), (float)Math.toRadians(-camera.getPitch()),
            (float)Math.toRadians(client.options.getFov().getValue()),
            (float)client.getFramebuffer().textureWidth / client.getFramebuffer().textureHeight};
    }
    @Override public void close() {
        mask.close();
        if (entities != null) entities.close();
        if (sky != null) sky.close();
        if (blur != null) blur.close();
        if (blurScratch != null) blurScratch.delete();
        if (glow != null) glow.delete();
        frameStarted = false;
    }
}
