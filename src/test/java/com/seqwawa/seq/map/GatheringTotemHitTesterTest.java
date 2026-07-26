package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.seqwawa.seq.map.GatheringTotemSolver.Placement;
import com.seqwawa.seq.map.GatheringTotemSolver.Position;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatheringTotemHitTesterTest {
    @Test
    void selectedHullWinsWhenSeveralHullsContainThePoint() {
        Placement large = placement("large", -10, -10, 10, 10);
        Placement selected = placement("selected", -5, -5, 5, 5);

        Placement hit = GatheringTotemHitTester.containingHull(
                List.of(large, selected),
                selected,
                0,
                0);

        assertSame(selected, hit);
    }

    @Test
    void smallestContainingHullWinsWhenTheSelectedHullDoesNotContainThePoint() {
        Placement large = placement("large", -10, -10, 10, 10);
        Placement smallest = placement("small", -3, -3, 3, 3);
        Placement selectedElsewhere = placement("selected", 50, 50, 60, 60);

        Placement hit = GatheringTotemHitTester.containingHull(
                List.of(large, smallest, selectedElsewhere),
                selectedElsewhere,
                0,
                0);

        assertSame(smallest, hit);
    }

    @Test
    void returnsNullOutsideEveryHull() {
        Placement placement = placement("only", -5, -5, 5, 5);

        assertNull(GatheringTotemHitTester.containingHull(
                List.of(placement),
                placement,
                20,
                20));
    }

    private static Placement placement(
            String key,
            double minX,
            double minZ,
            double maxX,
            double maxZ) {
        return new Placement(
                key,
                (minX + maxX) / 2,
                (minZ + maxZ) / 2,
                List.of(new GatheringNode(0, 64, 0, 0, "NODE", "OAK", 1)),
                0,
                false,
                List.of(
                        new Position(minX, minZ),
                        new Position(maxX, minZ),
                        new Position(maxX, maxZ),
                        new Position(minX, maxZ)));
    }
}
