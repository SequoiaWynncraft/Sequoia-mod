package com.seqwawa.seq.network.auth;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.ConfigManager;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftAuthServiceTest {

    @Test
    void validateChallengeAcceptsFreshHexServerId() {
        MinecraftAuthChallengeResponse response = new MinecraftAuthChallengeResponse(
                "123e4567-e89b-12d3-a456-426614174000",
                "0123456789abcdef0123456789abcdef01234567",
                Instant.now().plusSeconds(45), 2);

        assertEquals(response, MinecraftAuthService.validateChallenge(response));
    }

    @Test
    void validateChallengeRejectsMalformedServerId() {
        MinecraftAuthChallengeResponse response =
                new MinecraftAuthChallengeResponse("123e4567-e89b-12d3-a456-426614174000", "not-a-server-id", Instant.now().plusSeconds(45), 2);

        AuthException exception = assertThrows(AuthException.class, () -> MinecraftAuthService.validateChallenge(response));

        assertEquals(AuthErrorCode.MALFORMED_RESPONSE, exception.getCode());
    }

    @Test
    void validateChallengeRejectsExpiredChallenge() {
        MinecraftAuthChallengeResponse response = new MinecraftAuthChallengeResponse(
                "123e4567-e89b-12d3-a456-426614174000",
                "0123456789abcdef0123456789abcdef01234567",
                Instant.now().minusSeconds(1), 2);

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

    @Test
    void clearedSessionInvalidatesADeferredAuthenticationAttempt() {
        long startedFor = 7;

        assertTrue(MinecraftAuthService.isCurrentAuthenticationAttempt(startedFor, 7));
        assertFalse(MinecraftAuthService.isCurrentAuthenticationAttempt(startedFor, 8));
    }
    @Test
    void rejectsLegacyBackendBeforeSessionJoin() {
        var legacy = new MinecraftAuthChallengeResponse("123e4567-e89b-12d3-a456-426614174000",
                "0123456789abcdef0123456789abcdef01234567", Instant.now().plusSeconds(45), 0);
        assertThrows(AuthException.class, () -> MinecraftAuthService.validateChallenge(legacy));
    }

    @Test
    void actualSessionServiceReceivesOnlyDerivedProofForMainAndRaidOrigins() {
        String seed = "0123456789abcdef0123456789abcdef01234567";
        String id = "123e4567-e89b-12d3-a456-426614174000";
        var joined = new java.util.concurrent.atomic.AtomicReference<String>();
        var minecraftService = (com.mojang.authlib.minecraft.MinecraftSessionService)
                java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[] {com.mojang.authlib.minecraft.MinecraftSessionService.class},
                        (proxy, method, args) -> {
                            if (!method.getName().equals("joinServer")) {
                                throw new AssertionError("Unexpected session-service operation");
                            }
                            joined.set((String) args[2]);
                            return null;
                        });
        var user = new net.minecraft.client.User("SyntheticPlayer", java.util.UUID.fromString(id),
                "synthetic-offline-fixture", java.util.Optional.empty(), java.util.Optional.empty());
        var auth = new MinecraftAuthService() {
            @Override
            net.minecraft.client.User requireLoggedInUser() { return user; }

            @Override
            com.mojang.authlib.minecraft.MinecraftSessionService resolveSessionService() {
                return minecraftService;
            }
        };
        for (String endpoint : new String[] {"https://api.seqwawa.com/api", "https://staging.seqwawa.com/api"}) {
            var challenge = new MinecraftAuthChallengeResponse(id, seed, Instant.now().plusSeconds(45), 2);
            var completion = auth.joinServer(challenge, endpoint);
            org.junit.jupiter.api.Assertions.assertNotEquals(seed, joined.get());
            assertEquals(MinecraftSessionProof.derive(endpoint, "account", id, seed, completion.client_nonce()),
                    joined.get());
            assertEquals(id, completion.challengeId());
            assertEquals("SyntheticPlayer", completion.username());
        }
        joined.set(null);
        assertThrows(AuthException.class, () -> auth.joinServer(new MinecraftAuthChallengeResponse(
                id, seed, Instant.now().minusSeconds(1), 2), "https://api.seqwawa.com/api"));
        org.junit.jupiter.api.Assertions.assertNull(joined.get());
    }

    @TempDir
    Path tempDir;

    private interface Scenario {
        void run() throws Exception;
    }

    private static final StoredAuthSession SESSION = new StoredAuthSession(
            "session-token", Instant.now().plusSeconds(86_400), "123e4567-e89b-12d3-a456-426614174000", "Player");

    /** Runs signed in, with a real token store under a temporary directory, as clearing a session needs one. */
    private void withConfig(Scenario scenario) throws Exception {
        withConfig(SESSION, scenario);
    }

    private void withConfig(StoredAuthSession session, Scenario scenario) throws Exception {
        ConfigManager previous = SeqClient.configManager;
        var constructor = ConfigManager.class.getDeclaredConstructor(Path.class, Path.class, boolean.class);
        constructor.setAccessible(true);
        SeqClient.configManager = constructor.newInstance(tempDir.resolve("config.json"), tempDir.resolve("token"), false);
        if (session != null) SeqClient.configManager.setAuthSession(session);
        try {
            scenario.run();
        } finally {
            SeqClient.configManager = previous;
        }
    }

    @Test
    void aRequestFromAClearedSessionNeitherSignsInNorGetsAToken() throws Exception {
        withConfig(() -> {
            AtomicInteger starts = new AtomicInteger();
            var auth = new MinecraftAuthService() {
                @Override
                public CompletableFuture<String> ensureValidToken(boolean forceRefresh) {
                    starts.incrementAndGet();
                    return CompletableFuture.completedFuture("token");
                }
            };
            long session = auth.sessionGeneration();
            assertEquals("token", auth.ensureSessionToken(false, session).join());

            // The session ends after the request began and before it asks for a token again.
            auth.clearSession();

            assertTrue(auth.ensureSessionToken(false, session).isCompletedExceptionally());
            assertTrue(auth.ensureSessionToken(true, session).isCompletedExceptionally(),
                    "nor the fresh sign-in a 401 asks for");
            assertEquals(1, starts.get());
        });
    }

    @Test
    void aClearCannotLandBetweenTheSessionCheckAndTheStartOfASignIn() throws Exception {
        withConfig(() -> {
            AtomicReference<Thread> clearing = new AtomicReference<>();
            var auth = new MinecraftAuthService() {
                @Override
                public CompletableFuture<String> ensureValidToken(boolean forceRefresh) {
                    // The check has passed and a sign-in is starting. A clear asked for now has to wait.
                    Thread clear = new Thread(this::clearSession);
                    clearing.set(clear);
                    clear.start();
                    long deadline = System.nanoTime() + 5_000_000_000L;
                    while (clear.getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) Thread.onSpinWait();
                    assertEquals(Thread.State.BLOCKED, clear.getState(), "the clear waits for the sign-in to start");
                    return CompletableFuture.completedFuture("token");
                }
            };
            long session = auth.sessionGeneration();

            auth.ensureSessionToken(false, session).join();
            clearing.get().join(5_000);

            assertFalse(clearing.get().isAlive());
            assertFalse(MinecraftAuthService.isCurrentAuthenticationAttempt(session, auth.sessionGeneration()),
                    "the clear went ahead once the sign-in had started");
        });
    }

    @Test
    void aRequestWithNoSessionNeverSignsIn() throws Exception {
        withConfig(null, () -> {
            AtomicInteger starts = new AtomicInteger();
            var auth = new MinecraftAuthService() {
                @Override
                public CompletableFuture<String> ensureValidToken(boolean forceRefresh) {
                    starts.incrementAndGet();
                    return CompletableFuture.completedFuture("token");
                }
            };

            var token = auth.ensureSessionToken(false, auth.sessionGeneration());

            var failure = assertThrows(java.util.concurrent.CompletionException.class, token::join);
            org.junit.jupiter.api.Assertions.assertInstanceOf(NotSignedInException.class, failure.getCause());
            assertEquals(0, starts.get());
        });
    }

    @Test
    void aPlayerWhoSignedOutIsNotSignedInAgainByAnyFeature() throws Exception {
        withConfig(() -> {
            var auth = new MinecraftAuthService();

            auth.signOut();

            org.junit.jupiter.api.Assertions.assertNull(auth.getCurrentSession());
            assertTrue(auth.isSignedOutByUser());
            // Raid profiles, achievements, ally raids and the Leaf stream all ask this way.
            assertNotSignedIn(auth.ensureValidToken(false));
            assertNotSignedIn(auth.ensureValidToken(true));
            assertNotSignedIn(auth.beginAuthentication());
            // A raid-profiles backend of its own would sign in separately.
            assertNotSignedIn(auth.authenticateAgainst("https://staging.seqwawa.com/api"));
            assertEquals(AuthState.IDLE, auth.getState());
        });
    }

    @Test
    void connectingExplicitlyEndsASignOut() throws Exception {
        withConfig(() -> {
            var auth = new MinecraftAuthService();
            auth.signOut();

            auth.allowSignIn();

            assertFalse(auth.isSignedOutByUser());
        });
    }

    @Test
    void anAccountChangeLeavesTheNextAccountFreeToSignIn() throws Exception {
        withConfig(() -> {
            var auth = new MinecraftAuthService();
            auth.signOut();

            auth.clearSession();

            assertFalse(auth.isSignedOutByUser(), "the next account signs in as usual");
            org.junit.jupiter.api.Assertions.assertNull(auth.getCurrentSession());
        });
    }

    @Test
    void clearingOrInvalidatingASessionIsNotASignOut() throws Exception {
        withConfig(() -> {
            var auth = new MinecraftAuthService();

            auth.clearSession();
            assertFalse(auth.isSignedOutByUser());

            SeqClient.configManager.setAuthSession(SESSION);
            auth.invalidateSession(AuthErrorCode.TOKEN_INVALID, "Backend rejected the stored token.");
            assertFalse(auth.isSignedOutByUser(), "a rejected token signs in afresh");
        });
    }

    private static void assertNotSignedIn(CompletableFuture<?> future) {
        assertTrue(future.isCompletedExceptionally(), "fails at once, without starting a sign-in");
        var failure = assertThrows(java.util.concurrent.CompletionException.class, future::join);
        org.junit.jupiter.api.Assertions.assertInstanceOf(NotSignedInException.class, failure.getCause());
    }
}
