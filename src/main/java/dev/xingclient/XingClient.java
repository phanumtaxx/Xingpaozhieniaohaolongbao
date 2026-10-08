package dev.xingclient;

import dev.xingclient.ui.XingScreen;
import dev.xingclient.ui.NativeSmoke;
import dev.xingclient.ui.WorldSmoke;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class XingClient implements ClientModInitializer {
    public static XingClient INSTANCE;
    public ClientSettings settings;
    public SettingsStore store;
    public XingScreen screen;
    public MenuMusic music;
    private boolean shiftHeld;
    private final Set<String> pressed = new HashSet<>();
    private NativeSmoke smoke;
    private WorldSmoke worldSmoke;
    @Override public void onInitializeClient() {
        INSTANCE=this;
        boolean worldCheck=FabricLoader.getInstance().isDevelopmentEnvironment()&&Boolean.getBoolean("xingclient.worldSmoke");
        boolean guiCheck=FabricLoader.getInstance().isDevelopmentEnvironment()&&Boolean.getBoolean("xingclient.smoke");
        boolean smokeMode=guiCheck||worldCheck;
        music=new MenuMusic(smokeMode);
        Runtime.getRuntime().addShutdownHook(new Thread(music::close,"xingclient-music-on-exit"));
        store=new SettingsStore(FabricLoader.getInstance().getConfigDir().resolve(smokeMode?"xingclient-smoke":"xingclient").resolve("settings.json"));
        settings=store.load();screen=new XingScreen(this);
        if(smokeMode) { settings=new ClientSettings().normalize();if(worldCheck)worldSmoke=new WorldSmoke(this);else smoke=new NativeSmoke(this); }
        Runtime.getRuntime().addShutdownHook(new Thread(store::flush,"xingclient-save-on-exit"));
    }
    public void changed() { store.save(settings); }
    public void tick(MinecraftClient mc) {
        music.setActive(mc.currentScreen!=null);
        if(mc.getWindow()==null) return;
        long handle=mc.getWindow().getHandle();
        boolean shift=InputUtil.isKeyPressed(handle,GLFW.GLFW_KEY_RIGHT_SHIFT);
        if(shift&&!shiftHeld&&(mc.currentScreen==screen||mc.currentScreen instanceof TitleScreen||mc.currentScreen==null)&&mc.getOverlay()==null&&mc.isWindowFocused()) {
            if(mc.currentScreen==screen)screen.close();else{screen.parent(mc.currentScreen);mc.setScreen(screen);}
        }
        shiftHeld=shift;
        // Preview binds only change saved module toggles; no gameplay modules are implemented.
        for(var pair:settings.modules.entrySet()) {
            var entry=pair.getValue();boolean down=entry.key>=0&&(entry.mouse?GLFW.glfwGetMouseButton(handle,entry.key)==GLFW.GLFW_PRESS:InputUtil.isKeyPressed(handle,entry.key));
            if(down&&!pressed.contains(pair.getKey())&&mc.currentScreen==null&&mc.world!=null&&mc.isWindowFocused()) {entry.enabled=!entry.enabled;changed();}
            if(down)pressed.add(pair.getKey());else pressed.remove(pair.getKey());
        }
        if(smoke!=null)smoke.tick(mc);
        if(worldSmoke!=null)worldSmoke.tick(mc);
    }
}
