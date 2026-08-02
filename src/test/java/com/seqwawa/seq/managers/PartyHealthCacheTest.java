package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PartyHealthCacheTest {
    @Test
    void refreshesAtMostFiveTimesPerSecond() {
        long lastRefreshMs = 1_000;

        assertFalse(PartyHealthCache.isRefreshDue(lastRefreshMs + 199, lastRefreshMs));
        assertTrue(PartyHealthCache.isRefreshDue(lastRefreshMs + 200, lastRefreshMs));
    }

    @Test
    void initialRefreshIsImmediatelyDue() {
        assertTrue(PartyHealthCache.isRefreshDue(1_000, 0));
    }
}
