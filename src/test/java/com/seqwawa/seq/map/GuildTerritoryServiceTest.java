package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class GuildTerritoryServiceTest {

    @Test
    void bundledSnapshotContainsOfficialTerritoryBounds() throws Exception {
        try (InputStream input = GuildTerritoryServiceTest.class.getClassLoader()
                .getResourceAsStream("assets/seq/map/guild-territories.json")) {
            assertNotNull(input);
            List<GuildTerritory> territories = GuildTerritoryService.parseTerritories(
                    new String(input.readAllBytes(), StandardCharsets.UTF_8));

            assertEquals(437, territories.size());
            GuildTerritory ragni = territories.stream()
                    .filter(territory -> territory.name().equals("Ragni"))
                    .findFirst()
                    .orElseThrow();
            assertEquals(new MapBounds(-955, -1748, -756, -1415), ragni.bounds());
        }
    }

    @Test
    void parsesSortsAndNormalizesTerritoryBounds() {
        List<GuildTerritory> territories = GuildTerritoryService.parseTerritories("""
                {
                  "meta": {"source": "test"},
                  "data": [
                    {"name": "Zulu", "start": [20, 40], "end": [10, 30]},
                    {"name": "Alpha", "start": [-5, 8], "end": [5, -2]},
                    {"name": "", "start": [0, 0], "end": [1, 1]},
                    {"name": "Broken", "start": [0], "end": [1, 1]}
                  ]
                }
                """);

        assertEquals(List.of("Alpha", "Zulu"), territories.stream().map(GuildTerritory::name).toList());
        assertEquals(new MapBounds(-5, -2, 5, 8), territories.getFirst().bounds());
        assertEquals(new MapBounds(10, 30, 20, 40), territories.getLast().bounds());
    }

    @Test
    void acceptsAnArrayRoot() {
        List<GuildTerritory> territories = GuildTerritoryService.parseTerritories("""
                [{"name": "Ragni", "start": [-955, -1415], "end": [-756, -1748]}]
                """);

        assertEquals(List.of("Ragni"), territories.stream().map(GuildTerritory::name).toList());
        assertEquals(new MapBounds(-955, -1748, -756, -1415), territories.getFirst().bounds());
    }

    @Test
    void rejectsNonEmptyDataWithNoValidTerritories() {
        assertThrows(IllegalArgumentException.class, () -> GuildTerritoryService.parseTerritories("""
                {"data": [{"name": "Broken", "start": [0], "end": [1, 1]}]}
                """));
    }
}
