package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TreasurySessionAuthenticatorTest {
    private static final String NONCE = "7505801b-9e89-4ef8-a32e-8d55e2f4d011";

    @Test
    void joinsMinecraftSessionThenRequiresCorrelatedBackendConfirmation() {
        AtomicReference<String> joined = new AtomicReference<>();
        AtomicReference<String> response = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        TreasurySessionAuthenticator authenticator = new TreasurySessionAuthenticator(
                joined::set, Runnable::run, response::set, failure::set);

        assertTrue(authenticator.handleChallenge(NONCE));

        assertEquals(NONCE, joined.get());
        assertEquals(NONCE, response.get());
        assertNull(failure.get());
        assertEquals(TreasurySessionAuthenticator.State.WAITING_BACKEND, authenticator.state());
        assertFalse(authenticator.confirm("different-nonce-0000000000000000"));
        assertFalse(authenticator.isAuthenticated());
        assertTrue(authenticator.confirm(NONCE));
        assertTrue(authenticator.isAuthenticated());
    }

    @Test
    void rejectsInvalidOrRepeatedChallenges() {
        AtomicInteger joinCalls = new AtomicInteger();
        TreasurySessionAuthenticator authenticator = new TreasurySessionAuthenticator(
                ignored -> joinCalls.incrementAndGet(),
                Runnable::run,
                ignored -> {},
                ignored -> {});

        assertFalse(authenticator.handleChallenge("short"));
        assertEquals(0, joinCalls.get());

        authenticator.reset();
        assertTrue(authenticator.handleChallenge(NONCE));
        assertFalse(authenticator.handleChallenge("another-valid-nonce-000000000000"));
        assertEquals(1, joinCalls.get());
    }

    @Test
    void ignoresMalformedLateChallengeAfterAuthentication() {
        AtomicInteger failureCalls = new AtomicInteger();
        TreasurySessionAuthenticator authenticator = new TreasurySessionAuthenticator(
                ignored -> {}, Runnable::run, ignored -> {}, ignored -> failureCalls.incrementAndGet());

        assertTrue(authenticator.handleChallenge(NONCE));
        assertTrue(authenticator.confirm(NONCE));

        assertFalse(authenticator.handleChallenge("short"));
        assertTrue(authenticator.isAuthenticated());
        assertEquals(TreasurySessionAuthenticator.State.AUTHENTICATED, authenticator.state());
        assertEquals(0, failureCalls.get());
    }

    @Test
    void invalidInitialChallengeInvokesFailureHandlerOutsideMonitor() {
        AtomicReference<TreasurySessionAuthenticator> authenticatorReference = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicReference<Boolean> handlerHeldMonitor = new AtomicReference<>();
        TreasurySessionAuthenticator authenticator = new TreasurySessionAuthenticator(
                ignored -> {},
                Runnable::run,
                ignored -> {},
                error -> {
                    failure.set(error);
                    handlerHeldMonitor.set(Thread.holdsLock(authenticatorReference.get()));
                });
        authenticatorReference.set(authenticator);

        assertFalse(authenticator.handleChallenge("short"));

        assertEquals("Backend supplied an invalid Treasury authentication nonce.", failure.get().getMessage());
        assertFalse(handlerHeldMonitor.get());
        assertEquals(TreasurySessionAuthenticator.State.FAILED, authenticator.state());
    }

    @Test
    void joinFailureNeverSendsAuthenticationResponse() {
        AtomicReference<String> response = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        TreasurySessionAuthenticator authenticator = new TreasurySessionAuthenticator(
                ignored -> {
                    throw new IllegalStateException("invalid access token");
                },
                Runnable::run,
                response::set,
                failure::set);

        assertTrue(authenticator.handleChallenge(NONCE));

        assertNull(response.get());
        assertEquals("invalid access token", failure.get().getMessage());
        assertEquals(TreasurySessionAuthenticator.State.FAILED, authenticator.state());
        assertFalse(authenticator.isAuthenticated());
    }

    @Test
    void resetDiscardsAStaleInFlightJoinResult() {
        AtomicReference<Runnable> queued = new AtomicReference<>();
        AtomicReference<String> response = new AtomicReference<>();
        Executor delayedExecutor = queued::set;
        TreasurySessionAuthenticator authenticator = new TreasurySessionAuthenticator(
                ignored -> {}, delayedExecutor, response::set, ignored -> {});

        assertTrue(authenticator.handleChallenge(NONCE));
        authenticator.reset();
        queued.get().run();

        assertNull(response.get());
        assertEquals(TreasurySessionAuthenticator.State.WAITING_CHALLENGE, authenticator.state());
        assertFalse(authenticator.isAuthenticated());
    }
}
