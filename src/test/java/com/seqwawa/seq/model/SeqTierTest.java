package com.seqwawa.seq.model;

import static com.seqwawa.seq.model.SeqTier.ALL_RAIDS;
import static com.seqwawa.seq.model.SeqTier.SINGLE_RAID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeqTierTest {

    @Test
    void theFirstTargetIsBronze() {
        assertEquals(SeqTier.BRONZE, SeqTier.next(0, SINGLE_RAID));
        assertEquals(25, SeqTier.next(19, SINGLE_RAID).threshold(SINGLE_RAID));
    }

    @Test
    void theTargetMovesOnOnceAThresholdIsMet() {
        assertEquals(SeqTier.SILVER, SeqTier.next(25, SINGLE_RAID));
        assertEquals(SeqTier.GOLD, SeqTier.next(50, SINGLE_RAID));
        assertEquals(SeqTier.PLATINUM, SeqTier.next(100, SINGLE_RAID));
        assertEquals(SeqTier.DIAMOND, SeqTier.next(500, SINGLE_RAID));
        assertEquals(SeqTier.OBSIDIAN, SeqTier.next(1_000, SINGLE_RAID));
        assertEquals(SeqTier.MYTHRIL, SeqTier.next(2_500, SINGLE_RAID));
    }

    @Test
    void mythrilIsTheEndOfTheRoad() {
        assertEquals(5_000, SeqTier.MYTHRIL.threshold(SINGLE_RAID));
        assertNull(SeqTier.next(5_000, SINGLE_RAID));
        assertNull(SeqTier.next(9_999, SINGLE_RAID));
    }

    @Test
    void theCombinedRowAsksForTwiceAsMuch() {
        assertEquals(50, SeqTier.BRONZE.threshold(ALL_RAIDS));
        assertEquals(10_000, SeqTier.MYTHRIL.threshold(ALL_RAIDS));
        assertEquals(SeqTier.BRONZE, SeqTier.next(49, ALL_RAIDS));
        assertEquals(SeqTier.SILVER, SeqTier.next(50, ALL_RAIDS));
    }

    @Test
    void thresholdsOnlyEverGoUp() {
        int previous = 0;
        for (SeqTier tier : SeqTier.ordered()) {
            assertTrue(tier.threshold(SINGLE_RAID) > previous, tier + " does not climb");
            previous = tier.threshold(SINGLE_RAID);
        }
    }

    @Test
    void tierNamesAreReadWhateverTheirCase() {
        assertEquals(SeqTier.MYTHRIL, SeqTier.fromKey("mythril"));
        assertEquals(SeqTier.MYTHRIL, SeqTier.fromKey("MYTHRIL"));
        assertEquals(SeqTier.GOLD, SeqTier.fromKey(" Gold "));
    }

    @Test
    void anUnknownTierNameIsNoTier() {
        assertNull(SeqTier.fromKey(null));
        assertNull(SeqTier.fromKey(""));
        assertNull(SeqTier.fromKey("titanium"));
    }
}
