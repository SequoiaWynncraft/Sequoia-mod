package org.sequoia.seq.map;

import java.util.List;

public record GatheringNodeCluster(
        int id,
        GatheringProfession profession,
        int nodeCount,
        int levelMin,
        int levelMax,
        String resource,
        List<ClusterOutlinePoint> outline,
        List<GatheringNode> nodes,
        double centerX,
        double centerZ,
        double area,
        double density,
        double averageSpacing,
        double score) {}
