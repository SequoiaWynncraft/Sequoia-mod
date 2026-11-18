package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class SignInBackoffTest {

    private final AtomicLong now = new AtomicLong(5_000_000);
    private final SignInBackoff backoff = new SignInBackoff(now::get);

    @Test
    void aSignInMayStartAtOnceBeforeAnyFailure() {
        assertEquals(0, backoff.waitMs());
    }

    @Test
    void aFailureHoldsTheNextSignInBackForTwoSeconds() {
        backoff.recordFailure();

        assertEquals(2_000, backoff.waitMs());
        now.addAndGet(1_999);
        assertEquals(1, backoff.waitMs());
        now.addAndGet(1);
        assertEquals(0, backoff.waitMs());
    }

    @Test
    void eachFailureDoublesTheWaitUpToAMinute() {
        List<Long> waits = new ArrayList<>();
        for (int failure = 0; failure < 8; failure++) {
            backoff.recordFailure();
            waits.add(backoff.waitMs());
        }

        assertEquals(List.of(2_000L, 4_000L, 8_000L, 16_000L, 32_000L, 60_000L, 60_000L, 60_000L), waits);
    }

    @Test
    void theWaitCountsFromTheLatestFailure() {
        backoff.recordFailure();
        now.addAndGet(10_000);

        backoff.recordFailure();

        assertEquals(4_000, backoff.waitMs());
    }

    @Test
    void resettingLetsTheNextSignInStartAtOnceAndStartsTheWaitsOver() {
        backoff.recordFailure();
        backoff.recordFailure();

        backoff.reset();

        assertEquals(0, backoff.waitMs());
        backoff.recordFailure();
        assertEquals(2_000, backoff.waitMs());
    }
}
