package dev.xingclient.ui;

import dev.xingclient.XingClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.ScreenshotRecorder;
import org.lwjgl.glfw.GLFW;

/** Opt-in native visual/input check. Uses an isolated development settings file. */
public final class NativeSmoke {
    private final XingClient app;
    private int tick;
    private long started=System.currentTimeMillis();
    private boolean ready;
    public NativeSmoke(XingClient app){this.app=app;}
    public void tick(MinecraftClient mc){
        try {
            if(System.currentTimeMillis()-started>180_000)throw new AssertionError("Native check timed out");
            if(!ready){if(mc.getOverlay()!=null)return;if(!(mc.currentScreen instanceof TitleScreen)){System.out.println("Native check starting from "+mc.currentScreen);mc.setScreen(new TitleScreen());return;}ready=true;}
            tick++;
            // Synthetic drags do not focus the OS window. Simulate focus for these
            // fixture steps so normal focus-loss cancellation doesn't end them.
            if(tick>=270)mc.onWindowFocusChanged(true);
            switch(tick){
                case 5 -> {GLFW.glfwRestoreWindow(mc.getWindow().getHandle());GLFW.glfwSetWindowSize(mc.getWindow().getHandle(),1440,900);}
                case 65 -> {shot(mc,"01-title-background.png");require(app.music.isActive()&&app.music.decodedFrames()>0&&app.music.failure()==null,"menu music decodes");}
                case 68 -> mc.setScreen(new net.minecraft.client.gui.screen.world.SelectWorldScreen(new TitleScreen()));
                case 72 -> require(app.music.isActive(),"music continues in singleplayer menu");
                case 75 -> {app.screen.parent(new TitleScreen());mc.setScreen(app.screen);}
                case 90 -> shot(mc,"02-clickgui-default.png");
                case 95 -> {click(120,93,0);require(app.settings.modules.get("Combat.AutoArmor").enabled,"module toggle");}
                case 100 -> click(130,145,1);
                case 110 -> shot(mc,"03-module-settings.png");
                case 115 -> click(240,178,0);
                case 117 -> require(app.settings.modules.get("Combat.AutoCrystal").mode==1,"mode control");
                case 120 -> {click(270,273,0);app.screen.keyPressed(GLFW.GLFW_KEY_G,0,0);require(app.settings.modules.get("Combat.AutoCrystal").key==GLFW.GLFW_KEY_G,"key capture");}
                case 122 -> {click(270,273,0);click(20,20,4);require(app.settings.modules.get("Combat.AutoCrystal").mouse&&app.settings.modules.get("Combat.AutoCrystal").key==4,"mouse bind capture");}
                case 124 -> {click(270,273,0);app.screen.keyPressed(GLFW.GLFW_KEY_G,0,0);}
                case 125 -> click(130,145,1);
                case 130 -> {app.screen.keyPressed(GLFW.GLFW_KEY_F,0,GLFW.GLFW_MOD_CONTROL);app.screen.charTyped('f',GLFW.GLFW_MOD_CONTROL);for(char c:"HOLESNAP".toCharArray())app.screen.charTyped(c,0);require(app.screen.searchQuery().equals("HOLESNAP")&&app.screen.searchResultCount()==1,"auto-focused case-insensitive search");}
                case 140 -> shot(mc,"04-search-overlay.png");
                case 145 -> click(700,235,0);
                case 150 -> {require(!app.screen.searchOpen()&&app.screen.settingsExpanded("Movement.HoleSnap"),"click result opens HoleSnap settings");shot(mc,"05-holesnap-settings.png");}
                case 155 -> {app.screen.keyPressed(GLFW.GLFW_KEY_F,0,GLFW.GLFW_MOD_CONTROL);require(app.screen.searchResultCount()==91,"all results available");}
                case 160 -> {double f=app.screen.renderFactor();app.screen.mouseScrolled(700*f,350*f,0,-30);shot(mc,"06-search-all.png");}
                case 165 -> {app.screen.keyPressed(GLFW.GLFW_KEY_A,0,GLFW.GLFW_MOD_CONTROL);for(char c:"nomatchingmodule".toCharArray())app.screen.charTyped(c,0);require(app.screen.searchResultCount()==0,"no-result state");}
                case 170 -> shot(mc,"07-search-empty.png");
                case 175 -> {app.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);require(!app.screen.searchOpen()&&mc.currentScreen==app.screen,"Escape dismisses search only");}
                case 185 -> {double f=app.screen.renderFactor();app.screen.mouseClicked(165*f,65*f,0);app.screen.mouseDragged(210*f,100*f,0,45*f,35*f);app.screen.mouseReleased(210*f,100*f,0);require(app.settings.positions.containsKey("Combat"),"panel drag");}
                case 190 -> shot(mc,"08-dragged.png");
                case 200 -> {app.changed();app.store.flush();var restored=app.store.load();require(restored.modules.get("Combat.AutoArmor").enabled,"saved state restore");require(restored.modules.get("Combat.AutoCrystal").key==GLFW.GLFW_KEY_G,"saved bind restore");mc.options.getGuiScale().setValue(2);mc.onResolutionChanged();}
                case 210 -> shot(mc,"09-gui-scale-two.png");
                case 215 -> {GLFW.glfwSetWindowSize(mc.getWindow().getHandle(),1000,720);}
                case 235 -> shot(mc,"10-small-window.png");
                case 237 -> {double f=app.screen.renderFactor();app.screen.mouseScrolled(380*f,250*f,0,-8);}
                case 240 -> shot(mc,"11-scrolled.png");
                case 245 -> {app.screen.keyPressed(GLFW.GLFW_KEY_F,0,GLFW.GLFW_MOD_CONTROL);for(char c:"ViewModel".toCharArray())app.screen.charTyped(c,0);}
                case 250 -> app.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);
                case 260 -> {require(app.screen.settingsExpanded("Visuals.ViewModel"),"keyboard selection reveals lower module");shot(mc,"13-search-reveals-scrolled-settings.png");}
                case 265 -> {app.screen.close();require(mc.currentScreen instanceof TitleScreen,"returns to title");}
                case 270 -> mc.setScreen(app.screen);
                case 275 -> {
                    var m=app.screen.mascotMotion();double f=app.screen.renderFactor();
                    click(m.x()+m.width()-1,m.y()+1,0);require(!m.held(),"transparent mascot pixels ignore clicks");
                    app.screen.mouseClicked((m.x()+m.width()*.5)*f,(m.y()+m.height()*.55)*f,0);
                    require(m.held(),"mascot can be picked up");
                    app.screen.mouseDragged(700*f,200*f,0,0,0);require(m.y()<80,"mascot follows cursor offset");
                }
                case 280 -> {require(app.screen.mascotMotion().held(),"mascot stays held");shot(mc,"14-hazel-held.png");}
                case 285 -> {double f=app.screen.renderFactor();app.screen.mouseReleased(700*f,200*f,0);require(app.screen.mascotMotion().airborne(),"mascot released above floor");}
                case 290 -> {require(!app.screen.mascotMotion().held()&&app.screen.mascotMotion().airborne(),"gentle fall takes time");shot(mc,"15-hazel-falling.png");}
                case 295 -> {
                    var m=app.screen.mascotMotion();double f=app.screen.renderFactor();
                    app.screen.mouseClicked((m.x()+m.width()*.5)*f,(m.y()+m.height()*.55)*f,0);
                    require(m.held(),"mascot can be caught mid-fall above panels");
                    app.screen.keyPressed(GLFW.GLFW_KEY_F,0,GLFW.GLFW_MOD_CONTROL);require(!m.held(),"search releases mascot capture");
                    app.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
                }
                case 345 -> {require(!app.screen.mascotMotion().airborne(),"mascot settles on bottom edge");shot(mc,"16-hazel-landed.png");}
                case 350 -> {require(app.music.failure()==null,"music streaming stayed healthy");System.out.println("XINGCLIENT_NATIVE_SMOKE_PASS");mc.scheduleStop();}
                default -> {}
            }
        }catch(Throwable error){error.printStackTrace();System.out.println("XINGCLIENT_NATIVE_SMOKE_FAIL");mc.scheduleStop();}
    }
    private void click(double x,double y,int button){double f=app.screen.renderFactor();app.screen.mouseClicked(x*f,y*f,button);app.screen.mouseReleased(x*f,y*f,button);}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    private static void shot(MinecraftClient mc,String name){ScreenshotRecorder.saveScreenshot(mc.runDirectory,name,mc.getFramebuffer(),message->System.out.println(message.getString()));}
}
