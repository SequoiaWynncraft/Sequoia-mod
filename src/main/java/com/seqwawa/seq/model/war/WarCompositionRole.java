package com.seqwawa.seq.model.war;

import com.google.gson.annotations.SerializedName;
import java.util.Arrays;
import java.util.List;

/** Read-only war composition capabilities sourced from Discord roles. */
public enum WarCompositionRole {
    @SerializedName("SOLO")
    SOLO("Solo", "mage"),
    @SerializedName("DPS")
    DPS("DPS", "shaman"),
    @SerializedName("TANK")
    TANK("Tank", "warrior");

    private final String label;
    private final String assetKey;

    WarCompositionRole(String label, String assetKey) {
        this.label = label;
        this.assetKey = assetKey;
    }

    public String label() {
        return label;
    }

    public String assetKey() {
        return assetKey;
    }

    /** Adds the implicit Solo capability, then removes null/duplicate values in contract order. */
    public static List<WarCompositionRole> ordered(List<WarCompositionRole> roles) {
        return Arrays.stream(values())
                .filter(role -> role == SOLO || roles != null && roles.contains(role))
                .toList();
    }
}
