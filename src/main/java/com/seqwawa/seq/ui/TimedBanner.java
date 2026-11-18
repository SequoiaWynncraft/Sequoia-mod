package com.seqwawa.seq.ui;

import java.util.function.LongSupplier;

/**
 * A message that shows for a while after something happens and then hides by
 * itself, such as "Copied" or why an action failed.
 *
 * <p>It reads time from the millisecond clock it is given, {@code Clocks::monotonicMillis}
 * in the game, so a test can move time and setting the system clock can't keep a
 * message up for hours. A background reply may show a message while the game thread
 * draws, so the message and its end are swapped in together.
 */
final class TimedBanner {
    private final LongSupplier clock;
    private volatile Shown shown;

    private record Shown(String message, long hideAtMs) {}

    TimedBanner(LongSupplier clock) {
        this.clock = clock;
    }

    /** Shows {@code message} for {@code durationMs}, in place of what showed. A blank message shows nothing. */
    void show(String message, long durationMs) {
        if (message == null || message.isBlank()) return;
        shown = new Shown(message, clock.getAsLong() + durationMs);
    }

    /** The message while it shows, else null. Read it once per frame: it can run out between two reads. */
    String message() {
        Shown current = shown;
        return current != null && clock.getAsLong() < current.hideAtMs() ? current.message() : null;
    }
}
