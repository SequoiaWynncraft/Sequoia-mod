package com.seqwawa.seq.map;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GuildTerritoryIndex {
    private static final Comparator<GuildTerritory> HIT_ORDER = Comparator
            .comparingDouble(GuildTerritory::area)
            .thenComparing(GuildTerritory::name);
    public static final GuildTerritoryIndex EMPTY = new GuildTerritoryIndex(List.of());

    private final List<GuildTerritory> territories;
    private final List<GuildTerritory> hitTestOrder;
    private final Map<String, GuildTerritory> territoriesByName;
    private final int contentHash;

    public GuildTerritoryIndex(List<GuildTerritory> territories) {
        List<GuildTerritory> sorted = territories == null
                ? List.of()
                : territories.stream().sorted(Comparator.comparing(GuildTerritory::name)).toList();
        Map<String, GuildTerritory> byName = new LinkedHashMap<>();
        for (GuildTerritory territory : sorted) {
            byName.put(territory.name(), territory);
        }
        this.territories = List.copyOf(byName.values());
        this.hitTestOrder = this.territories.stream().sorted(HIT_ORDER).toList();
        this.territoriesByName = Map.copyOf(byName);
        this.contentHash = this.territories.hashCode();
    }

    public List<GuildTerritory> territories() {
        return territories;
    }

    public GuildTerritory territory(String name) {
        return name == null ? null : territoriesByName.get(name);
    }

    public GuildTerritory territoryAt(double x, double z) {
        for (GuildTerritory territory : hitTestOrder) {
            if (territory.contains(x, z)) {
                return territory;
            }
        }
        return null;
    }

    public boolean containsAny(double x, double z) {
        return territoryAt(x, z) != null;
    }

    public int contentHash() {
        return contentHash;
    }
}
