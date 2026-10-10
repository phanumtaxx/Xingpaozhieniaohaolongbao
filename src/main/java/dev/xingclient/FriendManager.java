package dev.xingclient;

import java.util.Locale;
import java.util.Objects;

/** Persistent friend names used by combat target selection. */
public final class FriendManager {
    private final ClientSettings settings;
    private final Runnable onChange;

    public FriendManager(ClientSettings settings, Runnable onChange) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    public boolean add(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty() || !settings.friends.add(normalized)) return false;
        onChange.run();
        return true;
    }

    public boolean remove(String name) {
        if (!settings.friends.remove(normalize(name))) return false;
        onChange.run();
        return true;
    }

    public boolean isFriend(String name) {
        return settings.friends.contains(normalize(name));
    }

    private static String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
