package org.sequoia.seq.network.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftAuthServiceTest {

    @Test
    void validateChallengeRejectsExpiredChallenge() {
        MinecraftAuthChallengeResponse challenge = new MinecraftAuthChallengeResponse(
                "challenge-1", "1a2b3c", Instant.now().minusSeconds(1));

        AuthException exception =
                assertThrows(AuthException.class, () -> MinecraftAuthService.validateChallenge(challenge));

        assertEquals(AuthErrorCode.CHALLENGE_EXPIRED, exception.getCode());
    }

    @Test
    void validateChallengeNormalizesBase64UrlServerId() {
        MinecraftAuthChallengeResponse challenge = new MinecraftAuthChallengeResponse(
                "challenge-1", "ZRUOgk626zsp_mVLVzH1ruw3K9K7AvSb", Instant.now().plusSeconds(60));

        MinecraftAuthChallengeResponse validated = MinecraftAuthService.validateChallenge(challenge);

        assertEquals("-671ac3219113b404f6f40fff33710d4390e69ffa", validated.serverId());
    }

    @Test
    void validateChallengeRejectsInvalidServerIdEncoding() {
        MinecraftAuthChallengeResponse challenge = new MinecraftAuthChallengeResponse(
                "challenge-1", "not-base64!", Instant.now().plusSeconds(60));

        AuthException exception =
                assertThrows(AuthException.class, () -> MinecraftAuthService.validateChallenge(challenge));

        assertEquals(AuthErrorCode.MALFORMED_RESPONSE, exception.getCode());
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

    @Test
    void sessionMatchesActiveProfileRequiresMatchingUuid() {
        UUID activeUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        StoredAuthSession session = new StoredAuthSession(
                "backend-token",
                Instant.now().plusSeconds(300),
                "123e4567-e89b-12d3-a456-426614174000",
                "VerifiedPlayer");

        assertEquals(true, MinecraftAuthService.sessionMatchesActiveProfile(session, activeUuid));
        assertEquals(
                false,
                MinecraftAuthService.sessionMatchesActiveProfile(
                        session, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")));
    }

    @Test
    void sessionMatchesActiveProfileRejectsMissingOrInvalidStoredUuid() {
        UUID activeUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

        assertEquals(
                false,
                MinecraftAuthService.sessionMatchesActiveProfile(
                        new StoredAuthSession("token", Instant.now().plusSeconds(300), null, "Player"), activeUuid));
        assertEquals(
                false,
                MinecraftAuthService.sessionMatchesActiveProfile(
                        new StoredAuthSession("token", Instant.now().plusSeconds(300), "not-a-uuid", "Player"),
                        activeUuid));
    }
}
