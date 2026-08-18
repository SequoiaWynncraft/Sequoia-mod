package com.seqwawa.seq.model;

import static com.seqwawa.seq.model.SeqTier.ALL_RAIDS;
import static com.seqwawa.seq.model.SeqTier.SINGLE_RAID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeqTierTest {

    @Test
    void nothingIsEarnedBeforeTheFirstThreshold() {
        assertNull(SeqTier.reached(0, SINGLE_RAID));
        assertNull(SeqTier.reached(24, SINGLE_RAID));
        assertEquals(SeqTier.BRONZE, SeqTier.next(0, SINGLE_RAID));
        assertEquals(25, SeqTier.next(19, SINGLE_RAID).threshold(SINGLE_RAID));
    }

    @Test
    void aTierIsEarnedOnItsExactThreshold() {
        assertEquals(SeqTier.BRONZE, SeqTier.reached(25, SINGLE_RAID));
        assertEquals(SeqTier.SILVER, SeqTier.next(25, SINGLE_RAID));
        assertEquals(SeqTier.SILVER, SeqTier.reached(50, SINGLE_RAID));
    }

    @Test
    void theLadderClimbsInOrder() {
        assertEquals(SeqTier.GOLD, SeqTier.reached(100, SINGLE_RAID));
        assertEquals(SeqTier.PLATINUM, SeqTier.reached(300, SINGLE_RAID));
        assertEquals(SeqTier.DIAMOND, SeqTier.reached(999, SINGLE_RAID));
        assertEquals(SeqTier.OBSIDIAN, SeqTier.reached(1000, SINGLE_RAID));
    }

    @Test
    void mythrilIsTheEndOfTheRoad() {
        assertEquals(SeqTier.MYTHRIL, SeqTier.reached(2500, SINGLE_RAID));
        assertNull(SeqTier.next(2500, SINGLE_RAID));
        assertNull(SeqTier.next(9999, SINGLE_RAID));
    }

    @Test
    void theCombinedRowAsksForTwiceAsMuch() {
        assertNull(SeqTier.reached(49, ALL_RAIDS));
        assertEquals(SeqTier.BRONZE, SeqTier.reached(50, ALL_RAIDS));
        assertEquals(50, SeqTier.BRONZE.threshold(ALL_RAIDS));
        assertEquals(5000, SeqTier.MYTHRIL.threshold(ALL_RAIDS));
    }

    @Test
    void thresholdsOnlyEverGoUp() {
        int previous = 0;
        for (SeqTier tier : SeqTier.ordered()) {
            assertTrue(tier.threshold(SINGLE_RAID) > previous, tier + " does not climb");
            previous = tier.threshold(SINGLE_RAID);
        }
    }
}
