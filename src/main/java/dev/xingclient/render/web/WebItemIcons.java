package dev.xingclient.render.web;

import com.mojang.blaze3d.systems.RenderSystem;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.component.ComponentMap;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.BufferUtils;
import org.slf4j.LoggerFactory;
import static org.lwjgl.opengl.GL33.*;

/** Captures real inventory models once and exposes cached PNGs to local HTML. */
final class WebItemIcons implements AutoCloseable {
    private static final int CACHE_LIMIT = 512;
    private record Key(Item item, ComponentMap components, int count, int size) {}
    private static final class Icon {
        final ItemStack stack;
        final Path file;
        final String url;
        boolean captured;
        volatile boolean ready;
        Icon(ItemStack stack, Path file) {
            this.stack = stack.copy(); this.file = file; this.url = file.toUri().toASCIIString();
        }
    }
    private final Map<Key, Icon> cache = new LinkedHashMap<>(64, .75f, true);
    private final Path directory = WebOverlay.assetDirectory().resolve("cache/item-icons/" + UUID.randomUUID());
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "xing-item-icons"); thread.setDaemon(true); return thread;
    });
    private SimpleFramebuffer target;
    private boolean routing;
    private int nextId;
    Framebuffer routedTarget() { return routing ? target : null; }
    String reference(ItemStack stack) {
        if (stack.isEmpty()) return null;
        int size = (int)Math.round(16 * MinecraftClient.getInstance().getWindow().getScaleFactor());
        Key key = new Key(stack.getItem(), stack.getImmutableComponents(), stack.getCount(), size);
        Icon icon = cache.get(key);
        if (icon == null) {
            icon = new Icon(stack, directory.resolve(++nextId + ".png"));
            cache.put(key, icon);
            if (cache.size() > CACHE_LIMIT) cache.remove(cache.keySet().iterator().next());
        }
        return icon.ready ? icon.url : null;
    }
    void prepare(DrawContext original) {
        if (cache.values().stream().noneMatch(icon -> !icon.captured)) return;
        var client = MinecraftClient.getInstance();
        int width = client.getFramebuffer().textureWidth, height = client.getFramebuffer().textureHeight;
        if (target == null) target = new SimpleFramebuffer("Xing item icons", width, height, true);
        else if (target.textureWidth != width || target.textureHeight != height) target.resize(width, height);
        original.draw();
        DrawContext icons = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
        int rendered = 0;
        for (var entry : cache.entrySet()) {
            Icon icon = entry.getValue();
            if (icon.captured) continue;
            BufferedImage image;
            try (ReadbackState state = new ReadbackState()) {
                RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    target.getColorAttachment(), 0, target.getDepthAttachment(), 1.0);
                routing = true;
                try { icons.drawItemWithoutEntity(icon.stack, 0, 0); icons.draw(); }
                finally { routing = false; }
                image = readImage(entry.getKey().size);
            }
            icon.captured = true;
            writer.execute(() -> {
                try {
                    Files.createDirectories(directory);
                    if (!ImageIO.write(image, "PNG", icon.file.toFile())) throw new IOException("PNG encoder unavailable");
                    icon.ready = true;
                } catch (IOException error) {
                    LoggerFactory.getLogger("xingclient-web").warn("Cannot write item icon", error);
                }
            });
            if (++rendered >= 2) break;
        }
    }
    private BufferedImage readImage(int size) {
        if (!(target.getColorAttachment() instanceof GlTexture color)) throw new IllegalStateException("Item capture requires OpenGL");
        int framebuffer = glGenFramebuffers();
        ByteBuffer pixels = BufferUtils.createByteBuffer(size * size * 4);
        try {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color.getGlId(), 0);
            glReadBuffer(GL_COLOR_ATTACHMENT0);
            if(glCheckFramebufferStatus(GL_READ_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Item icon framebuffer is incomplete");
            glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
            glPixelStorei(GL_PACK_ALIGNMENT, 1); glPixelStorei(GL_PACK_ROW_LENGTH, 0);
            glPixelStorei(GL_PACK_SKIP_ROWS, 0); glPixelStorei(GL_PACK_SKIP_PIXELS, 0);
            glReadPixels(0, target.textureHeight - size, size, size, GL_RGBA, GL_UNSIGNED_BYTE, pixels);
        } finally { glDeleteFramebuffers(framebuffer); }
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int index = ((size - 1 - y) * size + x) * 4;
            int alpha = pixels.get(index + 3) & 255;
            int red = straight(pixels.get(index) & 255, alpha);
            int green = straight(pixels.get(index + 1) & 255, alpha);
            int blue = straight(pixels.get(index + 2) & 255, alpha);
            image.setRGB(x, y, alpha << 24 | red << 16 | green << 8 | blue);
        }
        return image;
    }
    private static int straight(int channel, int alpha) { return alpha == 0 ? 0 : Math.min(255, (channel * 255 + alpha / 2) / alpha); }
    @Override public void close() {
        routing = false; writer.shutdown(); cache.clear();
        if (target != null) { target.delete(); target = null; }
    }
    private static final class ReadbackState implements AutoCloseable {
        private final int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING), draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        private final int packBuffer = glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING), alignment = glGetInteger(GL_PACK_ALIGNMENT);
        private final int rowLength = glGetInteger(GL_PACK_ROW_LENGTH), rows = glGetInteger(GL_PACK_SKIP_ROWS), pixels = glGetInteger(GL_PACK_SKIP_PIXELS);
        private final int[] viewport = new int[4];
        ReadbackState() { glGetIntegerv(GL_VIEWPORT, viewport); }
        @Override public void close() {
            glBindFramebuffer(GL_READ_FRAMEBUFFER, read); glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]); glBindBuffer(GL_PIXEL_PACK_BUFFER, packBuffer);
            glPixelStorei(GL_PACK_ALIGNMENT, alignment); glPixelStorei(GL_PACK_ROW_LENGTH, rowLength);
            glPixelStorei(GL_PACK_SKIP_ROWS, rows); glPixelStorei(GL_PACK_SKIP_PIXELS, pixels);
        }
    }
}
