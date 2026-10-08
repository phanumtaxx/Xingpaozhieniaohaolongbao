package dev.xingclient.ui;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;

/** Same antialiased Jost pixels and proportional advances as the approved HTML. */
public final class JostFont {
    private static final float[][] ADVANCE = new float[17][95];
    private static final Identifier[] ATLASES = new Identifier[17];
    static {
        try (var in = JostFont.class.getResourceAsStream("/assets/xingclient/textures/jost-metrics.json")) {
            if (in == null) throw new IllegalStateException("Missing Jost metrics");
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (int size=10;size<=16;size++) {
                ATLASES[size] = GuiDraw.texture("jost-"+size);
                var values=json.getAsJsonArray(Integer.toString(size));
                for(int i=0;i<95;i++) ADVANCE[size][i]=values.get(i).getAsFloat();
            }
        } catch(Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static int glyph(char c) { return c >= 32 && c <= 126 ? c-32 : '?' -32; }
    public static float width(String text, int size) { float result=0;for(char c:text.toCharArray())result+=ADVANCE[size][glyph(c)];return result; }
    /** y is the vertical center, matching the browser's line-box alignment. */
    public static void draw(DrawContext ctx, String text, double x, double y, int size, int color) {
        var matrix = new org.joml.Matrix4f(ctx.getMatrices().peek().getPositionMatrix());
        ctx.draw(provider -> {
            var vertices=provider.getBuffer(RenderLayer.getGuiTexturedOverlay(ATLASES[size]));
            float cursor=(float)x;
            for(char c:text.toCharArray()) {
                int i=glyph(c);float u=(i%16)/16f,v=(i/16)/6f,left=cursor-2,top=(float)y-16;
                if(c!=' ') {
                    vertices.vertex(matrix,left,top,0).texture(u,v).color(color);
                    vertices.vertex(matrix,left,top+32,0).texture(u,v+1/6f).color(color);
                    vertices.vertex(matrix,left+32,top+32,0).texture(u+1/16f,v+1/6f).color(color);
                    vertices.vertex(matrix,left+32,top,0).texture(u+1/16f,v).color(color);
                }
                cursor+=ADVANCE[size][i];
            }
        });
    }
    public static void center(DrawContext ctx,String text,double x,double y,int size,int color) { draw(ctx,text,x-width(text,size)/2,y,size,color); }
}
