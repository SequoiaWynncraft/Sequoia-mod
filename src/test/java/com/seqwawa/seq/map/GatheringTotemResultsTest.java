package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.seqwawa.seq.map.GatheringTotemSolver.Placement;
import com.seqwawa.seq.map.GatheringTotemSolver.Position;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatheringTotemResultsTest {
    @Test
    void preservesASelectedPlacementByStableKeyAcrossRecomputation() {
        Placement previous = placement("same-covered-nodes", 100, 100);
        Placement equivalentRecomputed = placement("same-covered-nodes", 101, 99);
        Placement other = placement("other", 0, 0);

        Placement selected = GatheringTotemResults.select(
                List.of(other, equivalentRecomputed),
                previous.key());

        assertSame(equivalentRecomputed, selected);
    }

    @Test
    void ordersByPlayerDistanceBeforeCoordinates() {
        Placement far = placement("far", 80, 80);
        Placement nearest = placement("near", 12, 10);
        Placement middle = placement("middle", 30, 10);

        List<Placement> ordered = GatheringTotemResults.ordered(
                List.of(far, nearest, middle),
                new Position(10, 10));

        assertEquals(List.of(nearest, middle, far), ordered);
        assertSame(nearest, GatheringTotemResults.select(ordered, null));
    }

    @Test
    void fallsBackToDeterministicCoordinateOrderWithoutAPlayer() {
        Placement last = placement("last", 20, -10);
        Placement first = placement("first", -5, 30);
        Placement middle = placement("middle", 20, -20);

        List<Placement> ordered = GatheringTotemResults.ordered(
                List.of(last, first, middle),
                null);

        assertEquals(List.of(first, middle, last), ordered);
    }

    private static Placement placement(String key, double x, double z) {
        GatheringNode coveredNode = new GatheringNode((int) x, 64, (int) z, 0, "NODE", "OAK", 1);
        return new Placement(
                key,
                x,
                z,
                List.of(coveredNode),
                0,
                false,
                List.of(
                        new Position(x - 5, z - 5),
                        new Position(x + 5, z - 5),
                        new Position(x + 5, z + 5),
                        new Position(x - 5, z + 5)));
    }
}
