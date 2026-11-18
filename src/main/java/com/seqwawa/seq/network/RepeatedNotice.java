package com.seqwawa.seq.network;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Lets the same message through at most once per {@code quietMs}. The backend's errors
 * don't say which message they answer, so an error caused by one of the mod's own
 * background reports, such as a rate limit on war submissions during a war, would
 * otherwise fill chat with copies of itself.
 */
final class RepeatedNotice {

    private final long quietMs;
    private final LongSupplier clock;
    private final Map<String, Long> shownAt = new HashMap<>();

    RepeatedNotice(long quietMs, LongSupplier clock) {
        this.quietMs = quietMs;
        this.clock = clock;
    }

    /** Whether {@code message} should be shown now; if so, the next copy waits out the quiet period. */
    synchronized boolean shouldShow(String message) {
        long now = clock.getAsLong();
        Long last = shownAt.get(message);
        if (last != null && now - last < quietMs) {
            return false;
        }
        // Keep the map to messages shown recently, so it can't grow without bound.
        shownAt.values().removeIf(at -> now - at >= quietMs);
        shownAt.put(message, now);
        return true;
    }
}
