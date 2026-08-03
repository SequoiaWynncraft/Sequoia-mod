package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.map.MapCalibration;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldMapGeometryTest {
    @Test
    void rectangleHitTestingKeepsInclusiveEdges() {
        assertTrue(WorldMapGeometry.contains(10, 20, 10, 20, 30, 40));
        assertTrue(WorldMapGeometry.contains(40, 60, 10, 20, 30, 40));
        assertFalse(WorldMapGeometry.contains(40.01f, 60, 10, 20, 30, 40));
    }

    @Test
    void dropdownScrollClampsForEmptyShortAndLongLists() {
        assertEquals(0, WorldMapGeometry.clampScroll(5, 0, 8));
        assertEquals(0, WorldMapGeometry.clampScroll(5, 4, 8));
        assertEquals(2, WorldMapGeometry.clampScroll(2, 12, 8));
        assertEquals(4, WorldMapGeometry.clampScroll(99, 12, 8));
    }

    @Test
    void segmentDistanceHandlesEndpointsProjectionAndDegenerateSegments() {
        assertEquals(3.0, WorldMapGeometry.distanceToSegment(5, 3, 0, 0, 10, 0), 0.0001);
        assertEquals(5.0, WorldMapGeometry.distanceToSegment(15, 0, 0, 0, 10, 0), 0.0001);
        assertEquals(5.0, WorldMapGeometry.distanceToSegment(3, 4, 0, 0, 0, 0), 0.0001);
    }

    @Test
    void imageCoordinatesMapExactlyToCalibrationBounds() {
        assertEquals(MapCalibration.MIN_WORLD_X, WorldMapGeometry.imageToWorldX(0, 1000), 0.0001);
        assertEquals(MapCalibration.MAX_WORLD_X, WorldMapGeometry.imageToWorldX(1000, 1000), 0.0001);
        assertEquals(MapCalibration.MIN_WORLD_Z, WorldMapGeometry.imageToWorldZ(0, 500), 0.0001);
        assertEquals(MapCalibration.MAX_WORLD_Z, WorldMapGeometry.imageToWorldZ(500, 500), 0.0001);
    }

    @Test
    void wrappingAndFittingUseInjectedWidthWithoutRendererState() {
        assertEquals(
                List.of("alpha beta", "gamma", "delta"),
                WorldMapGeometry.wrapText("alpha beta gamma\ndelta", 10, String::length));
        assertEquals("abcdef", WorldMapGeometry.fitText("abcdef", 6, String::length));
        assertEquals("abc...", WorldMapGeometry.fitText("abcdefgh", 6, String::length));
        assertEquals("", WorldMapGeometry.fitText("abcdefgh", 2, String::length));
    }
}
