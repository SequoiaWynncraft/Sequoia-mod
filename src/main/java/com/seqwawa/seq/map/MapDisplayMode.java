package com.seqwawa.seq.map;

public enum MapDisplayMode {
    GATHERING("Gathering"),
    WORLD_EVENTS("Events");

    private final String label;

    MapDisplayMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
