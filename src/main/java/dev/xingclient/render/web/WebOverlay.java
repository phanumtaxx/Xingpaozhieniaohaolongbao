package dev.xingclient.render.web;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

/** Hosts local HTML and publishes reusable world anchors and module data. Author: uint32. */
public final class WebOverlay implements AutoCloseable {
    private static final Gson JSON=new Gson();
    private final Map<String,Supplier<Vec3d>> anchors=new LinkedHashMap<>();
    private final Map<String,JsonElement> channels=new LinkedHashMap<>();
    private final Map<String,Supplier<?>> dynamicChannels=new LinkedHashMap<>();
    private final WorldProjection projection=new WorldProjection();
    private final Map<String,String> extensions=new LinkedHashMap<>();
    private final Map<String,Supplier<Double>> backdrops=new LinkedHashMap<>();
    private Supplier<Double> backgroundBlur;
    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override void close();
    }
    private ByteBuffer pixels;
    private final int[] paintedSize=new int[3];
    private final java.nio.FloatBuffer backdropRects=BufferUtils.createFloatBuffer(256*5);
    private WebSurface surface;
    private WebItemIcons itemIcons;
    private boolean running, loaded;
    private int width,height,fps;
    private long lastPublish;
    public static Path assetDirectory() {
        return FabricLoader.getInstance().getConfigDir().resolve("xingclient/web").toAbsolutePath();
    }
    public void start(int frameRate) {
        requireClientThread();
        if(running) return;
        Path runtime=NativeWebEngine.load();loaded=true;
        Path assets=assetDirectory(), cache=assets.resolve("cache");
        try {
            Files.createDirectories(cache);
            for(String file:new String[]{"index.html","style.css","engine.js"}) {
                Path target=assets.resolve(file);
                if(Files.exists(target)) continue;
                try(var input=WebOverlay.class.getResourceAsStream("/assets/xingclient/web/"+file)) {
                    if(input==null) throw new IOException("Missing web template: "+file);
                    Files.copy(input,target);
                }
            }
        } catch(IOException error) { throw new IllegalStateException("Cannot prepare HTML assets",error); }
        var framebuffer=MinecraftClient.getInstance().getFramebuffer();
        width=framebuffer.textureWidth;height=framebuffer.textureHeight;fps=resolveFrameRate(frameRate);
        NativeWebEngine.start(runtime.toString(),assets.resolve("index.html").toUri().toASCIIString(),cache.toString(),width,height,fps);
        running=true;lastPublish=0;
        itemIcons=new WebItemIcons();
        extensions.forEach(NativeWebEngine::installExtension);
    }
    public String itemIcon(net.minecraft.item.ItemStack stack) {
        requireClientThread();
        return running&&itemIcons!=null?itemIcons.reference(stack):null;
    }
    public Framebuffer routedItemTarget() { return itemIcons==null?null:itemIcons.routedTarget(); }
    public void installAsset(String file) {
        requireClientThread();
        Path target=assetDirectory().resolve(file);
        if(Files.exists(target)) return;
        try(var input=WebOverlay.class.getResourceAsStream("/assets/xingclient/web/"+file)) {
            if(input==null) throw new IOException("Missing web asset: "+file);
            Files.copy(input,target);
        } catch(IOException error) {throw new IllegalStateException("Cannot install web asset",error);}
    }
    public void installMarkup(String id,String file) {
        requireClientThread();installAsset(file);
        final String html;
        try {html=Files.readString(assetDirectory().resolve(file));}
        catch(IOException error) {throw new IllegalStateException("Cannot load HUD markup",error);}
        String source="""
            (() => {
              const id = %s;
              if (document.getElementById(id)) return;
              const templates = document.createElement('div');
              templates.id = id; templates.hidden = true;
              templates.innerHTML = %s;
              document.body.append(templates);
            })();
            """.formatted(JSON.toJson("xing-markup-"+id),JSON.toJson(html));
        String key="markup:"+id;
        extensions.put(key,source);
        if(running) NativeWebEngine.installExtension(key,source);
    }
    public Registration registerChannel(String name,Supplier<?> source) {
        requireClientThread();Objects.requireNonNull(name);Objects.requireNonNull(source);
        if(name.isBlank()||channels.containsKey(name)||dynamicChannels.putIfAbsent(name,source)!=null)
            throw new IllegalArgumentException("Duplicate or blank web channel: "+name);
        return ()->{requireClientThread();dynamicChannels.remove(name,source);};
    }
    public void installFont(String family,String file) {
        requireClientThread();
        installAsset(file);
        final String encoded;
        try { encoded=Base64.getEncoder().encodeToString(Files.readAllBytes(assetDirectory().resolve(file))); }
        catch(IOException error) {throw new IllegalStateException("Cannot load web font",error);}
        String source="""
            (() => {
              const family = %s;
              if ([...document.fonts].some(face => face.family === family)) return;
              const face = new FontFace(family, %s, {weight: '100 900', style: 'normal'});
              document.fonts.add(face);
              face.load().catch(error => console.error('Xing font failed to load:', family, error));
            })();
            """.formatted(JSON.toJson(family),JSON.toJson("url(data:"+(file.endsWith(".ttf")?"font/ttf":"font/woff2")+";base64,"+encoded+")"));
        String id="font:"+family;
        extensions.put(id,source);
        if(running) NativeWebEngine.installExtension(id,source);
    }
    public void installExtension(String id,String script,String stylesheet) {
        requireClientThread();
        for(String file:new String[]{script,stylesheet}) {
            if(!file.matches("[a-zA-Z0-9_-]+\\.(js|css)")) throw new IllegalArgumentException("Invalid extension filename");
            Path target=assetDirectory().resolve(file);
            if(Files.exists(target)) continue;
            try(var input=WebOverlay.class.getResourceAsStream("/assets/xingclient/web/"+file)) {
                if(input==null) throw new IOException("Missing web extension: "+file);
                Files.copy(input,target);
            } catch(IOException error) {throw new IllegalStateException("Cannot install web extension",error);}
        }
        String source="""
            (() => {
              const id = %s;
              if (document.getElementById(id + '-script')) return;
              const style = document.createElement('link');
              style.id = id + '-style'; style.rel = 'stylesheet'; style.href = %s;
              const script = document.createElement('script');
              script.id = id + '-script'; script.src = %s;
              document.head.append(style, script);
            })();
            """.formatted(JSON.toJson("xing-"+id),JSON.toJson(stylesheet),JSON.toJson(script));
        extensions.put(id,source);
        if(running) NativeWebEngine.installExtension(id,source);
    }
    public Registration requestBackgroundBlur(Supplier<Double> radius) {
        requireClientThread();
        Objects.requireNonNull(radius);
        if (backgroundBlur != null) throw new IllegalStateException("Background blur already registered");
        backgroundBlur = radius;
        return () -> {
            requireClientThread();
            if (backgroundBlur == radius) backgroundBlur = null;
        };
    }
    public Registration requestBackdrop(String id,Supplier<Double> radius) {
        requireClientThread();Objects.requireNonNull(id);Objects.requireNonNull(radius);
        if(backdrops.putIfAbsent(id,radius)!=null) throw new IllegalArgumentException("Duplicate backdrop owner: "+id);
        return ()->{requireClientThread();backdrops.remove(id,radius);};
    }
    public Registration track(String id,Supplier<Vec3d> position) {
        requireClientThread();Objects.requireNonNull(id);Objects.requireNonNull(position);
        if(id.isBlank()||anchors.putIfAbsent(id,position)!=null) throw new IllegalArgumentException("Duplicate or blank world anchor: "+id);
        return ()->{requireClientThread();anchors.remove(id,position);};
    }
    public void publish(String channel,Object data) {
        requireClientThread();Objects.requireNonNull(channel);
        if(dynamicChannels.containsKey(channel)) throw new IllegalArgumentException("Channel has a registered data source: "+channel);
        channels.put(channel,JSON.toJsonTree(data));
    }
    public void removeChannel(String channel) { requireClientThread();channels.remove(channel); }
    public void capture(Camera camera,Matrix4f position,Matrix4f lens) {
        if(running) projection.capture(camera,position,lens);
    }
    public boolean projectPolygons(double[] points, int[] sizes, double[] output, boolean twoSided) {
        requireClientThread();
        if (!running || !projection.ready()) return false;
        projection.projectPolygons(points, sizes, output, width, height, twoSided);
        return true;
    }
    public boolean projectPoints(double[] points, double[] output) {
        requireClientThread();
        if (!running || !projection.ready()) return false;
        projection.project(points, output, width, height);
        return true;
    }
    public void reload() { requireClientThread();if(running) NativeWebEngine.reload(); }
    public void render(DrawContext context,int frameRate) {
        requireClientThread();
        if(!running) return;
        int targetFps = resolveFrameRate(frameRate);
        if (fps != targetFps) {
            fps = targetFps;
            NativeWebEngine.setFrameRate(fps);
            lastPublish = 0;
        }
        var client=MinecraftClient.getInstance();
        var destination=client.getFramebuffer();
        if(client.world==null) projection.invalidate();
        int newWidth=destination.textureWidth,newHeight=destination.textureHeight;
        if(newWidth!=width||newHeight!=height) { width=newWidth;height=newHeight;NativeWebEngine.resize(width,height); }
        if(itemIcons!=null) itemIcons.prepare(context);
        long now=System.nanoTime();
        if (followsVsync() || now - lastPublish >= 1_000_000_000L / fps) {
            publishFrame();
            lastPublish = now;
        }
        int capacity=Math.multiplyExact(Math.multiplyExact(width,height),4);
        if(pixels==null||pixels.capacity()<capacity) pixels=BufferUtils.createByteBuffer(capacity);
        int changed=NativeWebEngine.copyPixels(pixels,paintedSize,backdropRects);
        if(changed<0) {
            pixels=BufferUtils.createByteBuffer(Math.multiplyExact(Math.multiplyExact(paintedSize[0],paintedSize[1]),4));
            changed=NativeWebEngine.copyPixels(pixels,paintedSize,backdropRects);
        }
        if(surface==null && changed>0) surface=new WebSurface();
        if(surface!=null) {
            context.draw();
            if(changed>0) { pixels.clear();surface.upload(pixels,paintedSize[0],paintedSize[1]); }
            double blur=0;
            for(var requested:backdrops.values()) {
                Double radius=requested.get();
                if(radius!=null&&Double.isFinite(radius)) blur=Math.max(blur,Math.clamp(radius,0,12));
            }
            if (backgroundBlur != null) {
                Double radius = backgroundBlur.get();
                blur = radius != null && Double.isFinite(radius) ? Math.clamp(radius, 0, 12) : 0;
            }
            surface.draw(destination,blur*client.getWindow().getScaleFactor(),backdropRects,paintedSize[2]);
        }
    }
    private static boolean followsVsync() {
        var client = MinecraftClient.getInstance();
        return dev.xingclient.XingClient.INSTANCE.settings.webOverlay.followVsync
            && client.options.getEnableVsync().getValue();
    }

    private static int resolveFrameRate(int manualFps) {
        if (!followsVsync()) return Math.clamp(manualFps, 15, 360);
        var window = MinecraftClient.getInstance().getWindow();
        var monitor = window.getMonitor();
        var mode = monitor == null ? null : org.lwjgl.glfw.GLFW.glfwGetVideoMode(monitor.getHandle());
        int refreshRate = mode == null ? window.getRefreshRate() : mode.refreshRate();
        return Math.clamp(refreshRate > 0 ? refreshRate : 60, 1, 1000);
    }

    public String frameRateStatus() {
        return fps + " FPS / " + (followsVsync() ? "VSync" : "manual");
    }

    private void publishFrame() {
        JsonObject frame=new JsonObject(), projected=new JsonObject();
        frame.addProperty("width",width);frame.addProperty("height",height);
        frame.addProperty("worldReady",projection.ready());
        frame.addProperty("guiScale",MinecraftClient.getInstance().getWindow().getScaleFactor());
        if(projection.ready()&&!anchors.isEmpty()) {
            var entries=new ArrayList<>(anchors.entrySet());
            double[] points=new double[entries.size()*3], output=new double[entries.size()*4];
            boolean[] valid=new boolean[entries.size()];
            for(int i=0;i<entries.size();i++) {
                var supplier=entries.get(i).getValue();
                Vec3d position=supplier.get();
                valid[i]=position!=null;
                if(position!=null) {points[i*3]=position.x;points[i*3+1]=position.y;points[i*3+2]=position.z;}
            }
            projection.project(points,output,width,height);
            for(int i=0;i<entries.size();i++) {
                JsonObject point=new JsonObject();
                point.addProperty("x",output[i*4]);point.addProperty("y",output[i*4+1]);point.addProperty("depth",output[i*4+2]);
                point.addProperty("visible",valid[i]&&output[i*4+3]!=0);
                projected.add(entries.get(i).getKey(),point);
            }
        }
        JsonObject data=JSON.toJsonTree(channels).getAsJsonObject();
        dynamicChannels.forEach((name,source)->data.add(name,JSON.toJsonTree(source.get())));
        frame.add("anchors",projected);frame.add("data",data);
        NativeWebEngine.publish(JSON.toJson(frame));
    }
    @Override public void close() {
        requireClientThread();
        if(running) {NativeWebEngine.stop();running=false;}
        if(surface!=null) {surface.close();surface=null;}
        if(itemIcons!=null) {itemIcons.close();itemIcons=null;}
        pixels=null;projection.invalidate();
    }
    public void shutdown() { close();if(loaded) {NativeWebEngine.shutdown();loaded=false;} }
    private static void requireClientThread() {
        if(!MinecraftClient.getInstance().isOnThread()) throw new IllegalStateException("Web overlay must run on the client thread");
    }
}
