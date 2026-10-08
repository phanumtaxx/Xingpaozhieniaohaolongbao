package dev.xingclient;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ClientSettings {
    public int version = 1;
    public int pink = 0xedacd0, blue = 0xa6d6f4, opacity = 90;
    public boolean mascot = true, blossoms = true;
    public Map<String, Entry> modules = new LinkedHashMap<>();
    public Map<String, Position> positions = new LinkedHashMap<>();
    public Map<String, SavedTarget> targets = new LinkedHashMap<>();
    public static final class SavedTarget {
        public String uuid,name;
        public SavedTarget(String uuid,String name){this.uuid=uuid;this.name=name;}
        public boolean valid(){try{java.util.UUID.fromString(uuid);return name!=null&&name.matches("[A-Za-z0-9_]{1,16}");}catch(Exception e){return false;}}
    }
    public static final class Entry {
        public boolean enabled, visible = true;
        public int mode, intensity = 50, key = -1;
        public boolean mouse;
    }
    public static final class Position {
        public double x, y;
        public Position(double x, double y) { this.x = x; this.y = y; }
    }
    public ClientSettings normalize() {
        pink &= 0xffffff; blue &= 0xffffff; opacity = Math.clamp(opacity, 40, 100);
        if (modules == null) modules = new LinkedHashMap<>();
        if (positions == null) positions = new LinkedHashMap<>();
        if (targets == null) targets = new LinkedHashMap<>();
        targets.entrySet().removeIf(e->e.getKey()==null||e.getValue()==null||!e.getValue().valid());
        for (var category : MenuData.CATEGORIES) for (String name : category.modules()) {
            String key = MenuData.key(category, name);
            if (modules.get(key) == null) { var e = new Entry(); e.enabled = category.on().contains(name); modules.put(key, e); }
            var e = modules.get(key); e.mode = Math.clamp(e.mode, 0, 2); e.intensity = Math.clamp(e.intensity, 0, 100);
            if (e.key < -1 || e.key > (e.mouse ? 7 : 348) || (!e.mouse && e.key == 344)) e.key = -1;
        }
        positions.entrySet().removeIf(e -> e.getValue() == null || !Double.isFinite(e.getValue().x) || !Double.isFinite(e.getValue().y));
        return this;
    }
    public int accent(int category) {
        double t = category / 5.0; int color = 0xff000000;
        for (int shift : new int[]{0, 8, 16}) color |= (int)Math.round(((pink >> shift) & 255) * (1 - t) + ((blue >> shift) & 255) * t) << shift;
        return color;
    }
    public void restoreTheme() { pink = 0xedacd0; blue = 0xa6d6f4; opacity = 90; mascot = blossoms = true; }
}
