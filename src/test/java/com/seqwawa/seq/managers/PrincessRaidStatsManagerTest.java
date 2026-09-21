package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.PrincessRaidStats;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

class PrincessRaidStatsManagerTest {
    private static final Instant NOW = Instant.parse("2026-08-19T12:00:00Z");

    @Test
    void refreshPublishesSnapshot() {
        FakeGateway gateway = new FakeGateway();
        PrincessRaidStatsManager manager = manager(gateway);

        manager.refresh();

        assertEquals(PrincessRaidStatsManager.State.LOADING, manager.snapshot().state());
        gateway.leaderboard.complete(stats(12, 3));

        assertEquals(PrincessRaidStatsManager.State.READY, manager.snapshot().state());
        assertEquals(12, manager.snapshot().ownRaidCount());
        assertEquals(3, manager.snapshot().ownRank());
    }

    @Test
    void completionUsesCachedCountThenAcceptsServerCount() {
        FakeGateway gateway = new FakeGateway();
        PrincessRaidStatsManager manager = manager(gateway);
        manager.refresh();
        gateway.leaderboard.complete(stats(12, 3));

        PrincessRaidStatsManager.Completion completion = manager.recordCompletion("The Nameless Anomaly");

        assertEquals(13, completion.displayedRaidCount());
        assertEquals(completion.eventId(), gateway.recordedEventIds.getFirst());
        gateway.records.getFirst().complete(stats(13, 2));
        assertEquals(13, completion.confirmedRaidCount().join());
    }

    @Test
    void unknownCountWaitsForServerAndDuplicatePacketIsSuppressed() {
        FakeGateway gateway = new FakeGateway();
        PrincessRaidStatsManager manager = manager(gateway);

        PrincessRaidStatsManager.Completion completion = manager.recordCompletion("Prelude to Annihilation");
        PrincessRaidStatsManager.Completion duplicate = manager.recordCompletion("prelude to annihilation");

        assertNotNull(completion);
        assertNull(duplicate);
        assertEquals(0, completion.displayedRaidCount());
        gateway.records.getFirst().complete(stats(81, 1));
        assertEquals(81, completion.confirmedRaidCount().join());
    }

    @Test
    void resetRejectsOldAccountResponse() {
        FakeGateway gateway = new FakeGateway();
        PrincessRaidStatsManager manager = manager(gateway);
        PrincessRaidStatsManager.Completion completion = manager.recordCompletion("Nest of the Grootslangs");

        manager.reset();
        gateway.records.getFirst().complete(stats(44, 4));

        assertEquals(PrincessRaidStatsManager.State.IDLE, manager.snapshot().state());
        assertTrue(completion.confirmedRaidCount().isCompletedExceptionally());
    }

    @Test
    void failureKeepsCachedCount() {
        FakeGateway gateway = new FakeGateway();
        PrincessRaidStatsManager manager = manager(gateway);
        manager.refresh();
        gateway.leaderboard.complete(stats(9, 5));
        gateway.leaderboard = new CompletableFuture<>();

        manager.refresh();
        gateway.leaderboard.completeExceptionally(new IllegalStateException("offline"));

        assertEquals(PrincessRaidStatsManager.State.UNAVAILABLE, manager.snapshot().state());
        assertTrue(manager.snapshot().countKnown());
        assertEquals(9, manager.snapshot().ownRaidCount());
    }

    @Test
    void staleRefreshCannotOverwriteCompletion() {
        FakeGateway gateway = new FakeGateway();
        PrincessRaidStatsManager manager = manager(gateway);
        manager.refresh();
        PrincessRaidStatsManager.Completion completion = manager.recordCompletion("The Nameless Anomaly");

        gateway.records.getFirst().complete(stats(13, 2));
        gateway.leaderboard.complete(stats(12, 3));

        assertEquals(13, completion.confirmedRaidCount().join());
        assertEquals(2, manager.snapshot().ownRank());
    }

    @Test
    void staleFailureCannotHideNewerSuccess() {
        FakeGateway gateway = new FakeGateway();
        PrincessRaidStatsManager manager = manager(gateway);
        manager.refresh();
        PrincessRaidStatsManager.Completion completion = manager.recordCompletion("The Nameless Anomaly");

        gateway.records.getFirst().complete(stats(13, 2));
        gateway.leaderboard.completeExceptionally(new IllegalStateException("old failure"));

        assertEquals(13, completion.confirmedRaidCount().join());
        assertEquals(PrincessRaidStatsManager.State.READY, manager.snapshot().state());
    }

    @Test
    void staleCompletionOnlyMergesCountIntoNewerFailureState() {
        FakeGateway gateway = new FakeGateway();
        PrincessRaidStatsManager manager = manager(gateway);
        PrincessRaidStatsManager.Completion completion = manager.recordCompletion("The Nameless Anomaly");
        manager.refresh();

        gateway.leaderboard.completeExceptionally(new IllegalStateException("newer failure"));
        gateway.records.getFirst().complete(stats(13, 2));

        assertEquals(13, completion.confirmedRaidCount().join());
        assertEquals(PrincessRaidStatsManager.State.UNAVAILABLE, manager.snapshot().state());
        assertNull(manager.snapshot().ownRank());
    }

    private static PrincessRaidStatsManager manager(FakeGateway gateway) {
        return new PrincessRaidStatsManager(gateway, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PrincessRaidStats stats(long ownCount, Integer ownRank) {
        return new PrincessRaidStats(
                1,
                new PrincessRaidStats.Self("LocalPlayer", ownCount, ownRank),
                List.of(new PrincessRaidStats.LeaderboardEntry(1, "RoyalOne", 99)));
    }

    private static final class FakeGateway implements PrincessRaidStatsManager.Gateway {
        private CompletableFuture<PrincessRaidStats> leaderboard = new CompletableFuture<>();
        private final List<CompletableFuture<PrincessRaidStats>> records = new ArrayList<>();
        private final List<UUID> recordedEventIds = new ArrayList<>();

        @Override
        public CompletableFuture<PrincessRaidStats> leaderboard() {
            return leaderboard;
        }

        @Override
        public CompletableFuture<PrincessRaidStats> record(UUID eventId, String raidName) {
            recordedEventIds.add(eventId);
            CompletableFuture<PrincessRaidStats> future = new CompletableFuture<>();
            records.add(future);
            return future;
        }
    }
}
