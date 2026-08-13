package com.seqwawa.seq.wynnbuilder.calc;

import com.seqwawa.seq.wynnbuilder.data.Powder;
import com.seqwawa.seq.wynnbuilder.data.Powder.PowderElement;
import java.util.List;

/**
 * Powder effects.
 *
 * <p>A powder does different things depending on where it is applied. On a weapon it converts part
 * of the neutral damage to its element and adds a flat damage range; on armour it adds health and
 * shifts elemental defences, raising its own element and lowering the one that opposes it.
 */
public final class PowderCalc {

    /** Per-element, per-tier statistics in element order E, T, W, F, A. */
    private static final int[][][] STATS = {
        // {damageMin, damageMax, conversionPercent, defenceBonus, defencePenalty}
        {{4, 5, 17, 2, 1}, {6, 7, 21, 5, 2}, {7, 9, 25, 9, 3}, {8, 9, 31, 14, 4}, {9, 11, 38, 22, 7}, {11, 12, 46, 29, 7}, {12, 14, 52, 37, 12}},
        {{1, 8, 9, 2, 1}, {1, 12, 11, 4, 1}, {2, 14, 13, 8, 2}, {2, 15, 17, 13, 3}, {3, 17, 22, 20, 5}, {4, 19, 28, 28, 6}, {5, 21, 32, 36, 11}},
        {{3, 4, 13, 3, 1}, {5, 6, 15, 6, 1}, {6, 8, 17, 11, 3}, {7, 8, 21, 16, 4}, {8, 10, 26, 23, 6}, {10, 13, 32, 32, 10}, {11, 15, 38, 40, 15}},
        {{2, 5, 14, 3, 1}, {4, 7, 16, 6, 1}, {5, 9, 19, 10, 2}, {6, 9, 24, 15, 3}, {7, 11, 30, 22, 5}, {9, 14, 37, 31, 9}, {10, 16, 44, 39, 14}},
        {{2, 6, 11, 3, 1}, {3, 9, 14, 6, 2}, {4, 11, 17, 10, 3}, {5, 11, 22, 16, 5}, {7, 12, 28, 23, 7}, {8, 15, 35, 30, 8}, {9, 17, 42, 38, 13}}
    };

    /** Health granted by an armour powder, the same for every element. */
    private static final int[] ARMOUR_HEALTH = {5, 10, 20, 30, 45, 60, 75};

    /** Minimum item level required to apply each powder tier. */
    private static final int[] LEVEL_REQUIREMENT = {1, 5, 15, 25, 40, 55, 70};

    public static final int MAX_TIER = 7;

    private PowderCalc() {}

    /** The result of applying powders to a weapon. */
    public record WeaponEffect(double[] conversions, int[] damageMin, int[] damageMax) {}

    /** The result of applying powders to an armour piece. */
    public record ArmourEffect(int health, int[] defences) {}

    private static int[] stats(Powder powder) {
        int tier = Math.max(1, Math.min(powder.tier(), MAX_TIER));
        return STATS[powder.elementIndex()][tier - 1];
    }

    public static int levelRequirement(int tier) {
        return LEVEL_REQUIREMENT[Math.max(1, Math.min(tier, MAX_TIER)) - 1];
    }

    /**
     * Applies a weapon's powders.
     *
     * <p>Conversions are applied in order and each one takes its share of what neutral damage
     * remains, so the total converted can never exceed the weapon's damage.
     *
     * @return conversion fractions and flat damage in n/e/t/w/f/a order
     */
    public static WeaponEffect applyToWeapon(List<Powder> powders) {
        // Index 0 is neutral; elements follow in e/t/w/f/a order.
        double[] conversions = new double[6];
        int[] damageMin = new int[6];
        int[] damageMax = new int[6];
        conversions[0] = 100.0;

        for (Powder powder : powders) {
            int[] stats = stats(powder);
            int elementIndex = powder.elementIndex() + 1;
            double conversion = stats[2];
            // A powder can only convert what is still neutral.
            double converted = Math.min(conversion, conversions[0]);
            conversions[0] -= converted;
            conversions[elementIndex] += converted;
            damageMin[elementIndex] += stats[0];
            damageMax[elementIndex] += stats[1];
        }
        return new WeaponEffect(conversions, damageMin, damageMax);
    }

