package dev.xingclient.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/** Read-only local camera. Keeping a screen open prevents gameplay movement input. */
public final class PlayerSpectateScreen extends Screen {
    private final XingScreen parent;
    private final PlayerEntity target;
    private final ClientWorld world;
    private final Entity previousCamera;
    private final Perspective previousPerspective;
    private boolean attached;
    private boolean orbitDragging;
    private float orbitYaw,orbitPitch;
    public PlayerSpectateScreen(XingScreen parent,PlayerEntity target){
        super(Text.literal("Spectate"));this.parent=parent;this.target=target;
        var mc=MinecraftClient.getInstance();world=mc.world;previousCamera=mc.getCameraEntity();previousPerspective=mc.options.getPerspective();
    }
    @Override protected void init(){orbitDragging=false;if(!attached){orbitYaw=target.getYaw();orbitPitch=Math.clamp(target.getPitch(),-85,85);client.options.setPerspective(Perspective.THIRD_PERSON_BACK);client.setCameraEntity(target);attached=true;}}
    public boolean ownsCamera(Entity entity){return attached&&entity==target;}
    public float cameraYaw(){return orbitYaw;}
    public float cameraPitch(){return orbitPitch;}
    @Override public boolean shouldPause(){return false;}
    @Override public void renderBackground(DrawContext ctx,int x,int y,float delta){}
    @Override public void render(DrawContext ctx,int x,int y,float delta){}
    @Override public void tick(){if(!client.isWindowFocused())orbitDragging=false;if(client.world!=world||client.player==null||target.isRemoved()||!target.isAlive()||world.getEntityById(target.getId())!=target)close();}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT)orbitDragging=true;return true;}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){
        if(orbitDragging&&button==GLFW.GLFW_MOUSE_BUTTON_RIGHT&&client.isWindowFocused()){
            double sensitivity=client.getWindow().getScaleFactor()*.25;
            orbitYaw=net.minecraft.util.math.MathHelper.wrapDegrees(orbitYaw+(float)(dx*sensitivity));
            orbitPitch=Math.clamp(orbitPitch+(float)(dy*sensitivity),-85,85);
        }
        return true;
    }
    @Override public boolean mouseReleased(double x,double y,int button){if(button==GLFW.GLFW_MOUSE_BUTTON_RIGHT)orbitDragging=false;return true;}
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(key==GLFW.GLFW_KEY_ESCAPE||key==GLFW.GLFW_KEY_P||key==GLFW.GLFW_KEY_RIGHT_SHIFT){close();return true;}return true;}
    @Override public void close(){boolean back=client.world==world&&client.player!=null;client.setScreen(back?parent:null);if(back)parent.playerList().inspect(target);}
    @Override public void removed(){
        orbitDragging=false;
        if(attached){client.options.setPerspective(previousPerspective);client.setCameraEntity(client.world==world&&previousCamera!=null&&!previousCamera.isRemoved()?previousCamera:client.player);attached=false;}
    }
}
