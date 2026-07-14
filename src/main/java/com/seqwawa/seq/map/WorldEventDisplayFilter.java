package com.seqwawa.seq.map;

public enum WorldEventDisplayFilter {
    ALL("All"),
    TRACKED("Tracked");

    private final String label;

    WorldEventDisplayFilter(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
