package com.seqwawa.seq.map;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GatheringClusterCache {
    private static final int MAX_ENTRIES = 32;
    private static final GatheringClusterCache INSTANCE = new GatheringClusterCache();

    private final Map<Key, Result> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Result> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private GatheringClusterCache() {}

    public static GatheringClusterCache getInstance() {
        return INSTANCE;
    }

    public synchronized Result getOrCompute(
            List<GatheringNode> sourceNodes,
            Set<String> resourceFilters,
            EnumMap<GatheringProfession, Boolean> professionToggles,
            GuildTerritoryIndex territoryIndex,
            GatheringAnalysisScope analysisScope,
            String selectedTerritoryName,
            ClusterScoreMode scoreMode,
            int eps,
            int minSamples) {
        GuildTerritoryIndex resolvedTerritoryIndex = territoryIndex == null
                ? GuildTerritoryIndex.EMPTY
                : territoryIndex;
        GatheringAnalysisScope resolvedScope = analysisScope == null ? GatheringAnalysisScope.ALL : analysisScope;
        String normalizedTerritoryName = selectedTerritoryName == null ? "" : selectedTerritoryName.trim();
        List<String> normalizedResourceFilters = resourceFilters == null
                ? List.of()
                : resourceFilters.stream()
                        .map(String::trim)
                        .filter(resource -> !resource.isEmpty())
                        .sorted()
                        .toList();
        Set<String> resourceFilterSet = Set.copyOf(normalizedResourceFilters);
        Key key = new Key(
                System.identityHashCode(sourceNodes),
                sourceNodes.size(),
                sourceNodes.hashCode(),
                String.join("\u0000", normalizedResourceFilters),
                professionToggles.getOrDefault(GatheringProfession.WOODCUTTING, true),
                professionToggles.getOrDefault(GatheringProfession.MINING, true),
                professionToggles.getOrDefault(GatheringProfession.FARMING, true),
                professionToggles.getOrDefault(GatheringProfession.FISHING, true),
                resolvedTerritoryIndex.contentHash(),
                resolvedScope,
                normalizedTerritoryName,
                scoreMode,
                eps,
                minSamples);
        Result cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        GuildTerritory selectedTerritory = resolvedTerritoryIndex.territory(normalizedTerritoryName);
        List<GatheringNode> scopedNodes = switch (resolvedScope) {
            case ALL -> sourceNodes;
            case ANY_TERRITORY -> sourceNodes.stream()
                    .filter(node -> resolvedTerritoryIndex.containsAny(node.x(), node.z()))
                    .toList();
            case SELECTED_TERRITORY -> selectedTerritory == null
                    ? List.of()
                    : sourceNodes.stream()
                            .filter(node -> selectedTerritory.contains(node.x(), node.z()))
                            .toList();
        };
        List<GatheringNode> filteredNodes = scopedNodes.stream()
                .filter(node -> resourceFilterSet.isEmpty() || resourceFilterSet.contains(node.resource()))
                .filter(node -> professionToggles.getOrDefault(node.profession(), true))
                .toList();
        List<String> resourceOptions = scopedNodes.stream()
                .map(GatheringNode::resource)
                .distinct()
                .sorted()
                .toList();
        List<GatheringNodeCluster> clusters = GatheringNodeClusterer.analyze(
                filteredNodes,
                eps,
                minSamples,
                scoreMode);
        Result result = new Result(filteredNodes, clusters, resourceOptions);
        cache.put(key, result);
        return result;
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }

    public record Result(
            List<GatheringNode> filteredNodes,
            List<GatheringNodeCluster> clusters,
            List<String> resourceOptions) {}

    private record Key(
            int sourceIdentity,
            int sourceSize,
            int sourceHash,
            String resourceFilters,
            boolean woodcutting,
            boolean mining,
            boolean farming,
            boolean fishing,
            int territoryDataHash,
            GatheringAnalysisScope analysisScope,
            String selectedTerritoryName,
            ClusterScoreMode scoreMode,
            int eps,
            int minSamples) {}
}
