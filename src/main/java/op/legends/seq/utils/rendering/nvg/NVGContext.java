package op.legends.seq.utils.rendering.nvg;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import op.legends.seq.client.SeqClient;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NanoVGGL3;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL33;

import java.awt.*;
import java.util.function.Consumer;

import static op.legends.seq.client.SeqClient.mc;
import static org.lwjgl.nanovg.NanoVG.*;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.opengl.GL11.*;

public class NVGContext {

    public static long context = -1L;

    public static void init() {
        //context = NanoVGGL3.nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
        //Bind framebuffer to fix Nanovg not rendering

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
        GlStateManager._glBindFramebuffer(GlConst.GL_FRAMEBUFFER,
                ((GlTexture) gpuTexture).getFbo(((GlDevice) RenderSystem.getDevice()).directStateAccess(), gpuTexture2));
        GlStateManager._viewport(0, 0, gpuTexture.getWidth(0), gpuTexture.getHeight(0));
    }

    public static void render(Consumer<Long> drawCall) {
        RenderSystem.assertOnRenderThread();
        float contentscale = (float) mc.getWindow().getGuiScale();
        float width = (int) (mc.getWindow().getWidth() / contentscale);
        float height = (int) (mc.getWindow().getHeight() / contentscale);
        GL33.glBindSampler(0,0);
        bindFrameBuffer();
        GL11.glEnable(GL_BLEND);
        GL11.glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        GL11.glEnable(GL_DEPTH_TEST);

        nvgBeginFrame(context, width, height, contentscale);
        drawCall.accept(context);
        nvgEndFrame(context);
        restoreState2();


    }

    public static void renderText(Consumer<Long> drawCall) {
        float contentscale = (float) mc.getWindow().getGuiScale();
        float width = (int) (mc.getWindow().getWidth() / contentscale);
        float height = (int) (mc.getWindow().getHeight() / contentscale);
        nvgSave(context);
        nvgBeginFrame(context, width, height, contentscale);

        drawCall.accept(context);
        nvgEndFrame(context);

    }

    public static NVGColor nvgColor(Color color) {
        NVGColor nvgColor = NVGColor.calloc();
        nvgColor.r(color.getRed() / 255.0f);
        nvgColor.g(color.getGreen() / 255.0f);
        nvgColor.b(color.getBlue() / 255.0f);
        nvgColor.a(color.getAlpha() / 255.0f);
        return nvgColor;
    }

    private static void setup() {
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);

    }

    private static void restoreState2() {
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);

    }


    public static void restoreState() {
        GlStateManager._disableCull();
        GlStateManager._disableDepthTest();
        GlStateManager._enableBlend();
        GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);


    }

}