package com.seqwawa.seq.integrations;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.wynnbuilder.data.Identifications;
import com.seqwawa.seq.wynnbuilder.live.LiveItem;
import com.seqwawa.seq.wynnbuilder.live.StatKeys;
import com.wynntils.models.stats.StatCalculator;
import com.wynntils.models.stats.type.SpellStatType;
import com.wynntils.models.stats.type.StatActualValue;
import com.wynntils.models.stats.type.StatPossibleValues;
import com.wynntils.models.stats.type.StatType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns Wynntils identifications into the calculator's, keeping both the roll and its ceiling.
 *
 * <p>Shared by gear and tomes because the shape is identical: a list of rolled values, a list of the
 * ranges they were drawn from, and a namespace to translate into.
 */
final class WynntilsStatRolls {

    private WynntilsStatRolls() {}

    /**
     * One item's identifications, as they rolled and as they could have.
     *
     * @param detail per-stat rolls, for explaining an audit verdict
     */
    record Rolls(Map<String, Integer> actual, Map<String, Integer> best, List<LiveItem.Roll> detail) {
        static Rolls empty() {
            return new Rolls(Map.of(), Map.of(), List.of());
        }
    }

    static Rolls read(List<StatActualValue> identifications, List<StatPossibleValues> possibleValues) {
        Map<String, Integer> actual = new LinkedHashMap<>();
        Map<String, Integer> best = new LinkedHashMap<>();
        List<LiveItem.Roll> detail = new ArrayList<>();
        if (identifications == null || identifications.isEmpty()) {
            return new Rolls(actual, best, detail);
        }
        Map<StatType, StatPossibleValues> possible = byType(possibleValues);

        for (StatActualValue stat : identifications) {
            StatType type = stat == null ? null : stat.statType();
            if (type == null) {
                continue;
            }
            String key = StatKeys.key(type.getKey(), spellNumber(type));
            if (key == null) {
                continue;
            }
            int sign = StatKeys.isNegated(type.getKey()) ? -1 : 1;
            int actualValue = sign * stat.value();

            StatPossibleValues values = possible.get(type);
            // Wynntils orders a range so its higher end is always the better roll, whatever the stat
            // means, so the ceiling is that end read back through the same sign.
            int bestValue = values == null ? actualValue : sign * values.range().high();

            actual.merge(key, actualValue, Integer::sum);
            best.merge(key, bestValue, Integer::sum);
            detail.add(new LiveItem.Roll(
                    key, Identifications.displayName(key), actualValue, bestValue, percentage(stat, values)));
        }
        return new Rolls(actual, best, detail);
    }

    private static float percentage(StatActualValue stat, StatPossibleValues values) {
        if (values == null) {
            // A stat with no range was never free to roll differently, so it counts as perfect
            // rather than dragging an item's audit down for a number it does not control.
            return 100f;
        }
        try {
            float percentage = StatCalculator.getPercentage(stat, values);
            return Float.isFinite(percentage) ? percentage : 100f;
        } catch (RuntimeException e) {
            SeqClient.LOGGER.debug("[Wynntils] Roll percentage could not be calculated", e);
            return 100f;
        }
    }

    private static int spellNumber(StatType type) {
        return type instanceof SpellStatType spell ? spell.getSpellType().getSpellNumber() : 0;
    }

    private static Map<StatType, StatPossibleValues> byType(List<StatPossibleValues> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<StatType, StatPossibleValues> byType = new HashMap<>();
        for (StatPossibleValues value : values) {
            if (value != null && value.statType() != null) {
                byType.putIfAbsent(value.statType(), value);
            }
        }
        return byType;
    }
}
