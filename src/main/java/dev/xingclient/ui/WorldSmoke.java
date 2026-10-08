package dev.xingclient.ui;

import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.world.Difficulty;
import net.minecraft.world.gen.WorldPresets;
import org.lwjgl.glfw.GLFW;
import com.mojang.authlib.GameProfile;
import dev.xingclient.HazelTarget;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import java.util.UUID;

/** Opt-in test in a newly created flat world under the isolated development run directory. */
public final class WorldSmoke {
    private final XingClient app;
    private final long started=System.currentTimeMillis();
    private int phase,ticks;
    private long worldTime;
    private boolean done;
    private FixturePlayer fixture;
    private net.minecraft.util.math.Vec3d originalPosition;
    private net.minecraft.client.option.Perspective originalPerspective;
    private static final class FixturePlayer extends OtherClientPlayerEntity {
        boolean gliding;
        FixturePlayer(ClientWorld world,GameProfile profile){super(world,profile);}
        @Override public boolean isGliding(){return gliding;}
    }
    public WorldSmoke(XingClient app){this.app=app;}
    public void tick(MinecraftClient mc){
        if(done)return;
        try {
            if(System.currentTimeMillis()-started>300_000)throw new AssertionError("World check timed out in phase "+phase);
            if(mc.getOverlay()!=null)return;
            if(phase==0){
                GLFW.glfwRestoreWindow(mc.getWindow().getHandle());
                GLFW.glfwSetWindowSize(mc.getWindow().getHandle(),1440,900);
                mc.options.pauseOnLostFocus=false;
                mc.options.getViewDistance().setValue(2);
                mc.options.getSimulationDistance().setValue(5);
                CreateWorldScreen.show(mc,new TitleScreen());phase=1;
            }else if(phase==1&&mc.currentScreen instanceof CreateWorldScreen create){
                var creator=create.getWorldCreator();
                creator.setWorldName("xingclient-gui-check-"+started);
                creator.setGameMode(WorldCreator.Mode.CREATIVE);
                creator.setDifficulty(Difficulty.PEACEFUL);
                creator.setGenerateStructures(false);
                creator.getNormalWorldTypes().stream().filter(type->type.preset()!=null&&type.preset().matchesKey(WorldPresets.FLAT)).findFirst().ifPresent(creator::setWorldType);
                create.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                    .filter(button->button.getMessage().getString().equals(Text.translatable("selectWorld.create").getString()))
                    .findFirst().orElseThrow().onPress();
                phase=2;
            }else if(phase==2&&mc.world!=null&&mc.player!=null&&mc.currentScreen==null){
                if(ticks==0)mc.player.setPitch(18f);
                if(++ticks<40)return;
                if(app.music.isActive())throw new AssertionError("Menu song must pause during gameplay");
                ScreenshotRecorder.saveScreenshot(mc.runDirectory,"14-world-before-blur.png",mc.getFramebuffer(),message->System.out.println(message.getString()));
                app.screen.parent(null);mc.setScreen(app.screen);
                worldTime=mc.world.getTime();ticks=0;phase=3;
            }else if(phase==3){
                ticks++;
                if(ticks==30){
                    if(!app.music.isActive()||app.music.failure()!=null)throw new AssertionError("Menu song must play in ClickGUI");
                    if(mc.isPaused()||app.screen.shouldPause()||mc.world.getTime()<=worldTime)throw new AssertionError("Game should keep ticking under the GUI");
                    ScreenshotRecorder.saveScreenshot(mc.runDirectory,"12-in-world.png",mc.getFramebuffer(),message->System.out.println(message.getString()));
                }
                if(ticks==40){
                    app.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
                    if(mc.currentScreen!=null)throw new AssertionError("Escape should return to gameplay");
                }
                if(ticks==50){if(app.music.isActive())throw new AssertionError("Music must pause after closing GUI");setupPlayers(mc);phase=4;ticks=0;}
            }else if(phase==4){
                checkPlayers(mc);
            }
        }catch(Throwable error){done=true;error.printStackTrace();System.out.println("XINGCLIENT_WORLD_SMOKE_FAIL");mc.scheduleStop();}
    }
    private void setupPlayers(MinecraftClient mc){
        for(int i=0;i<26;i++){
            var player=new FixturePlayer(mc.world,new GameProfile(UUID.randomUUID(),String.format("Fixture%02d",i)));
            player.setId(900000+i);player.setPosition(mc.player.getX()+(i==25?200:3+i*.6),mc.player.getY(),mc.player.getZ());
            player.setYaw(75);player.bodyYaw=75;player.headYaw=75;mc.world.addEntity(player);
            if(i==0){fixture=player;fixture.equipStack(EquipmentSlot.CHEST,new ItemStack(Items.ELYTRA));fixture.equipStack(EquipmentSlot.MAINHAND,new ItemStack(Items.NETHERITE_SWORD));}
        }
        app.screen.parent(null);mc.setScreen(app.screen);app.screen.keyPressed(GLFW.GLFW_KEY_P,0,0);
    }
    private void checkPlayers(MinecraftClient mc){
        ticks++;mc.onWindowFocusChanged(true);
        if(!fixture.isRemoved()){
            fixture.limbAnimator.updateLimbs(.7f,1,1);fixture.setPose(fixture.gliding?EntityPose.GLIDING:EntityPose.STANDING);
        }
        switch(ticks){
            case 15 -> {require(app.screen.playerList().isOpen()&&app.screen.playerList().count()==25,"P lists only loaded players inside render distance");shot(mc,"17-player-list.png");}
            case 20 -> {double f=app.screen.renderFactor();app.screen.mouseScrolled(500*f,350*f,0,-2);}
            case 22 -> {require(app.screen.playerList().firstRow()>0,"player grid scrolls");double f=app.screen.renderFactor();app.screen.mouseScrolled(500*f,350*f,0,10);}
            case 25 -> click(435,314);
            case 26 -> click(435,314);
            case 30 -> {require(fixture.getUuid().equals(app.screen.playerList().selected()),"double-click opens correct player");shot(mc,"18-player-details-walking.png");}
            case 35 -> click(720,448);
            case 36 -> {require(HazelTarget.get(app)!=null&&HazelTarget.get(app).uuid.equals(fixture.getUuidAsString()),"target saves UUID");app.store.flush();require(app.store.load().targets.get(HazelTarget.scope(mc)).uuid.equals(fixture.getUuidAsString()),"saved target persists");mc.getNetworkHandler().sendChatCommand("hazel");}
            case 40 -> {click(720,412);require(mc.currentScreen==app.screen&&mc.getCameraEntity()==mc.player,"disabled Kill is inert");fixture.gliding=true;}
            case 55 -> {shot(mc,"19-player-details-gliding.png");require(fixture.getYaw()==75,"preview does not mutate live player yaw");fixture.gliding=false;}
            case 60 -> {originalPosition=mc.player.getPos();originalPerspective=mc.options.getPerspective();click(720,556);}
            case 65 -> {require(mc.currentScreen instanceof PlayerSpectateScreen&&mc.getCameraEntity()==fixture,"spectate attaches local camera");shot(mc,"20-player-spectate.png");}
            case 66 -> {
                var screen=(PlayerSpectateScreen)mc.currentScreen;float yaw=screen.cameraYaw();
                screen.mouseDragged(100,100,1,100,30);require(screen.cameraYaw()==yaw,"orbit requires right-button hold");
                screen.mouseClicked(100,100,1);screen.mouseDragged(200,130,1,100,30);
                require(screen.cameraYaw()!=yaw,"right drag changes orbit");screen.mouseReleased(200,130,1);
                yaw=screen.cameraYaw();screen.mouseDragged(240,150,1,40,20);require(screen.cameraYaw()==yaw,"release stops orbit");
            }
            case 69 -> {
                var screen=(PlayerSpectateScreen)mc.currentScreen;var camera=mc.gameRenderer.getCamera();
                require(Math.abs(camera.getYaw()-screen.cameraYaw())<.01&&Math.abs(camera.getPitch()-screen.cameraPitch())<.01,"render camera uses orbit angles");
                require(fixture.getYaw()==75,"orbit does not rotate target");shot(mc,"22-player-spectate-orbit.png");
                screen.mouseClicked(0,0,1);screen.mouseDragged(0,0,1,0,10000);require(screen.cameraPitch()==85,"orbit clamps vertical angle");screen.mouseReleased(0,0,1);
            }
            case 80 -> mc.currentScreen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
            case 85 -> {require(mc.currentScreen==app.screen&&mc.getCameraEntity()==mc.player&&mc.options.getPerspective()==originalPerspective,"spectate restores camera and perspective");require(mc.player.getPos().squaredDistanceTo(originalPosition)<.001,"spectate does not move local player");require(fixture.getUuid().equals(app.screen.playerList().selected()),"spectate returns to selected details");}
            case 90 -> mc.world.removeEntity(fixture.getId(),Entity.RemovalReason.DISCARDED);
            case 100 -> {require(app.screen.playerList().count()==24,"unloaded player removed from list");shot(mc,"21-player-unavailable.png");click(720,556);require(mc.currentScreen==app.screen,"unavailable player cannot be spectated");}
            case 105 -> {app.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);require(app.screen.playerList().selected()==null,"Escape returns to list");app.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);}
            case 115 -> {app.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);require(mc.currentScreen==null,"closing list returns to gameplay normally");done=true;System.out.println("XINGCLIENT_WORLD_SMOKE_PASS");mc.scheduleStop();}
        }
    }
    private void click(double x,double y){double f=app.screen.renderFactor();app.screen.mouseClicked(x*f,y*f,0);app.screen.mouseReleased(x*f,y*f,0);}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void shot(MinecraftClient mc,String name){ScreenshotRecorder.saveScreenshot(mc.runDirectory,name,mc.getFramebuffer(),message->System.out.println(message.getString()));}
}
