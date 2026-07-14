package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorldEventFiltersTest {

    @Test
    void filtersVisibleEventsByAllOrTracked() {
        WorldEventDefinition tracked = event("Tracked", "tracked", "2026-07-14T08:20:00Z");
        WorldEventDefinition other = event("Other", "other", "2026-07-14T08:21:00Z");
        WorldEventDefinition unscheduled = new WorldEventDefinition(
                "Unscheduled", "unscheduled", null, null, null, null, tracked.locations(), null);

        assertEquals(
                List.of(tracked, other),
                WorldEventFilters.visibleEvents(
                        List.of(tracked, other, unscheduled), WorldEventDisplayFilter.ALL, Set.of("tracked")));
        assertEquals(
                List.of(tracked),
                WorldEventFilters.visibleEvents(
                        List.of(tracked, other, unscheduled), WorldEventDisplayFilter.TRACKED, Set.of("tracked")));
    }

    @Test
    void retainsSelectionByRunIdentityAndClearsMissingRuns() {
        WorldEventDefinition selected = event("Old name", "same", "2026-07-14T08:20:00Z");
        WorldEventDefinition refreshed = event("New name", "same", "2026-07-14T08:20:00Z");

        assertEquals(refreshed, WorldEventFilters.retainVisibleSelection(selected, List.of(refreshed)));
        assertNull(WorldEventFilters.retainVisibleSelection(selected, List.of()));
    }

    @Test
    void markerHitTestingUsesDistanceThenStableEventOrdering() {
        WorldEventDefinition beta = event("Beta", "beta", "2026-07-14T08:20:00Z");
        WorldEventDefinition alpha = event("Alpha", "alpha", "2026-07-14T08:20:00Z");
        List<WorldEventMarkerHitTester.Candidate> candidates = List.of(
                new WorldEventMarkerHitTester.Candidate(beta, 0, 4),
                new WorldEventMarkerHitTester.Candidate(alpha, 0, 4),
                new WorldEventMarkerHitTester.Candidate(beta, 1, 7));

        assertEquals(alpha, WorldEventMarkerHitTester.closest(candidates, 9).event());
        assertNull(WorldEventMarkerHitTester.closest(candidates, 3));
    }

    @Test
    void trackingOptionsCanShowOnlyTrackedEventsAndStillSearchThem() {
        WorldEventDefinition active = event("Active Tracked", "active", "2026-07-14T08:20:00Z");
        WorldEventDefinition unscheduled = new WorldEventDefinition(
                "Unscheduled Tracked", "unscheduled", null, null, null, null, active.locations(), null);
        WorldEventDefinition untracked = event("Other Event", "other", "2026-07-14T08:21:00Z");
        List<WorldEventDefinition> events = List.of(active, unscheduled, untracked);

        assertEquals(
                List.of(active, unscheduled),
                WorldEventFilters.trackingOptions(events, Set.of("active", "unscheduled"), true, ""));
        assertEquals(
                List.of(unscheduled),
                WorldEventFilters.trackingOptions(events, Set.of("active", "unscheduled"), true, "scheduled"));
        assertEquals(events, WorldEventFilters.trackingOptions(events, Set.of("active"), false, ""));
    }

    private static WorldEventDefinition event(String name, String internalName, String schedule) {
        return new WorldEventDefinition(
                name,
                internalName,
                null,
                "MEDIUM",
                50,
                "SHORT",
                List.of(new WorldEventLocation(1, 2, 3, 4, 5)),
                Instant.parse(schedule));
    }
}
