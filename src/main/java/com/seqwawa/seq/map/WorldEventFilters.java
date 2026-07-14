package com.seqwawa.seq.map;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class WorldEventFilters {
    private WorldEventFilters() {}

    public static List<WorldEventDefinition> visibleEvents(
            List<WorldEventDefinition> events,
            WorldEventDisplayFilter filter,
            Set<String> trackedInternalNames) {
        WorldEventDisplayFilter effectiveFilter = filter == null ? WorldEventDisplayFilter.ALL : filter;
        Set<String> tracked = trackedInternalNames == null ? Set.of() : trackedInternalNames;
        return events.stream()
                .filter(WorldEventDefinition::isVisible)
                .filter(event -> effectiveFilter == WorldEventDisplayFilter.ALL
                        || tracked.contains(event.internalName()))
                .toList();
    }

    public static WorldEventDefinition retainVisibleSelection(
            WorldEventDefinition selected,
            List<WorldEventDefinition> visibleEvents) {
        if (selected == null) {
            return null;
        }
        return visibleEvents.stream()
                .filter(event -> event.runId().equals(selected.runId()))
                .findFirst()
                .orElse(null);
    }

    public static List<WorldEventDefinition> trackingOptions(
            List<WorldEventDefinition> events,
            Set<String> trackedInternalNames,
            boolean trackedOnly,
            String searchQuery) {
        Set<String> tracked = trackedInternalNames == null ? Set.of() : trackedInternalNames;
        String query = searchQuery == null ? "" : searchQuery.trim().toLowerCase(Locale.ROOT);
        List<WorldEventDefinition> available = events.stream()
                .filter(event -> !trackedOnly || tracked.contains(event.internalName()))
                .toList();
        if (query.isEmpty()) {
            return available;
        }
        List<WorldEventDefinition> prefixMatches = available.stream()
                .filter(event -> event.name().toLowerCase(Locale.ROOT).startsWith(query))
                .toList();
        List<WorldEventDefinition> substringMatches = available.stream()
                .filter(event -> {
                    String name = event.name().toLowerCase(Locale.ROOT);
                    return !name.startsWith(query) && name.contains(query);
                })
                .toList();
        return java.util.stream.Stream.concat(prefixMatches.stream(), substringMatches.stream()).toList();
    }
}
