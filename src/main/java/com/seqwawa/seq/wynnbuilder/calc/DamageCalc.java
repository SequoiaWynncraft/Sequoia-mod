package com.seqwawa.seq.wynnbuilder.calc;

import com.seqwawa.seq.wynnbuilder.data.Identifications;
import com.seqwawa.seq.wynnbuilder.data.Powder;
import java.util.List;
import java.util.Map;

/**
 * The damage pipeline: turns a weapon, the build's stats and a set of damage multipliers into the
 * numbers a hit actually deals.
 *
 * <p>Order matters throughout, because each stage feeds the next: the weapon's damage is converted
 * by powders, scaled by attack speed, given flat additions, boosted by percentages, given raw
 * additions distributed across the elements, and finally multiplied.
 *
 * <p>Each element is boosted by its own skill: strength drives earth, dexterity thunder,
 * intelligence water, defence fire and agility air.
 */
public final class DamageCalc {

    /** Neutral first, then earth, thunder, water, fire, air. */
    public static final int ELEMENTS = 6;

    /** Damage multiplier per attack speed, slowest to fastest. */
    private static final Map<String, Double> ATTACK_SPEED_MULTIPLIER = Map.of(
            "SUPER_SLOW", 0.51,
            "VERY_SLOW", 0.83,
            "SLOW", 1.5,
            "NORMAL", 2.05,
            "FAST", 2.5,
            "VERY_FAST", 3.1,
            "SUPER_FAST", 4.3);

    /** Attacks per second per attack speed, used to turn a hit into a rate. */
    private static final Map<String, Double> ATTACKS_PER_SECOND = Map.of(
            "SUPER_SLOW", 0.51,
            "VERY_SLOW", 0.83,
            "SLOW", 1.5,
            "NORMAL", 2.05,
            "FAST", 2.5,
            "VERY_FAST", 3.1,
            "SUPER_FAST", 4.3);

    private DamageCalc() {}

    /**
     * The weapon a build attacks with.
     *
     * @param attackSpeed the weapon's own speed, which is what scales its damage
     * @param attackTier the build's {@code atkTier} bonus, which changes how often it swings but
     *     deliberately not how hard: the damage multiplier reads the weapon's printed speed, and
     *     only the rate and the displayed speed move along the ladder
     */
    public record Weapon(int[][] damages, String attackSpeed, List<Powder> powders, int attackTier) {
        /** Damage ranges in n/e/t/w/f/a order; each entry is {@code {min, max}}. */
        public Weapon {
            damages = damages.clone();
        }

        public Weapon(int[][] damages, String attackSpeed, List<Powder> powders) {
            this(damages, attackSpeed, powders, 0);
        }

        /** The speed the weapon actually swings at, tier bonus included. */
        public String effectiveAttackSpeed() {
            return shiftAttackSpeed(attackSpeed, attackTier);
        }
    }

    /**
     * The outcome of one damage calculation.
     *
     * @param perElementNormal per-element {@code {min, max}} before criticals, in n/e/t/w/f/a order
     * @param perElementCrit the same on a critical hit
     */
    public record Result(
            double normalMin, double normalMax, double critMin, double critMax,
            double[][] perElementNormal, double[][] perElementCrit, double[] conversions) {

        public Result(double normalMin, double normalMax, double critMin, double critMax) {
            this(normalMin, normalMax, critMin, critMax,
                    new double[ELEMENTS][2], new double[ELEMENTS][2], new double[ELEMENTS]);
        }

        /** The combined share across every element, which is the figure a part is labelled with. */
        public double totalConversion() {
            double total = 0;
            for (double conversion : conversions) {
                total += conversion;
            }
            return total;
        }
        public double averageNormal() {
            return (normalMin + normalMax) / 2;
        }

        public double averageCrit() {
            return (critMin + critMax) / 2;
        }

        /**
         * The expected hit, blending normal and critical.
         *
         * <p>Critical chance comes from dexterity, so it is supplied by the caller rather than
         * assumed.
         */
        public double expected(double critChance) {
            return averageNormal() * (1 - critChance) + averageCrit() * critChance;
        }
    }

