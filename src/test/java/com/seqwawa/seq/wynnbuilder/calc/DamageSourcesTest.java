package com.seqwawa.seq.wynnbuilder.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.atree.AbilityTree;
import com.seqwawa.seq.wynnbuilder.atree.AbilityTreeEngine;
import com.seqwawa.seq.wynnbuilder.atree.AbilityTreeState;
import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.EncodingConsts;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataFile;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * How spells are assembled and worked out, including the basic attack.
 *
 * <p>Fixtures follow the upstream schema but are hand-written: the WynnBuilder repository is GPL-3
 * while this mod is MIT.
 */
class DamageSourcesTest {

    private static final String ITEMS =
            """
            {"items": [
              {"name": "Plain", "displayName": "Plain", "category": "weapon", "type": "wand",
               "tier": "Unique", "lvl": 1, "id": 1, "atkSpd": "NORMAL", "fixID": true,
               "nDam": "100-100", "eDam": "0-0", "tDam": "0-0", "wDam": "0-0", "fDam": "0-0", "aDam": "0-0"}
            ]}
            """;

    private static final String TREE =
            """
            {"Mage": [
              {"id": 0, "display_name": "Blast", "parents": [], "cost": 1, "properties": {},
               "effects": [{"type": "replace_spell", "name": "Blast", "base_spell": 1, "cost": 30,
                 "display": "Tick DPS", "parts": [
                   {"name": "Early", "hits": {"Late": 2}},
                   {"name": "Hit", "multipliers": [100, 0, 0, 0, 0, 0]},
                   {"name": "Tick DPS", "hits": {"Hit": 3}, "tick_rounding": true},
                   {"name": "Mend", "power": 0.1},
                   {"name": "Mend Total", "hits": {"Mend": 2}},
                   {"name": "Late", "multipliers": [50, 0, 0, 0, 0, 0], "display": false}]}]},
              {"id": 1, "display_name": "Proficiency", "parents": [0], "cost": 1, "properties": {},
               "effects": [{"type": "add_spell_prop", "base_spell": 0, "target_part": "Melee",
                 "multipliers": [10, 0, 0, 0, 0, 0]}]},
              {"id": 2, "display_name": "Cheaper", "parents": [0], "cost": 1, "properties": {},
               "effects": [{"type": "add_spell_prop", "base_spell": 1, "cost": -5}]},
              {"id": 3, "display_name": "Swipes", "parents": [0], "cost": 1, "properties": {},
               "effects": [
                 {"type": "add_spell_prop", "base_spell": 0, "target_part": "Swipe", "multipliers": [50, 0, 0, 0, 0, 0]},
                 {"type": "add_spell_prop", "base_spell": 0, "target_part": "Total", "display": "Total",
                  "hits": {"Melee": 1, "Swipe": 1}}]},
              {"id": 4, "display_name": "Reworked", "parents": [0], "cost": 1, "properties": {},
               "effects": [{"type": "replace_spell", "name": "Blast", "base_spell": 1, "display": "Hit",
                 "parts": [{"name": "Hit", "multipliers": [100, 0, 0, 0, 0, 0]}]}]}
            ]}
            """;

    private record Fixture(WynnBuild build, WynnDataSet data, BuildStats stats) {}

    private static Fixture fixture() {
        Map<WynnDataFile, String> contents = new EnumMap<>(WynnDataFile.class);
        contents.put(WynnDataFile.ITEMS, ITEMS);
        WynnDataSet data = WynnDataSet.parse("test", contents);
        WynnBuild build = new WynnBuild(33, EncodingConsts.DEFAULT.maxLevel(),
                EncodingConsts.DEFAULT.tomeCount(), EncodingConsts.DEFAULT.aspectCount());
        build.setLevel(106);
        build.setEquipment(EquipmentSlot.WEAPON, new BuildEquipment.Normal(1));
        BuildStats stats = BuildStats.compute(build, data, IdentificationRolls.RollMode.AVERAGE, Map.of());
        return new Fixture(build, data, stats);
    }

    private static DamageSources.Report report(int... selected) {
        AbilityTree tree = AbilityTree.parseAll(TREE).get("Mage");
        AbilityTreeState state = new AbilityTreeState(tree);
        for (int id : selected) {
            assertTrue(state.toggle(id));
        }
        Fixture fixture = fixture();
        var evaluation = AbilityTreeEngine.evaluate(state, Map.of(), Set.of());
        return DamageSources.compute(fixture.build(), fixture.data(), fixture.stats(), evaluation);
    }

    private static DamageSources.SpellGroup blast(DamageSources.Report report) {
        return report.spells().stream().filter(spell -> spell.name().equals("Blast")).findFirst().orElseThrow();
    }

    private static DamageSources.Source part(DamageSources.SpellGroup spell, String name) {
        return spell.parts().stream().filter(part -> part.name().equals(name)).findFirst().orElse(null);
    }

    @Test
    void theBasicAttackIsAPlainSwingUntilTheTreeReshapesIt() {
        DamageSources.Report plain = report();
        assertEquals(100, plain.melee().perHit(), 0.01);
        assertEquals(100 * 2.05, plain.melee().perSecond(), 0.01);

        // A proficiency adds to the melee multiplier, which a swing computed apart from the tree
        // never saw: this was the ten percent missing from a spear with two of them.
        assertEquals(110, report(1).melee().perHit(), 0.01);
    }

    @Test
    void anAddedPartTakesItsTargetsNameAndCanBecomeTheHeadline() {
        DamageSources.Report report = report(1, 3);

        // 110 from the swing, 50 from the swipe it now carries.
        assertEquals(160, report.melee().perHit(), 0.01);
        assertEquals(160 * 2.05, report.melee().perSecond(), 0.01);
    }

    @Test
    void aCostChangeNeedsNoTargetPart() {
        assertEquals(30, blast(report()).cost(), 0.01);
        assertEquals(25, blast(report(2)).cost(), 0.01);
    }

    @Test
    void redefiningASpellKeepsWhatTheRedefinitionLeavesOut() {
        DamageSources.SpellGroup blast = blast(report(4));

        assertEquals(30, blast.cost(), 0.01, "the redefinition names no cost, so the old one stands");
        assertEquals(1, blast.parts().size());
    }

    @Test
    void totalsSnapToTicksAndReachPartsDeclaredLater() {
        DamageSources.SpellGroup blast = blast(report());
        double hit = part(blast, "Hit").perHit();
        assertEquals(205, hit, 0.01, "a spell hit carries the attack speed multiplier");

        // Three hits a second land every six ticks, which is 3.33 a second.
        assertEquals(hit * (1 / 0.3), part(blast, "Tick DPS").perHit(), 0.01);
        assertEquals(part(blast, "Tick DPS").perHit(), blast.headline(), 0.01);

        // "Early" names a part declared after it, which is hidden but still counted.
        assertEquals(2 * 102.5, part(blast, "Early").perHit(), 0.01);
        assertNotNull(part(blast, "Early"));
        assertEquals(null, part(blast, "Late"));
    }

    @Test
    void aTotalOfHealsIsAHeal() {
        DamageSources.SpellGroup blast = blast(report());
        DamageSources.Source mend = part(blast, "Mend");
        DamageSources.Source total = part(blast, "Mend Total");

        assertTrue(mend.perHit() > 0);
        assertEquals("heal", total.detail());
        assertEquals(2 * mend.perHit(), total.perHit(), 0.01, "it used to read zero");
        assertFalse(blast.headline() == total.perHit(), "healing is not the spell's damage");
    }
}
