package dev.xingclient.module;

public enum ModuleCategory {
    COMBAT("Combat"),
    PLAYER("Player"),
    VISUALS("Visuals"),
    MOVEMENT("Movement"),
    MISCELLANEOUS("Miscellaneous"),
    CORE("Core");

    private final String displayName;

    ModuleCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
