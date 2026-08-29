package com.seqwawa.seq.model;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;
import java.util.List;

/** Lightweight effect-feed response. */
public record SeqPointsEffects(
        @SerializedName("schema_version") int schemaVersion,
        @SerializedName("server_time") Instant serverTime,
        List<SeqPointsShopEffect> effects) {

    public SeqPointsEffects {
        effects = effects == null ? List.of() : List.copyOf(effects);
    }
}
