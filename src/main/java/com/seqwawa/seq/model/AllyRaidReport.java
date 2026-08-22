package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.List;

public record AllyRaidReport(
        @SerializedName("schema_version") int schemaVersion,
        @SerializedName("generated_at") Instant generatedAt,
        @SerializedName("cutoff_minutes") int cutoffMinutes,
        @SerializedName("protected_allies") List<GuildActivity> protectedAllies,
        List<GuildActivity> recent,
        @SerializedName("safe_to_review") List<GuildActivity> safeToReview,
        List<GuildActivity> unavailable) {

    public record GuildActivity(
            @SerializedName("guild_name") String guildName,
            @SerializedName("guild_prefix") String guildPrefix,
            @SerializedName("last_raided_at") Instant lastRaidedAt,
            @SerializedName("raid_count") int raidCount,
            @SerializedName("roster_available") boolean rosterAvailable) {}
}
