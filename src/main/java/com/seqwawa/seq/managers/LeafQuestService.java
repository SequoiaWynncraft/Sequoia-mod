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
import com.seqwawa.seq.network.auth.NotSignedInException;
import com.seqwawa.seq.session.SessionManager;
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
     * level, the quests, and the latest events.
     *
     * @param recentFailed whether the latest events could not be read, so {@code recent} is empty for that reason
     */
    public record Snapshot(LeafAccount account, LeafQuests quests, LeafEvents recent, boolean recentFailed) {
        public static final Snapshot EMPTY = new Snapshot(LeafAccount.EMPTY, LeafQuests.EMPTY);

        public Snapshot(LeafAccount account, LeafQuests quests) {
            this(account, quests, LeafEvents.EMPTY, false);
        }

        public Snapshot(LeafAccount account, LeafQuests quests, LeafEvents recent) {
            this(account, quests, recent, false);
        }

        public Snapshot {
            recent = recent == null ? LeafEvents.EMPTY : recent;
        }

        /** This snapshot after a reroll Leaf made: the new quest in its slot, and the balance after paying. */
        Snapshot withRerolled(LeafQuests.Rerolled rerolled) {
            LeafAccount shownAccount = account == null ? LeafAccount.EMPTY : account;
            LeafQuests shownQuests = quests == null ? LeafQuests.EMPTY : quests;
            return new Snapshot(shownAccount.withLeaf(rerolled.leaf()), shownQuests.withQuest(rerolled.quest()), recent,
                    recentFailed);
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

    /** One read of Leaf: whether the board still wants it. */
    record Read(BooleanSupplier wanted) {}

    /** How a reroll the board asked for ended. */
    public enum Reroll {
        /** The quest was swapped; the board already shows the new one. */
        DONE,
        /** The balance doesn't cover what it costs. */
        NOT_ENOUGH_LEAF,
        /** The quest was finished or swapped since the board read it; the board reads again. */
        CHANGED,
        /** Rerolling it costs something other than the board showed now; the board reads again. */
        COST_CHANGED,
        /** Another reroll was still waiting on Leaf, so this one wasn't sent. */
        BUSY,
        FAILED
    }

    private final Executor gameThread;
    /** Asks Leaf to swap one quest for another, paying the cost it was shown with. */
    private final Function<LeafQuests.Quest, CompletableFuture<LeafQuests.Rerolled>> rerolls;
    /**
     * Keeps what the board shows when a refresh fails, and forgets it when there is no
     * session to read with or the player is not a member. A read that takes longer
     * than the deadline fails, so a reply that stalls after its headers can't keep the
     * board loading for good.
     */
    private final Refreshable<Snapshot> reads;
    /** Fixed sample data the development preview shows instead of reading Leaf, or null. */
    private Snapshot preview;
    /** The quest a reroll is waiting on Leaf for, or null. Kept here, not on a board, so reopening one can't start a second. */
    private String rerolling;
    /** Counts {@link #reset()}s, so a reroll's answer that arrives after one changes nothing. */
    private int generation;

    LeafQuestService(Function<Read, CompletableFuture<Snapshot>> fetcher, LongSupplier clock, Duration readDeadline,
            TickQueue gameThread) {
        this(fetcher, quest -> CompletableFuture.failedFuture(new UnsupportedOperationException("No rerolls here")),
                clock, readDeadline, gameThread);
    }

    LeafQuestService(Function<Read, CompletableFuture<Snapshot>> fetcher,
            Function<LeafQuests.Quest, CompletableFuture<LeafQuests.Rerolled>> rerolls, LongSupplier clock,
            Duration readDeadline,
            TickQueue gameThread) {
        this.gameThread = gameThread;
        this.rerolls = rerolls;
        this.reads = Refreshable.<Snapshot>builderWithTicket("Leaf quests",
                        ticket -> fetcher.apply(new Read(ticket::current)))
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

    /** The current session's. */
    public static LeafQuestService getInstance() {
        return SessionManager.getInstance().current().leafQuests();
    }

    /** Reads Leaf with one session's {@code tokens}, which refuse once it has ended. */
    public static LeafQuestService forSession(Function<Boolean, CompletableFuture<String>> tokens) {
        // The session's tokens refuse once it has ended, so a reroll never goes out for the next account.
        return new LeafQuestService(read -> fetch(read, tokens),
                quest -> ApiClient.getInstance().rerollLeafQuest(quest, tokens), Clocks::monotonicMillis,
                READ_DEADLINE, ClientThread.queue());
    }

    private static CompletableFuture<Snapshot> fetch(Read read, Function<Boolean, CompletableFuture<String>> tokens) {
        ApiClient api = ApiClient.getInstance();
        Function<Boolean, CompletableFuture<String>> tokenFor = sessionToken(read.wanted(), tokens);
        // Quests first: with sync=1 Leaf pays what was just finished, so the balance read after it includes it.
        // The recent events are a nicety: without them the board still shows the balance and the quests, and
        // says the list could not be read. A blank answer is an empty list, not a failure.
        return api.getLeafQuests(true, tokenFor).thenCompose(quests -> {
            CompletableFuture<LeafEvents> events = api.getLeafEvents(tokenFor)
                    .orTimeout(EVENTS_DEADLINE.toMillis(), TimeUnit.MILLISECONDS)
                    .thenApply(recent -> recent == null ? LeafEvents.EMPTY : recent)
                    .exceptionally(failure -> null);
            return api.getLeafAccount(tokenFor).thenCombine(events, (account, recent) -> recent == null
                    ? new Snapshot(account, quests, LeafEvents.EMPTY, true)
                    : new Snapshot(account, quests, recent, false));
        });
    }

    /**
     * Where a read gets its tokens, the fresh one after a 401 included: from
     * {@code tokens} while the read is still wanted, and nowhere once the board
     * has been reset. The tokens themselves are the board's own session's, which
     * refuse once that session has ended, so a read still running then can finish
     * but never signs the player in again.
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

    /** Forgets everything and drops the read running, for a session that ended. Game thread only. */
    public void reset() {
        preview = null;
        rerolling = null;
        generation++;
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
     * Asks Leaf to swap {@code quest} for another, paying the cost the board showed with
     * it, {@link LeafQuests.Quest#rerollLeaf()}, and nothing else. Once Leaf has, the
     * snapshot shows the new quest and balance at once, and the next refresh reads the
     * rest. Completes on the game thread. Game thread only.
     */
    public CompletableFuture<Reroll> reroll(LeafQuests.Quest quest) {
        if (preview != null || quest == null || !quest.rerollable()) return CompletableFuture.completedFuture(Reroll.FAILED);
        if (rerolling != null) return CompletableFuture.completedFuture(Reroll.BUSY);
        String questId = quest.id();
        rerolling = questId;
        int started = generation;
        CompletableFuture<Reroll> outcome = new CompletableFuture<>();
        rerolls.apply(quest).whenComplete((rerolled, failure) -> gameThread.execute(() -> {
            if (started != generation) {
                outcome.complete(Reroll.FAILED);
                return;
            }
            rerolling = null;
            if (failure == null && rerolled != null && rerolled.quest() != null && rerolled.quest().slot() != null) {
                reads.mutate(shown -> shown.withRerolled(rerolled));
                reads.markStale();
                outcome.complete(Reroll.DONE);
                return;
            }
            String code = ApiClient.leafErrorCode(failure);
            if ("insufficient_leaf".equals(code)) {
                outcome.complete(Reroll.NOT_ENOUGH_LEAF);
                return;
            }
            reads.markStale();
            if ("cost_changed".equals(code)) {
                outcome.complete(Reroll.COST_CHANGED);
                return;
            }
            if ("not_current".equals(code) || "not_rerollable".equals(code)) {
                outcome.complete(Reroll.CHANGED);
                return;
            }
            if (!signedOut(failure)) SeqClient.LOGGER.warn("[Quests] Could not reroll {}", questId, failure);
            outcome.complete(Reroll.FAILED);
        }));
        return outcome;
    }

    /** The id of the quest a reroll is waiting on Leaf for, or null when none is. */
    public String rerolling() {
        return rerolling;
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
