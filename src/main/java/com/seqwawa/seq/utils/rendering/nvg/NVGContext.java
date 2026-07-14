package com.seqwawa.seq.utils.rendering.nvg;

import static com.seqwawa.seq.client.SeqClient.mc;

import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.function.LongConsumer;
import org.lwjgl.nanovg.NVGColor;

/**
 * Compatibility facade for UI code that has not yet migrated from native NanoVG calls.
 * New rendering code should submit backend-neutral commands through {@link UiRenderer}.
 */
public final class NVGContext {
    private NVGContext() {
    }

    public static void renderDeferred(LongConsumer drawCall) {
        UiRenderer.renderScreen(mc.screen, canvas -> drawCall.accept(nativeContext(canvas)));
    }

    public static float screenWidth() {
        return MinecraftUiRenderer.screenWidth();
    }

    public static float screenHeight() {
        return MinecraftUiRenderer.screenHeight();
    }

    public static float mouseX(double rawX) {
        return MinecraftUiRenderer.mouseX(rawX);
    }

    public static float mouseY(double rawY) {
        return MinecraftUiRenderer.mouseY(rawY);
    }

    public static NVGColor nvgColor(Color color) {
        return NVGWrapper.nvgColor(color);
    }

    private static long nativeContext(UiCanvas canvas) {
        if (canvas instanceof NanoVgCanvas nanoVgCanvas) {
            return nanoVgCanvas.context();
        }
        throw new IllegalStateException("Native NanoVG drawing requires the NanoVG backend");
    }

}
