package com.seqwawa.seq.wynnbuilder.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IdentificationsTest {

    @Test
    void ordinaryStatsAreDisplayable() {
        assertTrue(Identifications.isDisplayable("sdPct"));
        assertTrue(Identifications.isDisplayable("mr"));
        assertTrue(Identifications.isDisplayable("eDamPct"));
        assertTrue(Identifications.isDisplayable("eDamRaw"));
        assertTrue(Identifications.isDisplayable("atkTier"));
    }

    @Test
    void abilityInternalsAreHiddenFromTheIdentificationList() {
        // These target one part of one spell and are consumed by the damage calculation; listing
        // them shows the player rows like "damMult.MultiTotem:1.Tick Damage".
        assertFalse(Identifications.isDisplayable("damMult.MultiTotem:1.Tick Damage"));
        assertFalse(Identifications.isDisplayable("damMult.DTotem:7.Effigy Hit"));
        assertFalse(Identifications.isDisplayable("healMult.MultiTotem"));
        assertFalse(Identifications.isDisplayable("healMult.Rebound:3.Heal Amount"));
        assertFalse(Identifications.isDisplayable("defMult.Potion"));
    }

    @Test
    void flatSpellDamageAddsAreHidden() {
        // Half of a min/max pair each; meaningful to the damage pipeline, noise on their own.
        assertFalse(Identifications.isDisplayable("aDamAddMin"));
        assertFalse(Identifications.isDisplayable("aDamAddMax"));
        assertFalse(Identifications.isDisplayable("eDamAddMin"));
        assertFalse(Identifications.isDisplayable("nDamAddMax"));
    }

    @Test
    void similarLookingRealStatsAreNotCaughtByTheFilter() {
        // Guard against the rule being too broad: these are genuine identifications.
        assertTrue(Identifications.isDisplayable("eDamRaw"));
        assertTrue(Identifications.isDisplayable("damRaw"));
        assertTrue(Identifications.isDisplayable("damPct"));
        assertTrue(Identifications.isDisplayable("eMdRaw"));
        assertTrue(Identifications.isDisplayable("eSdRaw"));
    }

    @Test
    void blankKeysAreNotDisplayable() {
        assertFalse(Identifications.isDisplayable(null));
        assertFalse(Identifications.isDisplayable(""));
    }

    @Test
    void gatheringStatsHaveNamesRatherThanRawKeys() {
        assertEquals("Gathering XP %", Identifications.displayName("gXp"));
        assertEquals("Gathering Speed %", Identifications.displayName("gSpd"));
    }

    @Test
    void unknownKeysFallBackToTheRawKey() {
        assertEquals("someNewStat", Identifications.displayName("someNewStat"));
    }

    @Test
    void statsAreSortedIntoReadableGroups() {
        assertEquals(Identifications.Group.SKILL_POINTS, Identifications.group("str"));
        assertEquals(Identifications.Group.SKILL_POINTS, Identifications.group("agi"));

        assertEquals(Identifications.Group.OFFENCE, Identifications.group("sdPct"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("mdRaw"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("eDamPct"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("atkTier"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("critDamPct"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("poison"));

        assertEquals(Identifications.Group.DEFENCE, Identifications.group("hpBonus"));
        assertEquals(Identifications.Group.DEFENCE, Identifications.group("eDefPct"));
        assertEquals(Identifications.Group.DEFENCE, Identifications.group("hprRaw"));
        assertEquals(Identifications.Group.DEFENCE, Identifications.group("thorns"));

        assertEquals(Identifications.Group.MANA, Identifications.group("mr"));
        assertEquals(Identifications.Group.MANA, Identifications.group("ms"));
        assertEquals(Identifications.Group.MANA, Identifications.group("maxMana"));
        assertEquals(Identifications.Group.MANA, Identifications.group("spPct1"));
        assertEquals(Identifications.Group.MANA, Identifications.group("spRaw4"));

        assertEquals(Identifications.Group.MOVEMENT, Identifications.group("spd"));
        assertEquals(Identifications.Group.MOVEMENT, Identifications.group("sprint"));

        assertEquals(Identifications.Group.UTILITY, Identifications.group("xpb"));
        assertEquals(Identifications.Group.UTILITY, Identifications.group("lb"));
        assertEquals(Identifications.Group.UTILITY, Identifications.group("eSteal"));

        // Sustain sits with the other survivability stats.
        assertEquals(Identifications.Group.DEFENCE, Identifications.group("ls"));
    }

    @Test
    void unprefixedOffensiveStatsAreNotMistakenForUtility() {
        // The base forms are lower case while the element-prefixed ones are capitalised, so a
        // single substring rule silently dropped these into the catch-all group.
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("sdPct"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("sdRaw"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("mdPct"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("mdRaw"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("damPct"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("damRaw"));
        // And the prefixed ones still work.
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("eSdRaw"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("tMdRaw"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("rDamRaw"));
    }

    @Test
    void defencePercentagesDoNotFallIntoOffence() {
        // "eDefPct" contains no damage marker, but "eDamPct" does; the two must not be confused.
        assertEquals(Identifications.Group.DEFENCE, Identifications.group("aDefPct"));
        assertEquals(Identifications.Group.OFFENCE, Identifications.group("aDamPct"));
    }

    @Test
    void unknownStatsLandInUtilityRatherThanDisappearing() {
        assertEquals(Identifications.Group.UTILITY, Identifications.group("someFutureStat"));
        assertEquals(Identifications.Group.UTILITY, Identifications.group(null));
    }

    @Test
    void hiddenStatsStillAggregateSoTheDamageCalculationCanUseThem() {
        // isDisplayable governs presentation only; the key is still a valid identification and is
        // summed into the build totals.
        assertTrue(Identifications.isIdentification("damMult.MultiTotem:1.Tick Damage"));
        assertTrue(Identifications.isIdentification("eDamAddMin"));
    }
}
