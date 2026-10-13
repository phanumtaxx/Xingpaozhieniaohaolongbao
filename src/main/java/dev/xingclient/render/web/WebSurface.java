package dev.xingclient.render.web;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.BufferUtils;
import static org.lwjgl.opengl.GL33.*;

/** Uploads CEF's premultiplied BGRA paint buffer and restores the OpenGL state it touches. */
final class WebSurface implements AutoCloseable {
    private final int texture,sceneTexture,horizontalTexture,program,vao,framebuffer,blurFramebuffer;
    private int width,height,sceneWidth,sceneHeight;
    WebSurface() {
        int vertex=compile(GL_VERTEX_SHADER,NativeWebEngine.surfaceShader(0));
        int fragment=0,linked=0;
        try {
            fragment=compile(GL_FRAGMENT_SHADER,NativeWebEngine.surfaceShader(1));
            linked=glCreateProgram();glAttachShader(linked,vertex);glAttachShader(linked,fragment);glLinkProgram(linked);
            if(glGetProgrami(linked,GL_LINK_STATUS)==GL_FALSE) throw new IllegalStateException(glGetProgramInfoLog(linked));
        } catch(RuntimeException error) {if(linked!=0)glDeleteProgram(linked);throw error;}
        finally {glDeleteShader(vertex);if(fragment!=0)glDeleteShader(fragment);}
        program=linked;texture=glGenTextures();sceneTexture=glGenTextures();horizontalTexture=glGenTextures();vao=glGenVertexArrays();framebuffer=glGenFramebuffers();blurFramebuffer=glGenFramebuffers();
    }
    void upload(ByteBuffer pixels,int newWidth,int newHeight) {
        try(State state=new State()) {
            glBindTexture(GL_TEXTURE_2D,texture);glBindBuffer(GL_PIXEL_UNPACK_BUFFER,0);
            glPixelStorei(GL_UNPACK_ALIGNMENT,1);glPixelStorei(GL_UNPACK_ROW_LENGTH,0);
            glPixelStorei(GL_UNPACK_SKIP_ROWS,0);glPixelStorei(GL_UNPACK_SKIP_PIXELS,0);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            if(width!=newWidth||height!=newHeight) {
                width=newWidth;height=newHeight;glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA8,width,height,0,GL_BGRA,GL_UNSIGNED_BYTE,(ByteBuffer)null);
            }
            glTexSubImage2D(GL_TEXTURE_2D,0,0,0,width,height,GL_BGRA,GL_UNSIGNED_BYTE,pixels);
        }
    }
    void draw(Framebuffer destination, double blurRadius, FloatBuffer backdrops, int panelCount) {
        if (width == 0 || height == 0) return;
        if (!(destination.getColorAttachment() instanceof GlTexture color)) {
            throw new IllegalStateException("Web overlay requires OpenGL");
        }
        try (State state = new State()) {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            attach(color.getGlId());
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glDisable(GL_SCISSOR_TEST);
            glColorMask(true, true, true, true);
            glUseProgram(program);
            glBindVertexArray(vao);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture);
            glBindSampler(0, 0);
            glActiveTexture(GL_TEXTURE1);
            glBindSampler(1, 0);
            glUniform1i(glGetUniformLocation(program, "Page"), 0);
            glUniform1i(glGetUniformLocation(program, "Scene"), 1);
            glUniform2f(glGetUniformLocation(program, "Resolution"), destination.textureWidth, destination.textureHeight);
            glUniform1f(glGetUniformLocation(program, "BlurRadius"), (float) blurRadius);

            boolean blur = Double.isFinite(blurRadius) && blurRadius > 0 && panelCount > 0;
            if (blur) prepareGaussianBlur(destination);

            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            glViewport(0, 0, destination.textureWidth, destination.textureHeight);
            glEnable(GL_BLEND);
            glBlendEquationSeparate(GL_FUNC_ADD, GL_FUNC_ADD);
            glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
            if (blur) {
                glBindTexture(GL_TEXTURE_2D, sceneTexture);
                glUniform1i(glGetUniformLocation(program, "Pass"), 1);
                glEnable(GL_SCISSOR_TEST);
                for (int i = 0; i < Math.min(panelCount, 256); i++) {
                    int at = i * 5;
                    float x = backdrops.get(at), y = backdrops.get(at + 1);
                    float w = backdrops.get(at + 2), h = backdrops.get(at + 3);
                    int left = Math.max(0, (int) Math.floor(x) - 1);
                    int right = Math.min(destination.textureWidth, (int) Math.ceil(x + w) + 1);
                    int bottom = Math.max(0, (int) Math.floor(destination.textureHeight - y - h) - 1);
                    int top = Math.min(destination.textureHeight, (int) Math.ceil(destination.textureHeight - y) + 1);
                    if (right <= left || top <= bottom) continue;
                    glScissor(left, bottom, right - left, top - bottom);
                    glUniform4f(glGetUniformLocation(program, "BackdropRect"), x, y, w, h);
                    glUniform1f(glGetUniformLocation(program, "CornerRadius"), backdrops.get(at + 4));
                    glDrawArrays(GL_TRIANGLES, 0, 3);
                }
                glDisable(GL_SCISSOR_TEST);
            }
            glUniform1i(glGetUniformLocation(program, "Pass"), 0);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        }
    }

    private void prepareGaussianBlur(Framebuffer destination) {
        int blurWidth = Math.max(1, (destination.textureWidth + 1) / 2);
        int blurHeight = Math.max(1, (destination.textureHeight + 1) / 2);
        if (sceneWidth != blurWidth || sceneHeight != blurHeight) {
            sceneWidth = blurWidth;
            sceneHeight = blurHeight;
            allocateBlurTexture(sceneTexture);
            allocateBlurTexture(horizontalTexture);
        }
        glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffer);
        glReadBuffer(GL_COLOR_ATTACHMENT0);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, blurFramebuffer);
        attach(sceneTexture);
        glBlitFramebuffer(0, 0, destination.textureWidth, destination.textureHeight,
            0, 0, sceneWidth, sceneHeight, GL_COLOR_BUFFER_BIT, GL_LINEAR);

        glViewport(0, 0, sceneWidth, sceneHeight);
        glDisable(GL_BLEND);
        glUniform1i(glGetUniformLocation(program, "Pass"), 2);
        attach(horizontalTexture);
        glBindTexture(GL_TEXTURE_2D, sceneTexture);
        glUniform2f(glGetUniformLocation(program, "BlurDirection"), 1, 0);
        glDrawArrays(GL_TRIANGLES, 0, 3);

        attach(sceneTexture);
        glBindTexture(GL_TEXTURE_2D, horizontalTexture);
        glUniform2f(glGetUniformLocation(program, "BlurDirection"), 0, 1);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    private void allocateBlurTexture(int target) {
        glBindTexture(GL_TEXTURE_2D, target);
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, sceneWidth, sceneHeight, 0, GL_RGBA, GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    }

    private static void attach(int target) {
        glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, target, 0);
        glDrawBuffer(GL_COLOR_ATTACHMENT0);
        if (glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Web overlay framebuffer is incomplete");
        }
    }

    private static int compile(int kind,String source) {
        int shader=glCreateShader(kind);glShaderSource(shader,source);glCompileShader(shader);
        if(glGetShaderi(shader,GL_COMPILE_STATUS)==GL_FALSE) {String error=glGetShaderInfoLog(shader);glDeleteShader(shader);throw new IllegalStateException(error);}
        return shader;
    }
    @Override public void close() {glDeleteTextures(texture);glDeleteTextures(sceneTexture);glDeleteTextures(horizontalTexture);glDeleteProgram(program);glDeleteVertexArrays(vao);glDeleteFramebuffers(framebuffer);glDeleteFramebuffers(blurFramebuffer);}
    private static final class State implements AutoCloseable {
        private final int program=glGetInteger(GL_CURRENT_PROGRAM),vao=glGetInteger(GL_VERTEX_ARRAY_BINDING);
        private final int readFramebuffer=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        private final int framebuffer=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),activeTexture=glGetInteger(GL_ACTIVE_TEXTURE);
        private final int unpackBuffer=glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING),alignment=glGetInteger(GL_UNPACK_ALIGNMENT);
        private final int rowLength=glGetInteger(GL_UNPACK_ROW_LENGTH),skipRows=glGetInteger(GL_UNPACK_SKIP_ROWS),skipPixels=glGetInteger(GL_UNPACK_SKIP_PIXELS);
        private final int[] viewport=new int[4],scissorBox=new int[4];
        private final ByteBuffer colorMask=BufferUtils.createByteBuffer(4);
        private final boolean depth=glIsEnabled(GL_DEPTH_TEST),cull=glIsEnabled(GL_CULL_FACE),scissor=glIsEnabled(GL_SCISSOR_TEST),blend=glIsEnabled(GL_BLEND);
        private final int srcRgb=glGetInteger(GL_BLEND_SRC_RGB),dstRgb=glGetInteger(GL_BLEND_DST_RGB),srcAlpha=glGetInteger(GL_BLEND_SRC_ALPHA),dstAlpha=glGetInteger(GL_BLEND_DST_ALPHA);
        private final int eqRgb=glGetInteger(GL_BLEND_EQUATION_RGB),eqAlpha=glGetInteger(GL_BLEND_EQUATION_ALPHA);
        private final int texture,sampler,sceneTexture,sceneSampler;
        State() {glGetIntegerv(GL_VIEWPORT,viewport);glGetIntegerv(GL_SCISSOR_BOX,scissorBox);glGetBooleanv(GL_COLOR_WRITEMASK,colorMask);glActiveTexture(GL_TEXTURE0);texture=glGetInteger(GL_TEXTURE_BINDING_2D);sampler=glGetInteger(GL_SAMPLER_BINDING);glActiveTexture(GL_TEXTURE1);sceneTexture=glGetInteger(GL_TEXTURE_BINDING_2D);sceneSampler=glGetInteger(GL_SAMPLER_BINDING);glActiveTexture(GL_TEXTURE0);}
        @Override public void close() {
            glUseProgram(program);glBindVertexArray(vao);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,framebuffer);glBindFramebuffer(GL_READ_FRAMEBUFFER,readFramebuffer);glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            glScissor(scissorBox[0],scissorBox[1],scissorBox[2],scissorBox[3]);
            toggle(GL_DEPTH_TEST,depth);toggle(GL_CULL_FACE,cull);toggle(GL_SCISSOR_TEST,scissor);toggle(GL_BLEND,blend);
            glBlendFuncSeparate(srcRgb,dstRgb,srcAlpha,dstAlpha);glBlendEquationSeparate(eqRgb,eqAlpha);
            glColorMask(colorMask.get(0)!=0,colorMask.get(1)!=0,colorMask.get(2)!=0,colorMask.get(3)!=0);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER,unpackBuffer);glPixelStorei(GL_UNPACK_ALIGNMENT,alignment);glPixelStorei(GL_UNPACK_ROW_LENGTH,rowLength);
            glPixelStorei(GL_UNPACK_SKIP_ROWS,skipRows);glPixelStorei(GL_UNPACK_SKIP_PIXELS,skipPixels);
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture);glBindSampler(0,sampler);glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,sceneTexture);glBindSampler(1,sceneSampler);glActiveTexture(activeTexture);
        }
        private static void toggle(int flag,boolean enabled) {if(enabled)glEnable(flag);else glDisable(flag);}
    }
}
