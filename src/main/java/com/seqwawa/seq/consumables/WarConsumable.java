package com.seqwawa.seq.consumables;

import java.util.Collection;
import java.util.TreeSet;

/**
 * A crafted potion, food or scroll carrying stats, as the war consumable tracker sees it.
 *
 * @param fingerprint identifies the consumable independently of its remaining charges and
 *     rolled values, so every copy of one consumable shares it
 * @param uses charges left
 * @param durationSeconds how long its effect lasts, or 0 when unknown
 * @param positiveStats stats Wynncraft shows as a bonus
 * @param negativeStats stats Wynncraft shows as a penalty
 */
public record WarConsumable(
        String fingerprint, int uses, int durationSeconds, int positiveStats, int negativeStats) {

    /**
     * Identifies a consumable by what it affects rather than by how much: crafting rolls each
     * stat within a range, so copies of one recipe carry different values, yet Wynncraft treats
     * them as the same consumable.
     */
    public static String fingerprint(String type, String name, Collection<String> affectedStats) {
        return type + '|' + name + '|' + String.join("|", new TreeSet<>(affectedStats));
    }

    /**
     * Whether a stat line is a bonus (1), a penalty (-1) or neither (0), following the
     * green/red coloring Wynncraft gives it: some stats, such as spell costs, are stored
     * negated and are good when their shown value is negative.
     */
    public static int statPolarity(int value, boolean calculateAsInverted, boolean displayAsInverted) {
        int shownValue = calculateAsInverted ? -value : value;
        if (shownValue == 0) {
            return 0;
        }
        return (shownValue > 0) != displayAsInverted ? 1 : -1;
    }
}
