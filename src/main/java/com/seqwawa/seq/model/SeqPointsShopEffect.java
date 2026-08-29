package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;

/** Active SeqMod-only cosmetic effect. */
public record SeqPointsShopEffect(
        long id,
        @SerializedName("target_player_uuid") String targetPlayerUuid,
        @SerializedName("target_username") String targetUsername,
        String value,
        @SerializedName("starts_at") Instant startsAt,
        @SerializedName("ends_at") Instant endsAt) {}
