package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.network.ApiClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The member's Leaf and quests, read from the Leaf service for the quest board.
 * The board asks for a fresh copy when it opens, resizes and while it stays open,
 * at most once a minute; every read asks Leaf to count raids and wars finished
 * moments ago.
 */
public final class LeafQuestService {

    public enum State {
        LOADING,
        READY,
        /** Leaf is only for current guild members. */
        NOT_MEMBER,
        UNAVAILABLE
    }

    /** What the board shows: both parts always come from the same read. */
    public record Snapshot(LeafAccount account, LeafQuests quests) {
        public static final Snapshot EMPTY = new Snapshot(LeafAccount.EMPTY, LeafQuests.EMPTY);
    }

    static final long REFRESH_INTERVAL_MS = Duration.ofMinutes(1).toMillis();

    private static LeafQuestService instance;

    private final Supplier<CompletableFuture<Snapshot>> fetcher;
    private final LongSupplier clock;

    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile State state = State.LOADING;
    private volatile boolean loading;
    private volatile boolean refreshFailed;
    private volatile long lastAttemptAtMs;
    private volatile int generation;

    LeafQuestService(Supplier<CompletableFuture<Snapshot>> fetcher, LongSupplier clock) {
        this.fetcher = fetcher;
        this.clock = clock;
    }

    public static synchronized LeafQuestService getInstance() {
        if (instance == null) {
            instance = new LeafQuestService(LeafQuestService::fetch, System::currentTimeMillis);
        }
        return instance;
    }

    private static CompletableFuture<Snapshot> fetch() {
        ApiClient api = ApiClient.getInstance();
        // Quests first: with sync=1 Leaf pays what was just finished, so the balance read after it includes it.
        return api.getLeafQuests(true).thenCompose(quests -> api.getLeafAccount()
                .thenApply(account -> new Snapshot(account, quests)));
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public State state() {
        return state;
    }

    /** Whether the last read failed while the board still shows what an earlier one found. */
    public boolean refreshFailed() {
        return refreshFailed;
    }

    /** Forgets everything, for a different account. */
    public synchronized void reset() {
        generation++;
        loading = false;
        refreshFailed = false;
        lastAttemptAtMs = 0;
        snapshot = Snapshot.EMPTY;
        state = State.LOADING;
    }

    /**
     * Reads unless a read is running or the last attempt was under a minute ago. The
     * first read after starting or {@link #reset()} always goes ahead.
     */
    public synchronized boolean requestRefresh() {
        long now = clock.getAsLong();
        if (loading || lastAttemptAtMs != 0 && now - lastAttemptAtMs < REFRESH_INTERVAL_MS) {
            return false;
        }
        loading = true;
        lastAttemptAtMs = now;
        if (state != State.READY) {
            state = State.LOADING;
        }
        int startedFor = generation;
        try {
            fetcher.get().whenComplete((fetched, failure) -> accept(startedFor, fetched, failure));
        } catch (RuntimeException failure) {
            accept(startedFor, null, failure);
        }
        return true;
    }

    private synchronized void accept(int startedFor, Snapshot fetched, Throwable failure) {
        if (startedFor != generation) {
            return;
        }
        loading = false;
        if (fetched != null) {
            snapshot = fetched;
            state = State.READY;
            refreshFailed = false;
            return;
        }
        if (ApiClient.isNotLeafMember(failure)) {
            snapshot = Snapshot.EMPTY;
            state = State.NOT_MEMBER;
            refreshFailed = false;
            return;
        }
        // A failed refresh keeps what the board already shows, and says it may be out of date.
        if (state == State.READY) {
            refreshFailed = true;
        } else {
            state = State.UNAVAILABLE;
        }
        SeqClient.LOGGER.warn("[Quests] Could not read quests from Leaf", failure);
    }
}
