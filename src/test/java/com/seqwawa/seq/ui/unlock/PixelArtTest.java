package com.seqwawa.seq.ui.unlock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PixelArtTest {

    @Test
    void aBarFillsWholeArtPixelsAndShowsAnyProgressAtAll() {
        assertEquals(0, PixelArt.barFill(100, 2, 0));
        assertEquals(2, PixelArt.barFill(100, 2, 0.001f), "anything above nothing shows one pixel");
        assertEquals(26, PixelArt.barFill(100, 2, 0.256f));
        // A third of 96 ends on the first of three notches, at 32.
        assertEquals(32, PixelArt.barFill(96, 2, 1 / 3f));
        assertEquals(PixelArt.snap(98 / 3f, 2), PixelArt.barFill(98, 2, 1 / 3f), "the same rounding as the notches");
        assertEquals(98, PixelArt.barFill(100, 2, 0.999f), "not quite there never looks full");
        assertEquals(100, PixelArt.barFill(100, 2, 1));
        assertEquals(100, PixelArt.barFill(100, 2, 3));
        assertEquals(0, PixelArt.barFill(100, 2, Float.NaN));
    }

    @Test
    void theLeafScalesWithTheFontsPixels() {
        assertEquals(1, PixelArt.fontPixel(10));
        assertEquals(2, PixelArt.fontPixel(20));
        assertEquals(3, PixelArt.fontPixel(30));
        // Where the outlined digits' ink starts, measured in game: 5 above the middle at 10, 17 at 30.
        assertEquals(95, PixelArt.leafTop(100, 10));
        assertEquals(89, PixelArt.leafTop(100, 20));
        assertEquals(83, PixelArt.leafTop(100, 30));
    }
}
