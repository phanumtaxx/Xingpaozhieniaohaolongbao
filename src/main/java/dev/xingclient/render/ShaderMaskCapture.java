package dev.xingclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexConsumers;
import net.minecraft.client.util.BufferAllocator;

/** Captures silhouettes into a private target without consuming vanilla outline buffers. */
public final class ShaderMaskCapture implements AutoCloseable {
    private final BufferAllocator allocator = new BufferAllocator(262144);
    private final VertexConsumerProvider.Immediate consumers = VertexConsumerProvider.immediate(allocator);
    private Framebuffer target;
    private boolean routing;
    private boolean hasGeometry;

    public Framebuffer routedTarget() { return routing ? target : null; }
    public Framebuffer target() { return target; }
    public boolean hasGeometry() { return hasGeometry; }

    public void begin(int width, int height) {
        if (target == null) {
            target = new SimpleFramebuffer("Xing shader mask", width, height, false);
            target.setFilter(FilterMode.LINEAR);
        }
        else if (target.textureWidth != width || target.textureHeight != height) target.resize(width, height);
        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorAttachment(), 0);
        hasGeometry = false;
    }

    public VertexConsumerProvider wrap(VertexConsumerProvider original, int category) {
        return wrap(original, category, true);
    }
    public VertexConsumerProvider wrap(VertexConsumerProvider original, int category, boolean textured) {
        return layer -> {
            VertexConsumer base = textured ? original.getBuffer(layer) : HiddenVertexConsumer.INSTANCE;
            RenderLayer outline = layer.isOutline() ? layer : layer.getAffectedOutline().orElse(null);
            if (outline == null || target == null) return base;
            VertexConsumer mask;
            routing = true;
            try { mask = consumers.getBuffer(outline); }
            finally { routing = false; }
            hasGeometry = true;
            return textured ? VertexConsumers.union(base, new MaskVertexConsumer(mask, (category + 1) * 32))
                : new MaskVertexConsumer(mask, (category + 1) * 32);
        };
    }

    public void flush() {
        routing = true;
        try { consumers.draw(); }
        finally { routing = false; }
    }

    @Override public void close() {
        if (target != null) { target.delete(); target = null; }
        allocator.close();
    }

    private record MaskVertexConsumer(VertexConsumer delegate, int category) implements VertexConsumer {
        @Override public VertexConsumer vertex(float x, float y, float z) {
            delegate.vertex(x, y, z); delegate.color(category, 0, 0, 255); return this;
        }
        @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
        @Override public VertexConsumer texture(float u, float v) { delegate.texture(u, v); return this; }
        @Override public VertexConsumer overlay(int u, int v) { return this; }
        @Override public VertexConsumer light(int u, int v) { return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { return this; }
    }
}
