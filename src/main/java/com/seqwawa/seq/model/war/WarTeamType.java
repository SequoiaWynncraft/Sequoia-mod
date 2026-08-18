package com.seqwawa.seq.model.war;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Locale;

/** Manager-selected team category. The backend owns uniqueness and numeric suffixes. */
public enum WarTeamType {
    @SerializedName("UNKNOWN")
    UNKNOWN("Legacy team", ""),
    @SerializedName("HQ")
    HQ("HQ Team", "HQ Team"),
    @SerializedName("VLOW_MUNCH")
    VLOW_MUNCH("VLow Munch", "VLow Munch "),
    @SerializedName("FFA")
    FFA("FFA", "FFA ");

    private static final List<WarTeamType> EDITABLE_VALUES = List.of(HQ, VLOW_MUNCH, FFA);

    private final String label;
    private final String namePrefix;

    WarTeamType(String label, String namePrefix) {
        this.label = label;
        this.namePrefix = namePrefix;
    }

    public String label() {
        return label;
    }

    public String namePrefix() {
        return namePrefix;
    }

    public boolean editable() {
        return this != UNKNOWN;
    }

    public static List<WarTeamType> editableValues() {
        return EDITABLE_VALUES;
    }

    /** Legacy compatibility only. New snapshots carry an explicit {@code team_type}. */
    public static WarTeamType fromTeamName(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("hq team")) return HQ;
        if (normalized.startsWith("ffa ")) return FFA;
        if (normalized.startsWith("vlow munch ")) return VLOW_MUNCH;
        return UNKNOWN;
    }
}
