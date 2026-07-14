package com.seqwawa.seq.map;

import java.util.Comparator;
import java.util.List;

public final class WorldEventMarkerHitTester {
    private static final Comparator<Candidate> ORDER = Comparator.comparingDouble(Candidate::distance)
            .thenComparing(candidate -> candidate.event().name())
            .thenComparing(candidate -> candidate.event().internalName())
            .thenComparingInt(Candidate::locationIndex);

    private WorldEventMarkerHitTester() {}

    public static Candidate closest(List<Candidate> candidates, double maxDistance) {
        return candidates.stream()
                .filter(candidate -> candidate.distance() <= maxDistance)
                .min(ORDER)
                .orElse(null);
    }

    public record Candidate(WorldEventDefinition event, int locationIndex, double distance) {}
}
