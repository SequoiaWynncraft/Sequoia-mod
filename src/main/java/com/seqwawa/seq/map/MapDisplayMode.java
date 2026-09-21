package com.seqwawa.seq.map;

public enum MapDisplayMode {
    GATHERING("Gathering"),
    WORLD_EVENTS("Events"),
    INGREDIENTS("Ingredients");

    private final String label;

    MapDisplayMode(String label) {
        this.label = label;
    }

    public String mapTitle() {
        return switch (this) {
            case GATHERING -> "Sequoia Gathering Map";
            case WORLD_EVENTS -> "Sequoia Event Map";
            case INGREDIENTS -> "Sequoia Ingredient Map";
        };
    }

    public String label() {
        return label;
    }
}
