package com.seqwawa.seq.model.war;

import java.util.Locale;

/** Manager-selected team category. The backend owns uniqueness and numeric suffixes. */
public enum WarTeamType {
    HQ("HQ Team", "HQ Team"),
    VLOW_MUNCH("VLow Munch", "VLow Munch "),
    FFA("FFA", "FFA ");

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

    public static WarTeamType fromTeamName(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("hq team")) return HQ;
        if (normalized.startsWith("ffa ")) return FFA;
        return VLOW_MUNCH;
    }
}
