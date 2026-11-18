package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UiBoundsTest {
    private static final UiBounds BUTTON = new UiBounds(10, 20, 100, 30);

    @Test
    void aPointOnTheEdgeIsInside() {
        assertTrue(BUTTON.contains(10, 20), "the top-left corner");
        assertTrue(BUTTON.contains(110, 50), "the bottom-right corner");
        assertTrue(BUTTON.contains(60, 35));
        assertFalse(BUTTON.contains(9.5f, 35));
        assertFalse(BUTTON.contains(110.5f, 35));
        assertFalse(BUTTON.contains(60, 50.5f));
    }

    @Test
    void anEmptyRectangleHasNothingInside() {
        assertFalse(new UiBounds(10, 20, 0, 30).contains(10, 25));
        assertFalse(new UiBounds(10, 20, 100, 0).contains(50, 20));
        assertFalse(new UiBounds(10, 20, -5, 30).contains(10, 25));
    }

    @Test
    void thePointerIsTestedTheSameWayAsAPoint() {
        assertTrue(BUTTON.contains(new UiPointer(110, 50)));
        assertFalse(BUTTON.contains(new UiPointer(111, 50)));
    }

    @Test
    void insetMovesEachSideInAndANegativeInsetGrowsIt() {
        assertEquals(new UiBounds(12, 23, 96, 24), BUTTON.inset(2, 3));
        assertEquals(new UiBounds(8, 20, 104, 30), BUTTON.inset(-2, 0));
    }

    @Test
    void theEdgesAndCentreFollowFromTheCorner() {
        assertEquals(110, BUTTON.right());
        assertEquals(50, BUTTON.bottom());
        assertEquals(60, BUTTON.centerX());
        assertEquals(35, BUTTON.centerY());
    }
}
