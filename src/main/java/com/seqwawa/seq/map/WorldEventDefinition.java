package com.seqwawa.seq.map;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record WorldEventDefinition(
        String name,
        String internalName,
        String lore,
        String difficulty,
        Integer level,
        String length,
        List<WorldEventLocation> locations,
        Instant schedule) {

    public WorldEventDefinition {
        name = Objects.requireNonNull(name, "name");
        internalName = Objects.requireNonNull(internalName, "internalName");
        locations = List.copyOf(Objects.requireNonNull(locations, "locations"));
        if (name.isBlank() || internalName.isBlank() || locations.isEmpty()) {
            throw new IllegalArgumentException("World events require a name, internal name, and location.");
        }
    }

    public boolean isVisible() {
        return schedule != null;
    }

    public WorldEventRunId runId() {
        if (schedule == null) {
            throw new IllegalStateException("Unscheduled world event has no run identity.");
        }
        return new WorldEventRunId(internalName, schedule);
    }
}
