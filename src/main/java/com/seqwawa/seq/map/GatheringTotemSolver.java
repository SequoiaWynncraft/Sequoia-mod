package com.seqwawa.seq.map;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Finds a maximum-coverage gathering totem placement. A cluster selection limits
 * placement anchors to that cluster while every eligible neighboring node still
 * contributes to the score.
 */
public final class GatheringTotemSolver {
    public static final double TOTEM_RADIUS = 50.0;
    public static final double NODE_INTERACTION_MARGIN = 2.0;
    public static final double EFFECTIVE_NODE_RADIUS = TOTEM_RADIUS + NODE_INTERACTION_MARGIN;
    public static final double DOUBLE_GATHER_CHANCE = 0.30;

    private static final int REGION_CIRCLE_SEGMENTS = 96;
    private static final double DIAMETER = EFFECTIVE_NODE_RADIUS * 2.0;
    private static final double DISTANCE_EPSILON = 1.0e-7;
    private static final double TWO_PI = Math.PI * 2.0;

    private GatheringTotemSolver() {}

    public static Optional<Placement> solve(
            List<GatheringNode> sourceNodes,
            Set<String> selectedResources,
            GuildTerritory selectedTerritory,
            List<GatheringNode> selectedClusterNodes) {
        return solveAll(sourceNodes, selectedResources, selectedTerritory, selectedClusterNodes)
                .stream()
                .findFirst();
    }

    public static List<Placement> solveAll(
            List<GatheringNode> sourceNodes,
            Set<String> selectedResources,
            GuildTerritory selectedTerritory,
            List<GatheringNode> selectedClusterNodes) {
        if (sourceNodes == null || sourceNodes.isEmpty()) {
            return List.of();
        }

        Set<String> resources = normalizeResources(selectedResources);
        List<GatheringNode> eligibleNodes = sourceNodes.stream()
                .filter(node -> resources.isEmpty() || resources.contains(node.resource()))
                .filter(node -> selectedTerritory == null || selectedTerritory.contains(node.x(), node.z()))
                .toList();
        if (eligibleNodes.isEmpty()) {
            return List.of();
        }

        List<GatheringNode> anchors = eligibleNodes;
        boolean clusterFocused = selectedClusterNodes != null && !selectedClusterNodes.isEmpty();
        if (clusterFocused) {
            Set<GatheringNode> selectedClusterSet = new HashSet<>(selectedClusterNodes);
            anchors = eligibleNodes.stream().filter(selectedClusterSet::contains).toList();
            if (anchors.isEmpty()) {
                return List.of();
            }
        }

        SpatialIndex spatialIndex = new SpatialIndex(eligibleNodes);
        OptimalCandidates optimalCandidates = new OptimalCandidates(spatialIndex);
        for (GatheringNode anchor : anchors) {
            if (Thread.currentThread().isInterrupted()) {
                return List.of();
            }
            Candidate anchorCandidate = candidateAt(anchor.x(), anchor.z(), spatialIndex);
            optimalCandidates.consider(anchorCandidate.x(), anchorCandidate.z(), anchorCandidate.nodeCount());

            List<GatheringNode> neighbors = spatialIndex.within(anchor.x(), anchor.z(), DIAMETER);
            int alwaysCovered = 1;
            List<AngleEvent> events = new ArrayList<>(neighbors.size() * 2);
            for (GatheringNode neighbor : neighbors) {
                if (neighbor == anchor) {
                    continue;
                }
                double dx = neighbor.x() - anchor.x();
                double dz = neighbor.z() - anchor.z();
                double distance = Math.hypot(dx, dz);
                if (distance <= DISTANCE_EPSILON) {
                    alwaysCovered++;
                    continue;
                }
                if (distance > DIAMETER + DISTANCE_EPSILON) {
                    continue;
                }
                double direction = normalizeAngle(Math.atan2(dz, dx));
                double halfWidth = Math.acos(Math.min(1.0, distance / DIAMETER));
                addInterval(events, direction - halfWidth, direction + halfWidth);
            }
            events.sort(Comparator.comparingDouble(AngleEvent::angle)
                    .thenComparing(Comparator.comparingInt(AngleEvent::starts).reversed()));

            int covered = alwaysCovered;
            int eventIndex = 0;
            while (eventIndex < events.size()) {
                double angle = events.get(eventIndex).angle();
                int starts = 0;
                int ends = 0;
                while (eventIndex < events.size()
                        && Double.compare(events.get(eventIndex).angle(), angle) == 0) {
                    starts += events.get(eventIndex).starts();
                    ends += events.get(eventIndex).ends();
                    eventIndex++;
                }
                covered += starts;
                if (covered >= optimalCandidates.nodeCount()) {
                    double centerX = anchor.x() + EFFECTIVE_NODE_RADIUS * Math.cos(angle);
                    double centerZ = anchor.z() + EFFECTIVE_NODE_RADIUS * Math.sin(angle);
                    optimalCandidates.consider(centerX, centerZ, covered);
                }
                covered -= ends;
            }
        }

        if (optimalCandidates.isEmpty()) {
            return List.of();
        }
        Set<GatheringNode> focusNodes = clusterFocused ? new HashSet<>(anchors) : Set.of();
        return optimalCandidates.entries().stream()
                .map(entry -> {
                    List<GatheringNode> coveredNodes = entry.getKey().nodes();
                    PlacementRegion placementRegion =
                            buildPlacementRegion(coveredNodes, spatialIndex, entry.getValue());
                    List<GatheringNode> finalCoveredNodes = spatialIndex.within(
                            placementRegion.bestPosition().x(),
                            placementRegion.bestPosition().z(),
                            EFFECTIVE_NODE_RADIUS);
                    int coveredClusterNodes = clusterFocused
                            ? (int) finalCoveredNodes.stream().filter(focusNodes::contains).count()
                            : 0;
                    return new Placement(
                            placementKey(finalCoveredNodes),
                            placementRegion.bestPosition().x(),
                            placementRegion.bestPosition().z(),
                            finalCoveredNodes,
                            coveredClusterNodes,
                            clusterFocused,
                            placementRegion.hull());
                })
                .sorted(Comparator.comparingDouble(Placement::x).thenComparingDouble(Placement::z))
                .toList();
    }

