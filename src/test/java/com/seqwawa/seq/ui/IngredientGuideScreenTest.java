package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class IngredientGuideScreenTest {
    @Test
    void limitsFarmSpotPreviewsToThree() {
        assertEquals(0, IngredientGuideScreen.farmSpotVisiblePreviewCount(-1));
        assertEquals(2, IngredientGuideScreen.farmSpotVisiblePreviewCount(2));
        assertEquals(3, IngredientGuideScreen.farmSpotVisiblePreviewCount(7));
    }

    @Test
    void keepsThreeOrFewerFarmSpotPreviewsStatic() {
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(3, 0, 12_000));
        assertEquals(1, IngredientGuideScreen.farmSpotPreviewIndex(3, 1, 12_000));
        assertEquals(2, IngredientGuideScreen.farmSpotPreviewIndex(3, 2, 12_000));
    }

    @Test
    void rotatesLabelWhenAllIngredientIconsFit() {
        assertEquals(0, IngredientGuideScreen.farmSpotLabelIndex(2, 0));
        assertEquals(1, IngredientGuideScreen.farmSpotLabelIndex(2, 2_500));
        assertEquals(0, IngredientGuideScreen.farmSpotLabelIndex(2, 5_000));
    }

    @Test
    void keepsSingleIngredientLabelStaticAndRejectsEmptyLists() {
        assertEquals(0, IngredientGuideScreen.farmSpotLabelIndex(1, 12_000));
        assertEquals(-1, IngredientGuideScreen.farmSpotLabelIndex(0, 12_000));
    }

    @Test
    void rotatesThreeVisiblePreviewsWhenFarmSpotHasMoreIngredients() {
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(4, 0, 0));
        assertEquals(2, IngredientGuideScreen.farmSpotPreviewIndex(4, 1, 2_500));
        assertEquals(3, IngredientGuideScreen.farmSpotPreviewIndex(4, 2, 2_500));
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(4, 2, 5_000));
        assertEquals(0, IngredientGuideScreen.farmSpotPreviewIndex(4, 0, 10_000));
    }

    @Test
    void rejectsPreviewSlotsBeyondThreeIconLimit() {
        assertEquals(-1, IngredientGuideScreen.farmSpotPreviewIndex(7, 3, 0));
        assertEquals(-1, IngredientGuideScreen.farmSpotPreviewIndex(0, 0, 0));
    }
}
