package dev.xingclient.render.web;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/** JNI adapter for the separately packaged CEF runtime. Author: uint32. */
public final class NativeWebEngine {
    public static final int POLYGON_STRIDE = 49;
    private static Path runtime;
    private NativeWebEngine() {}
    public static synchronized Path load() {
        if(runtime != null) return runtime;
        String configured=System.getProperty("xing.web.runtime");
        Path found=configured==null?findRuntime():Path.of(configured).toAbsolutePath();
        if(found==null || !Files.isRegularFile(found.resolve("xing-web.dll"))
                || !Files.isRegularFile(found.resolve("xing-web-process.exe")))
            throw new IllegalStateException("Missing CEF runtime; compile native/web or set xing.web.runtime");
        System.load(found.resolve("xing-web.dll").toString());
        runtime=found;
        return runtime;
    }
    private static Path findRuntime() {
        for(Path start:new Path[]{FabricLoader.getInstance().getGameDir(),Path.of(System.getProperty("user.dir"))}) {
            Path current=start.toAbsolutePath();
            for(int depth=0;current!=null && depth<6;depth++,current=current.getParent()) {
                Path candidate=current.resolve("native/web/runtime");
                if(Files.isRegularFile(candidate.resolve("xing-web.dll"))) return candidate;
            }
        }
        return null;
    }
    public static native void start(String directory,String url,String cache,int width,int height,int fps);
    public static native void resize(int width,int height);
    public static native void setFrameRate(int fps);
    public static native void publish(String json);
    public static native void reload();
    public static native void editorFocus(boolean focused);
    public static native void pointer(int kind, int x, int y, int button, int modifiers, int wheel);
    public static native void key(int kind, int code, int modifiers);
    public static native String[] drainCommands();
    public static native void installExtension(String id,String source);
    public static native String surfaceShader(int stage);
    public static native int copyPixels(ByteBuffer destination,int[] dimensions,java.nio.FloatBuffer backdrops);
    public static native void stop();
    public static native void shutdown();
    public static native void projectPolygons(double[] matrix, double[] camera, double[] points, int[] sizes, double[] output, int width, int height, boolean twoSided);
    public static native void project(double[] matrix,double[] camera,double[] points,double[] output,int width,int height);
}
