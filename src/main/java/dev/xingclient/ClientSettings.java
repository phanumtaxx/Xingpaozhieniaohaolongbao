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
    public CrashoutSettings crashout = new CrashoutSettings();
    public VelocitySettings velocity = new VelocitySettings();
    public AutoRefillSettings autoRefill = new AutoRefillSettings();
    public NoRenderSettings noRender = new NoRenderSettings();
    public Map<String, Position> positions = new LinkedHashMap<>();
    public Map<String, SavedTarget> targets = new LinkedHashMap<>();
    public Set<String> friends = new LinkedHashSet<>();

    public static final class AutoRefillSettings {
        public int delay = 3, threshold = 16;
        public boolean crystals = true, obsidian = true, rockets = true, xp = true, gaps = true, pearls = true;
        public int itemMask() {
            return (crystals ? 1 : 0) | (obsidian ? 2 : 0) | (rockets ? 4 : 0)
                    | (xp ? 8 : 0) | (gaps ? 16 : 0) | (pearls ? 32 : 0);
        }
        public void normalize() {
            delay = Math.clamp(delay, 0, 40);
            threshold = Math.clamp(threshold, 1, 63);
        }
    }

    public static final class NoRenderSettings {
        public boolean particles = true, weather = true, weatherParticles = true, clouds = true;
        public boolean fireOverlay = true, waterOverlay = false, blockOverlay = false, itemActivation = true;
        public boolean armorSelf = false, armorOthers = false;
        public int effectMask() {
            return (particles ? 1 : 0) | (weather ? 2 : 0) | (weatherParticles ? 4 : 0)
                    | (clouds ? 8 : 0) | (fireOverlay ? 16 : 0) | (waterOverlay ? 32 : 0)
                    | (blockOverlay ? 64 : 0) | (itemActivation ? 128 : 0)
                    | (armorSelf ? 256 : 0) | (armorOthers ? 512 : 0);
        }
    }

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
        public double placeRange = 4.5, wallsRange = 3.0;
        public double targetWeight = 2.0, safetyWeight = 0.8;
        public int scanRadius = 8, scanInterval = 1;
        public int placePredictionTicks = 1, breakPredictionTicks = 1, fullPredictionTicks = 2;
        public int attackRetryTicks = 1, predictionTimeout = 5, restoreSlotDelay = 0;
        public int silentRestoreDelay = 1;
        public boolean predictMovement = true, strictDirection = true, strictPlacementSpace;
        public boolean facePlace = true;
        public dev.xingclient.manager.InteractionManager.HandMode handMode =
                dev.xingclient.manager.InteractionManager.HandMode.PREFER_OFFHAND;
        public dev.xingclient.manager.InventoryManager.SwapMode swapMode =
                dev.xingclient.manager.InventoryManager.SwapMode.CLIENT;

        public void normalize() {
            minimumDamage = clampFinite(minimumDamage, 0, 36, 4);
            lowHealthMinimumDamage = clampFinite(lowHealthMinimumDamage, 0, 12, 1);
            lowHealthThreshold = clampFinite(lowHealthThreshold, 0, 36, 8);
            maximumSelfDamage = clampFinite(maximumSelfDamage, 0, 36, 10);
            breakRange = clampFinite(breakRange, 1, 12, 7);
            placeRange = clampFinite(placeRange, 1, 6, 4.5);
            wallsRange = clampFinite(wallsRange, 0, 6, 3);
            targetWeight = clampFinite(targetWeight, 0, 10, 2);
            safetyWeight = clampFinite(safetyWeight, 0, 10, .8);
            scanRadius = Math.clamp(scanRadius, 2, 16);
            scanInterval = Math.clamp(scanInterval, 1, 10);
            placePredictionTicks = Math.clamp(placePredictionTicks, 0, 6);
            breakPredictionTicks = Math.clamp(breakPredictionTicks, 0, 6);
            fullPredictionTicks = Math.clamp(fullPredictionTicks, 0, 8);
            attackRetryTicks = Math.clamp(attackRetryTicks, 0, 4);
            predictionTimeout = Math.clamp(predictionTimeout, 1, 20);
            restoreSlotDelay = Math.clamp(restoreSlotDelay, 0, 6);
            silentRestoreDelay = Math.clamp(silentRestoreDelay, 0, 6);
            if (swapMode == null) swapMode = dev.xingclient.manager.InventoryManager.SwapMode.CLIENT;
            if (handMode == null) handMode = dev.xingclient.manager.InteractionManager.HandMode.PREFER_OFFHAND;
        }

        public void applySyntheticPreset() {
            minimumDamage = 4; maximumSelfDamage = 10;
            lowHealthMinimumDamage = 1; lowHealthThreshold = 8;
            placeRange = 5.3; wallsRange = 3; breakRange = 7;
            scanRadius = 8; scanInterval = 1; targetWeight = 2; safetyWeight = .8;
            predictMovement = true; placePredictionTicks = 1; breakPredictionTicks = 1; fullPredictionTicks = 2;
            attackRetryTicks = 1; predictionTimeout = 5; restoreSlotDelay = 0; silentRestoreDelay = 1;
            strictDirection = false; strictPlacementSpace = false; facePlace = false;
            breakExisting = true; sameTickBreakPlace = true;
            swapMode = dev.xingclient.manager.InventoryManager.SwapMode.SILENT;
            handMode = dev.xingclient.manager.InteractionManager.HandMode.PREFER_MAINHAND;
        }
    }

    public static final class Position {
        public double x, y;
        public Position(double x, double y) { this.x = x; this.y = y; }
    }

    public static final class MaceKillSettings {
        public int charges = 5, chargeIntervalTicks = 12, holdSeconds = 15;
        public int stallMillis = 1500;
        /** Legacy config field; movement packets now use vanilla timing. */
        @Deprecated public int heartbeatTicks = 10;
        public double holdHeight = 1.2, minimumFall = 4, attackRange = 3;
        public boolean autoAttack;
        public void normalize() {
            charges = Math.clamp(charges, 1, 8);
            chargeIntervalTicks = Math.clamp(chargeIntervalTicks, 10, 30);
            heartbeatTicks = Math.clamp(heartbeatTicks, 1, 20);
            holdSeconds = Math.clamp(holdSeconds, 3, 30);
            stallMillis = Math.clamp(stallMillis, 100, 4000);
            holdHeight = clampFinite(holdHeight, .2, 3, 1.2);
            minimumFall = clampFinite(minimumFall, 1.6, 20, 4);
            attackRange = clampFinite(attackRange, 1, 3, 3);
        }
        public MaceKillSettings copy() {
            normalize();
            var copy = new MaceKillSettings();
            copy.charges = charges; copy.chargeIntervalTicks = chargeIntervalTicks;
            copy.holdSeconds = holdSeconds;
            copy.stallMillis = stallMillis;
            copy.heartbeatTicks = heartbeatTicks;
            copy.holdHeight = holdHeight; copy.minimumFall = minimumFall;
            copy.attackRange = attackRange; copy.autoAttack = autoAttack;
            return copy;
        }
    }

    public static final class CrashoutSettings {
        public enum FlipFlop { FULL, WITH_FIREWORK, NONE }
        public double turnSpeed = 25.0, safetyMargin = .2;
        public int packetGap = 20;
        public boolean inventoryFireworks = true, hideFlyPose = true, spoofChestplate;
        public FlipFlop flipFlop = FlipFlop.FULL;
        public void normalize() {
            turnSpeed = clampFinite(turnSpeed, 1, 180, 25);
            safetyMargin = clampFinite(safetyMargin, 0, 2, .2);
            packetGap = Math.clamp(packetGap, 1, 100);
            if (flipFlop == null) flipFlop = FlipFlop.FULL;
        }
    }

    public static final class VelocitySettings {
        public enum Mode { NCP, GRIM_V3 }
        public enum MotionMode { ALWAYS, ONLY_STILL, NEVER }
        public Mode mode = Mode.GRIM_V3;
        public MotionMode motionMode = MotionMode.ONLY_STILL;
        public double horizontal, vertical, lagPauseMillis = 250, nearDistance = .18;
        public int clippedGraceTicks = 6;
        public boolean cancelAll, redirect, noRotation, whileLiquid, whileElytra;
        public boolean walls = true, explosions = true, phaseLock = true, blockPush = true;
        public boolean onlyIntersecting = true, lenient = true;
        public boolean requireAssist, requireRecent, pushDebug, debug;
        public void normalize() {
            if (mode == null) mode = Mode.GRIM_V3;
            if (motionMode == null) motionMode = MotionMode.ONLY_STILL;
            horizontal = clampFinite(horizontal, 0, 100, 0);
            vertical = clampFinite(vertical, 0, 100, 0);
            lagPauseMillis = clampFinite(lagPauseMillis, 0, 1000, 250);
            nearDistance = clampFinite(nearDistance, .02, .35, .18);
            clippedGraceTicks = Math.clamp(clippedGraceTicks, 0, 20);
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
        if (crashout == null) crashout = new CrashoutSettings();
        crashout.normalize();
        if (velocity == null) velocity = new VelocitySettings();
        velocity.normalize();
        if (autoRefill == null) autoRefill = new AutoRefillSettings();
        autoRefill.normalize();
        if (noRender == null) noRender = new NoRenderSettings();
        autoCrystal.normalize();
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
