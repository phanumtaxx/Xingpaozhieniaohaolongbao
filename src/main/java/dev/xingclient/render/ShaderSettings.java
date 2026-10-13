package dev.xingclient.render;

/** Persisted controls for the native effects renderer. Author: uint32. */
public final class ShaderSettings {
    public enum Material { NEBULA, PLASMA, LIQUID, AURORA, ELECTRIC, HOLOGRAM, COSMIC, MARBLE, IRIDESCENT, FIRE, RAINBOW, STATIC, VORONOI, DIGITAL, PRISM, VORTEX, PASTEL, SOLID, SOFT_GRADIENT, PEARL, FRACTAL_SPACE, MILKY_WAY, CAUSTICS, DOMAIN_WARP, INTERFERENCE, LIQUID_CHROME }
    public enum Blend { ALPHA, ADDITIVE, SCREEN }
    public enum Palette { COTTON, LAVENDER, MINT, PEACH, AMETHYST, CYBERPUNK, GLACIER, INFERNO, TOXIC, PEARL, CUSTOM }
    public enum Sky { NEBULA, AURORA, GALAXY, STORM, SYNTHWAVE, BLACK_HOLE, PASTEL, NIGHT_GRADIENT, FRACTAL_SPACE, MILKY_WAY, CAUSTICS, DOMAIN_WARP, INTERFERENCE, LIQUID_CHROME }
    public Material material = Material.PASTEL, crystalMaterial = Material.PASTEL, handMaterial = Material.PASTEL;
    public int styleVersion;
    public Blend blend = Blend.ALPHA;
    public TargetStyle entityStyle = new TargetStyle(), crystalStyle = new TargetStyle();
    public TargetStyle handStyle = new TargetStyle(), skyStyle = new TargetStyle();
    public boolean animatedOutline = true;
    public double edgePulse = 0, glowFalloff = 1, starSpeed = 0.7;
    public Palette palette = Palette.COTTON;
    public Sky skyMode = Sky.PASTEL;
    public boolean players = true, self = false, crystals = true, hands = true;
    public boolean hostiles = false, passive = false, friends = false, invisible = true;
    public boolean sky = false, vanillaCelestials = false;
    public boolean vanillaSun = true, vanillaMoon = true;
    public int primary = 0xe8bfd8, secondary = 0xbdd8ed, accent = 0xd8cef2;
    public int quality = 16;
    public double range = 96, speed = 0.65, scale = 3, warp = 0.8;
    public double fillOpacity = 0.22, outlineOpacity = 0.55, outlineWidth = 1;
    public double glowStrength = 0.15, glowRadius = 4, brightness = 1, saturation = 0.85;
    public double pulse = 0, density = 0.65;
    public double playerStrength = 1, crystalStrength = 1.25, handStrength = 0.8;
    public double skyIntensity = 1, starDensity = 0.5;

    public static final class TargetStyle {
        public boolean customColors;
        public int primary = 0xe8bfd8, secondary = 0xbdd8ed, accent = 0xd8cef2;
        public double speed = 1, scale = 1, warp = 1, angle = 0;
        public double scrollX = 0, scrollY = 0, hueShift = 0, glow = 1, fill = 1;
        public void normalize() {
            speed=limit(speed,0,4,1); scale=limit(scale,0.25,4,1); warp=limit(warp,0,3,1);
            angle=limit(angle,0,360,0); scrollX=limit(scrollX,-2,2,0); scrollY=limit(scrollY,-2,2,0);
            hueShift=limit(hueShift,0,360,0); glow=limit(glow,0,3,1); fill=limit(fill,0,1,1);
            primary&=0xffffff; secondary&=0xffffff; accent&=0xffffff;
        }
    }
    public TargetStyle[] styles() { return new TargetStyle[] {entityStyle, crystalStyle, handStyle, skyStyle}; }

    public int targetMask() {
        return (players ? 1 : 0) | (self ? 2 : 0) | (crystals ? 4 : 0)
            | (hands ? 8 : 0) | (hostiles ? 16 : 0) | (passive ? 32 : 0);
    }

    public void usePalette() {
        int[] colors = switch (palette) {
            case COTTON -> new int[] {0xe8bfd8,0xbdd8ed,0xd8cef2};
            case LAVENDER -> new int[] {0xd0c2ea,0xe7d9f1,0xc4d6ec};
            case MINT -> new int[] {0xbddfcf,0xd7e9df,0xc4dced};
            case PEACH -> new int[] {0xf0cdbb,0xead9ce,0xe8c8d5};
            case AMETHYST -> new int[] {0x9664ff, 0x26ddff, 0xff66d9};
            case CYBERPUNK -> new int[] {0x24165c, 0x00f0ff, 0xff168e};
            case GLACIER -> new int[] {0x163a8c, 0x63e8ff, 0xe5fbff};
            case INFERNO -> new int[] {0x640723, 0xff6a00, 0xffeb83};
            case TOXIC -> new int[] {0x173f32, 0x6dff40, 0xeaff99};
            case PEARL -> new int[] {0x7c8baa, 0xe3efff, 0xffd3ef};
            case CUSTOM -> new int[] {primary, secondary, accent};
        };
        primary = colors[0]; secondary = colors[1]; accent = colors[2];
    }

