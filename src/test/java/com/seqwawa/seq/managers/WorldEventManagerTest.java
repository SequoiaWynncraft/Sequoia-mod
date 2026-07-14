package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.map.WorldEventDefinition;
import com.seqwawa.seq.map.WorldEventLocation;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorldEventManagerTest {

    @Test
    void detectionMessageIncludesCountdownWithoutLocation() {
        WorldEventDefinition event = event(
                "Skittering Spiders",
                List.of(new WorldEventLocation(-189, 76, -1709, 20, 12)),
                "2026-07-14T08:18:00Z");

        assertEquals(
                "World event detected: Skittering Spiders starts in 3m",
                WorldEventManager.detectionMessage(event, Instant.parse("2026-07-14T08:15:01Z")));
    }

    @Test
    void detectionMessageUsesStartedStateWithoutLocations() {
        WorldEventDefinition event = event(
                "Aeon Origin",
                List.of(
                        new WorldEventLocation(-396, 73, -1197, 17, 14),
                        new WorldEventLocation(-400, 73, -1200, 17, 14)),
                "2026-07-14T08:18:00Z");

        assertEquals(
                "World event detected: Aeon Origin started",
                WorldEventManager.detectionMessage(event, Instant.parse("2026-07-14T08:18:01Z")));
    }

    private static WorldEventDefinition event(
            String name,
            List<WorldEventLocation> locations,
            String schedule) {
        return new WorldEventDefinition(
                name,
                name.toLowerCase().replace(' ', '-'),
                null,
                "MEDIUM",
                30,
                "SHORT",
                locations,
                Instant.parse(schedule));
    }
}
