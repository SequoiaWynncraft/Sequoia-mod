package com.seqwawa.seq.map;

import com.seqwawa.seq.map.GatheringTotemSolver.Placement;
import com.seqwawa.seq.map.GatheringTotemSolver.Position;
import java.util.Comparator;
import java.util.List;

public final class GatheringTotemHitTester {
    private GatheringTotemHitTester() {}

    public static Placement containingHull(
            List<Placement> placements,
            Placement selectedPlacement,
            double worldX,
            double worldZ) {
        if (selectedPlacement != null && contains(selectedPlacement.validCenterHull(), worldX, worldZ)) {
            return selectedPlacement;
        }
        return placements.stream()
                .filter(placement -> placement != selectedPlacement)
                .filter(placement -> contains(placement.validCenterHull(), worldX, worldZ))
                .min(Comparator.comparingDouble(placement -> area(placement.validCenterHull())))
                .orElse(null);
    }

    public static boolean contains(List<Position> hull, double worldX, double worldZ) {
        if (hull == null || hull.size() < 3) {
            return false;
        }
        boolean inside = false;
        for (int currentIndex = 0, previousIndex = hull.size() - 1;
                currentIndex < hull.size();
                previousIndex = currentIndex++) {
            Position current = hull.get(currentIndex);
            Position previous = hull.get(previousIndex);
            boolean crosses = (current.z() > worldZ) != (previous.z() > worldZ)
                    && worldX
                            < (previous.x() - current.x())
                                            * (worldZ - current.z())
                                            / (previous.z() - current.z())
                                    + current.x();
            if (crosses) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static double area(List<Position> hull) {
        if (hull == null || hull.size() < 3) {
            return 0;
        }
        double sum = 0;
        for (int index = 0; index < hull.size(); index++) {
            Position current = hull.get(index);
            Position next = hull.get((index + 1) % hull.size());
            sum += current.x() * next.z() - next.x() * current.z();
        }
        return Math.abs(sum) / 2.0;
    }
}
