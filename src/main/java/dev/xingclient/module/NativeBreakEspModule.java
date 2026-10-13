package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.ClientSettings;
import dev.xingclient.mixin.ClientPlayerInteractionManagerAccessor;
import dev.xingclient.mixin.WorldBreakingAccessor;
import dev.xingclient.nativebridge.XingNativeBridge;
import dev.xingclient.render.web.WebOverlay;
import dev.xingclient.render.web.NativeWebEngine;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

/** Minecraft snapshots and HTML display data for the native block animator. Author: uint32. */
public final class NativeBreakEspModule extends Module {
    private static final int MARKER_STRIDE = 79, POLYGON_STRIDE = NativeWebEngine.POLYGON_STRIDE;
    private record Observation(double progress, int kind) {}
    private record Face(double[] points, double depth) {}
    private record Marker(String id, List<Face> faces, double x, double y, double depth,
                          boolean labelVisible, double progress, double alpha, String color, boolean self) {}
    private record Style(double fill, double outline, double lineWidth, double glow,
                         boolean labels, boolean dashed, double dashSpeed, double fontSize) {}
    private record Frame(List<Marker> markers, Style style) {}

    private long handle;
    private ClientWorld world;
    private WebOverlay.Registration channel;

    public NativeBreakEspModule() { super("break_esp", "Break ESP", ModuleCategory.VISUALS); }

    @Override
    protected void onEnable() {
        XingNativeBridge.loadLibrary();
        var app = XingClient.INSTANCE;
        handle = create();
        try {
            app.webModule.setEnabled(true);
            app.webOverlay.installFont("Xing Inter", "InterVariable.woff2");
            app.webOverlay.installExtension("break-esp", "break-esp.js", "break-esp.css");
            channel = app.webOverlay.registerChannel(id(), this::snapshot);
            app.settings.modules.get(app.webModule.id()).enabled = true;
        } catch (RuntimeException | LinkageError error) {
            destroy(handle);
            handle = 0;
            throw error;
        }
    }

    @Override
    protected void onDisable() {
        if (channel != null) { channel.close(); channel = null; }
        if (handle != 0) { destroy(handle); handle = 0; }
        world = null;
    }

    @Override
    public String status() { return "Native cube animation / Web UI / uint32"; }

