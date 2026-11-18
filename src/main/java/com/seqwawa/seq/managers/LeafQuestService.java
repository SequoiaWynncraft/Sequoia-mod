package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.Clocks;
import com.seqwawa.seq.client.Refreshable;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.client.TickQueue;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafEvents;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.NotSignedInException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
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
        /** There is no Sequoia session to read Leaf with, until the player connects. */
        SIGNED_OUT,
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

    /**
     * One read of Leaf: the sign-in session it belongs to, and whether the board
     * still wants it.
     */
    record Read(long session, BooleanSupplier wanted) {}

    private final Executor gameThread;
    /** The auth service's current session, as {@link MinecraftAuthService#sessionGeneration()} counts them. */
    private final LongSupplier authSession;
    /**
     * Keeps what the board shows when a refresh fails, and forgets it when there is no
     * session to read with or the player is not a member. A read that takes longer
     * than the deadline fails, so a reply that stalls after its headers can't keep the
     * board loading for good.
     */
    private final Refreshable<Snapshot> reads;
    /**
     * The session what this shows belongs to: the one current when it started or
     * was last reset. Clearing a session moves the auth service on first and resets
     * this after, so a read started in between still belongs to the old session,
     * and the auth service will not sign it in.
     */
    private long session;
    /** Fixed sample data the development preview shows instead of reading Leaf, or null. */
    private Snapshot preview;

    LeafQuestService(Function<Read, CompletableFuture<Snapshot>> fetcher, LongSupplier clock, Duration readDeadline,
            LongSupplier authSession, TickQueue gameThread) {
        this.gameThread = gameThread;
        this.authSession = authSession;
        this.session = authSession.getAsLong();
        this.reads = Refreshable.<Snapshot>builderWithTicket("Leaf quests",
                        ticket -> fetcher.apply(new Read(session, ticket::current)))
                .clock(clock)
                .handBack(gameThread)
                .refreshEveryMs(REFRESH_INTERVAL_MS)
                .deadlineMs(readDeadline.toMillis())
                .emptyValue(Snapshot.EMPTY)
                .clearOnFailure(failure -> signedOut(failure) || ApiClient.isNotLeafMember(failure))
                .whenFailed(failure -> {
                    if (!signedOut(failure) && !ApiClient.isNotLeafMember(failure)) {
                        SeqClient.LOGGER.warn("[Quests] Could not read quests from Leaf", failure);
                    }
                })
                .build();
    }

    public static synchronized LeafQuestService getInstance() {
        if (instance == null) {
            instance = new LeafQuestService(LeafQuestService::fetch, Clocks::monotonicMillis, READ_DEADLINE,
                    () -> SeqClient.getAuthService() == null ? 0 : SeqClient.getAuthService().sessionGeneration(),
                    ClientThread.queue());
        }
        return instance;
    }

    private static CompletableFuture<Snapshot> fetch(Read read) {
        ApiClient api = ApiClient.getInstance();
        MinecraftAuthService auth = SeqClient.getAuthService();
        Function<Boolean, CompletableFuture<String>> tokenFor = sessionToken(read.wanted(),
                force -> auth.ensureSessionToken(force, read.session()));
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

    /** Whether a read failed because there was no session to read with. */
    static boolean signedOut(Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof java.util.concurrent.CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof NotSignedInException;
    }

    public Snapshot snapshot() {
        Snapshot shown = preview;
        return shown != null ? shown : reads.value();
    }

    public State state() {
        if (preview != null) return State.READY;
        Refreshable.State<Snapshot> current = reads.state();
        if (current.hasValue()) return State.READY;
        if (current.loading() || current.failure() == null) return State.LOADING;
        if (signedOut(current.failure())) return State.SIGNED_OUT;
        if (ApiClient.isNotLeafMember(current.failure())) return State.NOT_MEMBER;
        return State.UNAVAILABLE;
    }

    /** Whether the last read failed while the board still shows what an earlier one found. */
    public boolean refreshFailed() {
        Refreshable.State<Snapshot> current = reads.state();
        return preview == null && current.hasValue() && current.failure() != null;
    }

    /** Forgets everything, for a different account. Game thread only. */
    public void reset() {
        session = authSession.getAsLong();
        preview = null;
        reads.reset();
    }

    /**
     * Makes the next {@link #requestRefresh()} read at once: Leaf just reported a change.
     * Any thread may call it; it takes effect on the game thread.
     */
    public void markStale() {
        gameThread.execute(reads::markStale);
    }

    /** Shows fixed sample data and never reads Leaf. Only for the development preview. */
    public void showPreview(Snapshot sample) {
        reads.cancel();
        preview = sample;
    }

    /**
     * Reads unless a read is running or the last attempt was under a minute ago. The
     * first read after starting, {@link #reset()} or {@link #markStale()} always goes ahead.
     * Game thread only.
     */
    public boolean requestRefresh() {
        return preview == null && reads.request();
    }
}
