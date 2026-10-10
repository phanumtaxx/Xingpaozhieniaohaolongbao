package dev.xingclient;

import dev.xingclient.module.Module;
import dev.xingclient.module.ModuleCategory;
import dev.xingclient.module.ModuleManager;
import java.util.ArrayList;
import java.util.List;

/** Immutable GUI view of the modules currently registered with the client. */
public final class MenuData {
    public record Category(String name, List<Module> modules) {}

    private final List<Category> categories;

    public MenuData(ModuleManager manager) {
        List<Category> registeredCategories = new ArrayList<>();
        for (ModuleCategory category : ModuleCategory.values()) {
            List<Module> modules = manager.all().stream()
                .filter(module -> module.category() == category)
                .toList();
            if (!modules.isEmpty()) {
                registeredCategories.add(new Category(category.displayName(), modules));
            }
        }
        categories = List.copyOf(registeredCategories);
    }

    public List<Category> categories() {
        return categories;
    }
}