    /**
     * Applies an armour piece's powders.
     *
     * <p>Each powder raises its own element's defence and lowers the element two positions later in
     * the cycle, which is the element it is weak against.
     *
     * @return added health and defence deltas in e/t/w/f/a order
     */
    public static ArmourEffect applyToArmour(List<Powder> powders) {
        int health = 0;
        int[] defences = new int[5];
        int elementCount = PowderElement.encodingOrder().size();

        for (Powder powder : powders) {
            int[] stats = stats(powder);
            int tier = Math.max(1, Math.min(powder.tier(), MAX_TIER));
            health += ARMOUR_HEALTH[tier - 1];
            int elementIndex = powder.elementIndex();
            defences[elementIndex] += stats[3];
            // The opposing element sits two steps along the E-T-W-F-A cycle.
            defences[(elementIndex + 2) % elementCount] -= stats[4];
        }
        return new ArmourEffect(health, defences);
    }

    /**
     * Applies powders to a weapon's damage.
     *
     * <p>Each powder converts a share of the remaining <em>neutral</em> damage into its own element
     * and adds a flat amount on top. This happens to the weapon itself, before any spell is
     * considered: a spell's multipliers scale whatever the weapon deals, they are not where the
     * conversion belongs.
     *
     * @param damages ranges in n/e/t/w/f/a order; not modified
     * @return a new array with the powders applied
     */
    public static int[][] applyToWeaponDamage(int[][] damages, List<Powder> powders) {
        int[][] result = new int[damages.length][2];
        for (int i = 0; i < damages.length; i++) {
            result[i][0] = damages[i][0];
            result[i][1] = damages[i][1];
        }
        // Powders of the same element merge before anything is converted: their conversion rates
        // add together and take their share of the neutral damage in one go, rather than each one
        // taking a share of what the previous one left behind. Three tier 7 water powders convert
        // 114% of the neutral damage, which is all of it; applied one after another they would
        // only ever reach 76%, leaving neutral damage that the real weapon does not have.
        java.util.Map<Integer, double[]> merged = new java.util.LinkedHashMap<>();
        for (Powder powder : powders) {
            int[] stats = stats(powder);
            double[] info = merged.computeIfAbsent(powder.elementIndex(), key -> new double[3]);
            info[0] += stats[2] / 100.0;
            info[1] += stats[0];
            info[2] += stats[1];
        }

        double neutralMin = result[0][0];
        double neutralMax = result[0][1];
        for (java.util.Map.Entry<Integer, double[]> entry : merged.entrySet()) {
            double[] info = entry.getValue();
            int element = entry.getKey() + 1;
            // A conversion can total more than 100%, which simply takes whatever neutral is left.
            double convertedMin = Math.min(neutralMin, info[0] * neutralMin);
            double convertedMax = Math.min(neutralMax, info[0] * neutralMax);
            neutralMin -= convertedMin;
            neutralMax -= convertedMax;
            result[element][0] += (int) Math.round(convertedMin + info[1]);
            result[element][1] += (int) Math.round(convertedMax + info[2]);
        }
        result[0][0] = (int) Math.round(neutralMin);
        result[0][1] = (int) Math.round(neutralMax);
        return result;
    }

    /** The highest powder tier an item of this level may carry, or 0 if none. */
    public static int maxTierForLevel(int itemLevel) {
        int best = 0;
        for (int tier = 1; tier <= MAX_TIER; tier++) {
            if (itemLevel >= LEVEL_REQUIREMENT[tier - 1]) {
                best = tier;
            }
        }
        return best;
    }
}
