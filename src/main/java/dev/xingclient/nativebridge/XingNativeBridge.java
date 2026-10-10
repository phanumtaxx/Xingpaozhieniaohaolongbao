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
    public static final int CYCLE_EVENT_PLAN = 0;
    public static final int CYCLE_OUTPUT_HEADER_BYTES = 48;
    public static final int CYCLE_OUTPUT_PLACEMENT_BYTES = 48;
    private static final ThreadLocal<ByteBuffer> CYCLE_INPUT_BUFFER = new ThreadLocal<>();
    private static final ThreadLocal<ByteBuffer> CYCLE_OUTPUT_BUFFER = new ThreadLocal<>();

    private static boolean libraryLoaded;

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

    private static void requireDirect(ByteBuffer buffer, String name) {
        if (buffer == null || !buffer.isDirect()) {
            throw new IllegalArgumentException(name + " must be a direct ByteBuffer");
        }
    }

    private static synchronized void loadLibrary() {
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
}
