package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MapSidebarTest {
    @Test
    void scrollingUsesTheSameOffsetForDrawingAndInputAndClampsAfterResize() {
        var sidebar = new MapSidebar();
        sidebar.contentHeight(1000, 600);
        sidebar.scroll(-3, 600);
        assertEquals(84, sidebar.scroll());
        assertEquals(300, sidebar.y(300) + sidebar.scroll());
        sidebar.scroll(-100, 600);
        assertEquals(400, sidebar.scroll());
        sidebar.contentHeight(1000, 900);
        assertEquals(100, sidebar.scroll());
        sidebar.contentHeight(800, 900);
        assertEquals(0, sidebar.scroll());
    }

    @Test
    void modesKeepIndependentScrollAndResetOnDeactivation() {
        var gathering = new MapSidebar();
        var ingredients = new MapSidebar();
        gathering.contentHeight(1000, 600);
        ingredients.contentHeight(1000, 600);
        gathering.scroll(-2, 600);
        assertEquals(56, gathering.scroll());
        assertEquals(0, ingredients.scroll());
        gathering.reset();
        assertEquals(0, gathering.scroll());
    }
}
