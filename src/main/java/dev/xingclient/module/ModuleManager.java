package dev.xingclient.module;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ModuleManager {
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public void register(Module module) {
        Objects.requireNonNull(module, "module");
        Module existing = modules.putIfAbsent(module.id(), module);
        if (existing != null) {
            throw new IllegalArgumentException("A module is already registered with id: " + module.id());
        }
    }

    public Optional<Module> find(String id) {
        return Optional.ofNullable(modules.get(Objects.requireNonNull(id, "id")));
    }

    public Module get(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("No module registered with id: " + id));
    }

    public Collection<Module> all() {
        return Collections.unmodifiableCollection(modules.values());
    }

    public void setEnabled(String id, boolean enabled) {
        get(id).setEnabled(enabled);
    }

    public void toggle(String id) {
        get(id).toggle();
    }
}
