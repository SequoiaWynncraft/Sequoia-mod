package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class IngredientGuideViewLayoutTest {
    @Test
    void positionsRowsForSingleAndSecondarySortModes() {
        IngredientGuideViewLayout.IngredientListLayout single =
                IngredientGuideViewLayout.ingredientListLayout(42, false);
        IngredientGuideViewLayout.IngredientListLayout secondary =
                IngredientGuideViewLayout.ingredientListLayout(42, true);

        assertEquals(new IngredientGuideViewLayout.IngredientListLayout(87, 113, 120, 132), single);
        assertEquals(new IngredientGuideViewLayout.IngredientListLayout(87, 113, 146, 158), secondary);
    }

    @Test
    void computesClampedScrollbarGeometryAndHitArea() {
        IngredientGuideViewLayout.ScrollbarGeometry middle =
                IngredientGuideViewLayout.scrollbarGeometry(100, 10, 100, 50, 100);
        IngredientGuideViewLayout.ScrollbarGeometry end =
                IngredientGuideViewLayout.scrollbarGeometry(100, 10, 100, 2_000, 900);

        assertNotNull(middle);
        assertEquals(35, middle.thumbY());
        assertEquals(50, middle.thumbHeight());
        assertTrue(middle.containsTrack(97, 10));
        assertFalse(middle.containsTrack(96, 10));
        assertEquals(90, end.thumbY());
        assertEquals(20, end.thumbHeight());
        assertNull(IngredientGuideViewLayout.scrollbarGeometry(0, 0, 100, 0, 0));
    }

    @Test
    void ellipsizesWithTheExistingSingleCharacterSuffix() {
        assertEquals("abcdef", IngredientGuideViewLayout.ellipsize("abcdef", 6, String::length));
        assertEquals("abcd…", IngredientGuideViewLayout.ellipsize("abcdef", 5, String::length));
        assertEquals("", IngredientGuideViewLayout.ellipsize(null, 5, String::length));
    }
}
