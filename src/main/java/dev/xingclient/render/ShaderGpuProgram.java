package dev.xingclient.render;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import static org.lwjgl.opengl.GL33.*;

/** OpenGL adapter for native shader sources. Every draw restores the state it changes. */
public final class ShaderGpuProgram implements AutoCloseable {
    private final Map<String, Integer> uniforms = new HashMap<>();
    private final int program;
    private final int vertexArray;
    private final int framebuffer;

    public ShaderGpuProgram(int fragmentStage) {
        int vertex = compile(GL_VERTEX_SHADER, NativeShaderEffects.source(0));
        int fragment = 0, linked = 0;
        try {
            fragment = compile(GL_FRAGMENT_SHADER, NativeShaderEffects.source(fragmentStage));
            linked = glCreateProgram();
            glAttachShader(linked, vertex); glAttachShader(linked, fragment); glLinkProgram(linked);
            if (glGetProgrami(linked, GL_LINK_STATUS) == GL_FALSE)
                throw new IllegalStateException("Shader link failed: " + glGetProgramInfoLog(linked));
        } catch (RuntimeException error) {
            if (linked != 0) glDeleteProgram(linked);
            throw error;
        } finally {
            glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
        }
        program = linked;
        vertexArray = glGenVertexArrays();
        framebuffer = glGenFramebuffers();
    }

