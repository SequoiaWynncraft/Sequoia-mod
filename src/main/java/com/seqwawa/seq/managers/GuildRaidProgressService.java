package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.Clocks;
import com.seqwawa.seq.client.Refreshable;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.client.TickQueue;
import com.seqwawa.seq.model.GuildRaidProgress;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.session.SessionManager;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The guild's raid progress for the achievements screen, read from the backend while
 * it is connected, and again a few seconds after the player finishes a raid. Game
 * thread only, except the readers.
 */
public final class GuildRaidProgressService {

    public enum State {
        LOADING,
        READY,
        UNAVAILABLE
    }

    static final long REFRESH_INTERVAL_MS = Duration.ofMinutes(5).toMillis();

    /** How long after a raid the backend has counted it. */
    static final long RAID_SETTLE_DELAY_MS = Duration.ofSeconds(6).toMillis();

    private final BooleanSupplier connected;
    private final TickQueue ticks;
    private final BiConsumer<List<String>, BooleanSupplier> announcer;
    /** A failed read keeps what is already on screen. */
    private final Refreshable<GuildRaidProgress> progress;

    /** Counts sessions: a new connection, a disconnect and a reset each start one. Announcements check it. */
    private long session;
    private boolean wasConnected;
    /** Whether the last tick could not read: disconnected, or a scope that doesn't allow it. */
    private boolean offline;
    private TickQueue.Timer pendingRaidRefresh;
    private GuildRaidProgress lastLogged = GuildRaidProgress.EMPTY;

    GuildRaidProgressService(
            Supplier<CompletableFuture<GuildRaidProgress>> fetcher,
            LongSupplier clock,
            BooleanSupplier connected,
            TickQueue ticks,
            BiConsumer<List<String>, BooleanSupplier> announcer) {
        this.connected = connected;
        this.ticks = ticks;
        this.announcer = announcer;
        this.wasConnected = connected.getAsBoolean();
        this.progress = Refreshable.builder("guild raid progress", fetcher)
                .clock(clock)
                .handBack(ticks)
                .refreshEveryMs(REFRESH_INTERVAL_MS)
                .emptyValue(GuildRaidProgress.EMPTY)
                .whenFetched(this::fetched)
                .whenFailed(failure -> SeqClient.LOGGER.warn("[Achievements] Could not read guild raid progress", failure))
                .build();
    }

    /** The current session's. */
    public static GuildRaidProgressService getInstance() {
        return SessionManager.getInstance().current().raidProgress();
    }

    /** The progress for one session, fetched with {@code api}, which carries that session's token. */
    public static GuildRaidProgressService create(ApiClient api) {
        return new GuildRaidProgressService(
                () -> api.getGuildRaidProgress(ChatManager.canAnnounceAchievements()),
                Clocks::monotonicMillis,
                ConnectionManager::isConnected,
                ClientThread.queue(),
                ChatManager::announceAchievements);
    }

    public GuildRaidProgress progress() {
        return progress.value();
    }

    public State state() {
        Refreshable.State<GuildRaidProgress> current = progress.state();
        if (current.hasValue()) return State.READY;
        if (current.loading()) return State.LOADING;
        return current.failure() != null || offline ? State.UNAVAILABLE : State.LOADING;
    }

    /** Forgets everything, for a different account or session; the next tick reads at once. */
    public void reset() {
        session++;
        cancelRaidRefresh();
        progress.reset();
    }

    public void tick() {
        tick(true);
    }

    /** Called every client tick; {@code allowRefresh} is false where the server scope doesn't allow reading. */
    public void tick(boolean allowRefresh) {
        boolean online = connected.getAsBoolean();
        if (online && !wasConnected) {
            reset();
        } else if (!online && wasConnected) {
            // A read for the lost connection must not land, but what is on screen stays.
            session++;
            cancelRaidRefresh();
            progress.cancel();
        }
        wasConnected = online;
        offline = !online || !allowRefresh;
        if (!offline) {
            progress.request();
        }
    }

    /** Reads again once the backend has had time to count the raid, whatever the interval. */
    public void onLocalRaidCompleted() {
        cancelRaidRefresh();
        pendingRaidRefresh = ticks.after(RAID_SETTLE_DELAY_MS, () -> {
            pendingRaidRefresh = null;
            if (!connected.getAsBoolean()) {
                return;
            }
            // Behind a read already running, or where the scope doesn't allow reading, the next
            // allowed tick reads.
            progress.markStale();
            if (!offline) {
                progress.request();
            }
        });
    }

    public boolean requestRefresh() {
        return progress.request();
    }

    private void cancelRaidRefresh() {
        if (pendingRaidRefresh != null) {
            pendingRaidRefresh.cancel();
            pendingRaidRefresh = null;
        }
    }

    private void fetched(GuildRaidProgress fetched) {
        if (!fetched.equals(lastLogged)) {
            SeqClient.LOGGER.info("[Achievements] Guild raid progress updated, {} completions", fetched.totalCount());
            lastLogged = fetched;
        }
        if (!fetched.announcements().isEmpty()) {
            long fetchedIn = session;
            announcer.accept(fetched.announcements(), () -> fetchedIn == session && connected.getAsBoolean());
        }
    }
}
