package dev.xingclient.render.web;

import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Captures the renderer's matrices, including its FOV and camera effects. */
public final class WorldProjection {
    private final double[] matrix=new double[16], camera=new double[3];
    private final float[] captured=new float[16];
    private boolean ready;
    public void capture(Camera view,Matrix4f position,Matrix4f projection) {
        new Matrix4f(projection).mul(position).get(captured);
        for(int i=0;i<16;i++) matrix[i]=captured[i];
        Vec3d location=view.getPos();camera[0]=location.x;camera[1]=location.y;camera[2]=location.z;
        ready=true;
    }
    public void invalidate() { ready=false; }
    public boolean ready() { return ready; }
    public void projectPolygons(double[] points, int[] sizes, double[] output, int width, int height, boolean twoSided) {
        if (!ready) throw new IllegalStateException("No world render frame captured");
        NativeWebEngine.projectPolygons(matrix, camera, points, sizes, output, width, height, twoSided);
    }
    public void project(double[] points,double[] output,int width,int height) {
        if(!ready) throw new IllegalStateException("No world render frame captured");
        NativeWebEngine.project(matrix,camera,points,output,width,height);
    }
}
