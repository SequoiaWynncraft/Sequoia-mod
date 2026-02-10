package org.sequoia.seq.utils.rendering.nvg;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import lombok.Getter;
import org.sequoia.seq.client.SeqClient;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.awt.*;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.LongConsumer;

import static org.sequoia.seq.client.SeqClient.mc;
import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;

public class NVGContext {

    @Getter
    private static long context = -1L;

    private static final Queue<LongConsumer> deferredDrawCalls = new ArrayDeque<>();

    private NVGContext() {
    }

    /**
     * Queues a draw call to be rendered after the HUD, so it appears on top of hotbar/crosshair.
     * Flushed by InGameHudMixin at the tail of Gui.render().
     */
    public static void renderDeferred(LongConsumer drawCall) {
        deferredDrawCalls.add(drawCall);
    }

    /**
     * Executes all deferred draw calls. Called from InGameHudMixin after HUD rendering.
     */
    public static void flushDeferred() {
        LongConsumer drawCall;
        while ((drawCall = deferredDrawCalls.poll()) != null) {
            render(drawCall);
        }
    }

    public static void init() {
        context = NanoVGGL3.nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        if (context == 0 || context == -1L) {
            SeqClient.LOGGER.error("Couldn't initialize NanoVG");
        } else {
            SeqClient.LOGGER.info("NVG LOADED");
        }
    }

