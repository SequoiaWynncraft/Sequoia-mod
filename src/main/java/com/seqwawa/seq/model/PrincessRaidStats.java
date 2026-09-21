package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** Versioned snapshot of the playful, client-reported Princess raid leaderboard. */
public record PrincessRaidStats(
        @SerializedName("schema_version") int schemaVersion,
        Self self,
        List<LeaderboardEntry> leaderboard) {

    public PrincessRaidStats {
        leaderboard = leaderboard == null ? List.of() : List.copyOf(leaderboard);
    }

    public record Self(
            @SerializedName("minecraft_username") String minecraftUsername,
            @SerializedName("raid_count") long raidCount,
            Integer rank) {}

    public record LeaderboardEntry(
            int rank,
            @SerializedName("minecraft_username") String minecraftUsername,
            @SerializedName("raid_count") long raidCount) {}
}
