package com.seqwawa.seq.wynnbuilder.live;

import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.List;

/**
 * One piece the player is actually wearing, resolved down to numbers.
 *
 * <p>Two versions of the same item are kept. {@link #actual} carries the roll this drop really got,
 * which is what the build's statistics are computed from; {@link #best} carries the same item rolled
 * perfectly, which is what prices a bad roll. Feeding both through the identical pipeline is what
 * lets the audit answer "how much damage is this piece's roll costing" rather than guessing from
 * percentages.
 *
 * @param quality the item's overall roll, 0 to 100, or {@code null} when it has none — a crafted
 *     piece, or an item whose stats are all fixed
 * @param powders powder symbols for display only; their effect is already inside {@link #actual}
 */
public record LiveItem(
        EquipmentSlot slot,
        String name,
        String tier,
        boolean crafted,
        WynnItem actual,
        WynnItem best,
        Float quality,
        List<Roll> rolls,
        List<String> powders) {

    public LiveItem {
        rolls = List.copyOf(rolls);
        powders = List.copyOf(powders);
    }

    /**
     * One identification, as it rolled and as it could have.
     *
     * @param percentage where the roll landed in its range, 0 to 100, as Wynntils computes it
     */
    public record Roll(String key, String displayName, int actual, int best, float percentage) {
        /** Whether the stat can still be improved, which fixed and maxed stats cannot. */
        public boolean hasHeadroom() {
            return actual != best;
        }
    }

    public BuildEquipment toEquipment() {
        return new BuildEquipment.Live(actual, crafted, best);
    }

    /** The same piece as if every identification had rolled perfectly. */
    public BuildEquipment toBestEquipment() {
        return new BuildEquipment.Live(best, crafted, best);
    }

    /** Rolls that landed below their ceiling, worst first, for explaining an audit verdict. */
    public List<Roll> weakestRolls(int limit) {
        return rolls.stream()
                .filter(Roll::hasHeadroom)
                .sorted(java.util.Comparator.comparingDouble(Roll::percentage))
                .limit(limit)
                .toList();
    }

    /**
     * Identifications that actively hurt, which is a crafted item's characteristic failure.
     *
     * <p>An ingredient can carry a negative modifier, so a craft can end up with a stat that drags
     * the build down rather than merely failing to help. Those are worth naming separately: no
     * amount of rerolling fixes them, the recipe has to change.
     */
    public List<Roll> harmfulRolls() {
        return rolls.stream()
                .filter(roll -> isHarmful(roll.key(), roll.actual()))
                .sorted(java.util.Comparator.comparingInt(Roll::actual))
                .toList();
    }

    private static boolean isHarmful(String key, int value) {
        if (value == 0) {
            return false;
        }
        // A spell cost reads backwards: the negative value is the helpful one.
        return com.seqwawa.seq.wynnbuilder.data.Identifications.isInverted(key) ? value > 0 : value < 0;
    }
}
