package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RaidGambitSlotDumperTest {
    @Test
    void waitsFiveSecondsBetweenDumps() {
        assertFalse(RaidGambitSlotDumper.dumpIntervalElapsed(10_000L, 14_999L));
        assertTrue(RaidGambitSlotDumper.dumpIntervalElapsed(10_000L, 15_000L));
    }

    @Test
    void dumpsImmediatelyBeforeFirstObservationAndAfterClockReset() {
        assertTrue(RaidGambitSlotDumper.dumpIntervalElapsed(Long.MIN_VALUE, 10_000L));
        assertTrue(RaidGambitSlotDumper.dumpIntervalElapsed(10_000L, 9_999L));
    }
}
