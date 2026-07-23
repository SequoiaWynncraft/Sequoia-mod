package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GatheringTotemSolverTest {

    @Test
    void maximizesNodesWithinTheFiftyTwoBlockInteractionReach() {
        List<GatheringNode> nodes = List.of(
                node(0, 0, "OAK"),
                node(52, 0, "OAK"),
                node(104, 0, "OAK"),
                node(500, 500, "OAK"));

        GatheringTotemSolver.Placement placement = solve(nodes, Set.of(), null, List.of());

        assertEquals(3, placement.nodeCount());
        assertTrue(placement.coveredNodes().containsAll(nodes.subList(0, 3)));
    }

    @Test
    void addsTwoBlocksForThePlayersInteractionDistanceFromTheNode() {
        GatheringTotemSolver.Placement placement = solve(
                List.of(node(0, 0, "OAK"), node(105, 0, "OAK")),
                Set.of(),
                null,
                List.of());

        assertEquals(50.0, GatheringTotemSolver.TOTEM_RADIUS);
        assertEquals(2.0, GatheringTotemSolver.NODE_INTERACTION_MARGIN);
        assertEquals(52.0, GatheringTotemSolver.EFFECTIVE_NODE_RADIUS);
        assertEquals(1, placement.nodeCount());
    }

    @Test
    void returnsAFeasibleHullAndAnIntegerBestPosition() {
        GatheringNode left = node(-20, 0, "OAK");
        GatheringNode right = node(20, 0, "OAK");

        GatheringTotemSolver.Placement placement = solve(
                List.of(left, right),
                Set.of(),
                null,
                List.of());

        assertTrue(placement.validCenterHull().size() >= 3);
        assertEquals(Math.rint(placement.x()), placement.x());
        assertEquals(Math.rint(placement.z()), placement.z());
        assertTrue(placement.validCenterHull().stream().allMatch(position ->
                Math.hypot(position.x() - left.x(), position.z() - left.z())
                                <= GatheringTotemSolver.EFFECTIVE_NODE_RADIUS + 0.0001
                        && Math.hypot(position.x() - right.x(), position.z() - right.z())
                                <= GatheringTotemSolver.EFFECTIVE_NODE_RADIUS + 0.0001));
    }

    @Test
    void returnsEveryDistinctHullWithTheSameOptimalNodeCount() {
        List<GatheringNode> nodes = List.of(
                node(0, 0, "OAK"),
                node(10, 0, "OAK"),
                node(20, 0, "OAK"),
                node(1_000, 0, "OAK"),
                node(1_010, 0, "OAK"),
                node(1_020, 0, "OAK"));

        List<GatheringTotemSolver.Placement> placements =
                GatheringTotemSolver.solveAll(nodes, Set.of(), null, List.of());

        assertEquals(2, placements.size());
        assertTrue(placements.stream().allMatch(placement -> placement.nodeCount() == 3));
        assertTrue(placements.stream().allMatch(placement -> !placement.validCenterHull().isEmpty()));
        assertTrue(placements.getFirst().x() < 100);
        assertTrue(placements.getLast().x() > 900);
    }

    @Test
    void collapsesDuplicateCandidatesThatCoverTheSameNodes() {
        List<GatheringNode> nodes = List.of(
                node(0, 0, "OAK"),
                node(12, 0, "OAK"),
                node(24, 0, "OAK"),
                node(36, 0, "OAK"));

        List<GatheringTotemSolver.Placement> placements =
                GatheringTotemSolver.solveAll(nodes, Set.of(), null, List.of());

        assertEquals(1, placements.size());
        assertEquals(4, placements.getFirst().nodeCount());
        assertFalse(placements.getFirst().key().isBlank());
    }

    @Test
    void excludesEveryPlacementBelowTheStrictGlobalMaximum() {
        List<GatheringNode> nodes = List.of(
                node(0, 0, "OAK"),
                node(10, 0, "OAK"),
                node(20, 0, "OAK"),
                node(1_000, 0, "OAK"),
                node(1_010, 0, "OAK"));

        List<GatheringTotemSolver.Placement> placements =
                GatheringTotemSolver.solveAll(nodes, Set.of(), null, List.of());

        assertEquals(1, placements.size());
        assertEquals(3, placements.getFirst().nodeCount());
        assertTrue(placements.getFirst().coveredNodes().stream().allMatch(node -> node.x() < 100));
    }

    @Test
    void appliesTheThirtyPercentDoubleGatherChanceToExpectedYield() {
        GatheringTotemSolver.Placement placement = solve(
                List.of(node(0, 0, "OAK"), node(10, 0, "OAK")),
                Set.of(),
                null,
                List.of());

        assertEquals(2.6, placement.expectedItemsPerGather(), 0.0001);
    }

    @Test
    void supportsMultipleResourcesAndStrictlyExcludesNodesOutsideTheSelectedTerritory() {
        GuildTerritory west = GuildTerritory.fromCorners("West", -100, -100, 100, 100);
        List<GatheringNode> nodes = List.of(
                node(0, 0, "OAK"),
                node(20, 0, "COPPER"),
                node(30, 0, "WHEAT"),
                node(105, 0, "OAK"),
                node(110, 0, "COPPER"));

        GatheringTotemSolver.Placement placement = solve(
                nodes,
                Set.of("OAK", "COPPER"),
                west,
                List.of());

        assertEquals(2, placement.nodeCount());
        assertEquals(Set.of("OAK", "COPPER"), placement.coveredNodes().stream()
                .map(GatheringNode::resource)
                .collect(java.util.stream.Collectors.toSet()));
        assertTrue(placement.coveredNodes().stream().allMatch(node -> west.contains(node.x(), node.z())));
    }

    @Test
    void clusterFocusScoresEligibleNeighboringNodesWithoutJumpingToAnotherCluster() {
        GatheringNode focusA = node(0, 0, "OAK");
        GatheringNode focusB = node(10, 0, "OAK");
        GatheringNode neighbor = node(60, 0, "OAK");
        List<GatheringNode> unrelatedDenseCluster = java.util.stream.IntStream.range(0, 8)
                .mapToObj(index -> node(1_000 + index, 1_000, "OAK"))
                .toList();
        List<GatheringNode> nodes = new java.util.ArrayList<>(List.of(focusA, focusB, neighbor));
        nodes.addAll(unrelatedDenseCluster);

        GatheringTotemSolver.Placement placement = solve(
                nodes,
                Set.of("OAK"),
                null,
                List.of(focusA, focusB));

        assertTrue(placement.clusterFocused());
        assertEquals(3, placement.nodeCount());
        assertEquals(2, placement.coveredClusterNodes());
        assertTrue(placement.coveredNodes().contains(neighbor));
        assertTrue(placement.coveredNodes().stream().noneMatch(unrelatedDenseCluster::contains));
    }

    @Test
    void clusterFocusNeverCountsANeighborOutsideTheSelectedTerritory() {
        GuildTerritory territory = GuildTerritory.fromCorners("Border", 0, -50, 100, 50);
        GatheringNode focus = node(90, 0, "OAK");
        GatheringNode insideNeighbor = node(95, 0, "OAK");
        GatheringNode outsideNeighbor = node(105, 0, "OAK");

        GatheringTotemSolver.Placement placement = solve(
                List.of(focus, insideNeighbor, outsideNeighbor),
                Set.of("OAK"),
                territory,
                List.of(focus));

        assertEquals(2, placement.nodeCount());
        assertTrue(placement.coveredNodes().contains(insideNeighbor));
        assertFalse(placement.coveredNodes().contains(outsideNeighbor));
    }

    private static GatheringTotemSolver.Placement solve(
            List<GatheringNode> nodes,
            Set<String> resources,
            GuildTerritory territory,
            List<GatheringNode> clusterNodes) {
        return GatheringTotemSolver.solve(nodes, resources, territory, clusterNodes).orElseThrow();
    }

    private static GatheringNode node(int x, int z, String resource) {
        return new GatheringNode(x, 64, z, 0, "NODE", resource, 1);
    }
}
