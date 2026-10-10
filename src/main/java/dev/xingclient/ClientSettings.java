package dev.xingclient;

import dev.xingclient.module.Module;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

public final class ClientSettings {
    public int version = 1;
    public int pink = 0xedacd0, blue = 0xa6d6f4, opacity = 90;
    public boolean mascot = true, blossoms = true;
    public Map<String, Entry> modules = new LinkedHashMap<>();
    public AutoCrystalSettings autoCrystal = new AutoCrystalSettings();
    public MaceKillSettings maceKill = new MaceKillSettings();
    public Map<String, Position> positions = new LinkedHashMap<>();
    public Map<String, SavedTarget> targets = new LinkedHashMap<>();
    public Set<String> friends = new LinkedHashSet<>();

    public static final class SavedTarget {
        public String uuid, name;
        public SavedTarget(String uuid, String name) { this.uuid = uuid; this.name = name; }
        public boolean valid() {
            try {
                java.util.UUID.fromString(uuid);
                return name != null && name.matches("[A-Za-z0-9_]{1,16}");
            } catch (Exception e) {
                return false;
            }
        }
    }

    public static final class Entry {
        public boolean enabled, visible = true;
        public int mode, intensity = 50, key = -1;
        public boolean mouse;
    }

    public static final class AutoCrystalSettings {
        public double minimumDamage = 4.0;
        public double lowHealthMinimumDamage = 1.0;
        public double lowHealthThreshold = 8.0;
        public double maximumSelfDamage = 10.0;
        public double breakRange = 7.0;
        public boolean breakExisting = true;
        public boolean sameTickBreakPlace = true;
    }

    public static final class Position {
        public double x, y;
        public Position(double x, double y) { this.x = x; this.y = y; }
    }

    public static final class MaceKillSettings {
        public int charges = 5, chargeIntervalTicks = 12, heartbeatTicks = 10, holdSeconds = 15;
        public double holdHeight = 1.2, minimumFall = 4, attackRange = 3;
        public boolean autoAttack;
        public void normalize() {
            charges = Math.clamp(charges, 1, 8);
            chargeIntervalTicks = Math.clamp(chargeIntervalTicks, 10, 30);
            heartbeatTicks = Math.clamp(heartbeatTicks, 1, 20);
            holdSeconds = Math.clamp(holdSeconds, 3, 30);
            holdHeight = clampFinite(holdHeight, .2, 3, 1.2);
            minimumFall = clampFinite(minimumFall, 1.6, 20, 4);
            attackRange = clampFinite(attackRange, 1, 3, 3);
        }
        public MaceKillSettings copy() {
            normalize();
            var copy = new MaceKillSettings();
            copy.charges = charges; copy.chargeIntervalTicks = chargeIntervalTicks;
            copy.heartbeatTicks = heartbeatTicks; copy.holdSeconds = holdSeconds;
            copy.holdHeight = holdHeight; copy.minimumFall = minimumFall;
            copy.attackRange = attackRange; copy.autoAttack = autoAttack;
            return copy;
        }
    }

    public ClientSettings normalize() {
        return normalize(null);
    }

    public ClientSettings normalize(MenuData menuData) {
        pink &= 0xffffff;
        blue &= 0xffffff;
        opacity = Math.clamp(opacity, 40, 100);
        if (modules == null) modules = new LinkedHashMap<>();
        if (autoCrystal == null) autoCrystal = new AutoCrystalSettings();
        if (maceKill == null) maceKill = new MaceKillSettings();
        maceKill.normalize();
        autoCrystal.minimumDamage = clampFinite(autoCrystal.minimumDamage, 0.0, 20.0, 4.0);
        autoCrystal.lowHealthMinimumDamage = clampFinite(autoCrystal.lowHealthMinimumDamage, 0.0, 20.0, 1.0);
        autoCrystal.lowHealthThreshold = clampFinite(autoCrystal.lowHealthThreshold, 1.0, 20.0, 8.0);
        autoCrystal.maximumSelfDamage = clampFinite(autoCrystal.maximumSelfDamage, 0.0, 36.0, 10.0);
        autoCrystal.breakRange = clampFinite(autoCrystal.breakRange, 1.0, 7.0, 7.0);
        if (positions == null) positions = new LinkedHashMap<>();
        if (targets == null) targets = new LinkedHashMap<>();
        if (friends == null) friends = new LinkedHashSet<>();
        Set<String> normalizedFriends = new LinkedHashSet<>();
        for (String friend : friends) {
            if (friend != null && !friend.isBlank()) {
                normalizedFriends.add(friend.trim().toLowerCase(java.util.Locale.ROOT));
            }
        }
        friends = normalizedFriends;
        targets.entrySet().removeIf(entry -> entry.getKey() == null || entry.getValue() == null || !entry.getValue().valid());

        if (menuData != null) {
            Set<String> registeredIds = new LinkedHashSet<>();
            Set<String> registeredCategories = new LinkedHashSet<>();
            for (MenuData.Category category : menuData.categories()) {
                registeredCategories.add(category.name());
                for (Module module : category.modules()) {
                    registeredIds.add(module.id());
                    modules.computeIfAbsent(module.id(), ignored -> new Entry());
                }
            }
            modules.keySet().removeIf(id -> !registeredIds.contains(id));
            positions.keySet().removeIf(name -> !registeredCategories.contains(name));
        }

        for (Entry entry : modules.values()) {
            if (entry == null) continue;
            entry.mode = Math.clamp(entry.mode, 0, 2);
            entry.intensity = Math.clamp(entry.intensity, 0, 100);
            if (entry.key < -1 || entry.key > (entry.mouse ? 7 : 348) || (!entry.mouse && entry.key == 344)) {
                entry.key = -1;
            }
        }
        positions.entrySet().removeIf(entry -> entry.getValue() == null
            || !Double.isFinite(entry.getValue().x) || !Double.isFinite(entry.getValue().y));
        return this;
    }

    public int accent(int category) {
        double t = category / 5.0;
        int color = 0xff000000;
        for (int shift : new int[]{0, 8, 16}) {
            color |= (int) Math.round(((pink >> shift) & 255) * (1 - t) + ((blue >> shift) & 255) * t) << shift;
        }
        return color;
    }

    public void restoreTheme() {
        pink = 0xedacd0;
        blue = 0xa6d6f4;
        opacity = 90;
        mascot = blossoms = true;
    }

    private static double clampFinite(double value, double minimum, double maximum, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, minimum, maximum) : fallback;
    }
}
