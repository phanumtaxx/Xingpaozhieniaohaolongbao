package dev.xingclient;

import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.EventBus;
import dev.xingclient.module.Module;
import dev.xingclient.module.ModuleManager;
import dev.xingclient.module.NativeExampleModule;
import dev.xingclient.module.NativeAutoCrystalModule;
import dev.xingclient.module.FakePlayerModule;
import dev.xingclient.module.MaceKillModule;
import dev.xingclient.manager.ClientManagers;
import dev.xingclient.ui.NativeSmoke;
import dev.xingclient.ui.WorldSmoke;
import dev.xingclient.ui.MaceSmoke;
import dev.xingclient.ui.XingScreen;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

public final class XingClient implements ClientModInitializer {
    public static XingClient INSTANCE;
    public ClientSettings settings;
    public SettingsStore store;
    public FriendManager friends;
    public final MiningSyncState miningSync = new MiningSyncState();
    public XingScreen screen;
    public MenuMusic music;
    public final EventBus events = new EventBus();
    public final ModuleManager modules = new ModuleManager();
    public MaceKillModule maceKill;
    public final ClientManagers managers = new ClientManagers(events);
    public MenuData menuData;
    private boolean shiftHeld;
    private final Set<String> pressed = new HashSet<>();
    private NativeSmoke smoke;
    private WorldSmoke worldSmoke;
    private MaceSmoke maceSmoke;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        modules.register(new NativeExampleModule(events));
        modules.register(new FakePlayerModule(events));
        modules.register(new NativeAutoCrystalModule(events));
        maceKill = new MaceKillModule(events);
        modules.register(maceKill);
        menuData = new MenuData(modules);

        boolean worldCheck = FabricLoader.getInstance().isDevelopmentEnvironment()
            && Boolean.getBoolean("xingclient.worldSmoke");
        boolean guiCheck = FabricLoader.getInstance().isDevelopmentEnvironment()
            && Boolean.getBoolean("xingclient.smoke");
        boolean maceCheck = FabricLoader.getInstance().isDevelopmentEnvironment() && Boolean.getBoolean("xingclient.maceSmoke");
        boolean smokeMode = guiCheck || worldCheck || maceCheck;
        music = new MenuMusic(smokeMode);
        Runtime.getRuntime().addShutdownHook(new Thread(music::close, "xingclient-music-on-exit"));
        store = new SettingsStore(FabricLoader.getInstance().getConfigDir()
            .resolve(smokeMode ? "xingclient-smoke" : "xingclient")
            .resolve("settings.json"));
        settings = store.load(menuData);
        friends = new FriendManager(settings, this::changed);
        restoreModules();
        screen = new XingScreen(this);
        if (smokeMode) {
            settings = new ClientSettings().normalize(menuData);
            if (maceCheck) maceSmoke = new MaceSmoke(this);
            else if (worldCheck) worldSmoke = new WorldSmoke(this);
            else smoke = new NativeSmoke(this);
        }
        Runtime.getRuntime().addShutdownHook(new Thread(store::flush, "xingclient-save-on-exit"));
    }

    private void restoreModules() {
        for (Module module : modules.all()) {
            ClientSettings.Entry entry = settings.modules.get(module.id());
            // An experimental launch must always be started explicitly, never on login.
            if (module instanceof MaceKillModule) { entry.enabled = false; continue; }
            if (!entry.enabled) {
                continue;
            }
            try {
                module.setEnabled(true);
            } catch (LinkageError error) {
                entry.enabled = false;
                LoggerFactory.getLogger("xingclient").warn("Could not enable module " + module.name(), error);
                changed();
            }
        }
    }

    public void changed() {
        store.save(settings);
    }

    public void postTickEvent(ClientTickEvent.Phase phase) {
        if (phase == ClientTickEvent.Phase.START) managers.tick(MinecraftClient.getInstance());
        events.post(new ClientTickEvent(phase));
        if (phase == ClientTickEvent.Phase.END) managers.scheduler.resolve();
    }

    public void tick(MinecraftClient mc) {
        music.setActive(mc.currentScreen != null);
        if (mc.getWindow() == null) return;
        long handle = mc.getWindow().getHandle();
        boolean shift = InputUtil.isKeyPressed(handle, GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (shift && !shiftHeld
            && (mc.currentScreen == screen || mc.currentScreen instanceof TitleScreen || mc.currentScreen == null)
            && mc.getOverlay() == null && mc.isWindowFocused()) {
            if (mc.currentScreen == screen) screen.close();
            else {
                screen.parent(mc.currentScreen);
                mc.setScreen(screen);
            }
        }
        shiftHeld = shift;

        for (Module module : modules.all()) {
            ClientSettings.Entry entry = settings.modules.get(module.id());
            boolean down = entry.key >= 0 && (entry.mouse
                ? GLFW.glfwGetMouseButton(handle, entry.key) == GLFW.GLFW_PRESS
                : InputUtil.isKeyPressed(handle, entry.key));
            if (down && !pressed.contains(module.id()) && mc.currentScreen == null
                && mc.world != null && mc.isWindowFocused()) {
                try {
                    module.toggle();
                    entry.enabled = module.isEnabled();
                    changed();
                } catch (LinkageError error) {
                    LoggerFactory.getLogger("xingclient").warn("Could not toggle module " + module.name(), error);
                }
            }
            if (down) pressed.add(module.id());
            else pressed.remove(module.id());
        }

        if (smoke != null) smoke.tick(mc);
        if (worldSmoke != null) worldSmoke.tick(mc);
        if (maceSmoke != null) maceSmoke.tick(mc);
    }
}
