package com.seqwawa.seq.map;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WorldEventNotificationTracker {
    private final Set<WorldEventRunId> knownRuns = new HashSet<>();
    private boolean baselinePending = true;

    public synchronized List<WorldEventDefinition> update(
            List<WorldEventDefinition> events,
            Set<String> trackedInternalNames) {
        List<WorldEventDefinition> visible = events.stream()
                .filter(WorldEventDefinition::isVisible)
                .toList();
        Set<WorldEventRunId> currentRuns = visible.stream()
                .map(WorldEventDefinition::runId)
                .collect(java.util.stream.Collectors.toSet());

        if (baselinePending) {
            knownRuns.addAll(currentRuns);
            baselinePending = false;
            return List.of();
        }

        Set<String> tracked = trackedInternalNames == null ? Set.of() : trackedInternalNames;
        List<WorldEventDefinition> detected = visible.stream()
                .filter(event -> !knownRuns.contains(event.runId()))
                .filter(event -> tracked.contains(event.internalName()))
                .toList();
        knownRuns.addAll(currentRuns);
        return detected;
    }

    public synchronized void resetToBaseline() {
        knownRuns.clear();
        baselinePending = true;
    }
}
