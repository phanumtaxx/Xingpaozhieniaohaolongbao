package dev.xingclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

/** Snapshot on the client thread; debounced atomic writes on one background worker. */
public final class SettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(r -> { var t = new Thread(r, "xingclient-settings"); t.setDaemon(true); return t; });
    private ScheduledFuture<?> pending;
    private String latest;
    public SettingsStore(Path file) { this.file = file; }
    public ClientSettings load() {
        if (Files.exists(file)) try {
            var settings = GSON.fromJson(Files.readString(file), ClientSettings.class);
            if (settings != null) return settings.normalize();
        } catch (Exception e) {
            LoggerFactory.getLogger("xingclient").warn("Could not read settings; retaining a backup", e);
            try { Files.copy(file, file.resolveSibling("settings-invalid-" + System.currentTimeMillis() + ".json")); } catch (Exception ignored) {}
        }
        return new ClientSettings().normalize();
    }
    public synchronized void save(ClientSettings settings) {
        latest = GSON.toJson(settings);
        if (pending != null) pending.cancel(false);
        String snapshot = latest;
        pending = writer.schedule(() -> write(snapshot), 350, TimeUnit.MILLISECONDS);
    }
    private void write(String json) {
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (Exception e) { LoggerFactory.getLogger("xingclient").error("Could not save settings", e); }
    }
    public synchronized void flush() {
        if (latest == null) return;
        if (pending != null) pending.cancel(false);
        String snapshot = latest;
        try { writer.submit(() -> write(snapshot)).get(3, TimeUnit.SECONDS); }
        catch (Exception e) { LoggerFactory.getLogger("xingclient").warn("Settings flush did not complete", e); }
    }
}
