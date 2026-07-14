package com.seqwawa.seq.map;

import java.time.Instant;
import java.util.Objects;

public record WorldEventRunId(String internalName, Instant schedule) {
    public WorldEventRunId {
        Objects.requireNonNull(internalName, "internalName");
        Objects.requireNonNull(schedule, "schedule");
    }
}
