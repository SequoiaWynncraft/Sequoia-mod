package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.LeafAccount;
import org.junit.jupiter.api.Test;

class LeafHeaderTest {

    @Test
    void theMedalHasTheSameSpaceToItsLeftAsAboveAndBelowIt() {
        assertEquals(7, LeafHeader.medalInset());
        assertEquals(LeafHeader.HEIGHT, 32 + 2 * LeafHeader.medalInset());
    }

    @Test
    void theBalanceSitsBesideTheLevelWhenThereIsRoom() {
        // About 484 UI pixels at 1280x720 and GUI scale 2, with a 92-pixel balance drawn large.
        LeafHeader.Layout layout = LeafHeader.layout(478, 120, 92);

        assertTrue(layout.balanceBeside());
        assertEquals(30, layout.balanceSize());
        assertEquals(LeafHeader.HEIGHT * LeafHeader.P, layout.height());
        assertEquals((7 + 32 + 6) * LeafHeader.P, layout.levelX());
        assertTrue(layout.levelWidth() >= LeafHeader.MIN_LEVEL_COLUMN);
    }

    @Test
    void aBigBalanceIsDrawnSmallerBeforeItMovesUnderneath() {
        LeafHeader.Layout smaller = LeafHeader.layout(400, 200, 130);
        assertTrue(smaller.balanceBeside());
        assertEquals(20, smaller.balanceSize());

        LeafHeader.Layout under = LeafHeader.layout(300, 200, 130);
        assertFalse(under.balanceBeside());
        assertEquals((LeafHeader.HEIGHT + LeafHeader.STRIP) * LeafHeader.P, under.height());
    }

    @Test
    void theLevelLinesSayHowFarAlongAndHowFarToGo() {
        LeafAccount.Level level = new LeafAccount.Level(7, 640, 2500);

        assertEquals("640 / 2,500 XP", LeafHeader.xpLine(level));
        assertEquals("1,860 XP to go", LeafHeader.toNextLine(level));
        assertEquals(0.256f, LeafHeader.xpRatio(level), 1e-6);
        assertEquals(0f, LeafHeader.xpRatio(new LeafAccount.Level(1, 0, 0)));
    }
}
