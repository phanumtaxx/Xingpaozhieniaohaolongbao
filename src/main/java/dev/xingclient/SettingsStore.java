package dev.xingclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.LoggerFactory;

/** Snapshot on the client thread; debounced atomic writes on one background worker. */
public final class SettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path file;
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(r -> {
        var thread = new Thread(r, "xingclient-settings");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> pending;
    private String latest;

    public SettingsStore(Path file) {
        this.file = file;
    }

    public ClientSettings load() {
        return load(null);
    }

    public ClientSettings load(MenuData menuData) {
        if (Files.exists(file)) {
            try {
                ClientSettings settings = GSON.fromJson(Files.readString(file), ClientSettings.class);
                if (settings != null) {
                    return settings.normalize(menuData);
                }
            } catch (Exception exception) {
                LoggerFactory.getLogger("xingclient").warn("Could not read settings; retaining a backup", exception);
                try {
                    Files.copy(file, file.resolveSibling("settings-invalid-" + System.currentTimeMillis() + ".json"));
                } catch (Exception ignored) {
                }
            }
        }
        return new ClientSettings().normalize(menuData);
    }

    public synchronized void save(ClientSettings settings) {
        latest = GSON.toJson(settings);
        if (pending != null) {
            pending.cancel(false);
        }
        String snapshot = latest;
        pending = writer.schedule(() -> write(snapshot), 350, TimeUnit.MILLISECONDS);
    }

    private void write(String json) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, json, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception exception) {
            LoggerFactory.getLogger("xingclient").error("Could not save settings", exception);
        }
    }

    public synchronized void flush() {
        if (latest == null) {
            return;
        }
        if (pending != null) {
            pending.cancel(false);
        }
        String snapshot = latest;
        try {
            writer.submit(() -> write(snapshot)).get(3, TimeUnit.SECONDS);
        } catch (Exception exception) {
            LoggerFactory.getLogger("xingclient").warn("Settings flush did not complete", exception);
        }
    }
}