    public static Material[] simpleMaterials() {
        return new Material[] {Material.PASTEL,Material.SOLID,Material.SOFT_GRADIENT,Material.PEARL,Material.FRACTAL_SPACE,Material.MILKY_WAY,Material.CAUSTICS,Material.DOMAIN_WARP,Material.INTERFERENCE,Material.LIQUID_CHROME};
    }
    public static int materialId(Material mode) {
        return switch(mode) { case SOLID -> 1; case SOFT_GRADIENT -> 2; case PEARL -> 3; case FRACTAL_SPACE -> 4; case MILKY_WAY -> 5; case CAUSTICS -> 6; case DOMAIN_WARP -> 7; case INTERFERENCE -> 8; case LIQUID_CHROME -> 9; default -> 0; };
    }
    public static Sky[] skyModes() {
        return new Sky[] {Sky.PASTEL,Sky.NIGHT_GRADIENT,Sky.FRACTAL_SPACE,Sky.MILKY_WAY,Sky.CAUSTICS,Sky.DOMAIN_WARP,Sky.INTERFERENCE,Sky.LIQUID_CHROME};
    }
    public static int skyId(Sky mode) {
        return switch(mode) {case NIGHT_GRADIENT -> 1; case FRACTAL_SPACE -> 2; case MILKY_WAY -> 3;
            case CAUSTICS -> 4; case DOMAIN_WARP -> 5; case INTERFERENCE -> 6; case LIQUID_CHROME -> 7; default -> 0;};
    }
    private static Material simplify(Material mode) {
        return mode != null && materialId(mode) != 0 ? mode : Material.PASTEL;
    }
    private void pastelDefaults() {
        material=crystalMaterial=handMaterial=Material.PASTEL;
        palette=Palette.COTTON; usePalette(); blend=Blend.ALPHA;
        entityStyle=new TargetStyle(); crystalStyle=new TargetStyle(); handStyle=new TargetStyle(); skyStyle=new TargetStyle();
        sky=false; skyMode=Sky.PASTEL; vanillaCelestials=true;
        speed=0; scale=1; warp=0; pulse=0; edgePulse=0;
        fillOpacity=0.22; outlineOpacity=0.55; outlineWidth=1;
        glowStrength=0.15; glowRadius=4; brightness=1; saturation=0.85;
        playerStrength=1; crystalStrength=1; handStrength=0.7; animatedOutline=false;
        skyIntensity=1; starDensity=0.1; starSpeed=0;
        styleVersion=2;
    }
    public void normalize() {
        if(styleVersion<2) pastelDefaults();
        if (styleVersion < 3) {
            vanillaSun = vanillaMoon = !sky || vanillaCelestials;
            styleVersion = 3;
        }
        material=simplify(material); crystalMaterial=simplify(crystalMaterial); handMaterial=simplify(handMaterial);
        if(skyMode==null || skyId(skyMode)==0) skyMode=Sky.PASTEL;
        if(palette!=Palette.COTTON && palette!=Palette.LAVENDER && palette!=Palette.MINT && palette!=Palette.PEACH && palette!=Palette.CUSTOM) {
            palette=Palette.COTTON; usePalette();
        }
        if (blend == null) blend = Blend.ALPHA;
        if (entityStyle == null) entityStyle = new TargetStyle();
        if (crystalStyle == null) crystalStyle = new TargetStyle();
        if (handStyle == null) handStyle = new TargetStyle();
        if (skyStyle == null) skyStyle = new TargetStyle();
        for (var style : styles()) style.normalize();
        edgePulse=limit(edgePulse,0,1,0); glowFalloff=limit(glowFalloff,0.25,3,1);
        starSpeed=limit(starSpeed,0,3,0.7);
        if (material == null) material = Material.NEBULA;
        if (crystalMaterial == null) crystalMaterial = Material.ELECTRIC;
        if (handMaterial == null) handMaterial = Material.LIQUID;
        if (palette == null) palette = Palette.AMETHYST;
        if (skyMode == null) skyMode = Sky.GALAXY;
        quality = quality <= 8 ? 8 : quality <= 16 ? 16 : 32;
        range = limit(range, 1, 256, 96); speed = limit(speed, 0, 4, 0.65);
        scale = limit(scale, 0.25, 12, 3); warp = limit(warp, 0, 3, 0.8);
        fillOpacity = limit(fillOpacity, 0, 1, 0.22); outlineOpacity = limit(outlineOpacity, 0, 1, 0.55);
        outlineWidth = limit(outlineWidth, 0, 6, 1); glowStrength = limit(glowStrength, 0, 3, 0.15);
        glowRadius = limit(glowRadius, 1, 24, 4); brightness = limit(brightness, 0.1, 3, 1);
        saturation = limit(saturation, 0, 3, 0.85); pulse = limit(pulse, 0, 1, 0.15);
        density = limit(density, 0, 1, 0.65);
        playerStrength = limit(playerStrength, 0, 3, 1); crystalStrength = limit(crystalStrength, 0, 3, 1.25);
        handStrength = limit(handStrength, 0, 3, 0.8); skyIntensity = limit(skyIntensity, 0, 3, 1);
        starDensity = limit(starDensity, 0, 1, 0.5);
        primary &= 0xffffff; secondary &= 0xffffff; accent &= 0xffffff;
    }

    private static double limit(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
