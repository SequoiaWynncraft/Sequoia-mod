package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SeqPointsPayoutScreenTest {

    @Test
    void computesWholeLeCostAtTheCatalogRate() {
        assertEquals(1_200L, SeqPointsPayoutScreen.payoutCost("12", 100));
        assertNull(SeqPointsPayoutScreen.validationError("12", 100, 1_200));
    }

    @Test
    void rejectsZeroFractionsAndAmountsThatOverflow() {
        assertEquals("Enter a positive whole LE amount.", SeqPointsPayoutScreen.validationError("0", 100, 1_000));
        assertEquals("Enter a positive whole LE amount.", SeqPointsPayoutScreen.validationError("1.5", 100, 1_000));
        assertEquals(
                "Amount is too large.",
                SeqPointsPayoutScreen.validationError(Long.toString(Long.MAX_VALUE), 2, Long.MAX_VALUE));
    }

    @Test
    void rejectsCostsAboveTheTotalAvailableBalanceWithoutAnExtraPayoutCap() {
        assertNull(SeqPointsPayoutScreen.validationError("10", 100, 1_000));
        assertEquals(
                "Not enough Seq Points for that payout.",
                SeqPointsPayoutScreen.validationError("11", 100, 1_000));
    }
}
