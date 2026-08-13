package com.seqwawa.seq.wynnbuilder.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.EncodingConsts;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.Powder;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataFile;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DamageCalcTest {

    private static final String ITEMS =
            """
            {"items": [
              {"name": "Plain", "displayName": "Plain", "category": "weapon", "type": "wand",
               "tier": "Unique", "lvl": 1, "id": 1, "atkSpd": "NORMAL", "slots": 3, "fixID": true,
               "nDam": "100-100", "eDam": "0-0", "tDam": "0-0", "wDam": "0-0", "fDam": "0-0", "aDam": "0-0"},
              {"name": "Boost Ring", "displayName": "Boost Ring", "category": "accessory", "type": "ring",
               "tier": "Unique", "lvl": 1, "id": 2, "sdPct": 100, "fixID": true},
              {"name": "Raw Ring", "displayName": "Raw Ring", "category": "accessory", "type": "ring",
               "tier": "Unique", "lvl": 1, "id": 3, "sdRaw": 50, "fixID": true},
              {"name": "Melee Ring", "displayName": "Melee Ring", "category": "accessory", "type": "ring",
               "tier": "Unique", "lvl": 1, "id": 4, "mdPct": 100, "fixID": true}
            ]}
            """;

    private static WynnDataSet data() {
        Map<WynnDataFile, String> contents = new EnumMap<>(WynnDataFile.class);
        contents.put(WynnDataFile.ITEMS, ITEMS);
        return WynnDataSet.parse("test", contents);
    }

    private static WynnBuild buildWithWeapon() {
        WynnBuild build = new WynnBuild(33, EncodingConsts.DEFAULT.maxLevel(),
                EncodingConsts.DEFAULT.tomeCount(), EncodingConsts.DEFAULT.aspectCount());
        build.setLevel(106);
        build.setEquipment(EquipmentSlot.WEAPON, new BuildEquipment.Normal(1));
        return build;
    }

    private static BuildStats statsFor(WynnBuild build, WynnDataSet data) {
        return BuildStats.compute(build, data, IdentificationRolls.RollMode.AVERAGE, Map.of());
    }

    private static DamageCalc.Weapon weapon(List<Powder> powders) {
        int[][] damages = new int[DamageCalc.ELEMENTS][2];
        damages[0] = new int[] {100, 100};
        return new DamageCalc.Weapon(damages, "NORMAL", powders);
    }

    @Test
    void aPlainMeleeHitIsTheWeaponDamage() {
        WynnDataSet data = data();
        BuildStats stats = statsFor(buildWithWeapon(), data);

        DamageCalc.Result hit = DamageCalc.meleeHit(stats, weapon(List.of()));

        // No boosts, no skill points: the hit is exactly the weapon's damage.
        assertEquals(100, hit.normalMin(), 0.01);
        assertEquals(100, hit.normalMax(), 0.01);
    }

    @Test
    void attackSpeedScalesASpellButNotASingleMeleeHit() {
        WynnDataSet data = data();
        BuildStats stats = statsFor(buildWithWeapon(), data);
        DamageCalc.Weapon weapon = weapon(List.of());

        DamageCalc.Result melee = DamageCalc.meleeHit(stats, weapon);
        DamageCalc.Result spell = DamageCalc.calculate(
                stats, weapon, DamageCalc.fullNeutral(), true, false, true);

        assertEquals(100, melee.normalMax(), 0.01);
        // A normal weapon multiplies spell damage by 2.05.
        assertEquals(205, spell.normalMax(), 0.01);
    }

    @Test
    void spellDamagePercentageAppliesToSpellsOnly() {
        WynnDataSet data = data();
        WynnBuild build = buildWithWeapon();
        build.setEquipment(EquipmentSlot.RING1, new BuildEquipment.Normal(2));
        BuildStats stats = statsFor(build, data);
        DamageCalc.Weapon weapon = weapon(List.of());

        assertEquals(100, stats.identification("sdPct"));
        // +100% spell damage doubles the spell.
        DamageCalc.Result spell = DamageCalc.calculate(
                stats, weapon, DamageCalc.fullNeutral(), true, false, true);
        assertEquals(410, spell.normalMax(), 0.01);
        // But leaves melee alone.
        assertEquals(100, DamageCalc.meleeHit(stats, weapon).normalMax(), 0.01);
    }

    @Test
    void meleeDamagePercentageAppliesToMeleeOnly() {
        WynnDataSet data = data();
        WynnBuild build = buildWithWeapon();
        build.setEquipment(EquipmentSlot.RING1, new BuildEquipment.Normal(4));
        BuildStats stats = statsFor(build, data);
        DamageCalc.Weapon weapon = weapon(List.of());

        assertEquals(200, DamageCalc.meleeHit(stats, weapon).normalMax(), 0.01);
        assertEquals(205, DamageCalc.calculate(
                stats, weapon, DamageCalc.fullNeutral(), true, false, true).normalMax(), 0.01);
    }

    @Test
    void rawSpellDamageIsAddedAfterPercentages() {
        WynnDataSet data = data();
        WynnBuild build = buildWithWeapon();
        build.setEquipment(EquipmentSlot.RING1, new BuildEquipment.Normal(3));
        BuildStats stats = statsFor(build, data);

        DamageCalc.Result spell = DamageCalc.calculate(
                stats, weapon(List.of()), DamageCalc.fullNeutral(), true, false, true);

        // 100 * 2.05 attack speed, then +50 raw.
        assertEquals(255, spell.normalMax(), 0.01);
    }

    @Test
    void spellMultipliersScaleTheWeaponDamage() {
        WynnDataSet data = data();
        BuildStats stats = statsFor(buildWithWeapon(), data);

        double[] half = new double[DamageCalc.ELEMENTS];
        half[0] = 50;
        DamageCalc.Result spell = DamageCalc.calculate(stats, weapon(List.of()), half, true, false, true);

        assertEquals(102.5, spell.normalMax(), 0.01);
    }

    @Test
    void powdersConvertNeutralDamageIntoTheirElement() {
        WynnDataSet data = data();
        BuildStats stats = statsFor(buildWithWeapon(), data);
        DamageCalc.Weapon powdered = weapon(List.of(new Powder(Powder.PowderElement.EARTH, 6)));

        DamageCalc.Result hit = DamageCalc.meleeHit(stats, powdered);

        // Conversion moves damage between elements without creating or destroying it.
        assertEquals(100, hit.normalMax(), 0.5);
    }

    @Test
    void criticalHitsAreStrongerThanNormalOnes() {
        WynnDataSet data = data();
        BuildStats stats = statsFor(buildWithWeapon(), data);

        DamageCalc.Result hit = DamageCalc.meleeHit(stats, weapon(List.of()));

        assertTrue(hit.critMax() > hit.normalMax(), "a critical must beat a normal hit");
        assertEquals(200, hit.critMax(), 0.01);
    }

    @Test
    void selfDamagePartsSkipTheStrengthBonusAndCriticals() {
        WynnDataSet data = data();
        BuildStats stats = statsFor(buildWithWeapon(), data);

        DamageCalc.Result selfDamage = DamageCalc.calculate(
                stats, weapon(List.of()), DamageCalc.fullNeutral(), true, false, false);

        assertEquals(selfDamage.normalMax(), selfDamage.critMax(), 0.01,
                "a part that ignores strength cannot critically hit either");
    }

    @Test
    void attackRateTurnsAHitIntoDamagePerSecond() {
        WynnDataSet data = data();
        BuildStats stats = statsFor(buildWithWeapon(), data);
        DamageCalc.Weapon weapon = weapon(List.of());

        double dps = DamageCalc.meleeDps(stats, weapon);
        double hit = DamageCalc.meleeHit(stats, weapon).expected(DamageCalc.critChance(stats));

        assertEquals(hit * DamageCalc.attacksPerSecond("NORMAL"), dps, 0.01);
    }

    @Test
    void damageNeverGoesNegative() {
        WynnDataSet data = data();
        BuildStats stats = statsFor(buildWithWeapon(), data);

        double[] none = new double[DamageCalc.ELEMENTS];
        DamageCalc.Result nothing = DamageCalc.calculate(stats, weapon(List.of()), none, true, false, true);

        assertTrue(nothing.normalMin() >= 0);
        assertTrue(nothing.normalMax() >= 0);
    }
}
