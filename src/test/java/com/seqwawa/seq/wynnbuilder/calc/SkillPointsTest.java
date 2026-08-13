package com.seqwawa.seq.wynnbuilder.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SkillPointsTest {

    private static SkillPoints.Item item(int[] requirements, int[] bonuses) {
        return new SkillPoints.Item(requirements, bonuses, false);
    }

    private static int[] skills(int strength, int dexterity, int intelligence, int defence, int agility) {
        return new int[] {strength, dexterity, intelligence, defence, agility};
    }

    private static List<SkillPoints.Item> pad(List<SkillPoints.Item> items) {
        List<SkillPoints.Item> padded = new java.util.ArrayList<>(items);
        while (padded.size() < 8) {
            padded.add(SkillPoints.Item.empty());
        }
        return padded;
    }

    @Test
    void curveIsZeroBelowOneAndFlatAtTheCap() {
        assertEquals(0.0, SkillPoints.rawPercentage(0));
        assertEquals(0.0, SkillPoints.rawPercentage(-5));
        assertEquals(SkillPoints.rawPercentage(150), SkillPoints.rawPercentage(200));
        assertTrue(SkillPoints.rawPercentage(50) > SkillPoints.rawPercentage(20));
    }

    @Test
    void curveHasDiminishingReturns() {
        double firstTwenty = SkillPoints.rawPercentage(20) - SkillPoints.rawPercentage(0);
        double lastTwenty = SkillPoints.rawPercentage(150) - SkillPoints.rawPercentage(130);
        assertTrue(firstTwenty > lastTwenty, "early skill points must be worth more than late ones");
    }

    @Test
    void levelBudgetMatchesTheGame() {
        assertEquals(0, SkillPoints.levelToSkillPoints(1));
        assertEquals(2, SkillPoints.levelToSkillPoints(2));
        assertEquals(200, SkillPoints.levelToSkillPoints(101));
        assertEquals(200, SkillPoints.levelToSkillPoints(106), "the budget stops growing past 101");
        assertEquals(0, SkillPoints.levelToSkillPoints(0));
    }

    @Test
    void baseHealthFollowsLevel() {
        assertEquals(10, SkillPoints.levelToBaseHealth(1));
        assertEquals(535, SkillPoints.levelToBaseHealth(106));
        assertEquals(SkillPoints.levelToBaseHealth(121), SkillPoints.levelToBaseHealth(200));
    }

    @Test
    void requirementsAreMetByAssigningPoints() {
        SkillPoints.Allocation allocation = SkillPoints.allocate(
                pad(List.of(item(skills(40, 0, 0, 0, 0), skills(0, 0, 0, 0, 0)))),
                SkillPoints.Item.empty());

        assertEquals(40, allocation.assigned()[0]);
        assertEquals(40, allocation.totalAssigned());
        assertTrue(allocation.valid());
    }

    @Test
    void itemBonusesPayForLaterRequirements() {
        // A ring granting +30 Strength should cover a boot requiring 30 Strength if worn first,
        // which is the whole point of solving for equip order.
        SkillPoints.Item ring = item(skills(0, 0, 0, 0, 0), skills(30, 0, 0, 0, 0));
        SkillPoints.Item boots = item(skills(30, 0, 0, 0, 0), skills(0, 0, 0, 0, 0));

        SkillPoints.Allocation allocation =
                SkillPoints.allocate(pad(List.of(boots, ring)), SkillPoints.Item.empty());

        assertEquals(0, allocation.totalAssigned(), "the ring's bonus should cover the boots");
    }

    @Test
    void anItemCannotPayForItsOwnRequirement() {
        // Granting +20 Strength while requiring 20 Strength still needs the points assigned.
        SkillPoints.Item selfish = item(skills(20, 0, 0, 0, 0), skills(20, 0, 0, 0, 0));

        SkillPoints.Allocation allocation =
                SkillPoints.allocate(pad(List.of(selfish)), SkillPoints.Item.empty());

        assertEquals(20, allocation.assigned()[0]);
    }

    @Test
    void weaponRequirementsAreIncluded() {
        SkillPoints.Allocation allocation = SkillPoints.allocate(
                pad(List.of()), item(skills(0, 25, 0, 0, 0), skills(0, 0, 0, 0, 0)));

        assertEquals(25, allocation.assigned()[1]);
    }

    @Test
    void multipleRequirementsAcrossElementsAccumulate() {
        SkillPoints.Allocation allocation = SkillPoints.allocate(
                pad(List.of(
                        item(skills(30, 0, 0, 0, 0), skills(0, 0, 0, 0, 0)),
                        item(skills(0, 0, 40, 0, 0), skills(0, 0, 0, 0, 0)))),
                item(skills(0, 0, 0, 20, 0), skills(0, 0, 0, 0, 0)));

        assertEquals(30, allocation.assigned()[0]);
        assertEquals(40, allocation.assigned()[2]);
        assertEquals(20, allocation.assigned()[3]);
        assertEquals(90, allocation.totalAssigned());
    }

    @Test
    void chainedBonusesResolveInTheCheapestOrder() {
        // A grants +20 dex, B needs 20 dex and grants +20 int, C needs 20 int.
        // Worn A, B, C nothing needs assigning; any other order costs points.
        SkillPoints.Item a = item(skills(0, 0, 0, 0, 0), skills(0, 20, 0, 0, 0));
        SkillPoints.Item b = item(skills(0, 20, 0, 0, 0), skills(0, 0, 20, 0, 0));
        SkillPoints.Item c = item(skills(0, 0, 20, 0, 0), skills(0, 0, 0, 0, 0));

        SkillPoints.Allocation allocation =
                SkillPoints.allocate(pad(List.of(c, b, a)), SkillPoints.Item.empty());

        assertEquals(0, allocation.totalAssigned());
    }

    @Test
    void craftedItemsCannotContributeTheirOwnSkillPoints() {
        SkillPoints.Item crafted = new SkillPoints.Item(skills(0, 0, 0, 0, 0), skills(30, 0, 0, 0, 0), true);
        SkillPoints.Item needsStrength = item(skills(30, 0, 0, 0, 0), skills(0, 0, 0, 0, 0));

        SkillPoints.Allocation allocation =
                SkillPoints.allocate(pad(List.of(crafted, needsStrength)), SkillPoints.Item.empty());

        assertEquals(30, allocation.assigned()[0], "a crafted item's bonuses do not help meet requirements");
    }

    @Test
    void budgetCheckUsesTheLevelAllowance() {
        assertTrue(SkillPoints.fitsLevelBudget(skills(50, 50, 50, 0, 0), 106));
        assertTrue(!SkillPoints.fitsLevelBudget(skills(100, 100, 100, 0, 0), 106));
    }
}
