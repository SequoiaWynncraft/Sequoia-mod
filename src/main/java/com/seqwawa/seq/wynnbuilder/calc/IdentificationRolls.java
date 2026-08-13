package com.seqwawa.seq.wynnbuilder.calc;

import com.seqwawa.seq.wynnbuilder.data.Identifications;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns an item's base identification values into the range a real drop can roll.
 *
 * <p>A stat rolls between 30% and 130% of its base value in the direction that helps the player, and
 * between 130% and 70% in the direction that hurts. Items flagged {@code fixID} always roll exactly
 * their base value.
 *
 * <p>The two ends of a range are named {@code worst} and {@code best} rather than minimum and
 * maximum on purpose: they are ordered by how good the roll is, not numerically. For a drawback such
 * as {@code hpBonus -100} the best roll is {@code -70}, and for a spell cost reduction of
 * {@code -10} the best roll is {@code -13}. Treating either as a numeric minimum gets the sign
 * backwards.
 */
public final class IdentificationRolls {

    private static final double WORST_MULTIPLIER = 0.3;
    private static final double BEST_MULTIPLIER = 1.3;
    private static final double DRAWBACK_BEST_MULTIPLIER = 0.7;

    /** Skill point bonuses are granted verbatim and never roll. */
    private static final Set<String> NEVER_ROLLED = Set.of("str", "dex", "int", "def", "agi");

    private IdentificationRolls() {}

    /** Which end of a range to use when computing stats. */
    public enum RollMode {
        WORST("Worst"),
        AVERAGE("Average"),
        BEST("Best");

        private final String label;

        RollMode(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public RollMode next() {
            RollMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    /**
     * The range a stat can roll into, ordered by quality rather than by number.
     *
     * @param worst the least useful roll
     * @param best the most useful roll
     */
    public record Range(int worst, int best) {
        public boolean isFixed() {
            return worst == best;
        }

        /** The numerically smaller end, for display purposes. */
        public int lower() {
            return Math.min(worst, best);
        }

        /** The numerically larger end, for display purposes. */
        public int upper() {
            return Math.max(worst, best);
        }

        public int value(RollMode mode) {
            return switch (mode) {
                case WORST -> worst;
                case BEST -> best;
                case AVERAGE -> Math.round((worst + best) / 2.0f);
            };
        }
    }

    /**
     * Rounds a rolled value, never collapsing a non-zero stat to nothing.
     *
     * <p>A stat that rounded to zero would read as absent, so it is pushed out to ±1 instead.
     */
    public static int round(double value) {
        long rounded = Math.round(value);
        if (rounded == 0) {
            return value > 0 ? 1 : value < 0 ? -1 : 0;
        }
        return (int) rounded;
    }

    /** The range a single stat rolls into. */
    public static Range range(String key, int baseValue, boolean fixedIds) {
        if (fixedIds || NEVER_ROLLED.contains(key) || baseValue == 0) {
            return new Range(baseValue, baseValue);
        }
        // Spell cost stats are better when lower, which flips what counts as beneficial.
        boolean beneficial = (baseValue > 0) != Identifications.isInverted(key);
        if (beneficial) {
            return new Range(round(baseValue * WORST_MULTIPLIER), round(baseValue * BEST_MULTIPLIER));
        }
        return new Range(round(baseValue * BEST_MULTIPLIER), round(baseValue * DRAWBACK_BEST_MULTIPLIER));
    }

    /** Every rolled range for an item. */
    public static Map<String, Range> ranges(WynnItem item) {
        Map<String, Range> ranges = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : item.identifications().entrySet()) {
            ranges.put(entry.getKey(), range(entry.getKey(), entry.getValue(), item.fixedIds()));
        }
        return ranges;
    }

    /** An item's identifications resolved at a given roll mode. */
    public static Map<String, Integer> resolve(WynnItem item, RollMode mode) {
        Map<String, Integer> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> entry : item.identifications().entrySet()) {
            Range range = range(entry.getKey(), entry.getValue(), item.fixedIds());
            int value = range.value(mode);
            if (value != 0) {
                resolved.put(entry.getKey(), value);
            }
        }
        return resolved;
    }
}
