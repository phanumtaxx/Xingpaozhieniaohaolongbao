package dev.xingclient.nativebridge;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;

/** Direct-buffer JNI access to Xing's native modules and managers. */
public final class XingNativeBridge {
    public static final int AUTOCRYSTAL_POLICY = 0;
    public static final int AUTOCRYSTAL_BREAK_POLICY = 1;
    public static final int AUTOCRYSTAL_PLACE_LIFECYCLE = 2;
    public static final int AUTOCRYSTAL_BREAK_LIFECYCLE = 3;
    public static final int AUTOCRYSTAL_DIRECT_PREPLACE_LIFECYCLE = 4;
    public static final int AUTOCRYSTAL_EXECUTION_POLICY = 5;
    public static final int AUTOCRYSTAL_CYCLE = 6;
    public static final int WORLD_STATE = 9;
    public static final int AUTO_REFILL = 10;
    public static final int CYCLE_EVENT_PLAN = 0;
    public static final int CYCLE_OUTPUT_HEADER_BYTES = 48;
    public static final int CYCLE_OUTPUT_PLACEMENT_BYTES = 48;
    private static final ThreadLocal<ByteBuffer> CYCLE_INPUT_BUFFER = new ThreadLocal<>();
    private static final ThreadLocal<ByteBuffer> CYCLE_OUTPUT_BUFFER = new ThreadLocal<>();

    private static boolean libraryLoaded;

    public record PauseResult(int elapsedMillis, boolean cancelled) {}

    private XingNativeBridge() { }

    public static ByteBuffer allocate(int capacity) {
        if (capacity < 0) throw new IllegalArgumentException("capacity must not be negative");
        return ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
    }

    public static ByteBuffer cycleInputBuffer(int requiredCapacity) {
        return reusableBuffer(CYCLE_INPUT_BUFFER, requiredCapacity);
    }

    public static ByteBuffer cycleOutputBuffer(int requiredCapacity) {
        return reusableBuffer(CYCLE_OUTPUT_BUFFER, requiredCapacity);
    }

    private static ByteBuffer reusableBuffer(ThreadLocal<ByteBuffer> local, int requiredCapacity) {
        if (requiredCapacity < 0) throw new IllegalArgumentException("capacity must not be negative");
        ByteBuffer buffer = local.get();
        if (buffer == null || buffer.capacity() < requiredCapacity) {
            int capacity = (int) Math.min(Integer.MAX_VALUE,
                    Math.max((long) requiredCapacity, buffer == null ? requiredCapacity : buffer.capacity() * 2L));
            buffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
            local.set(buffer);
        }
        buffer.clear();
        buffer.limit(requiredCapacity);
        return buffer.slice().order(ByteOrder.LITTLE_ENDIAN);
    }

    public static int dispatch(int policy, int event, ByteBuffer input, ByteBuffer output) {
        requireDirect(input, "input");
        requireDirect(output, "output");
        input.order(ByteOrder.LITTLE_ENDIAN);
        output.order(ByteOrder.LITTLE_ENDIAN);
        loadLibrary();
        return nativeDispatch(policy, event, input, output);
    }

    public static int planCycle(ByteBuffer input, ByteBuffer output) {
        return dispatch(AUTOCRYSTAL_CYCLE, CYCLE_EVENT_PLAN, input, output);
    }

    public static int planBreak(ByteBuffer input, ByteBuffer output) {
        return dispatch(AUTOCRYSTAL_CYCLE, 1, input, output);
    }

    public static int planPlacement(ByteBuffer input, ByteBuffer output) {
        return dispatch(AUTOCRYSTAL_CYCLE, 2, input, output);
    }

    public static int crashout(int event, ByteBuffer input, ByteBuffer output, dev.xingclient.manager.FlightGameAccess game) {
        requireDirect(input, "input");
        requireDirect(output, "output");
        loadLibrary();
        return nativeCrashout(event, input, output, game);
    }

    public static int velocity(int event, ByteBuffer input, ByteBuffer output, dev.xingclient.manager.MovementWorldAccess world) {
        requireDirect(input, "input");
        requireDirect(output, "output");
        loadLibrary();
        return nativeVelocity(event, input, output, world);
    }

    public static void prepareGameThreadPause() {
        loadLibrary();
        if (nativePauseVersion() != 2)
            throw new IllegalStateException("Native menu pause requires the updated DLL");
    }

    public static PauseResult pauseGameThread(long windowHandle, int durationMillis) {
        if (!net.minecraft.client.MinecraftClient.getInstance().isOnThread())
            throw new IllegalStateException("Game pause must run on the client thread");
        if (durationMillis < 100 || durationMillis > 4000)
            throw new IllegalArgumentException("Pause duration must be between 100 and 4000 ms");
        loadLibrary();
        long result = nativePauseGameThread(windowHandle, durationMillis);
        if (result < 0) throw new IllegalStateException("Native menu pause requires a focused window with a title bar");
        return new PauseResult((int) (result >>> 1), (result & 1) != 0);
    }

    private static void requireDirect(ByteBuffer buffer, String name) {
        if (buffer == null || !buffer.isDirect()) {
            throw new IllegalArgumentException(name + " must be a direct ByteBuffer");
        }
    }

    public static synchronized void loadLibrary() {
        if (libraryLoaded) return;
        String configuredPath = System.getProperty("xing.native.path");
        Path library = configuredPath == null || configuredPath.isBlank()
                ? findLibrary() : Path.of(configuredPath).toAbsolutePath();
        if (library == null || !Files.isRegularFile(library)) {
            throw new UnsatisfiedLinkError("Could not locate native/xing/x64/{Debug,Release}/xing.dll. Set -Dxing.native.path to the DLL path.");
        }
        System.load(library.toString());
        libraryLoaded = true;
    }

    private static Path findLibrary() {
        Set<Path> roots = new LinkedHashSet<>();
        addAncestors(roots, FabricLoader.getInstance().getGameDir());
        addAncestors(roots, Path.of(System.getProperty("user.dir", ".")));
        for (Path root : roots) {
            for (String configuration : new String[] {"Release", "Debug"}) {
                Path candidate = root.resolve(Path.of("native", "xing", "x64", configuration, "xing.dll"));
                if (Files.isRegularFile(candidate)) return candidate.toAbsolutePath();
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

    private static native int nativeDispatch(int policy, int event, ByteBuffer input, ByteBuffer output);
    private static native int nativeCrashout(int event, ByteBuffer input, ByteBuffer output, dev.xingclient.manager.FlightGameAccess game);
    private static native int nativeVelocity(int event, ByteBuffer input, ByteBuffer output, dev.xingclient.manager.MovementWorldAccess world);
    private static native long nativePauseGameThread(long windowHandle, int durationMillis);
    private static native int nativePauseVersion();
}
