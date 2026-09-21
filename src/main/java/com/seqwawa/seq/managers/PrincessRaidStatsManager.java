package com.seqwawa.seq.managers;

import com.seqwawa.seq.model.PrincessRaidStats;
import com.seqwawa.seq.network.ApiClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Keeps the latest Princess raid count and leaderboard snapshot. */
public final class PrincessRaidStatsManager {
    private static final int SUPPORTED_SCHEMA_VERSION = 1;
    private static final Duration LOCAL_DUPLICATE_WINDOW = Duration.ofSeconds(10);

    public enum State {
        IDLE,
        LOADING,
        READY,
        UNAVAILABLE
    }

    public interface Gateway {
        CompletableFuture<PrincessRaidStats> leaderboard();

        CompletableFuture<PrincessRaidStats> record(UUID eventId, String raidName);
    }

    public record Snapshot(
            State state,
            boolean countKnown,
            long ownRaidCount,
            Integer ownRank,
            List<PrincessRaidStats.LeaderboardEntry> leaderboard) {
        public Snapshot {
            state = state == null ? State.IDLE : state;
            leaderboard = leaderboard == null ? List.of() : List.copyOf(leaderboard);
        }

        static Snapshot idle() {
            return new Snapshot(State.IDLE, false, 0, null, List.of());
        }
    }

    public record Completion(
            UUID eventId, long displayedRaidCount, CompletableFuture<Long> confirmedRaidCount) {}

    private final Gateway gateway;
    private final Clock clock;

    private volatile Snapshot snapshot = Snapshot.idle();
    private long generation;
    private long nextRequestId;
    private long latestAppliedRequestId;
    private String recentRaidName;
    private Instant recentRaidAt;

    public PrincessRaidStatsManager() {
        this(new ApiGateway(ApiClient.getInstance()), Clock.systemUTC());
    }

    public PrincessRaidStatsManager(Gateway gateway, Clock clock) {
        this.gateway = Objects.requireNonNull(gateway, "gateway");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public synchronized CompletableFuture<Snapshot> refresh() {
        long requestGeneration = generation;
        long requestId = ++nextRequestId;
        Snapshot current = snapshot;
        snapshot = new Snapshot(
                State.LOADING,
                current.countKnown(),
                current.ownRaidCount(),
                current.ownRank(),
                current.leaderboard());
        return gateway.leaderboard()
                .handle((response, error) -> error == null
                        ? applyRefresh(requestGeneration, requestId, response)
                        : markUnavailable(requestGeneration, requestId));
    }

    public synchronized Completion recordCompletion(String raidName) {
        if (raidName == null || raidName.isBlank()) {
            throw new IllegalArgumentException("Princess raid name is required.");
        }

        String normalizedRaidName = raidName.trim();
        Instant now = clock.instant();
        if (recentRaidAt != null
                && normalizedRaidName.equalsIgnoreCase(recentRaidName)
                && !now.isBefore(recentRaidAt)
                && Duration.between(recentRaidAt, now).compareTo(LOCAL_DUPLICATE_WINDOW) < 0) {
            return null;
        }
        recentRaidName = normalizedRaidName;
        recentRaidAt = now;

        UUID eventId = UUID.randomUUID();
        long requestGeneration = generation;
        long requestId = ++nextRequestId;
        long displayedCount = snapshot.countKnown() ? snapshot.ownRaidCount() + 1 : 0;
        CompletableFuture<Long> confirmedCount = gateway.record(eventId, normalizedRaidName)
                .thenApply(response -> acceptRecord(requestGeneration, requestId, response));
        confirmedCount.whenComplete((count, error) -> {
            if (error != null) {
                markUnavailable(requestGeneration, requestId);
            }
        });
        return new Completion(eventId, displayedCount, confirmedCount);
    }

    public synchronized void reset() {
        generation++;
        recentRaidName = null;
        recentRaidAt = null;
        latestAppliedRequestId = 0;
        snapshot = Snapshot.idle();
    }

    private synchronized long acceptRecord(
            long requestGeneration, long requestId, PrincessRaidStats response) {
        if (requestGeneration != generation) {
            throw new IllegalStateException("Minecraft account changed.");
        }
        if (!isCompatible(response)) {
            throw new IllegalStateException("Princess stats response is incompatible.");
        }
        Snapshot updated = applyResponse(requestGeneration, requestId, response, true);
        return updated.ownRaidCount();
    }

    private synchronized Snapshot applyRefresh(
            long requestGeneration, long requestId, PrincessRaidStats response) {
        return isCompatible(response)
                ? applyResponse(requestGeneration, requestId, response, false)
                : markUnavailable(requestGeneration, requestId);
    }

    private synchronized Snapshot applyResponse(
            long requestGeneration, long requestId, PrincessRaidStats response, boolean completionResponse) {
        if (requestGeneration != generation) {
            return snapshot;
        }
        boolean newestResponse = requestId >= latestAppliedRequestId;
        if (!newestResponse && !completionResponse) {
            return snapshot;
        }
        if (newestResponse) {
            latestAppliedRequestId = requestId;
        }

        snapshot = new Snapshot(
                newestResponse ? State.READY : snapshot.state(),
                true,
                Math.max(snapshot.ownRaidCount(), Math.max(0, response.self().raidCount())),
                newestResponse ? response.self().rank() : snapshot.ownRank(),
                newestResponse ? response.leaderboard() : snapshot.leaderboard());
        return snapshot;
    }

    private synchronized Snapshot markUnavailable(long requestGeneration, long requestId) {
        if (requestGeneration != generation || requestId < latestAppliedRequestId) {
            return snapshot;
        }
        latestAppliedRequestId = requestId;
        snapshot = new Snapshot(
                State.UNAVAILABLE,
                snapshot.countKnown(),
                snapshot.ownRaidCount(),
                snapshot.ownRank(),
                snapshot.leaderboard());
        return snapshot;
    }

    private static boolean isCompatible(PrincessRaidStats response) {
        return response != null
                && response.schemaVersion() == SUPPORTED_SCHEMA_VERSION
                && response.self() != null;
    }

    private record ApiGateway(ApiClient apiClient) implements Gateway {
        @Override
        public CompletableFuture<PrincessRaidStats> leaderboard() {
            return apiClient.getPrincessRaidLeaderboard();
        }

        @Override
        public CompletableFuture<PrincessRaidStats> record(UUID eventId, String raidName) {
            return apiClient.recordPrincessRaid(eventId, raidName);
        }
    }
}
