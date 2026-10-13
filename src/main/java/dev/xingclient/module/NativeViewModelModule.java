package dev.xingclient.module;

import dev.xingclient.XingClient;
import dev.xingclient.nativebridge.XingNativeBridge;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import org.joml.Quaternionf;

/** Applies native first-person transforms to Minecraft's matrix stack. Author: uint32. */
public final class NativeViewModelModule extends Module {
    private final double[] input = new double[12], output = new double[9];
    public NativeViewModelModule() { super("view_model", "ViewModel", ModuleCategory.VISUALS); }
    @Override protected void onEnable() { XingNativeBridge.loadLibrary(); }
    public static NativeViewModelModule active() {
        var app = XingClient.INSTANCE;
        return app != null && app.settings != null && app.viewModel != null && app.viewModel.isEnabled() ? app.viewModel : null;
    }
    public static boolean renderArms() {
        return active() == null || renders(true, XingClient.INSTANCE.settings.viewModel.renderArms);
    }
    public static boolean renderVanillaItems() {
        return active() == null || renders(true, XingClient.INSTANCE.settings.viewModel.vanillaItems);
    }
    public void apply(MatrixStack matrices, Hand hand, Arm arm) {
        var s = XingClient.INSTANCE.settings.viewModel;
        s.normalize();
        input[0]=s.mainX; input[1]=s.mainY; input[2]=s.mainZ;
        input[3]=s.offX; input[4]=s.offY; input[5]=s.offZ;
        input[6]=s.rotateX; input[7]=s.rotateY; input[8]=s.rotateZ;
        input[9]=s.scaleX; input[10]=s.scaleY; input[11]=s.scaleZ;
        transform(input, output, hand == Hand.MAIN_HAND, arm == Arm.RIGHT, s.syncHands);
        matrices.translate(output[0], output[1], output[2]);
        matrices.multiply(new Quaternionf().rotationXYZ((float)output[3], (float)output[4], (float)output[5]));
        matrices.scale((float)output[6], (float)output[7], (float)output[8]);
    }
    private static native void transform(double[] input, double[] output, boolean mainHand, boolean rightArm, boolean syncHands);
    private static native boolean renders(boolean enabled, boolean configured);
}
