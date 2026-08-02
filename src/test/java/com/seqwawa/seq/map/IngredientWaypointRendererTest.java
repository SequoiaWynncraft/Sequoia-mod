package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.map.IngredientWaypointManager.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngredientWaypointRendererTest {
    @Test
    void stacksThreeRadiusRingsFourBlocksApartAtSeventyPercentOpacity() {
        assertEquals(List.of(0.0, 4.0, 8.0), IngredientWaypointRenderer.RADIUS_RING_VERTICAL_OFFSETS);
        assertEquals(0.7, IngredientWaypointRenderer.RADIUS_ALPHA / 255.0, 0.01);
    }

    @Test
    void rendersOnlyPositiveRadiiWithinEightChunks() {
        assertTrue(IngredientWaypointRenderer.shouldRenderRadius(12, 100));
        assertTrue(IngredientWaypointRenderer.shouldRenderRadius(12, 128.0 * 128.0));
        assertFalse(IngredientWaypointRenderer.shouldRenderRadius(0, 100));
        assertFalse(IngredientWaypointRenderer.shouldRenderRadius(-1, 100));
        assertFalse(IngredientWaypointRenderer.shouldRenderRadius(12, 129.0 * 129.0));
    }

    @Test
    void colorsRadiusGreenInsideAndRedOutsideWhenProximityColorsAreEnabled() {
        assertEquals(
                0xFF55FF55,
                IngredientWaypointRenderer.resolveRadiusColor(Kind.INGREDIENT_SPAWN, true, 99, 10));
        assertEquals(
                0xFF55FF55,
                IngredientWaypointRenderer.resolveRadiusColor(Kind.TOTEM_SPOT, true, 100, 10));
        assertEquals(
                0xFFFF5555,
                IngredientWaypointRenderer.resolveRadiusColor(Kind.INGREDIENT_SPAWN, true, 101, 10));
    }

    @Test
    void keepsWaypointTypeColorsWhenProximityColorsAreDisabled() {
        assertEquals(
                0xFF55FFFF,
                IngredientWaypointRenderer.resolveRadiusColor(Kind.INGREDIENT_SPAWN, false, 101, 10));
        assertEquals(
                0xFFFFAA33,
                IngredientWaypointRenderer.resolveRadiusColor(Kind.TOTEM_SPOT, false, 0, 10));
    }

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
