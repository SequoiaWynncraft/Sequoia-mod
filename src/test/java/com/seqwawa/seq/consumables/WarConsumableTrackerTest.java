package com.seqwawa.seq.consumables;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.seqwawa.seq.consumables.WarConsumableTracker.Status;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WarConsumableTrackerTest {
    private static final long SESSION = 1_000L;
    private static final int DURATION_SECONDS = 771;
    private static final long DURATION_MILLIS = DURATION_SECONDS * 1_000L;

    private final WarConsumableTracker tracker = new WarConsumableTracker();

    @Test
    void ranksBonusOnlyConsumablesFirstAndPenaltiesLast() {
        WarConsumable twoPenalties = consumable("scroll", 3, 4, 2);
        WarConsumable onePenalty = consumable("food", 3, 3, 1);
        WarConsumable bonusOnly = consumable("potion", 3, 2, 0);

        tracker.observe(SESSION, Map.of(0, twoPenalties, 1, onePenalty, 5, bonusOnly), 0);

        assertEquals(List.of("potion", "food", "scroll"), tracker.order());
        assertEquals(Status.NEXT, tracker.statusOf("potion"));
        assertEquals(Status.UNUSED, tracker.statusOf("food"));
        assertEquals(Status.UNUSED, tracker.statusOf("scroll"));
    }

    @Test
    void equallyRankedConsumablesKeepInventoryOrder() {
        tracker.observe(
                SESSION,
                Map.of(20, consumable("late", 3, 1, 0), 2, consumable("hotbar", 3, 5, 0)),
                0);

        assertEquals(List.of("hotbar", "late"), tracker.order());
    }

    @Test
    void nextMovesOnWhenAChargeIsUsed() {
        tracker.observe(SESSION, Map.of(0, consumable("first", 3, 2, 0), 1, consumable("second", 3, 2, 1)), 0);

        tracker.observe(SESSION, Map.of(0, consumable("first", 2, 2, 0), 1, consumable("second", 3, 2, 1)), 50);

        assertEquals(Status.ACTIVE, tracker.statusOf("first"));
        assertEquals(Status.NEXT, tracker.statusOf("second"));
    }

    @Test
    void effectWearsOffAfterItsDuration() {
        tracker.observe(SESSION, Map.of(0, consumable("scroll", 3, 2, 0), 1, consumable("food", 3, 2, 1)), 0);
        tracker.observe(SESSION, Map.of(0, consumable("scroll", 2, 2, 0), 1, consumable("food", 3, 2, 1)), 1_000);

        tracker.observe(
                SESSION,
                Map.of(0, consumable("scroll", 2, 2, 0), 1, consumable("food", 3, 2, 1)),
                1_000 + DURATION_MILLIS - 1);
        assertEquals(Status.ACTIVE, tracker.statusOf("scroll"));

        tracker.observe(
                SESSION,
                Map.of(0, consumable("scroll", 2, 2, 0), 1, consumable("food", 3, 2, 1)),
                1_000 + DURATION_MILLIS);
        assertEquals(Status.NEXT, tracker.statusOf("scroll"));
        assertEquals(Status.UNUSED, tracker.statusOf("food"));
    }

    @Test
    void nextExpiryIsTheSoonestActiveEffectEnd() {
        tracker.observe(SESSION, Map.of(0, consumable("scroll", 3, 2, 0), 1, consumable("food", 3, 2, 0)), 0);
        assertEquals(Long.MAX_VALUE, tracker.nextExpiryMillis(0));

        tracker.observe(SESSION, Map.of(0, consumable("scroll", 2, 2, 0), 1, consumable("food", 3, 2, 0)), 1_000);
        tracker.observe(SESSION, Map.of(0, consumable("scroll", 2, 2, 0), 1, consumable("food", 2, 2, 0)), 5_000);

        assertEquals(1_000 + DURATION_MILLIS, tracker.nextExpiryMillis(5_000));
        assertEquals(5_000 + DURATION_MILLIS, tracker.nextExpiryMillis(1_000 + DURATION_MILLIS));
        assertEquals(Long.MAX_VALUE, tracker.nextExpiryMillis(5_000 + DURATION_MILLIS));
    }

    @Test
    void effectWithoutKnownDurationLastsUntilTheSessionEnds() {
        WarConsumable fresh = new WarConsumable("scroll", 3, 0, 2, 0);
        tracker.observe(SESSION, Map.of(0, fresh), 0);
        tracker.observe(SESSION, Map.of(0, new WarConsumable("scroll", 2, 0, 2, 0)), 50);

        tracker.observe(SESSION, Map.of(0, new WarConsumable("scroll", 2, 0, 2, 0)), 100 * DURATION_MILLIS);

        assertEquals(Status.ACTIVE, tracker.statusOf("scroll"));
    }

    @Test
    void lastChargeCountsOnlyAfterARightClick() {
        WarConsumable dropped = consumable("dropped", 1, 2, 0);
        WarConsumable drunk = consumable("drunk", 1, 2, 0);
        WarConsumable remaining = consumable("remaining", 3, 1, 0);
        tracker.observe(SESSION, Map.of(0, dropped, 1, drunk, 2, remaining), 0);

        tracker.onUseAttempt(1, drunk, 50);
        tracker.observe(SESSION, Map.of(2, remaining, 9, dropped), 150);

        assertNull(tracker.statusOf("drunk"));
        assertEquals(Status.NEXT, tracker.statusOf("remaining"));
        assertEquals(Status.UNUSED, tracker.statusOf("dropped"));

        // A spare copy of the drunk consumable shares its active effect.
        tracker.observe(SESSION, Map.of(1, consumable("drunk", 3, 2, 0), 2, remaining, 9, dropped), 200);
        assertEquals(Status.ACTIVE, tracker.statusOf("drunk"));
    }

    @Test
    void staleRightClickDoesNotMarkAMovedConsumable() {
        WarConsumable moved = consumable("moved", 1, 2, 0);
        tracker.observe(SESSION, Map.of(0, moved), 0);
        tracker.onUseAttempt(0, moved, 0);

        long later = WarConsumableTracker.USE_CONFIRM_WINDOW_MILLIS + 1;
        tracker.observe(SESSION, Map.of(10, moved), later);

        assertEquals(Status.NEXT, tracker.statusOf("moved"));
    }

    @Test
    void copiesShareTheActiveStatus() {
        WarConsumable spare = consumable("scroll", 3, 2, 0);
        tracker.observe(SESSION, Map.of(0, consumable("scroll", 2, 2, 0), 10, spare), 0);

        tracker.observe(SESSION, Map.of(0, consumable("scroll", 1, 2, 0), 10, spare), 50);

        assertEquals(Status.ACTIVE, tracker.statusOf("scroll"));
    }

    @Test
    void alreadyActiveMarksTheClickedConsumableForItsDuration() {
        WarConsumable scroll = consumable("scroll", 3, 2, 0);
        WarConsumable food = consumable("food", 3, 1, 0);
        tracker.observe(SESSION, Map.of(0, scroll, 1, food), 0);

        tracker.onUseAttempt(0, scroll, 1_000);
        tracker.onAlreadyActive(1_100);
        tracker.observe(SESSION, Map.of(0, scroll, 1, food), 1_150);
        assertEquals(Status.ACTIVE, tracker.statusOf("scroll"));
        assertEquals(Status.NEXT, tracker.statusOf("food"));

        tracker.observe(SESSION, Map.of(0, scroll, 1, food), 1_100 + DURATION_MILLIS);
        assertEquals(Status.NEXT, tracker.statusOf("scroll"));
    }

    @Test
    void alreadyActiveAfterTheExpectedEndChecksAgainShortly() {
        WarConsumable scroll = consumable("scroll", 3, 2, 0);
        tracker.observe(SESSION, Map.of(0, scroll), 0);
        tracker.observe(SESSION, Map.of(0, consumable("scroll", 2, 2, 0)), 0);

        long pastEnd = DURATION_MILLIS + 1_000;
        tracker.onUseAttempt(0, consumable("scroll", 2, 2, 0), pastEnd);
        tracker.onAlreadyActive(pastEnd + 100);

        long recheck = pastEnd + 100 + WarConsumableTracker.LAG_RECHECK_MILLIS;
        tracker.observe(SESSION, Map.of(0, consumable("scroll", 2, 2, 0)), recheck - 1);
        assertEquals(Status.ACTIVE, tracker.statusOf("scroll"));
        tracker.observe(SESSION, Map.of(0, consumable("scroll", 2, 2, 0)), recheck);
        assertEquals(Status.NEXT, tracker.statusOf("scroll"));
    }

    @Test
    void alreadyActiveLongAfterTheClickIsIgnored() {
        WarConsumable scroll = consumable("scroll", 3, 2, 0);
        tracker.observe(SESSION, Map.of(0, scroll), 0);

        tracker.onUseAttempt(0, scroll, 0);
        long later = WarConsumableTracker.USE_CONFIRM_WINDOW_MILLIS + 1;
        tracker.onAlreadyActive(later);
        tracker.observe(SESSION, Map.of(0, scroll), later);

        assertEquals(Status.NEXT, tracker.statusOf("scroll"));
    }

    @Test
    void fingerprintIgnoresStatOrder() {
        assertEquals(
                WarConsumable.fingerprint("SCROLL", List.of("earthDamage", "walkSpeed")),
                WarConsumable.fingerprint("SCROLL", List.of("walkSpeed", "earthDamage")));
    }

    @Test
    void changingServerOrClassEndsEffects() {
        tracker.observe(SESSION, Map.of(0, consumable("potion", 3, 2, 0)), 0);
        tracker.observe(SESSION, Map.of(0, consumable("potion", 2, 2, 0)), 50);
        assertEquals(Status.ACTIVE, tracker.statusOf("potion"));

        tracker.observe(SESSION + 1, Map.of(0, consumable("potion", 2, 2, 0)), 100);

        assertEquals(Status.NEXT, tracker.statusOf("potion"));
    }

    @Test
    void statPolarityFollowsWynncraftColors() {
        assertEquals(1, WarConsumable.statPolarity(20, false, false));
        assertEquals(-1, WarConsumable.statPolarity(-20, false, false));
        assertEquals(0, WarConsumable.statPolarity(0, false, false));
        // Spell costs are stored negated: a positive stored value shows as a cost reduction.
        assertEquals(1, WarConsumable.statPolarity(5, true, true));
        assertEquals(-1, WarConsumable.statPolarity(-5, true, true));
        assertEquals(1, WarConsumable.statPolarity(-10, false, true));
    }

    private static WarConsumable consumable(String fingerprint, int uses, int positiveStats, int negativeStats) {
        return new WarConsumable(fingerprint, uses, DURATION_SECONDS, positiveStats, negativeStats);
    }
}
