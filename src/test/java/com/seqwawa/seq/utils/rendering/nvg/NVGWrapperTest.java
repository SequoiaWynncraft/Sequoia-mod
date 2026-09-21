package com.seqwawa.seq.utils.rendering.nvg;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import org.junit.jupiter.api.Test;
import org.lwjgl.nanovg.NVGColor;

class NVGWrapperTest {
    @Test
    void allocatesColorsThatCanBeReleasedByLwjgl() {
        Color source = new Color(32, 96, 160, 224);

        for (int index = 0; index < 10_000; index++) {
            try (NVGColor color = NVGWrapper.nvgColor(source)) {
                assertEquals(32 / 255f, color.r());
                assertEquals(96 / 255f, color.g());
                assertEquals(160 / 255f, color.b());
                assertEquals(224 / 255f, color.a());
            }
        }
    }
    @Test
    void preservesTransparentAndTranslucentAlphaAtTheNativeRendererBoundary() {
        for (int alpha : new int[] {0, 1, 42, 127, 254, 255}) {
            try (NVGColor color = NVGWrapper.nvgColor(new Color(32, 96, 160, alpha))) {
                assertEquals(alpha / 255f, color.a());
            }
        }
    }

}
