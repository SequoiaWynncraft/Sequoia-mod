package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SequoiaScreenTest {
    @Test
    void authorizedMenuFitsSevenRowsOnShortGuiScales() {
        SequoiaScreen.MenuLayout layout = SequoiaScreen.menuLayout(320, 7);

        assertEquals(7, layout.rowCount());
        assertTrue(layout.titleY() < layout.startY());
        assertTrue(layout.bottom() <= 308);
    }

    @Test
    void tallMenusRetainTheNormalEightPixelGap() {
        SequoiaScreen.MenuLayout layout = SequoiaScreen.menuLayout(600, 7);

        assertEquals(32, layout.rowStep());
    }
}
