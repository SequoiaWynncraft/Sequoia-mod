package com.seqwawa.seq.map;

public enum GatheringTotemSearchTarget {
    ALL_FILTERED("All Filtered"),
    SELECTED_CLUSTER("Selected Cluster");

    private final String label;

    GatheringTotemSearchTarget(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
