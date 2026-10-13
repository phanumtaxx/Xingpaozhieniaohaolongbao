package dev.xingclient.render.hud;

import dev.xingclient.XingClient;
import dev.xingclient.render.web.NativeWebEngine;
import dev.xingclient.render.web.WebOverlay;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

/** Routes screen input to the HTML editor; gameplay receives no editor input. Author: uint32. */
public final class HudEditorScreen extends Screen {
    private final Screen parent;
    private HudEditorSession session;
    private WebOverlay.Registration backdrop;
    private int heldButton = -1;

    public HudEditorScreen(Screen parent) {
        super(Text.literal("HUD editor"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (session == null) session = XingClient.INSTANCE.hud.beginEdit();
        if (backdrop == null) backdrop = XingClient.INSTANCE.webOverlay.requestBackgroundBlur(() -> XingClient.INSTANCE.settings.hudEditor.blur);
        NativeWebEngine.editorFocus(true);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    public void renderBackground(DrawContext context, int x, int y, float delta) {}

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (client.world == null) context.fill(0, 0, width, height, 0xff101116);
        var app = XingClient.INSTANCE;
        try {
            app.webOverlay.render(context, app.settings.webOverlay.fps);
        } catch (RuntimeException | LinkageError error) {
            close();
            app.webModule.failed(error);
        }
    }

    @Override
    public void tick() {
        for (String command : NativeWebEngine.drainCommands()) {
            try {
                if (session.handle(command)) { client.setScreen(parent); return; }
            } catch (RuntimeException | LinkageError error) {
                LoggerFactory.getLogger("xingclient-hud").warn("Could not apply HUD edit", error);
            }
        }
    }

    private int modifiers() {
        long window = client.getWindow().getHandle();
        int result = 0;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS) result |= 2;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS) result |= 4;
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS) result |= 8;
        if (heldButton >= 0) result |= heldButton == 0 ? 16 : heldButton == 1 ? 64 : 32;
        return result;
    }

    private void pointer(int kind, double x, double y, int button, int wheel) {
        double scale = client.getWindow().getScaleFactor();
        NativeWebEngine.pointer(kind, (int) Math.round(x * scale), (int) Math.round(y * scale), button, modifiers(), wheel);
    }

    @Override
    public void mouseMoved(double x, double y) { pointer(0, x, y, Math.max(0, heldButton), 0); }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button > 2) return false;
        heldButton = button;
        pointer(1, x, y, button, 0);
        return true;
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        pointer(0, x, y, button, 0);
        return true;
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button > 2) return false;
        heldButton = -1;
        pointer(2, x, y, button, 0);
        return true;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        pointer(3, x, y, 0, (int) Math.round(vertical * 80));
        return true;
    }

    private static int virtualKey(int key) {
        if (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z || key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) return key;
        return switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> 8;
            case GLFW.GLFW_KEY_TAB -> 9;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> 13;
            case GLFW.GLFW_KEY_SPACE -> 32;
            case GLFW.GLFW_KEY_END -> 35;
            case GLFW.GLFW_KEY_HOME -> 36;
            case GLFW.GLFW_KEY_LEFT -> 37;
            case GLFW.GLFW_KEY_UP -> 38;
            case GLFW.GLFW_KEY_RIGHT -> 39;
            case GLFW.GLFW_KEY_DOWN -> 40;
            case GLFW.GLFW_KEY_DELETE -> 46;
            default -> 0;
        };
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_ESCAPE) { close(); return true; }
        int code = virtualKey(key);
        if (code != 0) NativeWebEngine.key(0, code, modifiers());
        return true;
    }

    @Override
    public boolean keyReleased(int key, int scan, int modifiers) {
        int code = virtualKey(key);
        if (code != 0) NativeWebEngine.key(1, code, modifiers());
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        NativeWebEngine.key(2, character, modifiers());
        return true;
    }

    @Override
    public void close() {
        if (session != null) session.finish(false);
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        if (session != null) session.finish(false);
        NativeWebEngine.editorFocus(false);
        if (backdrop != null) {
            backdrop.close();
            backdrop = null;
        }
        heldButton = -1;
    }
}
