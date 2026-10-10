package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.manager.ActionOwner;
import java.util.Objects;

public abstract class Module {
    protected final ActionOwner actionOwner;
    private final String id;
    private final String name;
    private final ModuleCategory category;
    private boolean enabled;

    protected Module(String id, String name, ModuleCategory category) {
        this.id = requireText(id, "id");
        this.name = requireText(name, "name");
        this.actionOwner = new ActionOwner(this.name);
        this.category = Objects.requireNonNull(category, "category");
    }

    public final String id() {
        return id;
    }

    public final String name() {
        return name;
    }

    public final ModuleCategory category() {
        return category;
    }

    public final boolean isEnabled() {
        return enabled;
    }

    public String status() {
        return enabled ? "Enabled" : "Disabled";
    }

    public final void setEnabled(boolean enabled) {
        if (this.enabled == enabled) {
            return;
        }

        if (enabled) {
            onEnable();
            this.enabled = true;
        } else {
            this.enabled = false;
            try {
                onDisable();
            } finally {
                if (XingClient.INSTANCE != null) XingClient.INSTANCE.managers.release(actionOwner);
            }
        }
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    protected void onEnable() {}

    protected void onDisable() {}

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
