package dev.xingclient.render.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.xingclient.XingClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Live editor transaction with bounded undo history. Author: uint32. */
public final class HudEditorSession {
    private record Saved(HudManager.Style style, boolean enabled) {}
    private record Item(String id, String name, boolean enabled, HudManager.Style style) {}
    private record Options(double windowX, double windowY, double blur, boolean snap, boolean grid) {}
    private record Snapshot(Map<String, Saved> widgets, Options options) {}
    private record Theme(int accent, int secondary, int opacity) {}
    private record State(List<Item> items, boolean canUndo, boolean canRedo, int revision, Options options, Theme theme) {}

    private final HudManager hud;
    private final Snapshot initial;
    private final List<Snapshot> history = new ArrayList<>();
    private int cursor;
    private int revision;
    private boolean finished;

    HudEditorSession(HudManager hud) {
        this.hud = hud;
        initial = capture();
        history.add(initial);
    }

    Object snapshot() {
        var items = new ArrayList<Item>();
        var app = XingClient.INSTANCE;
        hud.definitions().values().forEach(definition -> items.add(new Item(definition.id(), definition.name(),
            app.modules.get(definition.id()).isEnabled(), definition.style().get())));
        return new State(items, cursor > 0, cursor + 1 < history.size(), revision, options(),
            new Theme(app.settings.pink, app.settings.blue, app.settings.opacity));
    }

    /** Returns true when Save or Cancel requests that the screen close. */
    public boolean handle(String json) {
        if (finished) return true;
        JsonObject command = JsonParser.parseString(json).getAsJsonObject();
        switch (command.get("action").getAsString()) {
            case "save" -> { finish(true); return true; }
            case "cancel" -> { finish(false); return true; }
            case "undo" -> { if (cursor > 0) restore(history.get(--cursor)); }
            case "redo" -> { if (cursor + 1 < history.size()) restore(history.get(++cursor)); }
            case "edit", "toggle", "reset" -> change(command);
            case "layout" -> changeLayout(command);
            default -> throw new IllegalArgumentException("Unknown HUD editor action");
        }
        return false;
    }

    private void change(JsonObject command) {
        String id = command.get("id").getAsString();
        var definition = hud.definitions().get(id);
        if (definition == null) throw new IllegalArgumentException("Unknown HUD widget: " + id);
        var app = XingClient.INSTANCE;
        switch (command.get("action").getAsString()) {
            case "toggle" -> {
                var module = app.modules.get(id);
                module.setEnabled(command.get("enabled").getAsBoolean());
                app.settings.modules.get(id).enabled = module.isEnabled();
            }
            case "reset" -> definition.update().accept(definition.defaults());
            case "edit" -> {
                var style = new HudManager.Style(HudManager.Anchor.valueOf(command.get("anchor").getAsString()),
                    command.get("x").getAsDouble(), command.get("y").getAsDouble(), command.get("fontSize").getAsDouble());
                if (Math.abs(style.x()) > 16384 || Math.abs(style.y()) > 16384 || style.fontSize() < 4 || style.fontSize() > 24) {
                    throw new IllegalArgumentException("HUD style outside editor limits");
                }
                definition.update().accept(style);
            }
        }
        remember();
    }

    private void changeLayout(JsonObject command) {
        var settings = XingClient.INSTANCE.settings.hudEditor;
        double x = command.has("windowX") ? command.get("windowX").getAsDouble() : settings.windowX;
        double y = command.has("windowY") ? command.get("windowY").getAsDouble() : settings.windowY;
        double blur = command.has("blur") ? command.get("blur").getAsDouble() : settings.blur;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(blur)) {
            throw new IllegalArgumentException("Invalid HUD editor layout");
        }
        settings.windowX = x;
        settings.windowY = y;
        settings.blur = blur;
        if (command.has("snap")) settings.snap = command.get("snap").getAsBoolean();
        if (command.has("grid")) settings.grid = command.get("grid").getAsBoolean();
        settings.normalize();
        remember();
    }

    private void remember() {
        Snapshot next = capture();
        if (next.equals(history.get(cursor))) return;
        history.subList(cursor + 1, history.size()).clear();
        history.add(next);
        if (history.size() > 100) history.removeFirst();
        cursor = history.size() - 1;
        revision++;
    }

    private Options options() {
        var settings = XingClient.INSTANCE.settings.hudEditor;
        return new Options(settings.windowX, settings.windowY, settings.blur, settings.snap, settings.grid);
    }

    private Snapshot capture() {
        var result = new LinkedHashMap<String, Saved>();
        hud.definitions().forEach((id, definition) -> result.put(id,
            new Saved(definition.style().get(), XingClient.INSTANCE.modules.get(id).isEnabled())));
        return new Snapshot(result, options());
    }

    private void restore(Snapshot values) {
        var app = XingClient.INSTANCE;
        values.widgets().forEach((id, saved) -> {
            hud.definitions().get(id).update().accept(saved.style());
            var module = app.modules.get(id);
            module.setEnabled(saved.enabled());
            app.settings.modules.get(id).enabled = module.isEnabled();
        });
        var settings = app.settings.hudEditor;
        settings.windowX = values.options().windowX();
        settings.windowY = values.options().windowY();
        settings.blur = values.options().blur();
        settings.snap = values.options().snap();
        settings.grid = values.options().grid();
        revision++;
    }

    public void finish(boolean save) {
        if (finished) return;
        if (!save) restore(initial);
        finished = true;
        hud.finishEdit(this);
        XingClient.INSTANCE.changed();
    }
}
