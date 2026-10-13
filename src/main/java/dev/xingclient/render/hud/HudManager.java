package dev.xingclient.render.hud;

import dev.xingclient.XingClient;
import dev.xingclient.nativebridge.XingNativeBridge;
import dev.xingclient.render.web.WebOverlay;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.Consumer;
import net.minecraft.client.MinecraftClient;

/** Registry and data lifecycle for HTML HUD widgets. Author: uint32. */
public final class HudManager {
    public enum Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    public record Style(Anchor anchor, double x, double y, double fontSize) {
        public Style {
            Objects.requireNonNull(anchor, "anchor");
            if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(fontSize) || fontSize <= 0) {
                throw new IllegalArgumentException("Invalid HUD widget style");
            }
        }
    }

    private record Entry(String id, String template, Supplier<Style> style, Supplier<?> data) {}
    private record Widget(String id, String template, double x, double y,
                          double originX, double originY, double fontSize, Object data) {}
    private record Frame(List<Widget> widgets) {}

    record Definition(String id, String name, Supplier<Style> style, Consumer<Style> update, Style defaults) {}
    private final Map<String, Definition> definitions = new LinkedHashMap<>();
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private WebOverlay.Registration channel;
    private WebOverlay.Registration editorChannel;
    private HudEditorSession editing;

    public void define(String id, String name, Supplier<Style> style, Consumer<Style> update, Style defaults) {
        requireClientThread();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (id.isBlank() || name.isBlank()) throw new IllegalArgumentException("Blank HUD editor definition");
        var definition = new Definition(id, name, Objects.requireNonNull(style), Objects.requireNonNull(update), Objects.requireNonNull(defaults));
        if (definitions.putIfAbsent(id, definition) != null) {
            throw new IllegalArgumentException("Duplicate HUD editor definition: " + id);
        }
    }

    Map<String, Definition> definitions() { return definitions; }

    public HudEditorSession beginEdit() {
        requireClientThread();
        if (editing != null) throw new IllegalStateException("HUD editor already open");
        prepare();
        XingClient.INSTANCE.webOverlay.installFont("Xing Jost", "Jost-Regular.ttf");
        XingClient.INSTANCE.webOverlay.installAsset("Jost-OFL.txt");
        XingClient.INSTANCE.webOverlay.installExtension("hud-editor", "hud-editor.js", "hud-editor.css");
        if (editorChannel == null) {
            editorChannel = XingClient.INSTANCE.webOverlay.registerChannel("hudEditor", () -> editing == null ? null : editing.snapshot());
        }
        editing = new HudEditorSession(this);
        return editing;
    }

    void finishEdit(HudEditorSession session) {
        requireClientThread();
        if (editing == session) editing = null;
    }

    private void prepare() {
        var app = XingClient.INSTANCE;
        XingNativeBridge.loadLibrary();
        app.webModule.setEnabled(true);
        app.webOverlay.installFont("Xing Inter", "InterVariable.woff2");
        app.webOverlay.installAsset("Inter-LICENSE.txt");
        app.webOverlay.installExtension("hud", "hud.js", "hud.css");
        if (channel == null) channel = app.webOverlay.registerChannel("hud", this::snapshot);
        app.settings.modules.get(app.webModule.id()).enabled = true;
    }

    public WebOverlay.Registration register(String id, String template, Supplier<Style> style, Supplier<?> data) {
        requireClientThread();
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(data, "data");
        if (id.isBlank() || template.isBlank() || entries.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate or blank HUD widget: " + id);
        }

        prepare();

        Entry entry = new Entry(id, template, style, data);
        entries.put(id, entry);
        return () -> {
            requireClientThread();
            entries.remove(id, entry);
        };
    }

    private Frame snapshot() {
        requireClientThread();
        if (entries.isEmpty()) return new Frame(List.of());

        var client = MinecraftClient.getInstance();
        var widgets = new ArrayList<>(entries.values());
        int[] anchors = new int[widgets.size()];
        double[] offsets = new double[widgets.size() * 2];
        double[] output = new double[widgets.size() * 4];
        var styles = new ArrayList<Style>(widgets.size());
        for (int i = 0; i < widgets.size(); i++) {
            Style style = Objects.requireNonNull(widgets.get(i).style().get());
            styles.add(style);
            anchors[i] = style.anchor().ordinal();
            offsets[i * 2] = style.x();
            offsets[i * 2 + 1] = style.y();
        }

        NativeHudLayout.place(anchors, offsets, output, client.getFramebuffer().textureWidth,
            client.getFramebuffer().textureHeight, client.getWindow().getScaleFactor());
        var result = new ArrayList<Widget>(widgets.size());
        for (int i = 0; i < widgets.size(); i++) {
            Entry widget = widgets.get(i);
            result.add(new Widget(widget.id(), widget.template(), output[i * 4], output[i * 4 + 1],
                output[i * 4 + 2], output[i * 4 + 3], styles.get(i).fontSize(), widget.data().get()));
        }
        return new Frame(result);
    }

    private static void requireClientThread() {
        if (!MinecraftClient.getInstance().isOnThread()) {
            throw new IllegalStateException("HUD access must run on the client thread");
        }
    }
}
