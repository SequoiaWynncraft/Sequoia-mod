package com.seqwawa.seq.model.war;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;

/** Shared last-observed Sequoia HQ; this is never a manually assigned map marker. */
public record GuildWarState(
        @SerializedName("schema_version") int schemaVersion,
        long revision,
        @SerializedName("server_time") Instant serverTime,
        @SerializedName("hq_territory") String hqTerritory,
        @SerializedName("hq_observed_at") Instant hqObservedAt,
        @SerializedName("hq_source") String hqSource,
        @SerializedName("hq_style") String hqStyle,
        @SerializedName("style_observed_at") Instant styleObservedAt,
        @SerializedName("styles_invalidated_at") Instant stylesInvalidatedAt,
        @SerializedName("valid_until") Instant validUntil) {
    public boolean supported() { return schemaVersion == 1 && serverTime != null; }
}
