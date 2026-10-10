package dev.xingclient.ui;

import com.mojang.authlib.GameProfile;
import dev.xingclient.XingClient;
import dev.xingclient.module.MaceKillModule;
import dev.xingclient.module.MaceSequence;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldCreator;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Difficulty;
import net.minecraft.world.gen.WorldPresets;
import org.lwjgl.glfw.GLFW;
import java.util.EnumSet;
import java.util.UUID;

/** Opt-in vanilla integration test; this deliberately makes no Grim compatibility claim. */
public final class MaceSmoke {
    private final XingClient app;
    private final long started = System.currentTimeMillis();
    private int phase, ticks, heldTicks, oldSlot;
    private boolean done;
    private Vec3d held;
    private long heldWorldTime;
    private final EnumSet<MaceSequence.Phase> seen = EnumSet.noneOf(MaceSequence.Phase.class);
    public MaceSmoke(XingClient app) { this.app = app; }
    public void tick(MinecraftClient mc) {
        if (done) return;
        try {
            require(System.currentTimeMillis() - started < 240_000, "integration timeout");
            mc.onWindowFocusChanged(true);
            if (mc.getOverlay() != null) return;
            if (phase == 0) {
                mc.options.pauseOnLostFocus = false;
                mc.options.getViewDistance().setValue(2); mc.options.getSimulationDistance().setValue(5);
                CreateWorldScreen.show(mc, new TitleScreen()); phase = 1;
            } else if (phase == 1 && mc.currentScreen instanceof CreateWorldScreen create) {
                var creator = create.getWorldCreator();
                creator.setWorldName("xing-mace-check-" + started);
                creator.setGameMode(WorldCreator.Mode.CREATIVE); creator.setDifficulty(Difficulty.PEACEFUL);
                creator.setGenerateStructures(false);
                creator.getNormalWorldTypes().stream().filter(t -> t.preset() != null && t.preset().matchesKey(WorldPresets.FLAT)).findFirst().ifPresent(creator::setWorldType);
                create.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast)
                        .filter(b -> b.getMessage().getString().equals(Text.translatable("selectWorld.create").getString())).findFirst().orElseThrow().onPress();
                phase = 2;
            } else if (phase == 2 && mc.player != null && mc.world != null && mc.currentScreen == null) {
                if (++ticks < 40) return;
                mc.getNetworkHandler().sendChatCommand("give @s minecraft:mace");
                mc.getNetworkHandler().sendChatCommand("give @s minecraft:wind_charge 64");
                phase = 3; ticks = 0;
            } else if (phase == 3) {
                if (++ticks < 20) return;
                require(mc.player.getInventory().contains(new net.minecraft.item.ItemStack(Items.MACE)), "server supplied mace");
                oldSlot = mc.player.getInventory().getSelectedSlot();
                app.screen.parent(null); mc.setScreen(app.screen);
                app.screen.keyPressed(GLFW.GLFW_KEY_F,0,GLFW.GLFW_MOD_CONTROL);
                for(char c : "MaceKill".toCharArray()) app.screen.charTyped(c,0);
                require(app.screen.searchResultCount()==1,"registered and searchable");
                app.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);
                require(app.screen.settingsExpanded(MaceKillModule.ID),"settings expand");
                phase=4;ticks=0;
            } else if (phase==4) {
                if(++ticks<12)return;
                ScreenshotRecorder.saveScreenshot(mc.runDirectory,"23-mace-settings.png",mc.getFramebuffer(),m->System.out.println(m.getString()));
                app.settings.maceKill.charges=2; app.settings.maceKill.minimumFall=1.6;
                app.maceKill.setEnabled(true); mc.setScreen(null); phase=5;ticks=0;
            } else if (phase==5) {
                ticks++;
                var current=app.maceKill.phase();
                if(seen.add(current))System.out.println("MACE_STAGE "+current+" "+app.maceKill.status());
                require(app.maceKill.isEnabled(),"sequence stopped: "+app.maceKill.status());
                require(ticks<400,"sequence did not reach hold");
                if(current==MaceSequence.Phase.HELD){held=mc.player.getPos();heldWorldTime=mc.world.getTime();phase=6;}
            } else if (phase==6) {
                heldTicks++;
                require(app.maceKill.frozen(),"hold remains enabled");
                require(mc.player.getPos().squaredDistanceTo(held)<.00001,"movement frozen without freezing the game");
                if(heldTicks==10){
                    require(mc.world.getTime()>heldWorldTime,"world and game loop continue");
                    require(seen.containsAll(EnumSet.of(MaceSequence.Phase.CHARGING,MaceSequence.Phase.ASCENDING,MaceSequence.Phase.FALLING,MaceSequence.Phase.HELD)),"full sequence visited");
                    ScreenshotRecorder.saveScreenshot(mc.runDirectory,"24-mace-held.png",mc.getFramebuffer(),m->System.out.println(m.getString()));
                    var target=new OtherClientPlayerEntity(mc.world,new GameProfile(UUID.randomUUID(),"MaceFixture"));
                    target.setId(900700);target.setPosition(mc.player.getPos().add(0,0,2));mc.world.addEntity(target);
                    mc.interactionManager.attackEntity(mc.player,target);
                    require(!app.maceKill.isEnabled()&&!app.maceKill.frozen(),"manual attack releases hold");
                    require(mc.player.getInventory().getSelectedSlot()==oldSlot,"original slot restored");
                    mc.world.removeEntity(target.getId(),net.minecraft.entity.Entity.RemovalReason.DISCARDED);
                    phase=7;ticks=0;
                }
            } else if(phase==7) {
                require(++ticks<100,"released player lands");
                if(mc.player.isOnGround()){
                    app.settings.maceKill.autoAttack=true;app.maceKill.setEnabled(true);phase=8;ticks=0;
                }
            } else if(phase==8) {
                require(++ticks<400&&app.maceKill.isEnabled(),"second sequence: "+app.maceKill.status());
                if(app.maceKill.phase()==MaceSequence.Phase.HELD){
                    var friend=new OtherClientPlayerEntity(mc.world,new GameProfile(UUID.randomUUID(),"MaceFriend"));
                    friend.setId(900701);friend.setPosition(mc.player.getPos().add(1,0,0));mc.world.addEntity(friend);
                    app.friends.add("MaceFriend");phase=9;ticks=0;
                }
            } else if(phase==9) {
                require(app.maceKill.isEnabled(),"auto attack excludes friends");
                if(++ticks==6){
                    var enemy=new OtherClientPlayerEntity(mc.world,new GameProfile(UUID.randomUUID(),"MaceEnemy"));
                    enemy.setId(900702);enemy.setPosition(mc.player.getPos().add(0,0,2));mc.world.addEntity(enemy);
                    phase=10;ticks=0;
                }
            } else if(phase==10) {
                require(++ticks<20,"auto attack selects nearby non-friend");
                if(!app.maceKill.isEnabled()){
                    require(app.maceKill.status().startsWith("Attack sent"),"auto attack consumed attempt");
                    require(!app.maceKill.frozen()&&mc.player.getInventory().getSelectedSlot()==oldSlot,"auto attack restores movement and slot");
                    done=true;System.out.println("XINGCLIENT_MACE_SMOKE_PASS");mc.scheduleStop();
                }
            }
        } catch(Throwable failure){done=true;failure.printStackTrace();System.out.println("XINGCLIENT_MACE_SMOKE_FAIL");mc.scheduleStop();}
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
