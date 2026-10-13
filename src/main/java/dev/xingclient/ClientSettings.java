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
    public FlightSettings flight = new FlightSettings();
    public VelocitySettings velocity = new VelocitySettings();
    public AutoRefillSettings autoRefill = new AutoRefillSettings();
    public NoRenderSettings noRender = new NoRenderSettings();
    public ViewModelSettings viewModel = new ViewModelSettings();
    public SwingSettings swing = new SwingSettings();
    public WebOverlaySettings webOverlay = new WebOverlaySettings();
    public NametagsSettings nametags = new NametagsSettings();
    public WatermarkSettings watermark = new WatermarkSettings();
    public HudEditorSettings hudEditor = new HudEditorSettings();
    public SpeedMineSettings speedMine = new SpeedMineSettings();
    public BreakEspSettings breakEsp = new BreakEspSettings();
    public dev.xingclient.render.ShaderSettings shaders = new dev.xingclient.render.ShaderSettings();
    public Map<String, Position> positions = new LinkedHashMap<>();
    public Map<String, SavedTarget> targets = new LinkedHashMap<>();
    public Set<String> friends = new LinkedHashSet<>();

    public static final class SpeedMineSettings {
        public double range = 5, threshold = 1;
        public boolean multitask = true, strictDirection = true, rotate, swing = true;
        public int confirmationTicks = 30, retries = 2;
        public dev.xingclient.manager.InventoryManager.SwapMode swapMode = dev.xingclient.manager.InventoryManager.SwapMode.SILENT;

        public void normalize() {
            range = clampFinite(range, 1, 6, 5);
            threshold = clampFinite(threshold, .5, 1, 1);
            confirmationTicks = Math.clamp(confirmationTicks, 10, 100);
            retries = Math.clamp(retries, 0, 5);
            if (swapMode == null) swapMode = dev.xingclient.manager.InventoryManager.SwapMode.SILENT;
        }
    }

    public static final class BreakEspSettings {
        public enum Animation { GROW, RISE, PULSE, TWIST, BOUNCE, SHRINK }
        public enum Easing { LINEAR, SMOOTH, CUBIC, SINE, QUINT }
        public Animation animation = Animation.GROW;
        public Easing easing = Easing.SMOOTH;
        public boolean self = true, others = true, throughWalls = true, backFaces;
        public boolean progressColor = true, labels = true, dashed;
        public int maximum = 16, startColor = 0xedacd0, endColor = 0xa6d6f4;
        public double range = 32, smoothing = 90, fade = 220, minimum = 0, inset = 0;
        public double rotation = 120, pulse = .18, frequency = 2;
        public double fill = .18, outline = .9, lineWidth = 1.25, glow = 0;
        public double dashSpeed = 1, fontSize = 8;

        public void normalize() {
            if (animation == null) animation = Animation.GROW;
            if (easing == null) easing = Easing.SMOOTH;
            maximum = Math.clamp(maximum, 1, 64);
            startColor &= 0xffffff; endColor &= 0xffffff;
            range = clampFinite(range, 4, 128, 32);
            smoothing = clampFinite(smoothing, 0, 600, 90);
            fade = clampFinite(fade, 0, 1200, 220);
            minimum = clampFinite(minimum, 0, 1, 0);
            inset = clampFinite(inset, 0, .1, 0);
            rotation = clampFinite(rotation, 0, 360, 120);
            pulse = clampFinite(pulse, 0, .5, .18);
            frequency = clampFinite(frequency, 0, 6, 2);
            fill = clampFinite(fill, 0, 1, .18);
            outline = clampFinite(outline, 0, 1, .9);
            lineWidth = clampFinite(lineWidth, .25, 6, 1.25);
            glow = clampFinite(glow, 0, 12, 0);
            dashSpeed = clampFinite(dashSpeed, 0, 8, 1);
            fontSize = clampFinite(fontSize, 4, 16, 8);
        }
    }

    public static final class HudEditorSettings {
        public double windowX = 24, windowY = 48, blur = 4;
        public boolean snap = true, grid = true;

        public void normalize() {
            windowX = clampFinite(windowX, 0, 16384, 24);
            windowY = clampFinite(windowY, 0, 16384, 48);
            blur = clampFinite(blur, 0, 12, 4);
        }
    }

    public static final class WatermarkSettings {
        public dev.xingclient.render.hud.HudManager.Anchor anchor=dev.xingclient.render.hud.HudManager.Anchor.TOP_LEFT;
        public double x=8,y=8,fontSize=11;
        public void normalize() {
            if(anchor==null) anchor=dev.xingclient.render.hud.HudManager.Anchor.TOP_LEFT;
            x=clampFinite(x,-16384,16384,8);y=clampFinite(y,-16384,16384,8);fontSize=clampFinite(fontSize,4,24,11);
        }
    }
    public static final class NametagsSettings {
        public double range=256, offset=.35, fontSize=6, blur=12, backgroundAlpha=.2;
        public int backgroundColor=0xffffff;
        public double paddingX=3.25, paddingY=3.25, cornerRadius=2.25;
        public boolean invisible=true;
        public void normalize() {
            range=clampFinite(range,16,256,256);offset=clampFinite(offset,0,2,.35);
            fontSize=clampFinite(fontSize,4,18,6);blur=clampFinite(blur,0,12,12);
            backgroundAlpha=clampFinite(backgroundAlpha,0,1,.2);backgroundColor&=0xffffff;
            paddingX=clampFinite(paddingX,0,24,3.25);paddingY=clampFinite(paddingY,0,16,3.25);
            cornerRadius=clampFinite(cornerRadius,0,16,2.25);
        }
    }
    public static final class WebOverlaySettings {
        public int fps = 60;
        public boolean followVsync = true;
        public void normalize() { fps = Math.clamp(fps, 15, 360); }
    }
    public static final class ViewModelSettings {
        public boolean renderArms = true, vanillaItems = true, syncHands;
        public double mainX, mainY, mainZ, offX, offY, offZ, rotateX, rotateY, rotateZ;
        public double scaleX = 1, scaleY = 1, scaleZ = 1;
        public void normalize() {
            mainX=clampFinite(mainX,-2,2,0); mainY=clampFinite(mainY,-2,2,0); mainZ=clampFinite(mainZ,-2,2,0);
            offX=clampFinite(offX,-2,2,0); offY=clampFinite(offY,-2,2,0); offZ=clampFinite(offZ,-2,2,0);
            if(syncHands) { offX=mainX; offY=mainY; offZ=mainZ; }
            rotateX=clampFinite(rotateX,-180,180,0); rotateY=clampFinite(rotateY,-180,180,0); rotateZ=clampFinite(rotateZ,-180,180,0);
            scaleX=clampFinite(scaleX,.05,3,1); scaleY=clampFinite(scaleY,.05,3,1); scaleZ=clampFinite(scaleZ,.05,3,1);
        }
    }
    public static final class SwingSettings {
        public boolean overrideHand, overrideSpeed = true, oldAnimation;
        public net.minecraft.util.Hand hand = net.minecraft.util.Hand.MAIN_HAND;
        public int speed = 12;
        public void normalize() {
            if(hand == null) hand = net.minecraft.util.Hand.MAIN_HAND;
            speed = Math.clamp(speed, 1, 18);
        }
    }

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

    public static final class FlightSettings {
        public enum Mode { CREATIVE, GRIM, CRASHOUT }
        public Mode mode = Mode.CRASHOUT;
        public double horizontal = 3.0, vertical = 3.0;
        public double crashoutHorizontal = .35;
        public boolean antiKick, pitch, firework, idleFlipFlop = true;
        public int flipFlopTicks = 1;
        public enum FlipFlop { FULL, WITH_FIREWORK, NONE }
        public double turnSpeed = 25.0, safetyMargin = .2;
        public int packetGap = 20;
        public boolean inventoryFireworks = true, hideFlyPose = true, spoofChestplate;
        public FlipFlop flipFlop = FlipFlop.FULL;
        public void normalize() {
            if (mode == null) mode = Mode.CRASHOUT;
            horizontal = clampFinite(horizontal, 1, 5, 3);
            crashoutHorizontal = clampFinite(crashoutHorizontal, .08, 2, .35);
            vertical = clampFinite(vertical, 1, 5, 3);
            flipFlopTicks = Math.clamp(flipFlopTicks, 1, 20);
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
        if (flight == null) flight = new FlightSettings();
        flight.normalize();
        if (velocity == null) velocity = new VelocitySettings();
        velocity.normalize();
        if (autoRefill == null) autoRefill = new AutoRefillSettings();
        autoRefill.normalize();
        if (noRender == null) noRender = new NoRenderSettings();
        if (viewModel == null) viewModel = new ViewModelSettings();
        viewModel.normalize();
        if (swing == null) swing = new SwingSettings();
        swing.normalize();
        if (webOverlay == null) webOverlay = new WebOverlaySettings();
        webOverlay.normalize();
        if(nametags==null) nametags=new NametagsSettings();
        nametags.normalize();
        if(watermark==null) watermark=new WatermarkSettings();
        watermark.normalize();
        if (hudEditor == null) hudEditor = new HudEditorSettings();
        hudEditor.normalize();
        if (speedMine == null) speedMine = new SpeedMineSettings();
        speedMine.normalize();
        if (breakEsp == null) breakEsp = new BreakEspSettings();
        breakEsp.normalize();
        if (shaders == null) shaders = new dev.xingclient.render.ShaderSettings();
        shaders.normalize();
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
