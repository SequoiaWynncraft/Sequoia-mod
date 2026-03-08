package org.sequoia.seq.network.auth;

import java.time.Duration;
import java.time.Instant;

public record StoredAuthSession(String token, Instant expiresAt, String minecraftUuid, String minecraftUsername) {

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public boolean isExpired(Instant now) {
        if (!hasToken() || expiresAt == null) {
            return true;
        }
        return !expiresAt.isAfter(now);
    }

    public boolean expiresWithin(Duration window, Instant now) {
        if (!hasToken() || expiresAt == null) {
            return true;
        }
        return !expiresAt.isAfter(now.plus(window));
    }
}
