package org.sequoia.seq.map;

public final class GatheringMapSettings {
    private static final GatheringMapSettings INSTANCE = new GatheringMapSettings();

    private int clusterEps = GatheringNodeClusterer.DEFAULT_EPS;
    private int clusterMinSamples = GatheringNodeClusterer.DEFAULT_MIN_SAMPLES;
    private long version;

    private GatheringMapSettings() {}

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
