package com.seqwawa.seq.wynnbuilder.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.data.Powder.PowderElement;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BuffsTest {

    @Test
    void everyRaidGrantsBuffs() {
        for (RaidBuffs.Raid raid : RaidBuffs.Raid.values()) {
            assertFalse(RaidBuffs.forRaid(raid).isEmpty(), raid + " should have buffs");
        }
        assertEquals(79, RaidBuffs.all().size());
    }

    @Test
    void raidBuffStatsMatchTheSource() {
        RaidBuffs.Buff lightbearer = RaidBuffs.byName("Lightbearer-I");
        assertNotNull(lightbearer);
        assertEquals(RaidBuffs.Raid.NOTG, lightbearer.raid());
        assertEquals(25, lightbearer.stats().get("int"));
        assertEquals(20, lightbearer.stats().get("healPct"));
    }

    @Test
    void someRaidBuffsGrantMajorIdentifications() {
        RaidBuffs.Buff third = RaidBuffs.byName("Lightbearer-III");
        assertNotNull(third);
        assertEquals(java.util.List.of("ARCANES"), third.majorIds());

        RaidBuffs.Buff palisade = RaidBuffs.byName("Palisade");
        assertNotNull(palisade);
        assertEquals(-50, palisade.stats().get("spPct4"), "a drawback is preserved");
    }

    @Test
    void negativeBuffsAreKept() {
        // The final WTP entries are penalties; dropping them would overstate a build.
        RaidBuffs.Buff apathetic = RaidBuffs.byName("Apathetic");
        assertNotNull(apathetic);
        assertEquals(-45, apathetic.stats().get("spd"));
        assertEquals(-40, apathetic.stats().get("mdPct"));
    }

    @Test
    void eachElementHasAnActiveAndPassiveSpecial() {
        for (PowderElement element : PowderElement.encodingOrder()) {
            var specials = PowderSpecials.forElement(element);
            assertEquals(2, specials.size(), element + " should have two specials");
            assertTrue(specials.stream().anyMatch(s -> s.kind() == PowderSpecials.Kind.ACTIVE));
            assertTrue(specials.stream().anyMatch(s -> s.kind() == PowderSpecials.Kind.PASSIVE));
        }
    }

    @Test
    void powderSpecialsScaleWithTheirLevel() {
        PowderSpecials.Special curse = PowderSpecials.byName("Curse");
        assertNotNull(curse);
        assertEquals(10, curse.valueAt(1));
        assertEquals(25, curse.valueAt(7));
        assertEquals(0, curse.valueAt(0), "an unset special contributes nothing");
    }

    @Test
    void powderSpecialStatsAreCollected() {
        Map<String, Integer> stats = PowderSpecials.statsFor(Map.of("Curse", 7, "Kill Streak", 1));

        assertEquals(25 + 6, stats.get("damPct"), "both specials feed the same stat");
    }

    @Test
    void specialsWithoutAStatContributeNothing() {
        // Quake is a spell rather than a stat; it appears in the list but adds no numbers.
        assertTrue(PowderSpecials.statsFor(Map.of("Quake", 7)).isEmpty());
    }

    @Test
    void externalDamageBoostsTakeTheStrongestRatherThanStacking() {
        // Fortitude is 40% and the totem 20%; having both does not give 60%.
        var both = ExternalBoosts.statsFor(java.util.Set.of("fortitude", "totem"));
        assertEquals(40, both.get("damMult.Potion"));

        var totemOnly = ExternalBoosts.statsFor(java.util.Set.of("totem"));
        assertEquals(20, totemOnly.get("damMult.Potion"));
    }

    @Test
    void defensiveExternalBoostsDoStack() {
        var stats = ExternalBoosts.statsFor(java.util.Set.of("warscream", "emboldeningcry"));
        assertEquals(25, stats.get("defMult.Potion"), "20% and 5% add up");
        assertEquals(8, stats.get("damMult.Strength"));
    }

    @Test
    void judgementOverridesTheSkillPointBoosts() {
        assertEquals(1.4, ExternalBoosts.skillPointMultiplier(
                java.util.Set.of("judgement", "radiance")), 0.001);
        assertEquals(1.15, ExternalBoosts.skillPointMultiplier(java.util.Set.of("radiance")), 0.001);
        assertEquals(1.25, ExternalBoosts.skillPointMultiplier(
                java.util.Set.of("radiance", "divinehonor", "shine")), 0.001);
        assertEquals(1.0, ExternalBoosts.skillPointMultiplier(java.util.Set.of()), 0.001);
    }

    @Test
    void radianceAmplifiesItemSkillPointsRatherThanAddingStats() {
        // Radiance grants no stats of its own, which is why checking statsFor alone hid the bug:
        // its whole effect is the multiplier applied to the points gear already gives.
        assertTrue(ExternalBoosts.statsFor(java.util.Set.of("radiance")).isEmpty());
        assertEquals(1.15, ExternalBoosts.skillPointMultiplier(java.util.Set.of("radiance")), 0.001);
    }

    @Test
    void everyExternalBoostIsOffered() {
        assertEquals(10, ExternalBoosts.all().size());
    }

    @Test
    void aSpecialLevelOfZeroIsIgnored() {
        assertTrue(PowderSpecials.statsFor(Map.of("Curse", 0)).isEmpty());
    }
}
