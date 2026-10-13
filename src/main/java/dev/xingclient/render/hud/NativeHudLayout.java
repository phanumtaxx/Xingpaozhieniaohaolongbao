package dev.xingclient.render.hud;

/** Native screen-space placement adapter. Author: uint32. */
final class NativeHudLayout {
    private NativeHudLayout() {}
    static native void place(int[] anchors,double[] offsets,double[] output,int width,int height,double scale);
}
