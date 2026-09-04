package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GuildRaidActivityTrackerTest {

    private static final long BUSY_WINDOW_MS = GuildRaidActivityTracker.BUSY_WINDOW.toMillis();
    private static final long NOW = 1_700_000_000_000L;

    @BeforeEach
    @AfterEach
    void clearTracker() {
        GuildRaidActivityTracker.reset();
    }

    @Test
    void marksEveryMemberOfTheAnnouncedPartyBusy() {
        GuildRaidActivityTracker.recordCompletion(List.of("Visroul", "a3pki", "blousy", "divvy"), NOW);

        for (String username : List.of("Visroul", "a3pki", "blousy", "divvy")) {
            assertTrue(
                    GuildRaidActivityTracker.busyRemainingMillis(username, NOW) > 0L,
                    username + " should be busy right after the completion");
        }
        assertEquals(0L, GuildRaidActivityTracker.busyRemainingMillis("SomeoneElse", NOW));
    }

    @Test
    void busyWindowExpiresAfterEightMinutes() {
        GuildRaidActivityTracker.recordCompletion(List.of("Visroul"), NOW);

        assertEquals(BUSY_WINDOW_MS, GuildRaidActivityTracker.busyRemainingMillis("Visroul", NOW));
        assertEquals(
                BUSY_WINDOW_MS / 2,
                GuildRaidActivityTracker.busyRemainingMillis("Visroul", NOW + BUSY_WINDOW_MS / 2));
        assertEquals(0L, GuildRaidActivityTracker.busyRemainingMillis("Visroul", NOW + BUSY_WINDOW_MS));
        assertEquals(0L, GuildRaidActivityTracker.busyRemainingMillis("Visroul", NOW + BUSY_WINDOW_MS + 1));
    }

    @Test
    void matchesUsernamesRegardlessOfCaseAndSurroundingSpace() {
        GuildRaidActivityTracker.recordCompletion(List.of("  Visroul  "), NOW);

        assertTrue(GuildRaidActivityTracker.busyRemainingMillis("visroul", NOW) > 0L);
        assertTrue(GuildRaidActivityTracker.busyRemainingMillis("VISROUL", NOW) > 0L);
    }

    @Test
    void aLaterCompletionExtendsTheWindowAndAnEarlierOneDoesNotShortenIt() {
        GuildRaidActivityTracker.recordCompletion(List.of("Visroul"), NOW);
        GuildRaidActivityTracker.recordCompletion(List.of("Visroul"), NOW + 60_000L);

        assertEquals(
                BUSY_WINDOW_MS,
                GuildRaidActivityTracker.busyRemainingMillis("Visroul", NOW + 60_000L),
                "the newer completion should restart the window");

        // A duplicate relay of the first raid must not pull the window back.
        GuildRaidActivityTracker.recordCompletion(List.of("Visroul"), NOW);
        assertEquals(BUSY_WINDOW_MS, GuildRaidActivityTracker.busyRemainingMillis("Visroul", NOW + 60_000L));
    }

    @Test
    void ignoresBlankAndMissingNames() {
        GuildRaidActivityTracker.recordCompletion(null, NOW);
        GuildRaidActivityTracker.recordCompletion(List.of(), NOW);

        assertFalse(GuildRaidActivityTracker.isBusy(""));
        assertEquals(0L, GuildRaidActivityTracker.busyRemainingMillis(null, NOW));
    }
}
