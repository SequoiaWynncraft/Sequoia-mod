package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class MapViewportTest {
    @Test
    void zoomKeepsThePointerWorldPositionAnchoredAndClamps() {
        MapViewport viewport = new MapViewport(100, -200, 1, 12, 20, 800, 500);
        double pointerX = 260;
        double pointerY = 180;
        double worldX = viewport.screenToWorldX(pointerX);
        double worldZ = viewport.screenToWorldZ(pointerY);

        MapViewport zoomed = viewport.zoomAt(pointerX, pointerY, MapViewport.SCROLL_ZOOM_FACTOR);

        assertEquals(worldX, zoomed.screenToWorldX(pointerX), .0001);
        assertEquals(worldZ, zoomed.screenToWorldZ(pointerY), .0001);
        assertEquals(MapViewport.MAX_PIXELS_PER_BLOCK, viewport.zoomAt(pointerX, pointerY, 100).pixelsPerBlock());
        assertEquals(
                MapViewport.MIN_PIXELS_PER_BLOCK,
                new MapViewport(0, 0, .036, 0, 0, 100, 100).zoomAt(50, 50, .01).pixelsPerBlock());
        assertSame(viewport, viewport.zoomAt(pointerX, pointerY, 0));
    }

    @Test
    void panAndFitShareTheMapCameraBounds() {
        MapViewport viewport = new MapViewport(100, -200, 2, 12, 20, 800, 500);
        MapViewport panned = viewport.panByScreenDelta(20, -10);

        assertEquals(90, panned.centerX());
        assertEquals(-195, panned.centerZ());
        assertEquals(
                MapViewport.MIN_PIXELS_PER_BLOCK,
                MapViewport.fitPixelsPerBlock(MapCalibration.fullBounds(), 1, 1, 1));
        assertEquals(
                MapViewport.MAX_PIXELS_PER_BLOCK,
                MapViewport.fitPixelsPerBlock(new MapBounds(0, 0, 1, 1), 100, 100, 1));
    }
}
