package com.seqwawa.seq.wynnbuilder.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
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

class BuildStatsTest {

    private static final String ITEMS =
            """
            {"items": [
              {"name": "Plate", "displayName": "Plate", "category": "armor", "type": "chestplate",
               "tier": "Legendary", "lvl": 100, "id": 1, "hp": 3000, "eDef": 100, "fixID": true, "slots": 4},
              {"name": "Helm", "displayName": "Helm", "category": "armor", "type": "helmet",
               "tier": "Legendary", "lvl": 100, "id": 2, "hp": 1000, "hpBonus": 500, "fixID": true},
              {"name": "Heavy Ring", "displayName": "Heavy Ring", "category": "accessory", "type": "ring",
               "tier": "Rare", "lvl": 100, "id": 3, "strReq": 40, "fixID": true},
              {"name": "Giving Ring", "displayName": "Giving Ring", "category": "accessory", "type": "ring",
               "tier": "Rare", "lvl": 100, "id": 4, "str": 30, "fixID": true},
              {"name": "Blade", "displayName": "Blade", "category": "weapon", "type": "dagger",
               "tier": "Legendary", "lvl": 100, "id": 5, "atkSpd": "FAST", "slots": 3, "fixID": true,
               "nDam": "50-90", "eDam": "0-0", "tDam": "0-0", "wDam": "0-0", "fDam": "0-0", "aDam": "0-0",
               "sdPct": 20},
              {"name": "Rolled Ring", "displayName": "Rolled Ring", "category": "accessory", "type": "ring",
               "tier": "Rare", "lvl": 100, "id": 6, "hpBonus": 100},
              {"name": "Set Helm", "displayName": "Set Helm", "category": "armor", "type": "helmet",
               "tier": "Rare", "lvl": 100, "id": 7, "set": "Trio", "fixID": true},
              {"name": "Set Boots", "displayName": "Set Boots", "category": "armor", "type": "boots",
               "tier": "Rare", "lvl": 100, "id": 8, "set": "Trio", "fixID": true},
              {"name": "Twin Helm", "displayName": "Twin Helm", "category": "armor", "type": "helmet",
               "tier": "Rare", "lvl": 100, "id": 9, "set": "Twins", "fixID": true}
            ],
            "sets": {
              "Trio": {"items": ["Set Helm", "Set Boots"],
                       "bonuses": [{}, {"sdPct": 40, "hpBonus": 700}]},
              "Twins": {"items": ["Twin Helm"], "bonuses": [{}, {"illegal": true}]}
            }}
            """;

    private static final String RECIPES =
            """
            {"recipes": [
              {"type": "CHESTPLATE", "skill": "ARMOURING", "id": 10, "name": "Chestplate-100-103",
               "healthOrDamage": {"minimum": 1000, "maximum": 1200},
               "durability": {"minimum": 900, "maximum": 1100},
               "lvl": {"minimum": 100, "maximum": 103},
               "materials": [{"item": "A", "amount": 1}, {"item": "B", "amount": 1}]}
            ]}
            """;

    private static WynnDataSet data() {
        Map<WynnDataFile, String> contents = new EnumMap<>(WynnDataFile.class);
        contents.put(WynnDataFile.ITEMS, ITEMS);
        contents.put(WynnDataFile.RECIPES, RECIPES);
        return WynnDataSet.parse("test", contents);
    }

    private static WynnBuild build(int level) {
        WynnBuild build = new WynnBuild(33, EncodingConsts.DEFAULT.maxLevel(),
                EncodingConsts.DEFAULT.tomeCount(), EncodingConsts.DEFAULT.aspectCount());
        build.setLevel(level);
        return build;
    }

    private static BuildStats compute(WynnBuild build, WynnDataSet data) {
        return BuildStats.compute(build, data, IdentificationRolls.RollMode.AVERAGE, Map.of());
    }

    @Test
    void baseHealthFollowsTheLevelAlone() {
        BuildStats stats = compute(build(106), data());
        assertEquals(SkillPoints.levelToBaseHealth(106), stats.health());
    }

    @Test
    void armourHealthAndHealthBonusBothCount() {
        WynnDataSet data = data();
        WynnBuild build = build(106);
        build.setEquipment(EquipmentSlot.CHESTPLATE, new BuildEquipment.Normal(1));
        build.setEquipment(EquipmentSlot.HELMET, new BuildEquipment.Normal(2));

        BuildStats stats = compute(build, data);

        // 535 base + 3000 chestplate + 1000 helmet + 500 health bonus.
        assertEquals(SkillPoints.levelToBaseHealth(106) + 3000 + 1000 + 500, stats.health());
    }

    @Test
    void craftedGearContributesItsHealthAndIdentifications() {
        WynnDataSet data = data();
        WynnBuild build = build(106);
        CraftedItem craft = CraftedItem.empty(10).withMaterialTiers(3, 3);
        build.setEquipment(EquipmentSlot.CHESTPLATE, new BuildEquipment.Crafted(craft));

        BuildStats stats = compute(build, data);

        // A crafted chestplate is a real piece of armour; ignoring it loses its whole contribution.
        assertTrue(stats.health() > SkillPoints.levelToBaseHealth(106),
                "crafted armour must add its health, got " + stats.health());
    }

