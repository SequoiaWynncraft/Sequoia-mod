package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GuildMembersScreenTest {

    @Test
    void formatsTheBusyCountdownAsMinutesAndSeconds() {
        assertEquals("8:00", GuildMembersScreen.formatCountdown(480_000L));
        assertEquals("7:30", GuildMembersScreen.formatCountdown(450_000L));
        assertEquals("0:09", GuildMembersScreen.formatCountdown(9_000L));
    }

    @Test
    void roundsPartialSecondsUpSoTheChipNeverReadsZeroWhileStillBusy() {
        assertEquals("0:01", GuildMembersScreen.formatCountdown(1L));
        assertEquals("0:01", GuildMembersScreen.formatCountdown(999L));
        assertEquals("1:00", GuildMembersScreen.formatCountdown(59_001L));
    }

    @Test
    void clampsExpiredAndNegativeRemainders() {
        assertEquals("0:00", GuildMembersScreen.formatCountdown(0L));
        assertEquals("0:00", GuildMembersScreen.formatCountdown(-5_000L));
    }
}