    private static String placementKey(List<GatheringNode> coveredNodes) {
        return coveredNodes.stream()
                .sorted(Comparator.comparingInt(GatheringNode::x)
                        .thenComparingInt(GatheringNode::z)
                        .thenComparingInt(GatheringNode::y)
                        .thenComparing(GatheringNode::resource)
                        .thenComparingInt(GatheringNode::level)
                        .thenComparing(GatheringNode::type)
                        .thenComparingInt(GatheringNode::angle))
                .map(node -> node.x()
                        + ","
                        + node.y()
                        + ","
                        + node.z()
                        + ","
                        + node.resource()
                        + ","
                        + node.level()
                        + ","
                        + node.type()
                        + ","
                        + node.angle())
                .collect(java.util.stream.Collectors.joining(";"));
    }

    private static PlacementRegion buildPlacementRegion(
            List<GatheringNode> coveredNodes,
            SpatialIndex spatialIndex,
            Candidate originalBest) {
        List<Position> feasiblePoints = new ArrayList<>();
        for (GatheringNode node : coveredNodes) {
            for (int segment = 0; segment < REGION_CIRCLE_SEGMENTS; segment++) {
                double angle = TWO_PI * segment / REGION_CIRCLE_SEGMENTS;
                Position position = new Position(
                        node.x() + EFFECTIVE_NODE_RADIUS * Math.cos(angle),
                        node.z() + EFFECTIVE_NODE_RADIUS * Math.sin(angle));
                if (isValidPosition(position, coveredNodes)) {
                    feasiblePoints.add(position);
                }
            }
        }
        for (int leftIndex = 0; leftIndex < coveredNodes.size(); leftIndex++) {
            for (int rightIndex = leftIndex + 1; rightIndex < coveredNodes.size(); rightIndex++) {
                for (Position intersection : circleIntersections(
                        coveredNodes.get(leftIndex),
                        coveredNodes.get(rightIndex))) {
                    if (isValidPosition(intersection, coveredNodes)) {
                        feasiblePoints.add(intersection);
                    }
                }
            }
        }

        Position originalPosition = new Position(originalBest.x(), originalBest.z());
        if (isValidPosition(originalPosition, coveredNodes)) {
            feasiblePoints.add(originalPosition);
        }
        if (feasiblePoints.isEmpty()) {
            return new PlacementRegion(originalPosition, List.of(originalPosition));
        }

        List<Position> initialHull = convexHull(feasiblePoints);
        Position regionCenter = polygonCenter(initialHull);
        Position bestPosition = bestIntegerPosition(coveredNodes, spatialIndex, regionCenter);
        if (bestPosition == null) {
            bestPosition = regionCenter;
        }
        feasiblePoints.add(bestPosition);
        return new PlacementRegion(bestPosition, convexHull(feasiblePoints));
    }