    private Frame snapshot() {
        var app = XingClient.INSTANCE;
        var client = MinecraftClient.getInstance();
        var settings = app.settings.breakEsp;
        settings.normalize();
        var style = new Style(settings.fill, settings.outline, settings.lineWidth, settings.glow,
            settings.labels, settings.dashed, settings.dashSpeed, settings.fontSize);
        if (world != client.world) { reset(handle); world = client.world; }
        if (world == null || client.player == null || client.interactionManager == null) return new Frame(List.of(), style);

        var observations = new LinkedHashMap<BlockPos, Observation>();
        for (var info : ((WorldBreakingAccessor) client.worldRenderer).xing$getBreakingInfos().values()) {
            if (info.getStage() < 0 || info.getStage() > 9) continue;
            BlockPos position = info.getPos();
            if (world.getBlockState(position).isAir()) continue;
            int kind = info.getActorId() == client.player.getId() ? 0 : 1;
            double progress = (info.getStage() + 1) / 10.0;
            Observation previous = observations.get(position);
            if (previous == null || progress > previous.progress()) observations.put(position.toImmutable(), new Observation(progress, kind));
        }
        var mining = (ClientPlayerInteractionManagerAccessor) client.interactionManager;
        if (mining.xing$isBreakingBlock()) {
            BlockPos position = mining.xing$getCurrentBreakingPos();
            if (position != null && !world.getBlockState(position).isAir()) {
                observations.put(position.toImmutable(), new Observation(mining.xing$getCurrentBreakingProgress(), 0));
            }
        }
        for (var target : app.managers.mining.targets()) {
            if (!world.getBlockState(target.position()).isAir())
                observations.put(target.position(), new Observation(target.progress(), 0));
        }
        double[] input = new double[observations.size() * 5];
        int at = 0;
        for (Map.Entry<BlockPos, Observation> entry : observations.entrySet()) {
            input[at++] = entry.getKey().getX(); input[at++] = entry.getKey().getY(); input[at++] = entry.getKey().getZ();
            input[at++] = entry.getValue().progress(); input[at++] = entry.getValue().kind();
        }
        Vec3d camera = client.gameRenderer.getCamera().getPos();
        double[] nativeSettings = {settings.animation.ordinal(), settings.easing.ordinal(), settings.range,
            settings.smoothing, settings.fade, settings.minimum, settings.inset, settings.rotation, settings.pulse,
            settings.frequency, settings.startColor, settings.endColor, settings.progressColor ? 1 : 0,
            settings.self ? 1 : 0, settings.others ? 1 : 0, settings.maximum};
        double[] mesh = update(handle, input, nativeSettings, new double[]{camera.x, camera.y, camera.z}, System.nanoTime() / 1.e9);
        int count = mesh.length / MARKER_STRIDE;
        if (count == 0) return new Frame(List.of(), style);
        double[] vertices = new double[count * 72], centers = new double[count * 3];
        int[] sizes = new int[count * 6];
        Arrays.fill(sizes, 4);
        for (int i = 0; i < count; i++) {
            System.arraycopy(mesh, i * MARKER_STRIDE + 7, vertices, i * 72, 72);
            System.arraycopy(mesh, i * MARKER_STRIDE, centers, i * 3, 3);
        }
        double[] projected = new double[sizes.length * POLYGON_STRIDE], projectedCenters = new double[count * 4];
        if (!app.webOverlay.projectPolygons(vertices, sizes, projected, settings.backFaces)
                || !app.webOverlay.projectPoints(centers, projectedCenters)) return new Frame(List.of(), style);
        var markers = new ArrayList<Marker>();
        for (int i = 0; i < count; i++) {
            int base = i * MARKER_STRIDE;
            var position = BlockPos.ofFloored(mesh[base], mesh[base + 1], mesh[base + 2]);
            if (!settings.throughWalls && !visible(client, position, camera)) continue;
            var faces = new ArrayList<Face>();
            for (int face = 0; face < 6; face++) {
                int faceBase = (i * 6 + face) * POLYGON_STRIDE;
                int size = (int) projected[faceBase];
                if (size < 3) continue;
                double[] points = new double[size * 2];
                double depth = 0;
                for (int vertex = 0; vertex < size; vertex++) {
                    points[vertex * 2] = projected[faceBase + 1 + vertex * 3];
                    points[vertex * 2 + 1] = projected[faceBase + 2 + vertex * 3];
                    depth += projected[faceBase + 3 + vertex * 3];
                }
                faces.add(new Face(points, depth / size));
            }
            if (faces.isEmpty()) continue;
            faces.sort(Comparator.comparingDouble(Face::depth).reversed());
            int center = i * 4;
            markers.add(new Marker(Long.toString(position.asLong()), faces, projectedCenters[center], projectedCenters[center + 1],
                projectedCenters[center + 2], projectedCenters[center + 3] != 0, mesh[base + 3], mesh[base + 4],
                String.format("#%06x", (int) mesh[base + 6]), mesh[base + 5] == 0));
        }
        markers.sort(Comparator.comparingDouble(Marker::depth).reversed());
        return new Frame(markers, style);
    }

    private static boolean visible(MinecraftClient client, BlockPos position, Vec3d camera) {
        var hit = client.world.raycast(new RaycastContext(camera, position.toCenterPos(),
            RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, client.player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(position);
    }

    private static native long create();
    private static native void destroy(long handle);
    private static native void reset(long handle);
    private static native double[] update(long handle, double[] blocks, double[] settings, double[] camera, double now);
}
