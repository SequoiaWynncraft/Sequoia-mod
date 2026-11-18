package com.seqwawa.seq.network;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Spaces out sign-ins after the backend or the auth service refuses one, so a refusal
 * that keeps coming doesn't turn into a sign-in every tick. The wait starts at two
 * seconds and doubles to a minute; an accepted sign-in or a fresh socket clears it.
 */
final class SignInBackoff {

    private static final long BASE_MS = 2_000;
    private static final long CAP_MS = 60_000;
    /** 2 s doubled five times passes the cap, so later failures need not double further. */
    private static final int MAX_DOUBLINGS = 5;

    private final LongSupplier clockMs;
    private int failures;
    private long notBeforeMs;

    SignInBackoff(LongSupplier clockMs) {
        this.clockMs = Objects.requireNonNull(clockMs, "clockMs");
    }

    /** How long until a sign-in may start, in milliseconds; zero when it may start now. */
    synchronized long waitMs() {
        if (failures == 0) {
            return 0;
        }
        return Math.max(0, notBeforeMs - clockMs.getAsLong());
    }

    /** A sign-in failed: the next waits longer than the last. */
    synchronized void recordFailure() {
        long delay = Math.min(BASE_MS << Math.min(failures, MAX_DOUBLINGS), CAP_MS);
        failures++;
        notBeforeMs = clockMs.getAsLong() + delay;
    }

    /** A sign-in succeeded, or a new socket opened: the next may start at once. */
    synchronized void reset() {
        failures = 0;
        notBeforeMs = 0;
    }
}
