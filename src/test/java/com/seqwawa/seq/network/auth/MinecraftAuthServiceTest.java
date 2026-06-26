package com.seqwawa.seq.network.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftAuthServiceTest {

    @Test
    void validateChallengeAcceptsFreshHexServerId() {
        MinecraftAuthChallengeResponse response = new MinecraftAuthChallengeResponse(
                "challenge-1",
                "0123456789abcdef0123456789abcdef01234567",
                Instant.now().plusSeconds(45));

        assertEquals(response, MinecraftAuthService.validateChallenge(response));
    }

    @Test
    void validateChallengeRejectsMalformedServerId() {
        MinecraftAuthChallengeResponse response =
                new MinecraftAuthChallengeResponse("challenge-1", "not-a-server-id", Instant.now().plusSeconds(45));

        AuthException exception = assertThrows(AuthException.class, () -> MinecraftAuthService.validateChallenge(response));

        assertEquals(AuthErrorCode.MALFORMED_RESPONSE, exception.getCode());
    }

    @Test
    void validateChallengeRejectsExpiredChallenge() {
        MinecraftAuthChallengeResponse response = new MinecraftAuthChallengeResponse(
                "challenge-1",
                "0123456789abcdef0123456789abcdef01234567",
                Instant.now().minusSeconds(1));

        AuthException exception = assertThrows(AuthException.class, () -> MinecraftAuthService.validateChallenge(response));

        assertEquals(AuthErrorCode.CHALLENGE_EXPIRED, exception.getCode());
    }

    @Test
    void toStoredSessionUsesAuthoritativeBackendIdentity() {
        MinecraftAuthCompleteResponse response = new MinecraftAuthCompleteResponse(
                "backend-token",
                Instant.now().plusSeconds(300),
                new AuthenticatedMinecraftUser("123e4567-e89b-12d3-a456-426614174000", "VerifiedPlayer"));

        StoredAuthSession session = MinecraftAuthService.toStoredSession(response);

        assertEquals("backend-token", session.token());
        assertEquals("123e4567-e89b-12d3-a456-426614174000", session.minecraftUuid());
        assertEquals("VerifiedPlayer", session.minecraftUsername());
    }
}