    public static double attackSpeedMultiplier(String attackSpeed) {
        return ATTACK_SPEED_MULTIPLIER.getOrDefault(normalise(attackSpeed), 2.05);
    }

    public static double attacksPerSecond(String attackSpeed) {
        return ATTACKS_PER_SECOND.getOrDefault(normalise(attackSpeed), 2.05);
    }

    private static String normalise(String attackSpeed) {
        return attackSpeed == null ? "NORMAL" : attackSpeed.trim().toUpperCase(java.util.Locale.ROOT);
    }

    /** Attack speeds slowest to fastest, which is the ladder an attack tier bonus walks. */
    private static final List<String> ATTACK_SPEEDS = List.of(
            "SUPER_SLOW", "VERY_SLOW", "SLOW", "NORMAL", "FAST", "VERY_FAST", "SUPER_FAST");

    /**
     * The speed a weapon really swings at once the build's attack tier bonus is counted.
     *
     * <p>{@code atkTier} moves the weapon along the ladder rather than scaling anything, and the
     * ends are clamped: a normal weapon at three tiers down is super slow, not off the table. Both
     * the damage multiplier and the attack rate read the shifted speed, so a slower weapon hits
     * harder but less often, exactly as the game plays it.
     */
    public static String shiftAttackSpeed(String attackSpeed, int tiers) {
        int index = ATTACK_SPEEDS.indexOf(normalise(attackSpeed));
        if (index < 0) {
            return attackSpeed;
        }
        return ATTACK_SPEEDS.get(Math.clamp(index + tiers, 0, ATTACK_SPEEDS.size() - 1));
    }

    /** Critical hit chance, which dexterity alone determines. */
    public static double critChance(BuildStats stats) {
        return SkillPoints.rawPercentage(stats.skillPointTotals()[1]);
    }

    /**
     * Calculates the damage of one attack or spell part.
     *
     * @param multipliers the share of weapon damage this part deals per element, as percentages
     * @param useSpellDamage whether spell or melee damage stats apply
     * @param ignoreAttackSpeed true for a single melee hit, where the rate is applied separately
     * @param useStrength false for parts that ignore the strength bonus, such as self damage
     */
    public static Result calculate(
            BuildStats stats,
            Weapon weapon,
            double[] multipliers,
            boolean useSpellDamage,
            boolean ignoreAttackSpeed,
            boolean useStrength) {
        return calculate(stats, weapon, multipliers, useSpellDamage, ignoreAttackSpeed, useStrength, null);
    }

