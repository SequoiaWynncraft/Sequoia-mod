package org.sequoia.seq.network.auth;

import com.google.gson.annotations.SerializedName;
import java.time.Instant;

public record WynnOAuthStartResponse(
        @SerializedName("authorization_url") String authorizationUrl,
        @SerializedName("poll_token") String pollToken,
        @SerializedName("expires_at") Instant expiresAt) {}