    @Test
    void armourPowdersAddHealthAndDefence() {
        WynnDataSet data = data();
        WynnBuild build = build(106);
        build.setEquipment(EquipmentSlot.CHESTPLATE, new BuildEquipment.Normal(1));
        int without = compute(build, data).health();

        build.setPowders(EquipmentSlot.CHESTPLATE, List.of(new Powder(Powder.PowderElement.EARTH, 6)));
        BuildStats withPowder = compute(build, data);

        assertEquals(without + 60, withPowder.health(), "a tier 6 powder grants 60 health");
        assertTrue(withPowder.elementalDefences()[0] > 0, "earth defence should rise");
    }

    @Test
    void requirementsDriveTheAssignedSkillPoints() {
        WynnDataSet data = data();
        WynnBuild build = build(106);
        build.setEquipment(EquipmentSlot.RING1, new BuildEquipment.Normal(3));

        BuildStats stats = compute(build, data);

        assertEquals(40, stats.assignedSkillPoints()[0], "40 strength is required");
        assertEquals(40, stats.assignedTotal());
        assertEquals(40, stats.skillPointTotals()[0]);
    }

    @Test
    void itemSkillPointBonusesPayForRequirements() {
        WynnDataSet data = data();
        WynnBuild build = build(106);
        build.setEquipment(EquipmentSlot.RING1, new BuildEquipment.Normal(3));
        build.setEquipment(EquipmentSlot.RING2, new BuildEquipment.Normal(4));

        BuildStats stats = compute(build, data);

        // The +30 strength ring covers most of the 40 required, leaving 10 to assign.
        assertEquals(10, stats.assignedSkillPoints()[0]);
        // The total is what the character ends up with: 10 assigned plus the ring's 30.
        assertEquals(40, stats.skillPointTotals()[0]);
    }

    @Test
    void skillPointTotalsAreNotDoubleCounted() {
        WynnDataSet data = data();
        WynnBuild build = build(106);
        build.setEquipment(EquipmentSlot.RING2, new BuildEquipment.Normal(4));

        BuildStats stats = compute(build, data);

        assertEquals(0, stats.assignedSkillPoints()[0], "nothing requires strength here");
        assertEquals(30, stats.skillPointTotals()[0], "the ring's +30 counted exactly once");
    }

    @Test
    void manualAssignmentReplacesTheSolvedValue() {
        WynnDataSet data = data();
        WynnBuild build = build(106);
        build.setEquipment(EquipmentSlot.RING1, new BuildEquipment.Normal(3));
        build.setAssignedSkillPoint(0, 80);

        BuildStats stats = compute(build, data);

        assertEquals(80, stats.assignedSkillPoints()[0]);
        assertEquals(80, stats.skillPointTotals()[0]);
    }

    @Test
    void theLevelBudgetIsReported() {
        BuildStats stats = compute(build(106), data());
        assertEquals(200, stats.availableSkillPoints());

        BuildStats lowLevel = compute(build(50), data());
        assertEquals(98, lowLevel.availableSkillPoints());
    }

    @Test
    void identificationsFromEveryPieceAreSummed() {
        WynnDataSet data = data();
        WynnBuild build = build(106);
        build.setEquipment(EquipmentSlot.WEAPON, new BuildEquipment.Normal(5));

        BuildStats stats = compute(build, data);

        assertEquals(20, stats.identification("sdPct"));
    }

    @Test
    void anItemAboveTheBuildLevelIsReportedAsAProblem() {
        WynnDataSet data = data();
        WynnBuild build = build(50);
        build.setEquipment(EquipmentSlot.CHESTPLATE, new BuildEquipment.Normal(1));

        BuildStats stats = compute(build, data);

        assertTrue(stats.problems().stream().anyMatch(problem -> problem.contains("level 100")));
    }

    @Test
    void setBonusesApplyOnceEnoughPiecesAreWorn() {
        WynnDataSet data = data();
        WynnBuild build = build(106);
        build.setEquipment(EquipmentSlot.HELMET, new BuildEquipment.Normal(7));

        // One piece grants nothing.
        BuildStats one = compute(build, data);
        assertEquals(0, one.identification("sdPct"));
        assertTrue(one.activeSets().isEmpty());

        build.setEquipment(EquipmentSlot.BOOTS, new BuildEquipment.Normal(8));
        BuildStats two = compute(build, data);

        assertEquals(40, two.identification("sdPct"));
        assertEquals(2, two.activeSets().get("Trio"));
        // The set's health bonus counts towards health like any other.
        assertEquals(one.health() + 700, two.health());
    }

    @Test
    void rollModeChangesRolledStatsButNotFixedOnes() {
        WynnDataSet data = data();
        WynnBuild build = build(106);
        build.setEquipment(EquipmentSlot.RING1, new BuildEquipment.Normal(6));

        int worst = BuildStats.compute(build, data, IdentificationRolls.RollMode.WORST, Map.of()).health();
        int best = BuildStats.compute(build, data, IdentificationRolls.RollMode.BEST, Map.of()).health();

        assertTrue(best > worst, "a rolled health bonus must vary with the roll mode");
    }
}
