package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UiPointerTest {
    @AfterEach
    void letGoOfThePointer() {
        MinecraftUiRenderer.previewPointer = null;
    }

    @Test
    void whileThePreviewHoldsThePointerEveryScreenSeesItThere() {
        MinecraftUiRenderer.previewPointer = new MinecraftUiRenderer.PreviewPointer(70, 61);

        assertEquals(new UiPointer(70, 61), UiPointer.at(900, 12));
        assertEquals(new UiPointer(70, 61), UiPointer.at(0, 0));
    }

    @Test
    void aScreensPointerStartsAtTheTopLeftCorner() {
        assertEquals(new UiPointer(0, 0), UiPointer.ORIGIN);
    }
}
