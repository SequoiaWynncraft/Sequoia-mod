package com.seqwawa.seq.network;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Proves ownership of the active Minecraft session without exposing its access token. */
final class TreasurySessionAuthenticator {
    private static final Pattern NONCE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{16,128}$");

    private final SessionJoiner sessionJoiner;
    private final Executor executor;
    private final Consumer<String> responseSender;
    private final Consumer<Throwable> failureHandler;

    private State state = State.WAITING_CHALLENGE;
    private String nonce;

    TreasurySessionAuthenticator(
            SessionJoiner sessionJoiner,
            Executor executor,
            Consumer<String> responseSender,
            Consumer<Throwable> failureHandler) {
        this.sessionJoiner = Objects.requireNonNull(sessionJoiner);
        this.executor = Objects.requireNonNull(executor);
        this.responseSender = Objects.requireNonNull(responseSender);
        this.failureHandler = Objects.requireNonNull(failureHandler);
    }

    boolean handleChallenge(String challengeNonce) {
        IllegalArgumentException invalidNonceFailure = null;
        synchronized (this) {
            if (state != State.WAITING_CHALLENGE) {
                return false;
            }
            if (!isValidNonce(challengeNonce)) {
                state = State.FAILED;
                nonce = null;
                invalidNonceFailure = new IllegalArgumentException(
                        "Backend supplied an invalid Treasury authentication nonce.");
            } else {
                nonce = challengeNonce;
                state = State.JOINING_SESSION;
            }
        }

        if (invalidNonceFailure != null) {
            failureHandler.accept(invalidNonceFailure);
            return false;
        }

        CompletableFuture.runAsync(() -> {
                    try {
                        sessionJoiner.join(challengeNonce);
                    } catch (Exception exception) {
                        throw new CompletionException(exception);
                    }
                }, executor)
                .whenComplete((ignored, error) -> completeJoin(challengeNonce, error));
        return true;
    }

    synchronized boolean confirm(String confirmedNonce) {
        if (state != State.WAITING_BACKEND || !Objects.equals(nonce, confirmedNonce)) {
            return false;
        }
        state = State.AUTHENTICATED;
        nonce = null;
        return true;
    }

    synchronized boolean isAuthenticated() {
        return state == State.AUTHENTICATED;
    }

    synchronized State state() {
        return state;
    }

    synchronized void reset() {
        state = State.WAITING_CHALLENGE;
        nonce = null;
    }

    static boolean isValidNonce(String nonce) {
        return nonce != null && NONCE_PATTERN.matcher(nonce).matches();
    }

    private void completeJoin(String completedNonce, Throwable error) {
        Throwable failure = unwrap(error);
        synchronized (this) {
            if (state != State.JOINING_SESSION || !Objects.equals(nonce, completedNonce)) {
                return;
            }
            if (failure != null) {
                state = State.FAILED;
            } else {
                state = State.WAITING_BACKEND;
            }
        }

        if (failure != null) {
            failureHandler.accept(failure);
        } else {
            responseSender.accept(completedNonce);
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    @FunctionalInterface
    interface SessionJoiner {
        void join(String nonce) throws Exception;
    }

    enum State {
        WAITING_CHALLENGE,
        JOINING_SESSION,
        WAITING_BACKEND,
        AUTHENTICATED,
        FAILED
    }
}
