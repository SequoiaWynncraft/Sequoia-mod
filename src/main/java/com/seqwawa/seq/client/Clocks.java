package com.seqwawa.seq.client;

/**
 * The clock rule. Code that throttles, waits or gives up after a while takes a
 * {@code LongSupplier} of milliseconds, {@link #monotonicMillis()} in the game. Code that
 * shows or sends a moment takes a {@link java.time.Clock}. Both come in through the
 * constructor, so a test can move time, and neither reads the system clock itself.
 */
public final class Clocks {

    private static final long ORIGIN_NANOS = System.nanoTime();

    private Clocks() {}

    /**
     * Milliseconds since this class loaded. Unlike the wall clock it never jumps, so a
     * throttle can't stall for hours when the system clock is set back. It starts near
     * zero, so code must not use zero to mean "never".
     */
    public static long monotonicMillis() {
        return (System.nanoTime() - ORIGIN_NANOS) / 1_000_000L;
    }
}
