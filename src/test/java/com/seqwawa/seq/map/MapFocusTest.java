package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class MapFocusTest {
    @Test
    void boundsIncludeEveryMarkerRadius() {
        MapFocus focus = new MapFocus(
                "Ingredient",
                List.of(
                        new MapFocus.Marker("one", "Ingredient", "Mob A", 100, 20, 200, 10),
                        new MapFocus.Marker("two", "Ingredient", "Mob B", -50, 40, 500, 25)),
                "two");

        assertEquals(-75, focus.bounds().minX());
        assertEquals(190, focus.bounds().minZ());
        assertEquals(110, focus.bounds().maxX());
        assertEquals(525, focus.bounds().maxZ());
        assertEquals("two", focus.selectedMarker().id());
    }

    @Test
    void missingSelectionIsAllowed() {
        MapFocus focus = new MapFocus(
                "Ingredient",
                List.of(new MapFocus.Marker("one", "Ingredient", "Mob", 1, 2, 3, -5)),
                "missing");

        assertNull(focus.selectedMarker());
        assertEquals(0, focus.markers().getFirst().radius());
        assertEquals("1 2 3", focus.markers().getFirst().coordinates());
    }
}
