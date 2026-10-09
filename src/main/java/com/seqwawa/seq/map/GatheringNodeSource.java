package com.seqwawa.seq.map;

public enum GatheringNodeSource {
    STATIC("Static file"),
    WYNN_API("Wynn API");

    private final String label;

    GatheringNodeSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
