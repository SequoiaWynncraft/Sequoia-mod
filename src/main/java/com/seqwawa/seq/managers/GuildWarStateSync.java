package com.seqwawa.seq.managers;

import com.seqwawa.seq.model.war.GuildWarObservation;
import com.seqwawa.seq.model.war.GuildWarState;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Small shared-state feed, independent of presentation settings and full planner snapshots. */
public final class GuildWarStateSync {
    static final long POLL_MS = 15_000;
    static final long MAX_PENDING_AGE_MS = 30_000;
    private static final int MAX_PENDING = 24;
    public interface Gateway {
        CompletableFuture<GuildWarState> fetch();
        CompletableFuture<GuildWarState> observe(GuildWarObservation report);
    }

    private final Gateway gateway;
    private final LongSupplier clock;
    private final BooleanSupplier canSync;
    private final Consumer<Runnable> executor;
    private final CheapestQueueTracker tracker;
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private final LinkedHashMap<String, CheapestQueueTracker.Observation> recent = new LinkedHashMap<>();
    private CompletableFuture<GuildWarState> inFlight;
    private GuildWarState shared;
    private Instant serverAtSync;
    private long localAtSync;
    private long nextPoll;
    private long nextAttempt;
    private long generation;
    private long deniedUntil;
    private boolean previouslyAllowed;

    public GuildWarStateSync(Gateway gateway, LongSupplier clock, BooleanSupplier canSync,
            Consumer<Runnable> executor, Consumer<String> effect) {
        this.gateway = gateway;
        this.clock = clock;
        this.canSync = canSync;
        this.executor = executor;
        tracker = new CheapestQueueTracker(clock, effect, this::observed);
    }

    public CheapestQueueTracker tracker() { return tracker; }
    public GuildWarState shared() { return shared; }

    private void observed(CheapestQueueTracker.Observation observation) {
        // No offline history is replayed to a future account or guild session.
        if (!canSync.getAsBoolean() || clock.getAsLong() < deniedUntil) return;
        String key = "HQ_MENU";
        var previous = recent.get(key);
        if (observation.source().equals("HQ_MENU") && previous != null
                && Objects.equals(previous.territory(), observation.territory())
                && observation.capturedAtMs() - previous.capturedAtMs() < 60_000) return;
        if (observation.source().equals("HQ_CHANGED")) recent.clear();
        if (observation.source().equals("HQ_MENU")) recent.put(key, observation);
        if (pending.size() == MAX_PENDING) pending.removeFirst();
        pending.addLast(new Pending(observation, 0));
    }

    public void tick() {
        if (!canSync.getAsBoolean()) {
            if (previouslyAllowed) reset();
            return;
        }
        previouslyAllowed = true;
        long now = clock.getAsLong();
        pending.removeIf(p -> now - p.event().capturedAtMs() >= MAX_PENDING_AGE_MS);
        tracker.expireEvidence();
        if (inFlight != null || now < nextAttempt) return;
        if (serverAtSync == null) start(null);
        else if (!pending.isEmpty()) start(pending.removeFirst());
        else if (now >= nextPoll) start(null);
    }

    private void start(Pending sent) {
        long requestGeneration = generation;
        CompletableFuture<GuildWarState> request;
        try {
            if (sent == null) request = gateway.fetch();
            else {
                var event = sent.event();
                Instant capturedAt = serverAtSync.plusMillis(event.capturedAtMs() - localAtSync);
                request = gateway.observe(new GuildWarObservation(event.source(), event.territory(), event.style(), capturedAt));
            }
        } catch (RuntimeException error) {
            failed(sent);
            return;
        }
        inFlight = request;
        request.whenComplete((state, error) -> executor.accept(() -> {
            if (requestGeneration != generation || inFlight != request) return;
            inFlight = null;
            if (!canSync.getAsBoolean()) { reset(); return; }
            if (accessDenied(error)) {
                reset();
                deniedUntil = clock.getAsLong() + 60_000;
                nextAttempt = deniedUntil;
                return;
            }
            if (error != null || state == null || !state.supported()) { failed(sent); return; }
            nextAttempt = 0;
            nextPoll = clock.getAsLong() + POLL_MS;
            if (shared != null && (state.revision() < shared.revision()
                    || state.revision() == shared.revision() && state.serverTime().isBefore(shared.serverTime()))) return;
            // Freeze the session mapping: later network latency must never re-date queued events.
            if (serverAtSync == null) {
                serverAtSync = state.serverTime();
                localAtSync = clock.getAsLong();
            }
            shared = state;
            tracker.applySharedState(state.hqTerritory(), state.hqStyle(),
                    localTime(state.hqObservedAt()), localTime(state.styleObservedAt()),
                    localTime(state.stylesInvalidatedAt()));
        }));
    }

    private long localTime(Instant observedAt) {
        return observedAt == null ? Long.MIN_VALUE
                : localAtSync + java.time.Duration.between(serverAtSync, observedAt).toMillis();
    }

    private void failed(Pending sent) {
        if (sent != null && sent.attempts() < 2 && pending.size() < MAX_PENDING
                && clock.getAsLong() - sent.event().capturedAtMs() < MAX_PENDING_AGE_MS) {
            pending.addFirst(new Pending(sent.event(), sent.attempts() + 1));
        }
        nextAttempt = clock.getAsLong() + 5_000;
    }

    private static boolean accessDenied(Throwable error) {
        while (error != null && (error instanceof java.util.concurrent.CompletionException
                || error instanceof java.util.concurrent.ExecutionException)) error = error.getCause();
        return error instanceof com.seqwawa.seq.network.ApiClient.ApiException api
                && (api.getStatusCode() == 401 || api.getStatusCode() == 403);
    }

    public Instant serverNow() {
        return serverAtSync == null ? null : serverAtSync.plusMillis(clock.getAsLong() - localAtSync);
    }

    public void reset() {
        generation++;
        inFlight = null;
        pending.clear();
        recent.clear();
        shared = null;
        serverAtSync = null;
        nextAttempt = 0;
        nextPoll = 0;
        deniedUntil = 0;
        previouslyAllowed = false;
        tracker.reset();
    }

    private record Pending(CheapestQueueTracker.Observation event, int attempts) {}
}