    /**
     * As above, for a named spell part.
     *
     * @param partId identifies the part so multipliers scoped to it apply and others do not; see
     *     {@link DamageMultipliers#partId}
     */
    public static Result calculate(
            BuildStats stats,
            Weapon weapon,
            double[] multipliers,
            boolean useSpellDamage,
            boolean ignoreAttackSpeed,
            boolean useStrength,
            String partId) {

        // 1. The weapon's own damage, and which elements it actually deals.
        double[][] damages = new double[ELEMENTS][2];
        boolean[] present = new boolean[ELEMENTS];
        double weaponMin = 0;
        double weaponMax = 0;
        for (int i = 0; i < ELEMENTS; i++) {
            weaponMin += weapon.damages()[i][0];
            weaponMax += weapon.damages()[i][1];
            present[i] = weapon.damages()[i][0] != 0 || weapon.damages()[i][1] != 0;
        }

        // 2. Conversions are the part's own multipliers plus any conversion an ability grants.
        // Powders are deliberately absent: they belong to the weapon's damage, which arrives here
        // already converted. Folding them in here would zero the neutral multiplier and, with it,
        // the weapon's own elemental damage.
        double[] conversions = multipliers.clone();
        for (int i = 0; i < ELEMENTS; i++) {
            String prefix = Identifications.ELEMENT_PREFIXES.get(i);
            conversions[i] += stats.identification(prefix + "ConvBase");
            if (partId != null) {
                conversions[i] += stats.identification(prefix + "ConvBase:" + partId);
            }
        }

        // 2.1 Neutral keeps its share of the weapon's own spread.
        double neutralConvert = conversions[0] / 100.0;
        if (neutralConvert == 0) {
            java.util.Arrays.fill(present, false);
        }
        for (int i = 0; i < ELEMENTS; i++) {
            damages[i][0] = weapon.damages()[i][0] * neutralConvert;
            damages[i][1] = weapon.damages()[i][1] * neutralConvert;
        }

        // 2.2 Each element takes its share of the whole weapon.
        double totalConvert = neutralConvert;
        for (int i = 1; i < ELEMENTS; i++) {
            if (conversions[i] > 0) {
                double fraction = conversions[i] / 100.0;
                damages[i][0] += fraction * weaponMin;
                damages[i][1] += fraction * weaponMax;
                present[i] = true;
                totalConvert += fraction;
            }
        }

        // 3. Attack speed, skipped for a single melee hit.
        if (!ignoreAttackSpeed) {
            double speed = attackSpeedMultiplier(weapon.attackSpeed());
            for (int i = 0; i < ELEMENTS; i++) {
                damages[i][0] *= speed;
                damages[i][1] *= speed;
            }
        }

        // 4. Flat damage the abilities add, only to elements the attack actually deals.
        for (int i = 0; i < ELEMENTS; i++) {
            if (present[i]) {
                String prefix = Identifications.ELEMENT_PREFIXES.get(i);
                damages[i][0] += stats.identification(prefix + "DamAddMin");
                damages[i][1] += stats.identification(prefix + "DamAddMax");
            }
        }

        // 5. Percentage boosts.
        String boost = useSpellDamage ? "Sd" : "Md";
        double[] skillBoost = new double[ELEMENTS];
        for (int i = 1; i < ELEMENTS; i++) {
            // Element i is boosted by skill i - 1: earth by strength, thunder by dexterity, and so on.
            skillBoost[i] = SkillPoints.damagePercentage(i - 1, stats.skillPointTotals()[i - 1]);
        }
        double staticBoost =
                (stats.identification(boost.toLowerCase(java.util.Locale.ROOT) + "Pct")
                        + stats.identification("damPct")) / 100.0;

        double[][] beforeBoost = new double[ELEMENTS][2];
        double totalMin = 0;
        double totalMax = 0;
        for (int i = 0; i < ELEMENTS; i++) {
            beforeBoost[i][0] = damages[i][0];
            beforeBoost[i][1] = damages[i][1];
            totalMin += damages[i][0];
            totalMax += damages[i][1];
        }

        for (int i = 0; i < ELEMENTS; i++) {
            String prefix = Identifications.ELEMENT_PREFIXES.get(i);
            double damageBoost = 1 + skillBoost[i] + staticBoost
                    + (stats.identification(prefix + boost + "Pct")
                            + stats.identification(prefix + "DamPct")) / 100.0;
            if (i > 0) {
                // Rainbow bonuses apply to every element except neutral.
                damageBoost += (stats.identification("r" + boost + "Pct")
                        + stats.identification("rDamPct")) / 100.0;
            }
            damages[i][0] *= damageBoost;
            damages[i][1] *= damageBoost;
        }

        // 6. Raw damage, shared out in proportion to what each element already contributes.
        double totalElementMin = totalMin - beforeBoost[0][0];
        double totalElementMax = totalMax - beforeBoost[0][1];
        double proportionalRaw = stats.identification(boost.toLowerCase(java.util.Locale.ROOT) + "Raw")
                + stats.identification("damRaw");
        double rainbowRaw = stats.identification("r" + boost + "Raw") + stats.identification("rDamRaw");

        for (int i = 0; i < ELEMENTS; i++) {
            String prefix = Identifications.ELEMENT_PREFIXES.get(i);
            double rawBoost = present[i]
                    ? stats.identification(prefix + boost + "Raw") + stats.identification(prefix + "DamRaw")
                    : 0;
            double minBoost = rawBoost;
            double maxBoost = rawBoost;
            if (totalMax > 0) {
                minBoost += (totalMin == 0 ? beforeBoost[i][1] / totalMax : beforeBoost[i][0] / totalMin)
                        * proportionalRaw;
                maxBoost += (beforeBoost[i][1] / totalMax) * proportionalRaw;
            }
            if (i != 0 && totalElementMax > 0) {
                minBoost += (totalElementMin == 0
                                ? beforeBoost[i][1] / totalElementMax
                                : beforeBoost[i][0] / totalElementMin)
                        * rainbowRaw;
                maxBoost += (beforeBoost[i][1] / totalElementMax) * rainbowRaw;
            }
            damages[i][0] += minBoost * totalConvert;
            damages[i][1] += maxBoost * totalConvert;
        }

        // 7. The multiplicative bonuses abilities grant, scoped to this part.
        DamageMultipliers.Resolved bonuses =
                DamageMultipliers.damage(stats.identifications(), partId, useSpellDamage);
        // The share each element ends up carrying, once powders have converted damage and the
        // multipliers have been applied. This is what a part is labelled with, and it is why a
        // spell reading "100%" in the ability data can show as several hundred percent.
        double[] finalConversions = new double[ELEMENTS];
        for (int i = 0; i < ELEMENTS; i++) {
            damages[i][0] *= bonuses.perElement()[i];
            damages[i][1] *= bonuses.perElement()[i];
            finalConversions[i] = conversions[i] * bonuses.perElement()[i] * bonuses.global();
        }

        // 8. Strength scales everything, and criticals add their own multiplier on top.
        double strengthBoost = useStrength ? 1 + SkillPoints.damagePercentage(0, stats.skillPointTotals()[0]) : 1;
        double critMultiplier = useStrength ? 1 + stats.identification("critDamPct") / 100.0 : 0;

        double normalMin = 0;
        double normalMax = 0;
        double critMin = 0;
        double critMax = 0;
        double[][] perElementNormal = new double[ELEMENTS][2];
        double[][] perElementCrit = new double[ELEMENTS][2];
        for (int i = 0; i < ELEMENTS; i++) {
            double min = Math.max(0, damages[i][0]) * bonuses.global();
            double max = Math.max(0, damages[i][1]) * bonuses.global();
            perElementNormal[i][0] = min * strengthBoost;
            perElementNormal[i][1] = max * strengthBoost;
            perElementCrit[i][0] = min * (strengthBoost + critMultiplier);
            perElementCrit[i][1] = max * (strengthBoost + critMultiplier);
            normalMin += perElementNormal[i][0];
            normalMax += perElementNormal[i][1];
            critMin += perElementCrit[i][0];
            critMax += perElementCrit[i][1];
        }
        return new Result(normalMin, normalMax, critMin, critMax,
                perElementNormal, perElementCrit, finalConversions);
    }

    /** A plain melee hit: all neutral, no spell scaling. */
    public static Result meleeHit(BuildStats stats, Weapon weapon) {
        return calculate(stats, weapon, fullNeutral(), false, true, true);
    }

    /** Sustained melee damage per second, which is the single hit times the attack rate. */
    public static double meleeDps(BuildStats stats, Weapon weapon) {
        Result hit = meleeHit(stats, weapon);
        return hit.expected(critChance(stats)) * attacksPerSecond(weapon.effectiveAttackSpeed());
    }

    /** The multipliers for an attack that deals the weapon's damage unchanged. */
    public static double[] fullNeutral() {
        double[] multipliers = new double[ELEMENTS];
        multipliers[0] = 100;
        return multipliers;
    }
}
