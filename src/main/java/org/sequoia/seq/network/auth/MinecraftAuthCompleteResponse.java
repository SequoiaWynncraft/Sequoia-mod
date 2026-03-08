package org.sequoia.seq.network.auth;

import java.time.Instant;

public record MinecraftAuthCompleteResponse(String token, Instant expires_at, AuthenticatedMinecraftUser user) {

    public Instant expiresAt() {
        return expires_at;
    }
}
