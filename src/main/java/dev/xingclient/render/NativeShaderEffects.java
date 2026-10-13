package dev.xingclient.render;

/** Native material catalog and target policy. Author: uint32. */
public final class NativeShaderEffects {
    private NativeShaderEffects() { }
    public static native String source(int stage);
    public static native boolean captures(int category, int targets, double distanceSquared, double range,
        boolean invisible, boolean includeInvisible, boolean friendPlayer, boolean includeFriends);
}
