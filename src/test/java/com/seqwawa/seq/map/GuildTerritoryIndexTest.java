package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class GuildTerritoryIndexTest {

    @Test
    void containmentIncludesBordersAndRejectsOutsidePoints() {
        GuildTerritory territory = GuildTerritory.fromCorners("Ragni", 10, 20, 0, 0);
        GuildTerritoryIndex index = new GuildTerritoryIndex(List.of(territory));

        assertTrue(territory.contains(0, 0));
        assertTrue(territory.contains(10, 20));
        assertTrue(index.containsAny(5, 10));
        assertFalse(index.containsAny(10.01, 20));
        assertNull(index.territoryAt(-1, 0));
    }

    @Test
    void overlappingTerritoriesPreferSmallestAreaThenName() {
        GuildTerritory large = GuildTerritory.fromCorners("Large", 0, 0, 20, 20);
        GuildTerritory beta = GuildTerritory.fromCorners("Beta", 5, 5, 15, 15);
        GuildTerritory alpha = GuildTerritory.fromCorners("Alpha", 5, 5, 15, 15);
        GuildTerritoryIndex index = new GuildTerritoryIndex(List.of(large, beta, alpha));

        assertEquals(alpha, index.territoryAt(10, 10));
        assertEquals(large, index.territoryAt(2, 2));
        assertEquals(List.of("Alpha", "Beta", "Large"), index.territories().stream()
                .map(GuildTerritory::name)
                .toList());
    }

    @Test
    void sharedBorderResolutionIsDeterministic() {
        GuildTerritory west = GuildTerritory.fromCorners("West", 0, 0, 10, 10);
        GuildTerritory east = GuildTerritory.fromCorners("East", 10, 0, 20, 10);
        GuildTerritoryIndex index = new GuildTerritoryIndex(List.of(west, east));

        assertEquals("East", index.territoryAt(10, 5).name());
    }
}
