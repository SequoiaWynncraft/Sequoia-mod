package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.managers.PrincessRaidStatsManager;
import com.seqwawa.seq.model.PrincessRaidStats;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrincessLeaderboardPanelTest {

    @Test
    void limitsTheCompactBoardToFiveEntries() {
        List<PrincessRaidStats.LeaderboardEntry> entries = java.util.stream.IntStream.rangeClosed(1, 7)
                .mapToObj(rank -> new PrincessRaidStats.LeaderboardEntry(rank, "Player" + rank, 20 - rank))
                .toList();
        var snapshot = new PrincessRaidStatsManager.Snapshot(
                PrincessRaidStatsManager.State.READY, true, 12, 6, entries);

        assertEquals(5, PrincessLeaderboardPanel.visibleEntries(snapshot).size());
        assertEquals("You: 12  #6", PrincessLeaderboardPanel.ownSummary(snapshot));
    }

    @Test
    void distinguishesUnknownAndConfirmedZeroCounts() {
        var loading = new PrincessRaidStatsManager.Snapshot(
                PrincessRaidStatsManager.State.LOADING, false, 0, null, List.of());
        var empty = new PrincessRaidStatsManager.Snapshot(
                PrincessRaidStatsManager.State.READY, true, 0, null, List.of());

        assertEquals("You: …", PrincessLeaderboardPanel.ownSummary(loading));
        assertEquals("Loading royal records…", PrincessLeaderboardPanel.emptyMessage(loading));
        assertEquals("You: 0", PrincessLeaderboardPanel.ownSummary(empty));
        assertEquals("No Princess graids yet", PrincessLeaderboardPanel.emptyMessage(empty));
    }

    @Test
    void shortensLongUsernamesOnNarrowCards() {
        var entry = new PrincessRaidStats.LeaderboardEntry(1, "SixteenCharacter", 42);

        assertEquals("1.  SixteenCh…", PrincessLeaderboardPanel.entryLabel(entry, 129));
    }

}
