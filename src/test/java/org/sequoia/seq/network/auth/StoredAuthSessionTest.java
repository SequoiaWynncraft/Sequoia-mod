package org.sequoia.seq.network.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredAuthSessionTest {

    @Test
    void sessionWithoutExpiryIsTreatedAsExpired() {
        StoredAuthSession session = new StoredAuthSession("token", null, null, null);

        assertTrue(session.isExpired(Instant.now()));
    }

    @Test
    void expiringSoonSessionTriggersRefreshWindow() {
        Instant now = Instant.now();
        StoredAuthSession session = new StoredAuthSession(
                "token", now.plusSeconds(10), "123e4567-e89b-12d3-a456-426614174000", "VerifiedPlayer");

        assertTrue(session.expiresWithin(Duration.ofSeconds(30), now));
        assertFalse(session.expiresWithin(Duration.ofSeconds(5), now));
    }
}