    public static void bindFrameBuffer() {
        RenderTarget framebuffer = mc.getMainRenderTarget();
        GpuTexture gpuTexture = framebuffer.getColorTexture();
        GpuTexture gpuTexture2 = framebuffer.getDepthTexture();
        if (gpuTexture == null) {
            return;
        }
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER,
                ((GlTexture) gpuTexture).getFbo(((GlDevice) RenderSystem.getDevice()).directStateAccess(), gpuTexture2));
        GlStateManager._viewport(0, 0, gpuTexture.getWidth(0), gpuTexture.getHeight(0));
    }

    public static void render(LongConsumer drawCall) {
        renderWithScale(drawCall, 2f);
    }

    public static void renderWithScale(LongConsumer drawCall, float fixedScale) {
        RenderSystem.assertOnRenderThread();
        float width = (int) (mc.getWindow().getWidth() / fixedScale);
        float height = (int) (mc.getWindow().getHeight() / fixedScale);

        int prevProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int prevArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int prevElementBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        int prevActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] prevTextureBindings = new int[8];
        for (int i = 0; i < prevTextureBindings.length; i++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            prevTextureBindings[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }

        boolean[] prevAttribEnabled = new boolean[16];
        for (int i = 0; i < prevAttribEnabled.length; i++) {
            prevAttribEnabled[i] = GL20.glGetVertexAttribi(i, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) != 0;
        }

        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int prevCullFaceMode = GL11.glGetInteger(GL11.GL_CULL_FACE_MODE);
        int prevFrontFace = GL11.glGetInteger(GL11.GL_FRONT_FACE);
        int prevBlendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int prevBlendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int prevBlendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
        int prevBlendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        boolean prevScissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean prevStencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        int prevStencilFunc = GL11.glGetInteger(GL11.GL_STENCIL_FUNC);
        int prevStencilRef = GL11.glGetInteger(GL11.GL_STENCIL_REF);
        int prevStencilValueMask = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK);
        int prevStencilFail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL);
        int prevStencilPassDepthFail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL);
        int prevStencilPassDepthPass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS);
        int prevStencilWriteMask = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK);
        int prevFramebuffer = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);

        boolean prevDepthMask;
        boolean prevColorMaskR;
        boolean prevColorMaskG;
        boolean prevColorMaskB;
        boolean prevColorMaskA;
        int prevScissorX;
        int prevScissorY;
        int prevScissorW;
        int prevScissorH;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var depthMaskBuffer = stack.malloc(1);
            GL11.glGetBooleanv(GL11.GL_DEPTH_WRITEMASK, depthMaskBuffer);
            prevDepthMask = depthMaskBuffer.get(0) != 0;

            var colorMaskBuffer = stack.malloc(4);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMaskBuffer);
            prevColorMaskR = colorMaskBuffer.get(0) != 0;
            prevColorMaskG = colorMaskBuffer.get(1) != 0;
            prevColorMaskB = colorMaskBuffer.get(2) != 0;
            prevColorMaskA = colorMaskBuffer.get(3) != 0;

            var scissorBox = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
            prevScissorX = scissorBox.get(0);
            prevScissorY = scissorBox.get(1);
            prevScissorW = scissorBox.get(2);
            prevScissorH = scissorBox.get(3);
        }

        int[] prevSamplerBindings = new int[8];
        for (int i = 0; i < prevSamplerBindings.length; i++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            prevSamplerBindings[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
        }

        GL33.glBindSampler(0,0);
        bindFrameBuffer();

        // Reset GL state to ensure consistent UI rendering (use raw GL to avoid desyncing RenderSystem state cache)
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        // Unbind any active shader program to prevent world shaders from affecting UI
        GL20.glUseProgram(0);

        // Unbind all texture units to prevent world textures from affecting rendering
        for (int i = 0; i < 8; i++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(0);
        }
        GlStateManager._activeTexture(GL13.GL_TEXTURE0);

        // Disable all vertex attribute arrays to prevent world vertex data from bleeding into NanoVG
        // This is crucial because Minecraft may have enabled color attributes with world color data
        for (int i = 0; i < 16; i++) {
            GL20.glDisableVertexAttribArray(i);
        }

        // Unbind VAO and VBO to ensure clean state
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        nvgBeginFrame(context, width, height, fixedScale);
        drawCall.accept(context);
        nvgEndFrame(context);
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER, prevFramebuffer);
        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        if (prevDepthTest) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        if (prevBlend) {
            GL11.glEnable(GL11.GL_BLEND);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        if (prevCull) {
            GL11.glEnable(GL11.GL_CULL_FACE);
        } else {
            GL11.glDisable(GL11.GL_CULL_FACE);
        }
        GL11.glCullFace(prevCullFaceMode);
        GL11.glFrontFace(prevFrontFace);
        GL14.glBlendFuncSeparate(prevBlendSrcRgb, prevBlendDstRgb, prevBlendSrcAlpha, prevBlendDstAlpha);
        GL11.glDepthMask(prevDepthMask);
        GL11.glColorMask(prevColorMaskR, prevColorMaskG, prevColorMaskB, prevColorMaskA);
        if (prevScissorTest) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
        GL11.glScissor(prevScissorX, prevScissorY, prevScissorW, prevScissorH);
        if (prevStencilTest) {
            GL11.glEnable(GL11.GL_STENCIL_TEST);
        } else {
            GL11.glDisable(GL11.GL_STENCIL_TEST);
        }
        GL11.glStencilFunc(prevStencilFunc, prevStencilRef, prevStencilValueMask);
        GL11.glStencilOp(prevStencilFail, prevStencilPassDepthFail, prevStencilPassDepthPass);
        GL11.glStencilMask(prevStencilWriteMask);

        GL30.glBindVertexArray(prevVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevArrayBuffer);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevElementBuffer);

        for (int i = 0; i < prevAttribEnabled.length; i++) {
            if (prevAttribEnabled[i]) {
                GL20.glEnableVertexAttribArray(i);
            } else {
                GL20.glDisableVertexAttribArray(i);
            }
        }

        for (int i = 0; i < prevSamplerBindings.length; i++) {
            GL33.glBindSampler(i, prevSamplerBindings[i]);
        }

        for (int i = 0; i < prevTextureBindings.length; i++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(prevTextureBindings[i]);
        }
        GlStateManager._activeTexture(prevActiveTexture);
        GL20.glUseProgram(prevProgram);


    }

    public static void renderText(LongConsumer drawCall) {
        float contentscale = mc.getWindow().getGuiScale();
        float width = (int) (mc.getWindow().getWidth() / contentscale);
        float height = (int) (mc.getWindow().getHeight() / contentscale);
        nvgSave(context);
        nvgBeginFrame(context, width, height, contentscale);

        drawCall.accept(context);
        nvgEndFrame(context);

    }

    public static NVGColor nvgColor(Color color) {
        NVGColor colorBuffer = NVGColor.calloc();
        colorBuffer.r(color.getRed() / 255.0f);
        colorBuffer.g(color.getGreen() / 255.0f);
        colorBuffer.b(color.getBlue() / 255.0f);
        colorBuffer.a(color.getAlpha() / 255.0f);
        return colorBuffer;
    }

    public static void restoreState() {
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);

    }

}
