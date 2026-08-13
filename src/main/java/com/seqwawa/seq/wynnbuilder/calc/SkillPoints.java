package com.seqwawa.seq.wynnbuilder.calc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Skill point maths: the diminishing-returns curve, the level budget, and equip-order allocation.
 *
 * <p>Allocation is an optimisation problem rather than a sum. Items grant skill points as well as
 * requiring them, so the order pieces are equipped changes how many points must be spent manually:
 * equipping a ring that grants +10 Dexterity first can pay for a later item's Dexterity requirement.
 * The goal is the ordering that needs the fewest assigned points.
 */
public final class SkillPoints {
    /** Number of skill point types: strength, dexterity, intelligence, defence, agility. */
    public static final int TYPES = 5;

    /** The curve flattens completely at 150 points. */
    public static final int MAX_EFFECTIVE = 150;

    /** A single point of manual assignment cannot exceed this without the build being unusual. */
    public static final int SOFT_CAP = 100;

    private static final double RATIO = 0.9908;

    /**
     * Per-skill multipliers applied to the converted percentage.
     *
     * <p>Intelligence is spell cost reduction rather than damage, so it is normalised against the
     * value of the curve at its cap; defence and agility carry their own weightings.
     */
    private static final double[] FINAL_MULTIPLIER = {
        1, 1, 0.5 / rawPercentage(MAX_EFFECTIVE), 0.867, 0.951
    };

    /** Multipliers used when the skill contributes to damage rather than its own effect. */
    private static final double[] DAMAGE_MULTIPLIER = {1, 1, 1, 0.867, 0.951};

    private SkillPoints() {}

    /** An item as far as skill point allocation is concerned. */
    public record Item(int[] requirements, int[] bonuses, boolean crafted) {
        public Item {
            requirements = requirements.clone();
            bonuses = bonuses.clone();
        }

        public static Item empty() {
            return new Item(new int[TYPES], new int[TYPES], false);
        }
    }

    /** The outcome of an allocation: how many points to assign and what the totals become. */
    public record Allocation(int[] assigned, int[] totals, int totalAssigned, boolean valid, List<Integer> equipOrder) {
        public Allocation {
            assigned = assigned.clone();
            totals = totals.clone();
            equipOrder = List.copyOf(equipOrder);
        }
    }

    /**
     * Converts a skill point count into its effect fraction.
     *
     * <p>Strongly diminishing: the first points are worth far more than the last, and nothing is
     * gained past {@value #MAX_EFFECTIVE}.
     */
    public static double rawPercentage(int skillPoints) {
        if (skillPoints <= 0) {
            return 0.0;
        }
        int clamped = Math.min(skillPoints, MAX_EFFECTIVE);
        return (RATIO / (1 - RATIO) * (1 - Math.pow(RATIO, clamped))) / 100.0;
    }

    /** The effect fraction for a skill, including that skill's own weighting. */
    public static double percentage(int skillIndex, int skillPoints) {
        return rawPercentage(skillPoints) * FINAL_MULTIPLIER[skillIndex];
    }

    /** The damage contribution fraction for a skill. */
    public static double damagePercentage(int skillIndex, int skillPoints) {
        return rawPercentage(skillPoints) * DAMAGE_MULTIPLIER[skillIndex];
    }

    /** Skill points granted by reaching a level. Caps at 200 from level 101. */
    public static int levelToSkillPoints(int level) {
        if (level < 1) {
            return 0;
        }
        return level >= 101 ? 200 : (level - 1) * 2;
    }

    /** Base health for a level, before any item bonuses. */
    public static int levelToBaseHealth(int level) {
        int clamped = Math.max(1, Math.min(level, 121));
        return 5 * clamped + 5;
    }

    /**
     * Finds the equip order needing the fewest manually assigned skill points.
     *
     * <p>The weapon is always equipped last. With eight remaining slots the search space is 8!, small
     * enough to explore exhaustively with pruning, which avoids the redundant-ordering bookkeeping a
     * heuristic would need and always returns a true optimum.
     *
     * @param equipment the eight non-weapon pieces
     * @param weapon the weapon, equipped last
     */
    public static Allocation allocate(List<Item> equipment, Item weapon) {
        int count = equipment.size();
        Search search = new Search(equipment, weapon);
        search.explore(new int[TYPES], new int[TYPES], 0, new boolean[count], new ArrayList<>());
        if (search.bestAssigned == null) {
            return new Allocation(new int[TYPES], new int[TYPES], 0, false, List.of());
        }
        return new Allocation(
                search.bestAssigned,
                search.bestTotals,
                search.bestTotal,
                search.bestUnderCap,
                search.bestOrder);
    }

    private static final class Search {
        private final List<Item> equipment;
        private final Item weapon;

