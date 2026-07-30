package com.seqwawa.seq.map;

public enum IngredientMapCategory {
    SPAWNS("Spawns"),
    TOTEM_SPOTS("Totem Spots");

    private final String label;

    IngredientMapCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
