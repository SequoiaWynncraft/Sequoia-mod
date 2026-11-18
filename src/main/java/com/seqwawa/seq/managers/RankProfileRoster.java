package com.seqwawa.seq.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.seqwawa.seq.client.Background;
import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.Clocks;
import com.seqwawa.seq.client.Refreshable;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.RankProfilesResponse;
import com.seqwawa.seq.network.ApiClient;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Single source of Sequoia community profiles, fetched once and shared by every
 * feature that needs them.
 * <p>
 * Backed by {@code /v1/rank-profiles?scope=recognized}, which is a superset of
 * the other scopes: {@code scope=linked} is exactly this response filtered to
 * profiles carrying both a Discord and a Minecraft identity, with identical
 * per-member data and an identical catalog. Polling it once therefore serves
 * leaderboard badges and guild-chat ranks alike, and moves less data than the
 * two separate polls it replaces.
 * <p>
 * Consumers {@link #subscribe} and rebuild their own index whenever a snapshot
 * arrives; the roster itself interprets nothing.
 */
public final class RankProfileRoster {
    static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;

    private static RankProfileRoster instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<Consumer<RankProfilesResponse>> subscribers = new CopyOnWriteArrayList<>();
    private final Path cachePath;
    private final Executor disk;
    private final Clock clock;
    /** A failed refresh keeps the last snapshot, so badges and ranks keep working while the backend is down. */
    private final Refreshable<RankProfilesResponse> profiles;
    private volatile Instant lastSuccessfulRefresh;
    /** What reading the cache came to, shown until a refresh has worked. */
    private volatile String cacheStatus = "not loaded";

    private RankProfileRoster() {
        this(defaultCachePath(), () -> ApiClient.getInstance().getRecognizedRankProfiles(), Background.disk(),
                ClientThread.executor(), Clocks::monotonicMillis, Clock.systemUTC());
    }

    /** Test seam for exercising cache and refresh behavior without a game directory. */
    RankProfileRoster(Path cachePath, Supplier<CompletableFuture<RankProfilesResponse>> fetchProfiles, Executor disk,
            Executor handBack, LongSupplier millis, Clock clock) {
        this.cachePath = Objects.requireNonNull(cachePath, "cachePath");
        Objects.requireNonNull(fetchProfiles, "fetchProfiles");
        this.disk = Objects.requireNonNull(disk, "disk");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.profiles = Refreshable.<RankProfilesResponse>builder("rank profiles",
                        () -> fetchProfiles.get().thenApply(RankProfileRoster::requireValidSnapshot))
                .clock(millis)
                .handBack(handBack)
                .refreshEveryMs(REFRESH_INTERVAL_MS)
                .whenFetched(this::fetched)
                .whenFailed(failure -> SeqClient.LOGGER.debug(
                        "[RankProfiles] Failed to refresh roster: {}", rootMessage(failure)))
                .build();
        loadCache(handBack);
    }

    /** The shared roster; created on first use, which starts reading the on-disk cache. */
    public static synchronized RankProfileRoster getInstance() {
        if (instance == null) {
            instance = new RankProfileRoster();
        }
        return instance;
    }

    /**
     * Registers {@code consumer} for every future snapshot, and hands it the current
     * one straight away when there already is one. Without that catch-up a consumer
     * created after the cache load would sit empty until the next refresh. Snapshots
     * arrive on the game thread.
     */
    public void subscribe(Consumer<RankProfilesResponse> consumer) {
        ClientThread.check("RankProfileRoster.subscribe");
        subscribers.add(consumer);
        Refreshable.State<RankProfilesResponse> current = profiles.state();
        if (current.hasValue()) {
            deliver(consumer, current.value());
        }
    }

    /** Refreshes when there has been no refresh yet or the interval has passed. Game thread only. */
    public void tick() {
        profiles.request();
    }

    /**
     * Re-fetches the roster now and says how it went, for the refresh commands. A
     * failure leaves the previous snapshot in place rather than clearing it.
     */
    public CompletableFuture<String> refreshAsync() {
        if (!profiles.force()) {
            return CompletableFuture.completedFuture("Rank profile refresh already running.");
        }
        return profiles.settled().thenApply(state -> state.failure() == null
                ? "Rank profiles refreshed: " + profileCount(state.value()) + " profiles."
                : "Rank profile refresh failed; using cached profiles. Cause: " + rootMessage(state.failure()));
    }

    /** One-line diagnostic shared by the rank and badge status commands. */
    public String status() {
        Refreshable.State<RankProfilesResponse> state = profiles.state();
        String phase = state.loading() ? "refreshing"
                : state.failure() != null ? "refresh failed"
                : lastSuccessfulRefresh != null ? "loaded " + profileCount(state.value()) + " profiles"
                : cacheStatus;
        String refreshed = lastSuccessfulRefresh == null ? "never" : lastSuccessfulRefresh.toString();
        return "roster=" + profileCount(state.value()) + " profiles | status=" + phase + " | last refresh=" + refreshed;
    }

    private void fetched(RankProfilesResponse response) {
        lastSuccessfulRefresh = clock.instant();
        subscribers.forEach(consumer -> deliver(consumer, response));
        disk.execute(() -> writeCache(response));
    }

    /**
     * One bad consumer must not stop the others from seeing the snapshot, nor bring
     * down the refresh that produced it.
     */
    private static void deliver(Consumer<RankProfilesResponse> consumer, RankProfilesResponse response) {
        try {
            consumer.accept(response);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[RankProfiles] A roster consumer rejected the snapshot.", exception);
        }
    }

    /**
     * Reads and checks the cache on the disk thread, then shows it on the game thread
     * unless a refresh already brought a newer roster.
     */
    private void loadCache(Executor handBack) {
        CompletableFuture.supplyAsync(this::readCache, disk).whenComplete((cached, failure) -> handBack.execute(() -> {
            if (failure != null) {
                cacheStatus = "cache load failed";
                SeqClient.LOGGER.warn("[RankProfiles] Failed to load the cached roster.", failure);
            } else if (cached == null) {
                cacheStatus = "no cache";
            } else if (profiles.seed(cached)) {
                cacheStatus = "loaded " + profileCount(cached) + " cached profiles";
                subscribers.forEach(consumer -> deliver(consumer, cached));
            }
        }));
    }

    /** The cached roster, null when there is no cache file. Throws when it can't be read or isn't valid. */
    private RankProfilesResponse readCache() {
        if (!Files.isRegularFile(cachePath)) {
            return null;
        }
        try {
            return requireValidSnapshot(gson.fromJson(Files.readString(cachePath), RankProfilesResponse.class));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void writeCache(RankProfilesResponse response) {
        try {
            Files.createDirectories(cachePath.getParent());
            Path temp = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
            Files.writeString(temp, gson.toJson(response));
            try {
                Files.move(temp, cachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[RankProfiles] Failed to write the roster cache.", exception);
        }
    }

    private static int profileCount(RankProfilesResponse response) {
        return response == null || response.profiles() == null ? 0 : response.profiles().size();
    }

    private static RankProfilesResponse requireValidSnapshot(RankProfilesResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Rank-profile snapshot is empty");
        }
        if (response.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported rank-profile schema version " + response.schemaVersion());
        }
        if (response.catalog() == null || response.profiles() == null) {
            throw new IllegalArgumentException("Rank-profile snapshot is incomplete");
        }
        return response;
    }

    private static Path defaultCachePath() {
        return FabricLoader.getInstance()
                .getGameDir()
                .resolve("config")
                .resolve("sequoia")
                .resolve("cache")
                .resolve("rank-profiles.json");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        if (message == null || message.isBlank()) {
            return current == null ? "unknown" : current.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').replace('\r', ' ').toLowerCase(Locale.ROOT);
    }
}
