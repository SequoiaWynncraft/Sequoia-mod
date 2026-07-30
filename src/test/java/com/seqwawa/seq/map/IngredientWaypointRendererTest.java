package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IngredientWaypointRendererTest {
    @Test
    void displaysWaypointWithinDistanceAndFrontHorizontalHalfPlane() {
        assertTrue(IngredientWaypointRenderer.shouldDisplay(100, 20, 100, 0, 1));
    }

    @Test
    void displaysWaypointAtDistanceAndAngleBoundaries() {
        assertTrue(IngredientWaypointRenderer.shouldDisplay(0, 0, 8_000, 0, 1));
        assertTrue(IngredientWaypointRenderer.shouldDisplay(100, 0, 0, 0, 1));
        assertTrue(IngredientWaypointRenderer.shouldDisplay(-100, 0, 0, 0, 1));
    }

    @Test
    void hidesWaypointBeyondMaximumDistance() {
        assertFalse(IngredientWaypointRenderer.shouldDisplay(0, 0, 8_001, 0, 1));
    }

    @Test
    void hidesWaypointBehindPlayerHorizontalView() {
        assertFalse(IngredientWaypointRenderer.shouldDisplay(0, 0, -100, 0, 1));
        assertFalse(IngredientWaypointRenderer.shouldDisplay(100, 0, -1, 0, 1));
    }

    @Test
    void displaysVerticalWaypointWhenHorizontalAngleIsUndefined() {
        assertTrue(IngredientWaypointRenderer.shouldDisplay(0, 100, 0, 0, 1));
        assertTrue(IngredientWaypointRenderer.shouldDisplay(100, 0, 0, 0, 0));
    }
}
