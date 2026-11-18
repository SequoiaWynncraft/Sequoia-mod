package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafEvents;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.network.ApiClient;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;

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

    /**
     * What the board and the sidebar show, all from the same read: the balance and
     * level, the quests, and the latest events, newest first.
     */
    public record Snapshot(LeafAccount account, LeafQuests quests, List<LeafEvents.Event> recent, boolean recentFailed) {
        public static final Snapshot EMPTY = new Snapshot(LeafAccount.EMPTY, LeafQuests.EMPTY);

        public Snapshot(LeafAccount account, LeafQuests quests) {
            this(account, quests, List.of(), false);
        }

        public Snapshot(LeafAccount account, LeafQuests quests, List<LeafEvents.Event> recent) {
            this(account, quests, recent, false);
        }

        public Snapshot {
            recent = recent == null ? List.of() : recent.stream().filter(Objects::nonNull).toList();
        }
    }

    static final long REFRESH_INTERVAL_MS = Duration.ofMinutes(1).toMillis();
    /**
     * How long a whole read may take. A request's own timeout ends once the reply's
     * headers arrive, so a reply that stalls after them would otherwise keep the
     * board loading for good.
     */
    static final Duration READ_DEADLINE = Duration.ofSeconds(45);
    /** How long the recent events may take before the board shows the rest without them. */
    static final Duration EVENTS_DEADLINE = Duration.ofSeconds(20);

    private static LeafQuestService instance;

    /** Reads Leaf; it is told whether the session it started under is still the current one. */
    private final Function<BooleanSupplier, CompletableFuture<Snapshot>> fetcher;
    private final LongSupplier clock;
    private final Duration readDeadline;

    private volatile Snapshot snapshot = Snapshot.EMPTY;
    private volatile State state = State.LOADING;
    private volatile boolean loading;
    private volatile boolean refreshFailed;
    private volatile long lastAttemptAtMs;
    private volatile int generation;
    private volatile boolean preview;

    LeafQuestService(Function<BooleanSupplier, CompletableFuture<Snapshot>> fetcher, LongSupplier clock,
            Duration readDeadline) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.readDeadline = readDeadline;
    }

    public static synchronized LeafQuestService getInstance() {
        if (instance == null) {
            instance = new LeafQuestService(LeafQuestService::fetch, System::currentTimeMillis, READ_DEADLINE);
        }
        return instance;
    }

    private static CompletableFuture<Snapshot> fetch(BooleanSupplier sessionCurrent) {
        ApiClient api = ApiClient.getInstance();
        var auth = SeqClient.getAuthService();
        long session = auth.sessionGeneration();
        Function<Boolean, CompletableFuture<String>> tokenFor = sessionToken(sessionCurrent,
                force -> auth.ensureValidToken(force, session));
        // Quests first: with sync=1 Leaf pays what was just finished, so the balance read after it includes it.
        // The recent events are a nicety: without them the board still shows the balance and the quests, and
        // says the list could not be read. A blank answer is an empty list, not a failure.
        return api.getLeafQuests(true, tokenFor).thenCompose(quests -> {
            CompletableFuture<LeafEvents> events = api.getLeafEvents(tokenFor)
                    .orTimeout(EVENTS_DEADLINE.toMillis(), TimeUnit.MILLISECONDS)
                    .thenApply(recent -> recent == null ? LeafEvents.EMPTY : recent)
                    .exceptionally(failure -> null);
            return api.getLeafAccount(tokenFor).thenCombine(events, (account, recent) -> recent == null
                    ? new Snapshot(account, quests, List.of(), true)
                    : new Snapshot(account, quests, recent.eventsOrEmpty(), false));
        });
    }

    /**
     * Where a read gets its tokens, the fresh one after a 401 included: from
     * {@code tokens} while the read is still wanted, and nowhere once the board
     * has been reset. The tokens themselves come from the auth service bound to
     * the session the read began under, which refuses to sign in once that
     * session is cleared, so a read still running then can finish but never
     * signs the player in again.
     */
    static Function<Boolean, CompletableFuture<String>> sessionToken(BooleanSupplier sessionCurrent,
            Function<Boolean, CompletableFuture<String>> tokens) {
        return force -> sessionCurrent.getAsBoolean()
                ? tokens.apply(force)
                : CompletableFuture.failedFuture(new CancellationException("The session this read started under has ended"));
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
        preview = false;
        snapshot = Snapshot.EMPTY;
        state = State.LOADING;
    }

    /** Makes the next {@link #requestRefresh()} read at once: Leaf just reported a change. */
    public void markStale() {
        lastAttemptAtMs = 0;
    }

    /** Shows fixed sample data and never reads Leaf. Only for the development preview. */
    public synchronized void showPreview(Snapshot sample) {
        generation++;
        preview = true;
        loading = false;
        refreshFailed = false;
        snapshot = sample;
        state = State.READY;
    }

    /**
     * Reads unless a read is running or the last attempt was under a minute ago. The
     * first read after starting, {@link #reset()} or {@link #markStale()} always goes ahead.
     */
    public synchronized boolean requestRefresh() {
        long now = clock.getAsLong();
        if (loading || preview || lastAttemptAtMs != 0 && now - lastAttemptAtMs < REFRESH_INTERVAL_MS) {
            return false;
        }
        loading = true;
        lastAttemptAtMs = now;
        if (state != State.READY) {
            state = State.LOADING;
        }
        int startedFor = generation;
        try {
            fetcher.apply(() -> startedFor == generation)
                    .copy()
                    .orTimeout(readDeadline.toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((fetched, failure) -> accept(startedFor, fetched, failure));
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
