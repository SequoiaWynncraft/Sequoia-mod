package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemScaleTest {

    private static final Map<String, Float> ASCENDANCY_ROLLS = Map.of(
            "rawHealth", 94.98f,
            "rawSpellDamage", 95.20f,
            "fireDamage", 86.21f,
            "reflection", 90.00f,
            "jumpHeight", 33.33f);

    @Test
    void weightsAreNormalisedSoTheyNeedNotAddUpToOneHundred() {
        ItemScale hundred = new ItemScale(
                "Ascendancy",
                Map.of("rawSpellDamage", 35.0, "rawHealth", 35.0, "fireDamage", 29.0, "reflection", 1.0));
        ItemScale doubled = new ItemScale(
                "Ascendancy",
                Map.of("rawSpellDamage", 70.0, "rawHealth", 70.0, "fireDamage", 58.0, "reflection", 2.0));

        assertEquals(92.47f, hundred.score(ASCENDANCY_ROLLS), 0.02f);
        assertEquals(hundred.score(ASCENDANCY_ROLLS), doubled.score(ASCENDANCY_ROLLS), 0.001f);
    }

    @Test
    void statsMissingFromTheItemAreIgnored() {
        ItemScale scale = new ItemScale("Ascendancy", Map.of("rawHealth", 50.0, "walkSpeed", 50.0));

        assertEquals(94.98f, scale.score(ASCENDANCY_ROLLS), 0.02f);
    }

    @Test
    void negativeWeightRewardsALowRoll() {
        ItemScale scale = new ItemScale("Ascendancy", Map.of("fireDamage", -100.0));

        assertEquals(13.79f, scale.score(ASCENDANCY_ROLLS), 0.05f);
    }

    @Test
    void scoreIsNullWhenNoWeightedStatIsPresent() {
        ItemScale scale = new ItemScale("Ascendancy", Map.of("walkSpeed", 100.0));

        assertNull(scale.score(ASCENDANCY_ROLLS));
    }
}
