package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RepeatedNoticeTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final RepeatedNotice notice = new RepeatedNotice(600_000, now::get);

    @Test
    void theSameMessageShowsOnceUntilItsQuietPeriodHasPassed() {
        assertTrue(notice.shouldShow("Too many guild war submissions"));
        now.addAndGet(599_999);
        assertFalse(notice.shouldShow("Too many guild war submissions"));
        now.addAndGet(1);
        assertTrue(notice.shouldShow("Too many guild war submissions"));
    }

    @Test
    void differentMessagesDoNotHoldEachOtherBack() {
        assertTrue(notice.shouldShow("Invalid guild bank event payload"));
        assertTrue(notice.shouldShow("Too many guild war submissions"));
        assertFalse(notice.shouldShow("Invalid guild bank event payload"));
    }
}
