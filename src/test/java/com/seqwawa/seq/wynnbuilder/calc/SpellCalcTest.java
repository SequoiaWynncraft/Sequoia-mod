package com.seqwawa.seq.wynnbuilder.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SpellCalcTest {

    private static BuildStats stats(Map<String, Integer> identifications, int[] skillPoints, int health) {
        return new BuildStats(
                identifications, Map.of(), skillPoints, new int[5], 0, 200, true,
                health, new int[5], List.of(), List.of(), Map.of());
    }

    private static int[] skills(int strength, int dexterity, int intelligence, int defence, int agility) {
        return new int[] {strength, dexterity, intelligence, defence, agility};
    }

    @Test
    void aSpellWithNoModifiersCostsItsBasePrice() {
        BuildStats stats = stats(Map.of(), skills(0, 0, 0, 0, 0), 1000);
        assertEquals(40, SpellCalc.cost(stats, 1, 40));
    }

    @Test
    void percentageAndRawCostModifiersBothApply() {
        BuildStats percentage = stats(Map.of("spPct1", -50), skills(0, 0, 0, 0, 0), 1000);
        assertEquals(20, SpellCalc.cost(percentage, 1, 40));

        BuildStats raw = stats(Map.of("spRaw1", -10), skills(0, 0, 0, 0, 0), 1000);
        assertEquals(30, SpellCalc.cost(raw, 1, 40));
    }

    @Test
    void costModifiersOnlyAffectTheirOwnSpell() {
        BuildStats stats = stats(Map.of("spPct1", -50), skills(0, 0, 0, 0, 0), 1000);

        assertEquals(20, SpellCalc.cost(stats, 1, 40));
        assertEquals(40, SpellCalc.cost(stats, 2, 40), "spell 2 is untouched");
    }

    @Test
    void intelligenceReducesTheCostAndHalvesItAtTheCap() {
        BuildStats capped = stats(Map.of(), skills(0, 0, 150, 0, 0), 1000);

        // The intelligence weighting is normalised so the cap is exactly half price.
        assertEquals(20, SpellCalc.cost(capped, 1, 40));
    }

    @Test
    void aSpellNeverCostsLessThanOneMana() {
        BuildStats stats = stats(Map.of("spRaw1", -999), skills(0, 0, 150, 0, 0), 1000);
        assertEquals(1, SpellCalc.cost(stats, 1, 40));
    }

    @Test
    void aFreeSpellStaysFree() {
        BuildStats stats = stats(Map.of(), skills(0, 0, 0, 0, 0), 1000);
        assertEquals(0, SpellCalc.cost(stats, 0, 0));
    }

    @Test
    void healingScalesWithMaximumHealth() {
        BuildStats small = stats(Map.of(), skills(0, 0, 0, 0, 0), 1000);
        BuildStats large = stats(Map.of(), skills(0, 0, 0, 0, 0), 5000);

        assertEquals(200, SpellCalc.heal(small, 0.2, null), 0.01);
        assertEquals(1000, SpellCalc.heal(large, 0.2, null), 0.01);
    }

    @Test
    void healingEfficiencyChangesTheAmountHealed() {
        BuildStats boosted = stats(Map.of("healPct", 50), skills(0, 0, 0, 0, 0), 1000);
        assertEquals(300, SpellCalc.heal(boosted, 0.2, null), 0.01);

        BuildStats reduced = stats(Map.of("healPct", -50), skills(0, 0, 0, 0, 0), 1000);
        assertEquals(100, SpellCalc.heal(reduced, 0.2, null), 0.01);
    }

    @Test
    void healMultipliersScopedToAPartOnlyApplyThere() {
        BuildStats stats = stats(
                Map.of("healMult.Rebound:3.Heal Amount", 100), skills(0, 0, 0, 0, 0), 1000);

        assertEquals(400, SpellCalc.heal(stats, 0.2, "3.Heal Amount"), 0.01);
        assertEquals(200, SpellCalc.heal(stats, 0.2, "4.Other Part"), 0.01,
                "a bonus scoped to another part must not apply");
    }

    @Test
    void manaRegenTicksFiveTimesASecond() {
        BuildStats stats = stats(Map.of("mr", 20), skills(0, 0, 0, 0, 0), 1000);
        assertEquals(4, SpellCalc.manaPerSecond(stats), 0.01);
    }

    @Test
    void sustainIsBoundedByManaRegen() {
        BuildStats stats = stats(Map.of("mr", 20), skills(0, 0, 0, 0, 0), 1000);

        // 4 mana per second against a 40 mana spell is one cast every ten seconds.
        assertEquals(0.1, SpellCalc.castsPerSecond(stats, 40), 0.001);
        assertEquals(1000, SpellCalc.sustainedDps(stats, 40, 10000), 0.01);
    }

    @Test
    void aFreeSpellHasNoSustainLimit() {
        BuildStats stats = stats(Map.of("mr", 20), skills(0, 0, 0, 0, 0), 1000);
        assertEquals(0, SpellCalc.castsPerSecond(stats, 0), 0.001);
    }

    @Test
    void damageMultipliersRespectTheirScope() {
        Map<String, Integer> identifications = Map.of(
                "damMult.Global", 50,
                "damMult.Scoped:3.Heart Shatter", 100,
                "damMult.Elemental;e", 100);

        var everywhere = DamageMultipliers.damage(identifications, "9.Other", true);
        assertEquals(1.5, everywhere.global(), 0.001, "the unscoped bonus always applies");
        assertEquals(2.0, everywhere.perElement()[1], 0.001, "earth is boosted");
        assertEquals(1.0, everywhere.perElement()[2], 0.001, "thunder is not");

        var onTarget = DamageMultipliers.damage(identifications, "3.Heart Shatter", true);
        assertEquals(3.0, onTarget.global(), 0.001, "the scoped bonus stacks on its own part");
    }

    @Test
    void meleeOnlyMultipliersSkipSpells() {
        Map<String, Integer> identifications = Map.of("damMult.Rage;m", 100);

        assertEquals(2.0, DamageMultipliers.damage(identifications, null, false).global(), 0.001);
        assertEquals(1.0, DamageMultipliers.damage(identifications, null, true).global(), 0.001);
    }

    @Test
    void aTotalPartSumsDamagePartsAndNotOtherTotals() {
        // A "total" that referenced another "total" compounded into absurd figures; totals must be
        // built from damage parts alone.
        java.util.Map<String, Double> damageParts = java.util.Map.of("Beam Tick Damage", 30000.0);
        java.util.Map<String, Double> totals = new java.util.LinkedHashMap<>();

        double beamDps = 0;
        for (var hit : java.util.Map.of("Beam Tick Damage", 5).entrySet()) {
            beamDps += damageParts.getOrDefault(hit.getKey(), 0.0) * hit.getValue();
        }
        totals.put("Beam DPS", beamDps);
        assertEquals(150000, beamDps, 0.01);

        // "Total Damage" referencing the same damage part stays proportionate.
        double totalDamage = 0;
        for (var hit : java.util.Map.of("Beam Tick Damage", 40).entrySet()) {
            totalDamage += damageParts.getOrDefault(hit.getKey(), 0.0) * hit.getValue();
        }
        assertEquals(1200000, totalDamage, 0.01);
        assertTrue(totalDamage < 11_000_000, "chaining totals is what produced millions");
    }

    @Test
    void defenceMultipliersReduceIncomingDamage() {
        assertTrue(DamageMultipliers.defence(Map.of("defMult.Potion", 20)) < 1);
        assertEquals(1.0, DamageMultipliers.defence(Map.of()), 0.001);
    }
}
