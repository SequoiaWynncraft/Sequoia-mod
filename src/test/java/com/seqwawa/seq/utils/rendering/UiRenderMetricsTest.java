package com.seqwawa.seq.utils.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class UiRenderMetricsTest {
    @Test
    void convertsFramebufferAndMouseCoordinatesIntoUiSpace() {
        UiRenderMetrics metrics = new UiRenderMetrics(1920, 1080, 2.0, 1.5f);

        assertEquals(3f, metrics.pixelRatio());
        assertEquals(640f, metrics.width());
        assertEquals(360f, metrics.height());
        assertEquals(200f, metrics.mouseX(300));
        assertEquals(100f, metrics.mouseY(150));
        assertEquals(8.0, metrics.mouseDelta(12));
    }

    @Test
    void sanitizesInvalidDimensionsAndScales() {
        UiRenderMetrics metrics = new UiRenderMetrics(0, -1, Double.NaN, Float.NaN);

        assertEquals(1, metrics.framebufferWidth());
        assertEquals(1, metrics.framebufferHeight());
        assertEquals(1.0, metrics.minecraftGuiScale());
        assertEquals(1f, metrics.uiScale());
        assertEquals(0.5f, metrics.width());
        assertEquals(0.5f, metrics.height());
    }

    @Test
    void clampsExtremeManualUiScalesToOperationalLimits() {
        assertEquals(UiRenderMetrics.MIN_UI_SCALE, UiRenderMetrics.clampUiScale(-2f));
        assertEquals(UiRenderMetrics.MAX_UI_SCALE, UiRenderMetrics.clampUiScale(20f));
    }
}