    private static List<Position> circleIntersections(GatheringNode left, GatheringNode right) {
        double dx = right.x() - left.x();
        double dz = right.z() - left.z();
        double distance = Math.hypot(dx, dz);
        if (distance <= DISTANCE_EPSILON || distance > DIAMETER + DISTANCE_EPSILON) {
            return List.of();
        }
        double midpointX = (left.x() + right.x()) / 2.0;
        double midpointZ = (left.z() + right.z()) / 2.0;
        double offset = Math.sqrt(Math.max(
                0,
                EFFECTIVE_NODE_RADIUS * EFFECTIVE_NODE_RADIUS - distance * distance / 4.0));
        double offsetX = -dz / distance * offset;
        double offsetZ = dx / distance * offset;
        Position first = new Position(midpointX + offsetX, midpointZ + offsetZ);
        Position second = new Position(midpointX - offsetX, midpointZ - offsetZ);
        return first.equals(second) ? List.of(first) : List.of(first, second);
    }

    private static Position bestIntegerPosition(
            List<GatheringNode> coveredNodes,
            SpatialIndex spatialIndex,
            Position regionCenter) {
        double minX = coveredNodes.stream()
                .mapToDouble(node -> node.x() - EFFECTIVE_NODE_RADIUS)
                .max()
                .orElse(regionCenter.x());
        double maxX = coveredNodes.stream()
                .mapToDouble(node -> node.x() + EFFECTIVE_NODE_RADIUS)
                .min()
                .orElse(regionCenter.x());
        double minZ = coveredNodes.stream()
                .mapToDouble(node -> node.z() - EFFECTIVE_NODE_RADIUS)
                .max()
                .orElse(regionCenter.z());
        double maxZ = coveredNodes.stream()
                .mapToDouble(node -> node.z() + EFFECTIVE_NODE_RADIUS)
                .min()
                .orElse(regionCenter.z());
        Candidate best = null;
        double bestCenterDistance = Double.POSITIVE_INFINITY;
        for (int x = (int) Math.ceil(minX); x <= (int) Math.floor(maxX); x++) {
            for (int z = (int) Math.ceil(minZ); z <= (int) Math.floor(maxZ); z++) {
                Position position = new Position(x, z);
                if (!isValidPosition(position, coveredNodes)) {
                    continue;
                }
                Candidate candidate = candidateAt(x, z, spatialIndex);
                double centerDistance = Math.hypot(x - regionCenter.x(), z - regionCenter.z());
                if (best == null
                        || candidate.nodeCount() > best.nodeCount()
                        || (candidate.nodeCount() == best.nodeCount()
                                && (centerDistance < bestCenterDistance - DISTANCE_EPSILON
                                        || (Math.abs(centerDistance - bestCenterDistance) <= DISTANCE_EPSILON
                                                && hasEarlierCoordinates(candidate, best))))) {
                    best = candidate;
                    bestCenterDistance = centerDistance;
                }
            }
        }
        return best == null ? null : new Position(best.x(), best.z());
    }

    private static boolean isValidPosition(Position position, List<GatheringNode> coveredNodes) {
        double radiusSquared = EFFECTIVE_NODE_RADIUS * EFFECTIVE_NODE_RADIUS + DISTANCE_EPSILON;
        for (GatheringNode node : coveredNodes) {
            double dx = node.x() - position.x();
            double dz = node.z() - position.z();
            if (dx * dx + dz * dz > radiusSquared) {
                return false;
            }
        }
        return true;
    }

