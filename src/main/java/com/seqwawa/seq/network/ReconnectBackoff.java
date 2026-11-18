package com.seqwawa.seq.network;

/**
 * How long the connection waits before each automatic reconnect, and when it stops trying.
 * The wait starts at a second and doubles, so a backend that restarts is back within
 * seconds, and a few failures in a row hand over to the player instead of retrying forever.
 * Opening a socket starts the count again.
 */
final class ReconnectBackoff {

    /** Reconnects made without a socket opening before the player has to connect. */
    static final int MAX_ATTEMPTS = 5;
    private static final long BASE_MS = 1_000;
    private static final long CAP_MS = 60_000;

    private ReconnectBackoff() {}

    /** Whether {@code attemptsMade} reconnects in a row are enough to stop. */
    static boolean exhausted(int attemptsMade) {
        return attemptsMade >= MAX_ATTEMPTS;
    }

    /** The wait before the reconnect after {@code attemptsMade}: one second, doubling, at most a minute. */
    static long delayMs(int attemptsMade) {
        // Past 2^6 seconds the cap applies anyway; stopping the shift there keeps it from overflowing.
        int doublings = Math.max(0, Math.min(attemptsMade, 6));
        return Math.min(BASE_MS << doublings, CAP_MS);
    }
}
