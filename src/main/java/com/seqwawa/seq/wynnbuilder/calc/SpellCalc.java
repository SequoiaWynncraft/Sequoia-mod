package com.seqwawa.seq.wynnbuilder.calc;

/**
 * Spell economics: what a spell costs to cast, what it heals, and how long a build can sustain it.
 */
public final class SpellCalc {

    private SpellCalc() {}

    /** Mana regenerated per second by one point of mana regen, which ticks five times a second. */
    private static final double MANA_REGEN_TICKS_PER_SECOND = 5.0;

    /**
     * The mana a spell actually costs.
     *
     * <p>Percentage modifiers apply to the base cost, raw modifiers are added on top, and
     * intelligence then reduces the result — at its cap intelligence halves the cost. A spell always
     * costs at least one mana.
     *
     * @param spellIndex the spell number 1-4, which selects the matching cost stats
     */
    public static double cost(BuildStats stats, int spellIndex, int baseCost) {
        if (baseCost <= 0) {
            return 0;
        }
        int percent = stats.identification("spPct" + spellIndex);
        int raw = stats.identification("spRaw" + spellIndex);

        double cost = baseCost * (1 + percent / 100.0) + raw;
        double intelligenceReduction = SkillPoints.percentage(2, stats.skillPointTotals()[2]);
        cost *= 1 - intelligenceReduction;
        // Kept fractional: a spell's real cost is not a whole number of mana, and rounding it
        // up here would compound into the casts per second and the sustained damage.
        return Math.max(1, cost);
    }

    /**
     * The health a healing part restores.
     *
     * <p>Healing scales with maximum health rather than with weapon damage, so a tankier build heals
     * for more from the same spell.
     *
     * @param power the share of maximum health restored, where 1 is a full heal
     */
    public static double heal(BuildStats stats, double power, String partId) {
        double multiplier = DamageMultipliers.heal(
                stats.identifications(), partId, stats.identification("healPct"));
        return power * stats.health() * multiplier;
    }

    /** Mana regenerated per second from the mana regen stat. */
    public static double manaPerSecond(BuildStats stats) {
        return stats.identification("mr") / MANA_REGEN_TICKS_PER_SECOND;
    }

    /**
     * How many times a second a spell can be cast from mana regen alone.
     *
     * <p>Mana steal is deliberately excluded: it depends on hitting something, so it is not
     * sustain the build has on its own.
     */
    public static double castsPerSecond(BuildStats stats, double spellCost) {
        if (spellCost <= 0) {
            return 0;
        }
        return manaPerSecond(stats) / spellCost;
    }

    /** Sustained damage per second for a spell cast as often as mana allows. */
    public static double sustainedDps(BuildStats stats, int spellCost, double damagePerCast) {
        return castsPerSecond(stats, spellCost) * damagePerCast;
    }
}
