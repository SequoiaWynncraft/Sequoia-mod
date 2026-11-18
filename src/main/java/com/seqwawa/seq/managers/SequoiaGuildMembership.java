package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.Clocks;
import com.seqwawa.seq.client.Refreshable;
import com.seqwawa.seq.network.WynncraftGuildClient;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Confirms in-game guild membership independently of Discord ranks and nickname aliases. */
public final class SequoiaGuildMembership {
    static final long REFRESH_INTERVAL_MS = WynncraftGuildClient.MINIMUM_REFRESH_INTERVAL.toMillis();
    static final long MAX_AGE_MS = Duration.ofMinutes(2).toMillis();
    private static SequoiaGuildMembership instance;

    private record Snapshot(WynncraftGuildClient.GuildRoster roster, long fetchedAtMs) {}

    private final LongSupplier clock;
    /** Cleared when a refresh fails: an old roster must not keep decorating a player who left. */
    private final Refreshable<Snapshot> roster;

    SequoiaGuildMembership(
            Supplier<CompletableFuture<WynncraftGuildClient.GuildRoster>> fetcher, LongSupplier clock, Executor handBack) {
        Objects.requireNonNull(fetcher);
        this.clock = Objects.requireNonNull(clock);
        this.roster = Refreshable.<Snapshot>builder("Sequoia guild membership", () -> fetcher.get().thenApply(fetched -> {
                    if (fetched == null || !"SEQ".equalsIgnoreCase(fetched.guildPrefix())) {
                        throw new IllegalStateException("The roster is not Sequoia's");
                    }
                    return new Snapshot(fetched, clock.getAsLong());
                }))
                .clock(clock)
                .handBack(handBack)
                .refreshEveryMs(REFRESH_INTERVAL_MS)
                .clearOnFailure()
                .build();
    }

    public static synchronized SequoiaGuildMembership getInstance() {
        if (instance == null) {
            instance = new SequoiaGuildMembership(
                    () -> WynncraftGuildClient.getInstance().fetchRoster("SEQ", null),
                    Clocks::monotonicMillis,
                    ClientThread.executor());
        }
        return instance;
    }

    /** Unknown and expired membership never authorize decoration. No I/O on the rendering path. */
    public boolean isMember(String username) {
        Snapshot current = roster.value();
        long age = current == null ? -1 : clock.getAsLong() - current.fetchedAtMs();
        return current != null && age >= 0 && age < MAX_AGE_MS && current.roster().member(username) != null;
    }

    /** Refreshes asynchronously at most once per upstream cache interval. */
    public void tick() {
        roster.request();
    }

    public void reset() {
        roster.reset();
    }
}
