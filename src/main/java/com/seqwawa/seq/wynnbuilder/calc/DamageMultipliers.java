package com.seqwawa.seq.wynnbuilder.calc;

import com.seqwawa.seq.wynnbuilder.data.Identifications;
import java.util.Map;

/**
 * The multiplicative damage and healing bonuses abilities grant.
 *
 * <p>These arrive as namespaced stats and are the reason a spell's real number differs from a naive
 * sum of its parts. The key after the prefix says what the bonus applies to:
 *
 * <ul>
 *   <li>{@code damMult.Name} — every attack.
 *   <li>{@code damMult.Name:3.Part} — only that part of that spell, identified as
 *       {@code baseSpell.partName}.
 *   <li>{@code damMult.Name;e} — only that element; {@code ;m} means melee only.
 * </ul>
 *
 * <p>They multiply rather than add, so applying one to the wrong part inflates a spell noticeably.
 */
public final class DamageMultipliers {

    public static final String DAMAGE_PREFIX = "damMult.";
    public static final String HEAL_PREFIX = "healMult.";
    public static final String DEFENCE_PREFIX = "defMult.";

    private DamageMultipliers() {}

    /** The multipliers that apply to one damage calculation. */
    public record Resolved(double global, double[] perElement) {
        public Resolved {
            perElement = perElement.clone();
        }
    }

    /** Identifies a spell part the way the ability data does: {@code baseSpell.partName}. */
    public static String partId(int baseSpell, String partName) {
        return baseSpell + "." + partName;
    }

    /**
     * Collects the damage multipliers that apply.
     *
     * @param partId the part being calculated, or {@code null} for a plain attack; a bonus scoped to
     *     a different part is skipped
     * @param useSpellDamage whether this is a spell, which excludes the melee-only bonuses
     */
    public static Resolved damage(Map<String, Integer> stats, String partId, boolean useSpellDamage) {
        double global = 1;
        double[] perElement = new double[DamageCalc.ELEMENTS];
        java.util.Arrays.fill(perElement, 1);

        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            if (!entry.getKey().startsWith(DAMAGE_PREFIX)) {
                continue;
            }
            String key = entry.getKey().substring(DAMAGE_PREFIX.length());
            if (!appliesToPart(key, partId)) {
                continue;
            }
            double factor = 1 + entry.getValue() / 100.0;

            int elementMarker = key.indexOf(';');
            if (elementMarker < 0) {
                global *= factor;
                continue;
            }
            String scope = key.substring(elementMarker + 1);
            if ("m".equals(scope)) {
                // Melee-only bonuses do nothing to a spell.
                if (!useSpellDamage) {
                    global *= factor;
                }
                continue;
            }
            int element = Identifications.ELEMENT_PREFIXES.indexOf(scope);
            if (element >= 0) {
                perElement[element] *= factor;
            }
        }
        return new Resolved(global, perElement);
    }

    /** The healing multiplier that applies to a part, including healing efficiency. */
    public static double heal(Map<String, Integer> stats, String partId, int healingEfficiency) {
        double multiplier = 1;
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            if (!entry.getKey().startsWith(HEAL_PREFIX)) {
                continue;
            }
            String key = entry.getKey().substring(HEAL_PREFIX.length());
            if (appliesToPart(key, partId)) {
                multiplier *= 1 + entry.getValue() / 100.0;
            }
        }
        return multiplier * (1 + healingEfficiency / 100.0);
    }

    /** Incoming damage reduction, which stacks multiplicatively. */
    public static double defence(Map<String, Integer> stats) {
        double multiplier = 1;
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            if (entry.getKey().startsWith(DEFENCE_PREFIX)) {
                multiplier *= 1 - entry.getValue() / 100.0;
            }
        }
        return multiplier;
    }

    /**
     * Whether a scoped key applies to the part being calculated.
     *
     * <p>An unscoped key applies everywhere; a scoped one only to its own part.
     */
    private static boolean appliesToPart(String key, String partId) {
        int scopeMarker = key.indexOf(':');
        if (scopeMarker < 0) {
            return true;
        }
        String target = key.substring(scopeMarker + 1);
        // Strip a trailing element marker so "Name:3.Part;e" still matches part "3.Part".
        int elementMarker = target.indexOf(';');
        if (elementMarker >= 0) {
            target = target.substring(0, elementMarker);
        }
        return target.equals(partId);
    }
}
