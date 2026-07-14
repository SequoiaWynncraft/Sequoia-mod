package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorldEventNotificationTrackerTest {

    @Test
    void baselinesSilentlyThenDetectsOnlyNewTrackedRuns() {
        WorldEventNotificationTracker tracker = new WorldEventNotificationTracker();
        WorldEventDefinition first = event("First", "first", "2026-07-14T08:20:00Z");
        WorldEventDefinition second = event("Second", "second", "2026-07-14T08:21:00Z");

        assertTrue(tracker.update(List.of(first), Set.of("first")).isEmpty());
        assertTrue(tracker.update(List.of(first), Set.of("first")).isEmpty());
        assertEquals(List.of(second), tracker.update(List.of(first, second), Set.of("second")));
        assertTrue(tracker.update(List.of(first, second), Set.of("second")).isEmpty());
    }

    @Test
    void lateTrackingDoesNotNotifyAnAlreadyDetectedRun() {
        WorldEventNotificationTracker tracker = new WorldEventNotificationTracker();
        WorldEventDefinition baseline = event("Baseline", "baseline", "2026-07-14T08:20:00Z");
        WorldEventDefinition untracked = event("Untracked", "untracked", "2026-07-14T08:21:00Z");

        tracker.update(List.of(baseline), Set.of());
        assertTrue(tracker.update(List.of(baseline, untracked), Set.of()).isEmpty());
        assertTrue(tracker.update(List.of(baseline, untracked), Set.of("untracked")).isEmpty());
    }

    @Test
    void changedScheduleIsANewRunAndResetRequiresAnotherBaseline() {
        WorldEventNotificationTracker tracker = new WorldEventNotificationTracker();
        WorldEventDefinition firstRun = event("Repeat", "repeat", "2026-07-14T08:20:00Z");
        WorldEventDefinition secondRun = event("Repeat", "repeat", "2026-07-14T09:20:00Z");

        tracker.update(List.of(firstRun), Set.of("repeat"));
        assertEquals(List.of(secondRun), tracker.update(List.of(secondRun), Set.of("repeat")));
        tracker.resetToBaseline();
        assertTrue(tracker.update(List.of(secondRun), Set.of("repeat")).isEmpty());
    }

    private static WorldEventDefinition event(String name, String internalName, String schedule) {
        return new WorldEventDefinition(
                name,
                internalName,
                null,
                null,
                null,
                null,
                List.of(new WorldEventLocation(1, 2, 3, 4, 5)),
                Instant.parse(schedule));
    }
}
