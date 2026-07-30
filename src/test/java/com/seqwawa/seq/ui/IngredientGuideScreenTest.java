package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IngredientGuideScreenTest {
    @Test
    void keepsThreeOrFewerFarmSpotPreviewsStatic() {
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(3, 0, 12_000));
        assertEquals(1, IngredientGuideScreen.farmSpotPreviewIndex(3, 1, 12_000));
        assertEquals(2, IngredientGuideScreen.farmSpotPreviewIndex(3, 2, 12_000));
    }

    @Test
    void rotatesThreeVisiblePreviewsWhenFarmSpotHasMoreIngredients() {
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(4, 0, 0));
        assertEquals(2, IngredientGuideScreen.farmSpotPreviewIndex(4, 1, 1_200));
        assertEquals(3, IngredientGuideScreen.farmSpotPreviewIndex(4, 2, 1_200));
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(4, 2, 2_400));
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(4, 0, 4_800));
    }

    @Test
    void rejectsPreviewSlotsBeyondThreeIconLimit() {
        assertEquals(-1, IngredientGuideScreen.farmSpotPreviewIndex(7, 3, 0));
        assertEquals(-1, IngredientGuideScreen.farmSpotPreviewIndex(0, 0, 0));
    }
}
