package com.seqwawa.seq.map;

import com.seqwawa.seq.map.GatheringTotemSolver.Placement;
import com.seqwawa.seq.map.GatheringTotemSolver.Position;
import java.util.Comparator;
import java.util.List;

public final class GatheringTotemResults {
    private GatheringTotemResults() {}

    public static List<Placement> ordered(List<Placement> placements, Position playerPosition) {
        if (placements == null || placements.isEmpty()) {
            return List.of();
        }
        Comparator<Placement> comparator = playerPosition == null
                ? Comparator.comparingDouble(Placement::x).thenComparingDouble(Placement::z)
                : Comparator.comparingDouble((Placement placement) -> distanceSquared(placement, playerPosition))
                        .thenComparingDouble(Placement::x)
                        .thenComparingDouble(Placement::z);
        return placements.stream().sorted(comparator).toList();
    }

    public static Placement select(
            List<Placement> orderedPlacements,
            String previousPlacementKey) {
        if (orderedPlacements == null || orderedPlacements.isEmpty()) {
            return null;
        }
        if (previousPlacementKey != null) {
            for (Placement placement : orderedPlacements) {
                if (previousPlacementKey.equals(placement.key())) {
                    return placement;
                }
            }
        }
        return orderedPlacements.getFirst();
    }

    private static double distanceSquared(Placement placement, Position playerPosition) {
        double dx = placement.x() - playerPosition.x();
        double dz = placement.z() - playerPosition.z();
        return dx * dx + dz * dz;
    }
}
