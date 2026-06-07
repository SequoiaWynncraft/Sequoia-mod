package org.sequoia.seq.map;

public enum ClusterScoreMode {
    FOUR_TICK("4tick", 16),
    THREE_TICK("3tick", 20);

    private final String label;
    private final int nodeScoreCap;

    ClusterScoreMode(String label, int nodeScoreCap) {
        this.label = label;
        this.nodeScoreCap = nodeScoreCap;
    }

    public String label() {
        return label;
    }

    public int nodeScoreCap() {
        return nodeScoreCap;
    }

    public ClusterScoreMode next() {
        return this == FOUR_TICK ? THREE_TICK : FOUR_TICK;
    }
}
