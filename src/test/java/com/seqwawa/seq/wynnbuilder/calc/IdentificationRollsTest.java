package com.seqwawa.seq.wynnbuilder.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.calc.IdentificationRolls.Range;
import org.junit.jupiter.api.Test;

/**
 * Ranges are ordered by quality, not by number: {@code worst} is the least useful roll and
 * {@code best} the most useful, which for a drawback means {@code best} is the larger value.
 */
class IdentificationRollsTest {

    @Test
    void beneficialStatsRollBetweenThirtyAndOneHundredThirtyPercent() {
        Range range = IdentificationRolls.range("sdPct", 10, false);
        assertEquals(3, range.worst());
        assertEquals(13, range.best());
    }

    @Test
    void drawbacksRollTowardsZeroAtBest() {
        // -70 hurts less than -130, so it is the better roll despite being numerically larger.
        Range range = IdentificationRolls.range("hpBonus", -100, false);
        assertEquals(-130, range.worst());
        assertEquals(-70, range.best());
        assertEquals(-130, range.lower());
        assertEquals(-70, range.upper());
    }

    @Test
    void negativeSpellCostIsABonusSoItRollsFurtherFromZero() {
        // A bigger cost reduction is better, so the best roll is the more negative one.
        Range range = IdentificationRolls.range("spRaw3", -10, false);
        assertEquals(-3, range.worst());
        assertEquals(-13, range.best());
        assertEquals(-13, range.lower(), "display order must still be numeric");
        assertEquals(-3, range.upper());
    }

    @Test
    void positiveSpellCostIsADrawback() {
        Range range = IdentificationRolls.range("spPct1", 10, false);
        assertEquals(13, range.worst());
        assertEquals(7, range.best());
    }

    @Test
    void fixedItemsDoNotRoll() {
        Range range = IdentificationRolls.range("sdPct", 25, true);
        assertEquals(25, range.worst());
        assertEquals(25, range.best());
        assertTrue(range.isFixed());
    }

    @Test
    void skillPointBonusesNeverRoll() {
        Range range = IdentificationRolls.range("str", 10, false);
        assertEquals(10, range.worst());
        assertEquals(10, range.best());
    }

    @Test
    void zeroStaysZero() {
        Range range = IdentificationRolls.range("sdPct", 0, false);
        assertEquals(0, range.worst());
        assertEquals(0, range.best());
    }

    @Test
    void roundingNeverCollapsesAStatToNothing() {
        // 1 * 0.3 rounds to 0, which would read as "no stat"; it must stay at 1.
        assertEquals(1, IdentificationRolls.round(0.3));
        assertEquals(-1, IdentificationRolls.round(-0.3));
        assertEquals(0, IdentificationRolls.round(0.0));
        assertEquals(1, IdentificationRolls.range("sdPct", 1, false).worst());
    }

    @Test
    void averageSitsBetweenTheEnds() {
        Range range = new Range(3, 13);
        assertEquals(3, range.value(IdentificationRolls.RollMode.WORST));
        assertEquals(13, range.value(IdentificationRolls.RollMode.BEST));
        assertEquals(8, range.value(IdentificationRolls.RollMode.AVERAGE));
    }
}
