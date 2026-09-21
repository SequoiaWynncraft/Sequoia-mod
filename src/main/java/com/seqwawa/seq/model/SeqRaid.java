package com.seqwawa.seq.model;

public enum SeqRaid {
    NOTG("NOTG", "Nest of the Grootslangs", "notg"),
    TNA("TNA", "The Nameless Anomaly", "tna"),
    TCC("TCC", "The Canyon Colossus", "tcc"),
    NOL("NOL", "Nexus of Light", "nol"),
    TWP("TWP", "The Wartorn Palace", "twp");

    private final String code;
    private final String displayName;
    private final String assetKey;

    SeqRaid(String code, String displayName, String assetKey) {
        this.code = code;
        this.displayName = displayName;
        this.assetKey = assetKey;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public String assetKey() {
        return assetKey;
    }
}
