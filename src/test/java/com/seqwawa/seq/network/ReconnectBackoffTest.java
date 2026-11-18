package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReconnectBackoffTest {

    @Test
    void theWaitStartsAtASecondAndDoublesForEachAttempt() {
        assertEquals(
                List.of(1_000L, 2_000L, 4_000L, 8_000L, 16_000L),
                IntStream.range(0, ReconnectBackoff.MAX_ATTEMPTS).mapToObj(ReconnectBackoff::delayMs).toList());
    }

    @Test
    void theWaitNeverPassesAMinuteHoweverManyAttempts() {
        assertEquals(32_000L, ReconnectBackoff.delayMs(5));
        assertEquals(60_000L, ReconnectBackoff.delayMs(6));
        assertEquals(60_000L, ReconnectBackoff.delayMs(64));
        assertEquals(60_000L, ReconnectBackoff.delayMs(Integer.MAX_VALUE));
    }

    @Test
    void fiveReconnectsWithoutOpeningHandOverToThePlayer() {
        for (int attempts = 0; attempts < 5; attempts++) {
            assertFalse(ReconnectBackoff.exhausted(attempts), "attempt " + attempts);
        }
        assertTrue(ReconnectBackoff.exhausted(5));
        assertTrue(ReconnectBackoff.exhausted(6));
    }
}
