package dev.xingclient.module;

import dev.xingclient.event.ClientTickEvent;
import dev.xingclient.event.EventBus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/** Java lifecycle and event adapter for the native tick counter example. */
public final class NativeExampleModule extends Module {
    private static boolean libraryLoaded;

    private final EventBus events;
    private EventBus.Subscription tickSubscription;

    public NativeExampleModule(EventBus events) {
        super("native_tick_counter", "Native Tick Counter", ModuleCategory.CORE);
        this.events = events;
    }

    @Override
    protected void onEnable() {
        nativeSetEnabled(true);
        tickSubscription = events.subscribe(ClientTickEvent.class, this::onClientTick);
    }

    @Override
    protected void onDisable() {
        nativeSetEnabled(false);
        if (tickSubscription != null) {
            tickSubscription.close();
            tickSubscription = null;
        }
    }

    public long tickCount() {
        return nativeTickCount();
    }

    @Override
    public String status() {
        return isEnabled() ? "Client ticks: " + tickCount() : "Disabled";
    }

    private void onClientTick(ClientTickEvent event) {
        if (event.phase() == ClientTickEvent.Phase.END) {
            nativeOnTick();
        }
    }

    private static synchronized void loadLibrary() {
        if (libraryLoaded) {
            return;
        }

        String configuredPath = System.getProperty("xing.native.path");
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path library = Path.of(configuredPath).toAbsolutePath();
            if (!Files.isRegularFile(library)) {
                throw new UnsatisfiedLinkError("Configured native library does not exist: " + library);
            }
            System.load(library.toString());
        } else {
            Path library = findLibrary();
            if (library == null) {
                throw new UnsatisfiedLinkError("Could not find native/xing/x64/{Debug,Release}/xing.dll from the game directory. Set -Dxing.native.path to the DLL path.");
            }
            System.load(library.toString());
        }
        libraryLoaded = true;
    }

    private static Path findLibrary() {
        Set<Path> roots = new LinkedHashSet<>();
        addAncestors(roots, FabricLoader.getInstance().getGameDir());
        addAncestors(roots, Path.of(System.getProperty("user.dir", ".")));
        for (Path root : roots) {
            for (String configuration : new String[]{"Debug", "Release"}) {
                Path candidate = root.resolve(Path.of("native", "xing", "x64", configuration, "xing.dll"));
                if (Files.isRegularFile(candidate)) {
                    return candidate.toAbsolutePath();
                }
            }
        }
        return null;
    }

    private static void addAncestors(Set<Path> roots, Path start) {
        Path current = start.toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 6; depth++, current = current.getParent()) {
            roots.add(current);
        }
    }

    private static void nativeSetEnabled(boolean enabled) {
        loadLibrary();
        setNativeEnabled(enabled);
    }

    private static void nativeOnTick() {
        loadLibrary();
        recordNativeTick();
    }

    private static long nativeTickCount() {
        loadLibrary();
        return getNativeTickCount();
    }

    private static native void setNativeEnabled(boolean enabled);

    private static native void recordNativeTick();

    private static native long getNativeTickCount();
}