    private static List<Position> convexHull(List<Position> positions) {
        List<Position> sorted = positions.stream()
                .distinct()
                .sorted(Comparator.comparingDouble(Position::x).thenComparingDouble(Position::z))
                .toList();
        if (sorted.size() <= 1) {
            return sorted;
        }
        List<Position> lower = new ArrayList<>();
        for (Position position : sorted) {
            while (lower.size() >= 2
                    && cross(lower.get(lower.size() - 2), lower.getLast(), position) <= 0) {
                lower.removeLast();
            }
            lower.add(position);
        }
        List<Position> upper = new ArrayList<>();
        for (int index = sorted.size() - 1; index >= 0; index--) {
            Position position = sorted.get(index);
            while (upper.size() >= 2
                    && cross(upper.get(upper.size() - 2), upper.getLast(), position) <= 0) {
                upper.removeLast();
            }
            upper.add(position);
        }
        lower.removeLast();
        upper.removeLast();
        lower.addAll(upper);
        return List.copyOf(lower);
    }

    private static double cross(Position origin, Position left, Position right) {
        return (left.x() - origin.x()) * (right.z() - origin.z())
                - (left.z() - origin.z()) * (right.x() - origin.x());
    }

    private static Position polygonCenter(List<Position> hull) {
        if (hull.size() == 1) {
            return hull.getFirst();
        }
        if (hull.size() == 2) {
            return new Position(
                    (hull.getFirst().x() + hull.getLast().x()) / 2.0,
                    (hull.getFirst().z() + hull.getLast().z()) / 2.0);
        }
        double areaTwice = 0;
        double weightedX = 0;
        double weightedZ = 0;
        for (int index = 0; index < hull.size(); index++) {
            Position current = hull.get(index);
            Position next = hull.get((index + 1) % hull.size());
            double cross = current.x() * next.z() - next.x() * current.z();
            areaTwice += cross;
            weightedX += (current.x() + next.x()) * cross;
            weightedZ += (current.z() + next.z()) * cross;
        }
        if (Math.abs(areaTwice) <= DISTANCE_EPSILON) {
            return new Position(
                    hull.stream().mapToDouble(Position::x).average().orElse(0),
                    hull.stream().mapToDouble(Position::z).average().orElse(0));
        }
        return new Position(weightedX / (3.0 * areaTwice), weightedZ / (3.0 * areaTwice));
    }

