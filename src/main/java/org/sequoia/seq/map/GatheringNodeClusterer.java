package org.sequoia.seq.map;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class GatheringNodeClusterer {
    public static final int DEFAULT_EPS = 20;
    public static final int DEFAULT_MIN_SAMPLES = 8;

    private GatheringNodeClusterer() {}

    public static List<GatheringNodeCluster> analyze(
            List<GatheringNode> nodes,
            int eps,
            int minSamples,
            ClusterScoreMode scoreMode) {
        Map<GroupKey, List<GatheringNode>> groups = new HashMap<>();
        for (GatheringNode node : nodes) {
            GroupKey key = new GroupKey(node.profession(), node.level(), node.resource());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(node);
        }

        List<GatheringNodeCluster> clusters = new ArrayList<>();
        int nextClusterId = 0;
        List<GroupKey> sortedKeys = groups.keySet().stream()
                .sorted(Comparator.comparing(GroupKey::profession)
                        .thenComparingInt(GroupKey::level)
                        .thenComparing(GroupKey::resource))
                .toList();
        for (GroupKey key : sortedKeys) {
            List<GatheringNode> groupNodes = groups.get(key);
            for (List<GatheringNode> members : connectedComponents(groupNodes, eps)) {
                if (members.size() < minSamples) {
                    continue;
                }
                clusters.add(buildCluster(nextClusterId, key.profession(), key.resource(), members));
                nextClusterId++;
            }
        }
        return scoreClusters(clusters, scoreMode);
    }

    private static GatheringNodeCluster buildCluster(
            int id,
            GatheringProfession profession,
            String resource,
            List<GatheringNode> members) {
        List<ClusterOutlinePoint> outline = computeClusterOutline(members);
        double centerX = members.stream().mapToDouble(GatheringNode::x).average().orElse(0);
        double centerZ = members.stream().mapToDouble(GatheringNode::z).average().orElse(0);
        double area = Math.max(1, polygonArea(outline));
        return new GatheringNodeCluster(
                id,
                profession,
                members.size(),
                members.stream().mapToInt(GatheringNode::level).min().orElse(0),
                members.stream().mapToInt(GatheringNode::level).max().orElse(0),
                resource,
                outline,
                List.copyOf(members),
                centerX,
                centerZ,
                area,
                members.size() / area,
                averageNearestNeighborDistance(members),
                0);
    }

    // TODO : Find a better scoring system
    private static List<GatheringNodeCluster> scoreClusters(
            List<GatheringNodeCluster> clusters,
            ClusterScoreMode scoreMode) {
        double bestSpacing = clusters.stream()
                .mapToDouble(GatheringNodeCluster::averageSpacing)
                .filter(spacing -> spacing > 0)
                .min()
                .orElse(0);
        return clusters.stream()
                .map(cluster -> {
                    double nodeScore = (Math.min(cluster.nodeCount(), scoreMode.nodeScoreCap())
                            / (double) scoreMode.nodeScoreCap()) * 70.0;
                    double distanceScore = cluster.averageSpacing() > 0 && bestSpacing > 0
                            ? (bestSpacing / cluster.averageSpacing()) * 30.0
                            : 30.0;
                    double score = Math.round(Math.min(100.0, nodeScore + distanceScore) * 10.0) / 10.0;
                    return new GatheringNodeCluster(
                            cluster.id(),
                            cluster.profession(),
                            cluster.nodeCount(),
                            cluster.levelMin(),
                            cluster.levelMax(),
                            cluster.resource(),
                            cluster.outline(),
                            cluster.nodes(),
                            cluster.centerX(),
                            cluster.centerZ(),
                            cluster.area(),
                            cluster.density(),
                            cluster.averageSpacing(),
                            score);
                })
                .sorted(Comparator.comparingDouble(GatheringNodeCluster::score)
                        .reversed()
                        .thenComparing(Comparator.comparingInt(GatheringNodeCluster::nodeCount).reversed()))
                .toList();
    }

    private static List<List<GatheringNode>> connectedComponents(List<GatheringNode> nodes, int eps) {
        boolean[] visited = new boolean[nodes.size()];
        List<List<GatheringNode>> components = new ArrayList<>();
        for (int startIndex = 0; startIndex < nodes.size(); startIndex++) {
            if (visited[startIndex]) {
                continue;
            }
            visited[startIndex] = true;
            List<GatheringNode> component = new ArrayList<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(startIndex);
            component.add(nodes.get(startIndex));
            while (!queue.isEmpty()) {
                int pointIndex = queue.removeFirst();
                for (int neighborIndex = 0; neighborIndex < nodes.size(); neighborIndex++) {
                    if (visited[neighborIndex]) {
                        continue;
                    }
                    if (distance(nodes.get(pointIndex), nodes.get(neighborIndex)) > eps) {
                        continue;
                    }
                    visited[neighborIndex] = true;
                    queue.addLast(neighborIndex);
                    component.add(nodes.get(neighborIndex));
                }
            }
            components.add(component);
        }
        return components;
    }

    private static List<ClusterOutlinePoint> computeClusterOutline(List<GatheringNode> nodes) {
        Set<ClusterOutlinePoint> uniquePoints = new HashSet<>();
        for (GatheringNode node : nodes) {
            uniquePoints.add(new ClusterOutlinePoint(node.x(), node.z()));
        }
        return convexHull(uniquePoints.stream()
                .sorted(Comparator.comparingDouble(ClusterOutlinePoint::x).thenComparingDouble(ClusterOutlinePoint::z))
                .toList());
    }

    private static List<ClusterOutlinePoint> convexHull(List<ClusterOutlinePoint> points) {
        if (points.size() <= 1) {
            return points;
        }

        List<ClusterOutlinePoint> lower = new ArrayList<>();
        for (ClusterOutlinePoint point : points) {
            while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), point) <= 0) {
                lower.remove(lower.size() - 1);
            }
            lower.add(point);
        }

        List<ClusterOutlinePoint> upper = new ArrayList<>();
        for (int index = points.size() - 1; index >= 0; index--) {
            ClusterOutlinePoint point = points.get(index);
            while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), point) <= 0) {
                upper.remove(upper.size() - 1);
            }
            upper.add(point);
        }

        lower.remove(lower.size() - 1);
        upper.remove(upper.size() - 1);
        lower.addAll(upper);
        return lower;
    }

    private static double cross(ClusterOutlinePoint origin, ClusterOutlinePoint a, ClusterOutlinePoint b) {
        return (a.x() - origin.x()) * (b.z() - origin.z()) - (a.z() - origin.z()) * (b.x() - origin.x());
    }

    private static double polygonArea(List<ClusterOutlinePoint> points) {
        if (points.size() < 3) {
            return 1;
        }
        double sum = 0;
        for (int index = 0; index < points.size(); index++) {
            ClusterOutlinePoint current = points.get(index);
            ClusterOutlinePoint next = points.get((index + 1) % points.size());
            sum += current.x() * next.z() - next.x() * current.z();
        }
        return Math.abs(sum) / 2.0;
    }

    private static double averageNearestNeighborDistance(List<GatheringNode> nodes) {
        if (nodes.size() < 2) {
            return 0;
        }
        double sum = 0;
        for (int index = 0; index < nodes.size(); index++) {
            double closest = Double.POSITIVE_INFINITY;
            for (int otherIndex = 0; otherIndex < nodes.size(); otherIndex++) {
                if (index == otherIndex) {
                    continue;
                }
                closest = Math.min(closest, distance(nodes.get(index), nodes.get(otherIndex)));
            }
            sum += closest;
        }
        return sum / nodes.size();
    }

    private static double distance(GatheringNode left, GatheringNode right) {
        return Math.hypot(left.x() - right.x(), left.z() - right.z());
    }

    private record GroupKey(GatheringProfession profession, int level, String resource) {
        @Override
        public String toString() {
            return profession.name().toLowerCase(Locale.ROOT) + ":" + level + ":" + resource;
        }
    }
}
