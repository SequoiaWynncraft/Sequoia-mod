package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class WorldMapModeDropdownLayoutTest {
    @Test
    void staysInsideTheMapWhenTheRightSidebarChangesItsWidth() {
        for (float mapWidth : new float[] {700, 450, 200, 100, 12}) {
            var layout = WorldMapModeDropdownLayout.fit(230, mapWidth);
            assertTrue(layout.x() >= 230);
            assertTrue(layout.x() + layout.width() <= 230 + mapWidth);
            assertTrue(layout.width() >= 0 && layout.width() <= 160);
        }
    }

    @Test
    void optionRowsMatchVisibleChoicesAndExcludeTheTriggerAndOutsideEdges() {
        var layout = WorldMapModeDropdownLayout.fit(230, 500);
        float x = layout.x() + 1;
        assertTrue(layout.contains(x, 13, false));
        assertEquals(-1, layout.optionAt(x, 13));
        for (int row = 0; row < 3; row++) {
            float y = layout.y() + layout.rowHeight() * (row + 1);
            assertEquals(row, layout.optionAt(x, y));
            assertTrue(layout.contains(x, y, true));
            assertFalse(layout.contains(x, y, false));
        }
        assertEquals(-1, layout.optionAt(x, layout.y() + layout.rowHeight() * 4));
        assertEquals(-1, layout.optionAt(layout.x() + layout.width(), 40));
        assertEquals(-1, layout.optionAt(layout.x() - 1, 40));
    }
}