        private int[] bestAssigned;
        private int[] bestTotals;
        private List<Integer> bestOrder = List.of();
        private int bestTotal = Integer.MAX_VALUE;
        private boolean bestUnderCap;

        private Search(List<Item> equipment, Item weapon) {
            this.equipment = equipment;
            this.weapon = weapon;
        }

        private void explore(int[] assigned, int[] totals, int totalAssigned, boolean[] used, List<Integer> order) {
            // A partial order already needing more than the best complete one cannot win.
            if (totalAssigned > bestTotal && bestUnderCap) {
                return;
            }
            if (order.size() == equipment.size()) {
                finish(assigned, totals, totalAssigned, order);
                return;
            }
            for (int i = 0; i < equipment.size(); i++) {
                if (used[i]) {
                    continue;
                }
                Item item = equipment.get(i);
                int[] nextAssigned = assigned.clone();
                int[] nextTotals = totals.clone();
                int spent = applyToFit(nextTotals, nextAssigned, item);
                // A crafted item's own skill point bonuses do not help meet requirements.
                if (!item.crafted()) {
                    for (int skill = 0; skill < TYPES; skill++) {
                        nextTotals[skill] += item.bonuses()[skill];
                    }
                }
                used[i] = true;
                order.add(i);
                explore(nextAssigned, nextTotals, totalAssigned + spent, used, order);
                order.remove(order.size() - 1);
                used[i] = false;
            }
        }

        private void finish(int[] assigned, int[] totals, int totalAssigned, List<Integer> order) {
            int[] finalAssigned = assigned.clone();
            int[] finalTotals = totals.clone();
            int spent = totalAssigned + applyToFit(finalTotals, finalAssigned, weapon);

            // Removing a piece must not drop another below its requirement, so top up anything that
            // would "pop" once every bonus is accounted for.
            for (Item item : equipment) {
                spent += fixShouldPop(finalTotals, finalAssigned, item);
            }

            boolean underCap = underSoftCap(finalAssigned);
            // A build that stays under the cap always beats one that does not, however cheap.
            if (bestAssigned != null) {
                if (bestUnderCap && !underCap) {
                    return;
                }
                if (underCap == bestUnderCap && spent >= bestTotal) {
                    return;
                }
            }
            // A crafted piece's own skill points, and the weapon's, land only once the order is
            // settled: they cannot help satisfy a requirement, since the search must not rely on
            // gear it has not equipped yet, but they do count towards what the build ends up with.
            // Leaving them out here loses them entirely, and with them the damage every point of
            // strength, dexterity and intelligence is worth.
            for (Item item : equipment) {
                if (item.crafted()) {
                    for (int skill = 0; skill < TYPES; skill++) {
                        finalTotals[skill] += item.bonuses()[skill];
                    }
                }
            }
            for (int skill = 0; skill < TYPES; skill++) {
                finalTotals[skill] += weapon.bonuses()[skill];
            }

            bestAssigned = finalAssigned;
            bestTotals = finalTotals;
            bestTotal = spent;
            bestUnderCap = underCap;
            bestOrder = new ArrayList<>(order);
        }
    }

    /** Raises totals to meet an item's requirements, recording what had to be assigned. */
    private static int applyToFit(int[] totals, int[] assigned, Item item) {
        int spent = 0;
        for (int skill = 0; skill < TYPES; skill++) {
            int requirement = item.requirements()[skill];
            if (requirement <= 0) {
                continue;
            }
            int missing = requirement - totals[skill];
            if (missing > 0) {
                totals[skill] += missing;
                assigned[skill] += missing;
                spent += missing;
            }
        }
        return spent;
    }

    /**
     * Ensures an already-equipped item still meets its requirement once its own bonus is discounted.
     *
     * <p>An item cannot pay for its own requirement, so the threshold to check against is the
     * requirement plus whatever that item grants.
     */
    private static int fixShouldPop(int[] totals, int[] assigned, Item item) {
        int spent = 0;
        for (int skill = 0; skill < TYPES; skill++) {
            int requirement = item.requirements()[skill];
            if (requirement <= 0) {
                continue;
            }
            int threshold = item.crafted() ? requirement : requirement + item.bonuses()[skill];
            int missing = threshold - totals[skill];
            if (missing > 0) {
                totals[skill] += missing;
                assigned[skill] += missing;
                spent += missing;
            }
        }
        return spent;
    }

    private static boolean underSoftCap(int[] assigned) {
        for (int value : assigned) {
            if (value > SOFT_CAP) {
                return false;
            }
        }
        return true;
    }

    /** Whether the assignment fits within the points a character of this level actually has. */
    public static boolean fitsLevelBudget(int[] assigned, int level) {
        return Arrays.stream(assigned).sum() <= levelToSkillPoints(level);
    }
}