    private static int compile(int type, String source) {
        if (source == null) throw new IllegalStateException("Native shader source is unavailable");
        int shader = glCreateShader(type);
        glShaderSource(shader, source); glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader); glDeleteShader(shader);
            throw new IllegalStateException("Shader compilation failed: " + log);
        }
        return shader;
    }

    public void draw(Framebuffer destination, Framebuffer mask, Framebuffer glow, ShaderSettings s, float time, float[] camera, boolean horizontal) {
        if (!(destination.getColorAttachment() instanceof GlTexture color))
            throw new IllegalStateException("Xing shaders require Minecraft's OpenGL backend");
        try (State state = new State()) {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color.getGlId(), 0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
                throw new IllegalStateException("Shader output framebuffer is incomplete");
            glViewport(0, 0, destination.textureWidth, destination.textureHeight);
            glDisable(GL_DEPTH_TEST); glDisable(GL_CULL_FACE); glDisable(GL_SCISSOR_TEST);
            glColorMask(true, true, true, true);
            if (camera == null) glDisable(GL_BLEND); else glEnable(GL_BLEND); glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
            int destinationBlend = camera == null || mask == null ? GL_ONE_MINUS_SRC_ALPHA : switch(s.blend) {
                case ALPHA -> GL_ONE_MINUS_SRC_ALPHA;
                case ADDITIVE -> GL_ONE;
                case SCREEN -> GL_ONE_MINUS_SRC_COLOR;
            };
            glBlendFuncSeparate(GL_SRC_ALPHA, destinationBlend, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            glUseProgram(program); glBindVertexArray(vertexArray);
            glActiveTexture(GL_TEXTURE0); glBindSampler(0, 0);
            glBindTexture(GL_TEXTURE_2D, mask == null ? 0 : ((GlTexture) mask.getColorAttachment()).getGlId());
            glActiveTexture(GL_TEXTURE1); glBindSampler(1, 0);
            glBindTexture(GL_TEXTURE_2D, glow == null ? 0 : ((GlTexture)glow.getColorAttachment()).getGlId());
            integer("Glow", 1);
            vector2("BlurDirection", horizontal ? 1f / destination.textureWidth : 0, horizontal ? 0 : 1f / destination.textureHeight);
            integer("Mask", 0); vector2("Resolution", destination.textureWidth, destination.textureHeight);
            scalar("Time", time); scalar("Speed", s.speed); scalar("Scale", s.scale); scalar("Warp", s.warp);
            scalar("Density", s.density); scalar("Brightness", s.brightness); scalar("Saturation", s.saturation);
            scalar("Pulse", s.pulse);
            scalar("FillOpacity", s.fillOpacity); scalar("OutlineOpacity", s.outlineOpacity);
            scalar("OutlineWidth", s.outlineWidth); scalar("GlowStrength", s.glowStrength); scalar("GlowRadius", s.glowRadius);
            integer("Material", ShaderSettings.materialId(s.material)); integer("CrystalMaterial", ShaderSettings.materialId(s.crystalMaterial));
            integer("HandMaterial", ShaderSettings.materialId(s.handMaterial)); integer("Quality", s.quality);
            glUniform3f(location("CategoryStrength"), (float)s.playerStrength, (float)s.crystalStrength, (float)s.handStrength);
            color("Primary", s.primary); color("Secondary", s.secondary); color("Accent", s.accent);
            uploadStyles(s);
            scalar("EdgePulse",s.edgePulse); scalar("GlowFalloff",s.glowFalloff); scalar("StarSpeed",s.starSpeed);
            integer("AnimatedOutline",s.animatedOutline?1:0);
            integer("SkyMode", ShaderSettings.skyId(s.skyMode)); scalar("SkyIntensity", s.skyIntensity); scalar("StarDensity", s.starDensity);
            integer("CompositePass", 0);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            if (camera != null && mask != null && s.hands && s.fillOpacity >= .999
                    && s.handStyle.fill >= .999 && s.handStrength > 0) {
                glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
                integer("CompositePass", 1);
                glDrawArrays(GL_TRIANGLES, 0, 3);
            }
        }
    }

    private void uploadStyles(ShaderSettings settings) {
        var styles=settings.styles();
        for(int i=0;i<styles.length;i++) {
            var style=styles[i]; String index="["+i+"]";
            glUniform4f(location("Animation"+index),(float)style.speed,(float)style.scale,(float)style.warp,(float)Math.toRadians(style.angle));
            glUniform4f(location("Motion"+index),(float)style.scrollX,(float)style.scrollY,(float)Math.toRadians(style.hueShift),(float)style.glow);
            scalar("TargetFill"+index,style.fill);
            color("TargetPrimary"+index,style.customColors?style.primary:settings.primary);
            color("TargetSecondary"+index,style.customColors?style.secondary:settings.secondary);
            color("TargetAccent"+index,style.customColors?style.accent:settings.accent);
        }
    }

    private int location(String name) { return uniforms.computeIfAbsent(name, n -> glGetUniformLocation(program, n)); }
    private void scalar(String name, double value) { glUniform1f(location(name), (float)value); }
    private void integer(String name, int value) { glUniform1i(location(name), value); }
    private void vector2(String name, float x, float y) { glUniform2f(location(name), x, y); }
    private void color(String name, int rgb) {
        glUniform3f(location(name), (rgb >> 16 & 255) / 255f, (rgb >> 8 & 255) / 255f, (rgb & 255) / 255f);
    }

    @Override public void close() {
        glDeleteProgram(program); glDeleteVertexArrays(vertexArray); glDeleteFramebuffers(framebuffer);
    }

    private static final class State implements AutoCloseable {
        private final int program = glGetInteger(GL_CURRENT_PROGRAM), vao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        private final int framebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING), activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        private final int[] viewport = new int[4];
        private final boolean depth = glIsEnabled(GL_DEPTH_TEST), cull = glIsEnabled(GL_CULL_FACE);
        private final boolean scissor = glIsEnabled(GL_SCISSOR_TEST), blend = glIsEnabled(GL_BLEND);
        private final int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB);
        private final int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        private final int eqRgb = glGetInteger(GL_BLEND_EQUATION_RGB), eqAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        private final java.nio.ByteBuffer colorMask = org.lwjgl.BufferUtils.createByteBuffer(4);
        private final int texture, sampler, glowTexture, glowSampler;
        private State() {
            glGetIntegerv(GL_VIEWPORT, viewport); glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
            glActiveTexture(GL_TEXTURE0); texture = glGetInteger(GL_TEXTURE_BINDING_2D); sampler = glGetInteger(GL_SAMPLER_BINDING);
            glActiveTexture(GL_TEXTURE1); glowTexture = glGetInteger(GL_TEXTURE_BINDING_2D); glowSampler = glGetInteger(GL_SAMPLER_BINDING);
        }
        @Override public void close() {
            glUseProgram(program); glBindVertexArray(vao); glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            toggle(GL_DEPTH_TEST, depth); toggle(GL_CULL_FACE, cull); toggle(GL_SCISSOR_TEST, scissor); toggle(GL_BLEND, blend);
            glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha); glBlendEquationSeparate(eqRgb, eqAlpha);
            glColorMask(colorMask.get(0)!=0, colorMask.get(1)!=0, colorMask.get(2)!=0, colorMask.get(3)!=0);
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture); glBindSampler(0, sampler);
            glActiveTexture(GL_TEXTURE1); glBindTexture(GL_TEXTURE_2D, glowTexture); glBindSampler(1, glowSampler);
            glActiveTexture(activeTexture);
        }
        private static void toggle(int flag, boolean enabled) { if (enabled) glEnable(flag); else glDisable(flag); }
    }
}
