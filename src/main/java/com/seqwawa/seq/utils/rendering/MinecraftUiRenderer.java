package com.seqwawa.seq.utils.rendering;

import static com.seqwawa.seq.client.SeqClient.mc;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.nvg.NanoVgBackend;

/** Minecraft integration for renderer setup, frame flushing, and input coordinate conversion. */
public final class MinecraftUiRenderer {
    private MinecraftUiRenderer() {
    }

    public static void initialize() {
        UiRenderer.initialize(new NanoVgBackend());
    }

    public static void shutdown() {
        UiRenderer.shutdown();
    }

    public static void flush() {
        UiRenderer.flush(mc.screen, metrics());
    }

    public static UiRenderMetrics metrics() {
        return new UiRenderMetrics(
                mc.getWindow().getWidth(),
                mc.getWindow().getHeight(),
                mc.getWindow().getGuiScale(),
                uiScale());
    }

    /** A UI size the development preview shows the screens at, without touching the saved setting. Null otherwise. */
    public static volatile Float previewUiScale;

    public static float uiScale() {
        Float preview = previewUiScale;
        if (preview != null) return UiRenderMetrics.clampUiScale(preview);
        var setting = SeqClient.getUiSizePercentSetting();
        return UiRenderMetrics.clampUiScale(setting == null ? 1f : setting.getValue() / 100f);
    }

    public static float screenWidth() {
        return metrics().width();
    }

    public static float screenHeight() {
        return metrics().height();
    }

    /**
     * Where the development preview holds the pointer, in UI pixels, so a screenshot shows the
     * same hover states wherever the real cursor is. Null otherwise.
     */
    public static volatile PreviewPointer previewPointer;

    /** A pointer position in UI pixels. */
    public record PreviewPointer(float x, float y) {}

    public static float mouseX(double rawX) {
        PreviewPointer pinned = previewPointer;
        return pinned != null ? pinned.x() : metrics().mouseX(rawX);
    }

    public static float mouseY(double rawY) {
        PreviewPointer pinned = previewPointer;
        return pinned != null ? pinned.y() : metrics().mouseY(rawY);
    }

    public static double mouseDelta(double rawDelta) {
        return metrics().mouseDelta(rawDelta);
    }
}
