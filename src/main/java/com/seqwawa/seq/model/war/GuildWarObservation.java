package com.seqwawa.seq.model.war;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;

public record GuildWarObservation(
        String source, String territory, String style, @SerializedName("observed_at") Instant observedAt) {}
