package com.seqwawa.seq.map;

public enum GatheringAnalysisScope {
    ALL("All"),
    ANY_TERRITORY("Territories"),
    SELECTED_TERRITORY("Selected");

    private final String label;

    GatheringAnalysisScope(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
