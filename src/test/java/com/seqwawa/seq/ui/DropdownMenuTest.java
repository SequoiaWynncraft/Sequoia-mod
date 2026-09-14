package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DropdownMenuTest {
    @Test
    void popupStaysInsideViewportAndUsesWholeRows() {
        var down = DropdownMenu.fit(20, 10, 160, 18, 20, 0, 220);
        assertEquals(28, down.y());
        assertEquals(8, down.rows());
        var up = DropdownMenu.fit(20, 190, 160, 18, 20, 0, 220);
        assertEquals(14, up.y());
        assertEquals(8, up.rows());
        var shortView = DropdownMenu.fit(20, 50, 160, 18, 20, 20, 85);
        assertEquals(28, shortView.y());
        assertEquals(1, shortView.rows());
    }

    @Test
    void rowHitTestingExcludesBottomAndRightEdgesAndIncludesScrollOffset() {
        assertEquals(4, DropdownMenu.optionAt(20, 30, 20, 30, 160, 22, 3, 4));
        assertEquals(5, DropdownMenu.optionAt(20, 52, 20, 30, 160, 22, 3, 4));
        assertEquals(-1, DropdownMenu.optionAt(20, 96, 20, 30, 160, 22, 3, 4));
        assertEquals(-1, DropdownMenu.optionAt(180, 30, 20, 30, 160, 22, 3, 4));
        assertEquals(-1, DropdownMenu.optionAt(20, 30, 20, 30, 160, 22, 0, 4));
    }
}