    private static Set<String> normalizeResources(Set<String> selectedResources) {
        if (selectedResources == null || selectedResources.isEmpty()) {
            return Set.of();
        }
        return selectedResources.stream()
                .filter(resource -> resource != null && !resource.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Candidate candidateAt(double x, double z, SpatialIndex spatialIndex) {
        return new Candidate(x, z, spatialIndex.within(x, z, EFFECTIVE_NODE_RADIUS).size());
    }

    private static Candidate better(Candidate current, Candidate candidate) {
        if (current == null || candidate.nodeCount() > current.nodeCount()) {
            return candidate;
        }
        if (candidate.nodeCount() < current.nodeCount()) {
            return current;
        }
        if (hasEarlierCoordinates(candidate, current)) {
            return candidate;
        }
        return current;
    }

    private static boolean hasEarlierCoordinates(Candidate candidate, Candidate current) {
        int xComparison = Double.compare(candidate.x(), current.x());
        return xComparison < 0 || (xComparison == 0 && Double.compare(candidate.z(), current.z()) < 0);
    }

    private static void addInterval(List<AngleEvent> events, double start, double end) {
        double normalizedStart = normalizeAngle(start);
        double width = end - start;
        double normalizedEnd = normalizedStart + width;
        if (normalizedEnd <= TWO_PI) {
            events.add(new AngleEvent(normalizedStart, 1, 0));
            events.add(new AngleEvent(normalizedEnd, 0, 1));
            return;
        }
        events.add(new AngleEvent(0, 1, 0));
        events.add(new AngleEvent(normalizedEnd - TWO_PI, 0, 1));
        events.add(new AngleEvent(normalizedStart, 1, 0));
        events.add(new AngleEvent(TWO_PI, 0, 1));
    }

    private static double normalizeAngle(double angle) {
        double normalized = angle % TWO_PI;
        return normalized < 0 ? normalized + TWO_PI : normalized;
    }

    public record Placement(
            String key,
            double x,
            double z,
            List<GatheringNode> coveredNodes,
            int coveredClusterNodes,
            boolean clusterFocused,
            List<Position> validCenterHull) {
        public Placement {
            coveredNodes = List.copyOf(coveredNodes);
            validCenterHull = List.copyOf(validCenterHull);
        }

        public int nodeCount() {
            return coveredNodes.size();
        }

        public double expectedItemsPerGather() {
            return nodeCount() * (1.0 + DOUBLE_GATHER_CHANCE);
        }
    }

    public record Position(double x, double z) {}

    private record PlacementRegion(Position bestPosition, List<Position> hull) {}

    private record CoveredNodeKey(List<GatheringNode> nodes) {
        private CoveredNodeKey {
            nodes = List.copyOf(nodes);
        }
    }

    private record Candidate(double x, double z, int nodeCount) {}

    private record AngleEvent(double angle, int starts, int ends) {}

    private record Cell(int x, int z) {}

    private static final class OptimalCandidates {
        private final SpatialIndex spatialIndex;
        private final Map<CoveredNodeKey, Candidate> candidates = new HashMap<>();
        private int nodeCount;

        private OptimalCandidates(SpatialIndex spatialIndex) {
            this.spatialIndex = spatialIndex;
        }

        private void consider(double x, double z, int estimatedNodeCount) {
            if (estimatedNodeCount < nodeCount) {
                return;
            }
            List<GatheringNode> coveredNodes = spatialIndex.within(x, z, EFFECTIVE_NODE_RADIUS);
            int actualNodeCount = coveredNodes.size();
            if (actualNodeCount < nodeCount) {
                return;
            }
            if (actualNodeCount > nodeCount) {
                nodeCount = actualNodeCount;
                candidates.clear();
            }
            CoveredNodeKey key = new CoveredNodeKey(coveredNodes);
            Candidate candidate = new Candidate(x, z, actualNodeCount);
            candidates.merge(key, candidate, GatheringTotemSolver::better);
        }

        private int nodeCount() {
            return nodeCount;
        }

        private boolean isEmpty() {
            return candidates.isEmpty();
        }

        private List<Map.Entry<CoveredNodeKey, Candidate>> entries() {
            return List.copyOf(candidates.entrySet());
        }
    }

    private static final class SpatialIndex {
        private static final double CELL_SIZE = DIAMETER;

        private final Map<Cell, List<GatheringNode>> cells = new HashMap<>();

        private SpatialIndex(List<GatheringNode> nodes) {
            for (GatheringNode node : nodes) {
                cells.computeIfAbsent(cell(node.x(), node.z()), ignored -> new ArrayList<>()).add(node);
            }
        }

        private List<GatheringNode> within(double x, double z, double radius) {
            int minCellX = cellCoordinate(x - radius);
            int maxCellX = cellCoordinate(x + radius);
            int minCellZ = cellCoordinate(z - radius);
            int maxCellZ = cellCoordinate(z + radius);
            double radiusSquared = radius * radius + DISTANCE_EPSILON;
            List<GatheringNode> matches = new ArrayList<>();
            for (int cellX = minCellX; cellX <= maxCellX; cellX++) {
                for (int cellZ = minCellZ; cellZ <= maxCellZ; cellZ++) {
                    for (GatheringNode node : cells.getOrDefault(new Cell(cellX, cellZ), List.of())) {
                        double dx = node.x() - x;
                        double dz = node.z() - z;
                        if (dx * dx + dz * dz <= radiusSquared) {
                            matches.add(node);
                        }
                    }
                }
            }
            return matches;
        }

        private static Cell cell(double x, double z) {
            return new Cell(cellCoordinate(x), cellCoordinate(z));
        }

        private static int cellCoordinate(double coordinate) {
            return (int) Math.floor(coordinate / CELL_SIZE);
        }
    }
}
