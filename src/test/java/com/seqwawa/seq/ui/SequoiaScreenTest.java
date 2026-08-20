package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SequoiaScreenTest {
    @Test
    void authorizedMenuFitsEightRowsOnShortGuiScales() {
        SequoiaScreen.MenuLayout layout = SequoiaScreen.menuLayout(320, 8);

        assertEquals(8, layout.rowCount());
        assertTrue(layout.titleY() < layout.startY());
        assertTrue(layout.bottom() <= 308);
    }

    @Test
    void tallMenusRetainTheNormalEightPixelGap() {
        SequoiaScreen.MenuLayout layout = SequoiaScreen.menuLayout(600, 8);

        assertEquals(32, layout.rowStep());
    }

    @Test
    void authorizedMenuDoesNotOverlapItsTitleInACompactViewport() {
        SequoiaScreen.MenuLayout layout = SequoiaScreen.menuLayout(160, 8);

        assertTrue(layout.startY() >= 34);
        assertTrue(layout.buttonHeight() >= 10);
        assertTrue(layout.rowStep() >= layout.buttonHeight());
        assertTrue(layout.bottom() <= 148);
    }
}
