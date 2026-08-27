package com.seqwawa.seq.model.war;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;

/** Compact authorization and availability state used outside the full planner UI. */
public record WarPlannerAccess(
        @SerializedName("schema_version") int schemaVersion,
        @SerializedName("server_time") Instant serverTime,
        @SerializedName("player_uuid") String playerUuid,
        @SerializedName("available_until") Instant availableUntil) {
    public static final int SUPPORTED_SCHEMA_VERSION = 1;

    public boolean isSupported() {
        return schemaVersion == SUPPORTED_SCHEMA_VERSION;
    }
}
