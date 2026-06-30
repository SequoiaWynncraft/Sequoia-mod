package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;

public record LeaderboardBadgeAssignment(
        @SerializedName("player_uuid") String playerUuid,
        @SerializedName("type") String type,
        String tier,
        @SerializedName("badge") String legacyBadge) {
    public LeaderboardBadgeAssignment(String playerUuid, SeqBadge badge) {
        this(playerUuid, badge.type().apiName(), badge.tier().apiName(), null);
    }
}
