package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.EnumMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GatheringClusterCacheTest {
    private final GatheringClusterCache cache = GatheringClusterCache.getInstance();
    private final EnumMap<GatheringProfession, Boolean> professions = new EnumMap<>(GatheringProfession.class);
    private final GuildTerritoryIndex territories = new GuildTerritoryIndex(List.of(
            GuildTerritory.fromCorners("West", 0, 0, 10, 10),
            GuildTerritory.fromCorners("East", 10, 0, 20, 10)));
    private final List<GatheringNode> nodes = List.of(
            node(9, 5, "OAK"),
            node(11, 5, "OAK"),
            node(100, 100, "BIRCH"));

    @BeforeEach
    void setUp() {
        cache.clear();
        for (GatheringProfession profession : GatheringProfession.values()) {
            professions.put(profession, true);
        }
    }

    @Test
    void appliesAllAnyAndSelectedTerritoryScopesBeforeClustering() {
        GatheringClusterCache.Result all = analyze(GatheringAnalysisScope.ALL, null);
        GatheringClusterCache.Result any = analyze(GatheringAnalysisScope.ANY_TERRITORY, null);
        GatheringClusterCache.Result west = analyze(GatheringAnalysisScope.SELECTED_TERRITORY, "West");
        GatheringClusterCache.Result noSelection = analyze(GatheringAnalysisScope.SELECTED_TERRITORY, null);

        assertEquals(3, all.filteredNodes().size());
        assertEquals(List.of("BIRCH", "OAK"), all.resourceOptions());
        assertEquals(2, any.filteredNodes().size());
        assertEquals(List.of("OAK"), any.resourceOptions());
        assertEquals(1, west.filteredNodes().size());
        assertEquals(0, noSelection.filteredNodes().size());
    }

    @Test
    void anyTerritoryClustersCanSpanAdjacentBorders() {
        GatheringClusterCache.Result result = analyze(GatheringAnalysisScope.ANY_TERRITORY, null);

        assertEquals(1, result.clusters().size());
        assertEquals(2, result.clusters().getFirst().nodeCount());
        assertEquals(List.of(9, 11), result.clusters().getFirst().nodes().stream()
                .map(GatheringNode::x)
                .sorted()
                .toList());
    }

    @Test
    void cacheSeparatesScopesAndPreservesResultIdentityForRepeatedRequests() {
        GatheringClusterCache.Result firstAll = analyze(GatheringAnalysisScope.ALL, null);
        GatheringClusterCache.Result secondAll = analyze(GatheringAnalysisScope.ALL, null);
        GatheringClusterCache.Result any = analyze(GatheringAnalysisScope.ANY_TERRITORY, null);

        assertSame(firstAll, secondAll);
        assertNotSame(firstAll, any);
        assertEquals(2, cache.size());
    }

    private GatheringClusterCache.Result analyze(GatheringAnalysisScope scope, String selectedTerritory) {
        return cache.getOrCompute(
                nodes,
                Set.of(),
                professions,
                territories,
                scope,
                selectedTerritory,
                ClusterScoreMode.FOUR_TICK,
                3,
                2);
    }

    private static GatheringNode node(int x, int z, String resource) {
        return new GatheringNode(x, 64, z, 0, "NODE", resource, 1);
    }
}
