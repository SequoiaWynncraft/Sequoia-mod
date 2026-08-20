package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PrincessRaidCelebrationTest {

    @Test
    void startsAboveTheScreenAndSettlesAtFullOpacity() {
        PrincessRaidCelebration.AnimationFrame opening = PrincessRaidCelebration.frameAt(0);
        PrincessRaidCelebration.AnimationFrame settled = PrincessRaidCelebration.frameAt(1_000);

        assertTrue(opening.active());
        assertEquals(-72f, opening.bannerOffset(), 0.001f);
        assertEquals(0f, opening.opacity(), 0.001f);
        assertTrue(opening.flashOpacity() > 0f);

        assertTrue(settled.active());
        assertEquals(0f, settled.bannerOffset(), 0.001f);
        assertEquals(1f, settled.opacity(), 0.001f);
        assertEquals(1f, settled.confettiIntensity(), 0.001f);
    }

    @Test
    void fadesAndLiftsTheBannerBeforeEnding() {
        PrincessRaidCelebration.AnimationFrame leaving = PrincessRaidCelebration.frameAt(4_700);

        assertTrue(leaving.active());
        assertTrue(leaving.opacity() > 0f && leaving.opacity() < 1f);
        assertTrue(leaving.bannerOffset() < 0f);
        assertTrue(leaving.confettiIntensity() < 1f);
        assertFalse(PrincessRaidCelebration.frameAt(PrincessRaidCelebration.DURATION_MS).active());
        assertFalse(PrincessRaidCelebration.frameAt(-1).active());
    }

    @Test
    void formatsPendingSingularAndPluralRoyalCounts() {
        assertEquals("Counting Princess graids…", PrincessRaidCelebration.raidCountText(0));
        assertEquals("1 Princess graid", PrincessRaidCelebration.raidCountText(1));
        assertEquals("42 Princess graids", PrincessRaidCelebration.raidCountText(42));
    }
}
