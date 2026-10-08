package dev.xingclient.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

public final class GuiDraw {
    public static Identifier texture(String name) { return Identifier.of("xingclient", "textures/" + name + ".png"); }
    public static void rect(DrawContext ctx, double x, double y, double w, double h, int color) {
        if (w > 0 && h > 0) ctx.fill((int)Math.round(x), (int)Math.round(y), (int)Math.round(x + w), (int)Math.round(y + h), color);
    }
    public static void border(DrawContext ctx, double x, double y, double w, double h, int color) {
        rect(ctx,x,y,w,1,color);rect(ctx,x,y+h-1,w,1,color);rect(ctx,x,y,1,h,color);rect(ctx,x+w-1,y,1,h,color);
    }
    public static void texture(DrawContext ctx, Identifier texture, float x, float y, float w, float h, int color) { quad(ctx, texture, x, y, w, h, 0, 0, 1, 1, color); }
    public static void quad(DrawContext ctx, Identifier texture, float x, float y, float w, float h, float u, float v, float u2, float v2, int color) {
        var matrix = new org.joml.Matrix4f(ctx.getMatrices().peek().getPositionMatrix());
        ctx.draw(provider -> {
            var vertices = provider.getBuffer(RenderLayer.getGuiTexturedOverlay(texture));
            vertices.vertex(matrix,x,y,0).texture(u,v).color(color);
            vertices.vertex(matrix,x,y+h,0).texture(u,v2).color(color);
            vertices.vertex(matrix,x+w,y+h,0).texture(u2,v2).color(color);
            vertices.vertex(matrix,x+w,y,0).texture(u2,v).color(color);
        });
    }
    public static void titleBackground(DrawContext ctx, int width, int height, float alpha) {
        float imageAspect = 736f/414f, screenAspect = (float)width/height;
        float u = 0, v = 0, uw = 1, vh = 1;
        if (screenAspect > imageAspect) { vh = imageAspect/screenAspect; v = (1-vh)/2; }
        else { uw = screenAspect/imageAspect; u = (1-uw)/2; }
        quad(ctx,texture("title-background"),0,0,width,height,u,v,u+uw,v+vh,((int)(Math.clamp(alpha,0,1)*255)<<24)|0xffffff);
    }
    private GuiDraw() {}
}
