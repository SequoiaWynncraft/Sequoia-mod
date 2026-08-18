package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.GuildRaidProgress;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.ConnectionManager;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class GuildRaidProgressService {

    public enum State {
        LOADING,
        READY,
        UNAVAILABLE
    }

    static final long REFRESH_INTERVAL_MS = Duration.ofMinutes(5).toMillis();

    private static final long RAID_SETTLE_DELAY_MS = Duration.ofSeconds(6).toMillis();

    private static GuildRaidProgressService instance;

    private final Supplier<CompletableFuture<GuildRaidProgress>> fetcher;
    private final LongSupplier clock;
    private final BooleanSupplier connected;
    private final Consumer<Runnable> raidDelay;

    private volatile GuildRaidProgress progress = GuildRaidProgress.EMPTY;
    private volatile State state = State.LOADING;
    private volatile boolean loading;
    private volatile long lastAttemptAtMs;
    private volatile int generation;

    GuildRaidProgressService(
            Supplier<CompletableFuture<GuildRaidProgress>> fetcher,
            LongSupplier clock,
            BooleanSupplier connected,
            Consumer<Runnable> raidDelay) {
        this.fetcher = fetcher;
        this.clock = clock;
        this.connected = connected;
        this.raidDelay = raidDelay;
    }

    public static synchronized GuildRaidProgressService getInstance() {
        if (instance == null) {
            instance = new GuildRaidProgressService(
                    () -> ApiClient.getInstance().getGuildRaidProgress(),
                    System::currentTimeMillis,
                    ConnectionManager::isConnected,
                    GuildRaidProgressService::runAfterSettleDelay);
        }
        return instance;
    }

    public GuildRaidProgress progress() {
        return progress;
    }

    public State state() {
        return state;
    }

    public synchronized void reset() {
        generation++;
        loading = false;
        lastAttemptAtMs = 0;
        progress = GuildRaidProgress.EMPTY;
        state = State.LOADING;
    }

    public synchronized void tick() {
        if (connected.getAsBoolean()) {
            requestRefresh();
        } else if (state == State.LOADING && !loading) {
            state = State.UNAVAILABLE;
        }
    }

    public void onLocalRaidCompleted() {
        raidDelay.accept(this::forceRefresh);
    }

    public synchronized boolean requestRefresh() {
        long now = clock.getAsLong();
        if (lastAttemptAtMs != 0 && now - lastAttemptAtMs < REFRESH_INTERVAL_MS) {
            return false;
        }
        return start(now);
    }

    boolean forceRefresh() {
        if (!connected.getAsBoolean()) {
            return false;
        }
        return start(clock.getAsLong());
    }

    private synchronized boolean start(long now) {
        if (loading) {
            return false;
        }
        loading = true;
        lastAttemptAtMs = now;
        if (state != State.READY) {
            state = State.LOADING;
        }
        int startedFor = generation;
        fetcher.get().whenComplete((fetched, failure) -> accept(startedFor, fetched, failure));
        return true;
    }

    private static void runAfterSettleDelay(Runnable action) {
        CompletableFuture.delayedExecutor(RAID_SETTLE_DELAY_MS, TimeUnit.MILLISECONDS).execute(action);
    }

    private synchronized void accept(int startedFor, GuildRaidProgress fetched, Throwable failure) {
        if (startedFor != generation) {
            return;
        }
        loading = false;
        if (fetched == null) {
            if (state != State.READY) {
                state = State.UNAVAILABLE;
            }
            SeqClient.LOGGER.warn("[Achievements] Could not read the graid progress", failure);
            return;
        }
        if (!fetched.equals(progress)) {
            SeqClient.LOGGER.info("[Achievements] Graid progress updated, {} completions", fetched.totalCount());
        }
        progress = fetched;
        state = State.READY;
    }
}
