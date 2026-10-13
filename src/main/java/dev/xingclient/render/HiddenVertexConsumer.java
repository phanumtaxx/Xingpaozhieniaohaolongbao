package dev.xingclient.render;

import net.minecraft.client.render.VertexConsumer;

/** Discards the textured pass while shader masks can still receive geometry. */
public enum HiddenVertexConsumer implements VertexConsumer {
    INSTANCE;
    @Override public VertexConsumer vertex(float x, float y, float z) { return this; }
    @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
    @Override public VertexConsumer texture(float u, float v) { return this; }
    @Override public VertexConsumer overlay(int u, int v) { return this; }
    @Override public VertexConsumer light(int u, int v) { return this; }
    @Override public VertexConsumer normal(float x, float y, float z) { return this; }
}
