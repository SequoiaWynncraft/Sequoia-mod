package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;

public record LeaderboardBadgeAssignment(
        @SerializedName("player_uuid") String playerUuid,
        String event,
        String tier,
        @SerializedName("badge") String legacyBadge) {
    public LeaderboardBadgeAssignment(String playerUuid, SeqBadge badge) {
        this(playerUuid, badge.event().apiName(), badge.tier().apiName(), null);
    }
}
