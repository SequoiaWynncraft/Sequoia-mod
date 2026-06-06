package org.sequoia.seq.map;

import java.util.EnumMap;
import java.util.Set;
import java.util.TreeSet;

public final class GatheringMapSettings {
    private static final GatheringMapSettings INSTANCE = new GatheringMapSettings();

    private final EnumMap<GatheringProfession, Boolean> professionToggles = new EnumMap<>(GatheringProfession.class);
    private final Set<String> resourceFilters = new TreeSet<>();
    private int clusterEps = GatheringNodeClusterer.DEFAULT_EPS;
    private int clusterMinSamples = GatheringNodeClusterer.DEFAULT_MIN_SAMPLES;
    private boolean showClusters = true;
    private ClusterScoreMode clusterScoreMode = ClusterScoreMode.FOUR_TICK;
    private long version;

    private GatheringMapSettings() {
        for (GatheringProfession profession : GatheringProfession.values()) {
            professionToggles.put(profession, true);
        }
    }

    public static GatheringMapSettings getInstance() {
        return INSTANCE;
    }

    public synchronized int clusterEps() {
        return clusterEps;
    }

    public synchronized int clusterMinSamples() {
        return clusterMinSamples;
    }

    public synchronized long version() {
        return version;
    }

    public synchronized Set<String> resourceFilters() {
        return new TreeSet<>(resourceFilters);
    }

    public synchronized void setResourceFilters(Set<String> resourceFilters) {
        this.resourceFilters.clear();
        if (resourceFilters != null) {
            this.resourceFilters.addAll(resourceFilters);
        }
    }

    public synchronized EnumMap<GatheringProfession, Boolean> professionToggles() {
        return new EnumMap<>(professionToggles);
    }

    public synchronized void setProfessionEnabled(GatheringProfession profession, boolean enabled) {
        professionToggles.put(profession, enabled);
    }

    public synchronized boolean showClusters() {
        return showClusters;
    }

    public synchronized void setShowClusters(boolean showClusters) {
        this.showClusters = showClusters;
    }

    public synchronized ClusterScoreMode clusterScoreMode() {
        return clusterScoreMode;
    }

    public synchronized void setClusterScoreMode(ClusterScoreMode clusterScoreMode) {
        this.clusterScoreMode = clusterScoreMode == null ? ClusterScoreMode.FOUR_TICK : clusterScoreMode;
    }

    public synchronized void setClusterEps(int clusterEps) {
        if (this.clusterEps == clusterEps) {
            return;
        }
        this.clusterEps = clusterEps;
        version++;
        GatheringClusterCache.getInstance().clear();
    }

    public synchronized void setClusterMinSamples(int clusterMinSamples) {
        if (this.clusterMinSamples == clusterMinSamples) {
            return;
        }
        this.clusterMinSamples = clusterMinSamples;
        version++;
        GatheringClusterCache.getInstance().clear();
    }

    public synchronized void resetClusterParams() {
        boolean changed = clusterEps != GatheringNodeClusterer.DEFAULT_EPS
                || clusterMinSamples != GatheringNodeClusterer.DEFAULT_MIN_SAMPLES;
        clusterEps = GatheringNodeClusterer.DEFAULT_EPS;
        clusterMinSamples = GatheringNodeClusterer.DEFAULT_MIN_SAMPLES;
        if (changed) {
            version++;
            GatheringClusterCache.getInstance().clear();
        }
    }

    public synchronized String describe() {
        return "eps=" + clusterEps + ", minSamples=" + clusterMinSamples;
    }
}
