package com.seqwawa.seq.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.RankProfilesResponse;
import com.seqwawa.seq.network.ApiClient;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
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
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;

    private static RankProfileRoster instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<Consumer<RankProfilesResponse>> subscribers = new CopyOnWriteArrayList<>();
    private final Path cachePath;
    private final Supplier<CompletableFuture<RankProfilesResponse>> fetchProfiles;
    private volatile RankProfilesResponse snapshot;
    private volatile boolean cacheLoaded;
    private volatile boolean refreshInFlight;
    private volatile long lastRefreshAttemptMs;
    private volatile Instant lastSuccessfulRefresh;
    private volatile String status = "not loaded";

    private RankProfileRoster() {
        this(defaultCachePath(), () -> ApiClient.getInstance().getRecognizedRankProfiles());
    }

    /** Test seam for exercising cache and refresh behavior without a game directory. */
    RankProfileRoster(Path cachePath, Supplier<CompletableFuture<RankProfilesResponse>> fetchProfiles) {
        this.cachePath = Objects.requireNonNull(cachePath, "cachePath");
        this.fetchProfiles = Objects.requireNonNull(fetchProfiles, "fetchProfiles");
        loadCache();
    }

    /** The shared roster; created on first use, which loads the on-disk cache. */
    public static synchronized RankProfileRoster getInstance() {
        if (instance == null) {
            instance = new RankProfileRoster();
        }
        return instance;
    }

    /**
     * Registers {@code consumer} for every future snapshot, and hands it the current
     * one straight away when the cache has already produced one. Without that catch-up
     * a consumer created after the cache load would sit empty until the next refresh.
     */
    public void subscribe(Consumer<RankProfilesResponse> consumer) {
        subscribers.add(consumer);
        RankProfilesResponse current = snapshot;
        if (current != null) {
            deliver(consumer, current);
        }
    }

    /** Refreshes when the cache has never loaded or the interval has elapsed. */
    public void tick() {
        long now = System.currentTimeMillis();
        if (!cacheLoaded || now - lastRefreshAttemptMs >= REFRESH_INTERVAL_MS) {
            refreshAsync();
        }
    }

    /**
     * Re-fetches the roster and publishes it. A failure leaves the previous snapshot
     * in place rather than clearing it, so features keep working while the backend is
     * unreachable.
     */
    public CompletableFuture<String> refreshAsync() {
        if (refreshInFlight) {
            return CompletableFuture.completedFuture("Rank profile refresh already running.");
        }
        refreshInFlight = true;
        lastRefreshAttemptMs = System.currentTimeMillis();
        status = "refreshing";

        return fetchProfiles
                .get()
                .thenApply(response -> {
                    publish(response);
                    writeCache(response);
                    lastSuccessfulRefresh = Instant.now();
                    status = "loaded " + profileCount(response) + " profiles";
                    return "Rank profiles refreshed: " + profileCount(response) + " profiles.";
                })
                .exceptionally(throwable -> {
                    status = "refresh failed";
                    SeqClient.LOGGER.debug("[RankProfiles] Failed to refresh roster: {}", rootMessage(throwable));
                    return "Rank profile refresh failed; using cached profiles. Cause: " + rootMessage(throwable);
                })
                .whenComplete((ignored, throwable) -> refreshInFlight = false);
    }

    /** One-line diagnostic shared by the rank and badge status commands. */
    public String status() {
        String refreshed = lastSuccessfulRefresh == null ? "never" : lastSuccessfulRefresh.toString();
        return "roster=" + profileCount(snapshot) + " profiles | status=" + status + " | last refresh=" + refreshed;
    }

    private void publish(RankProfilesResponse response) {
        requireValidSnapshot(response);
        snapshot = response;
        subscribers.forEach(consumer -> deliver(consumer, response));
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

    private void loadCache() {
        cacheLoaded = true;
        if (!Files.isRegularFile(cachePath)) {
            status = "no cache";
            return;
        }
        try {
            publish(gson.fromJson(Files.readString(cachePath), RankProfilesResponse.class));
            status = "loaded " + profileCount(snapshot) + " cached profiles";
        } catch (IOException | RuntimeException exception) {
            status = "cache load failed";
            SeqClient.LOGGER.warn("[RankProfiles] Failed to load the cached roster.", exception);
        }
    }

    private void writeCache(RankProfilesResponse response) {
        requireValidSnapshot(response);
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

    private static void requireValidSnapshot(RankProfilesResponse response) {
        if (response == null) {
            throw new IllegalArgumentException("Rank-profile snapshot is empty");
        }
        if (response.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported rank-profile schema version " + response.schemaVersion());
        }
        if (response.catalog() == null || response.profiles() == null) {
            throw new IllegalArgumentException("Rank-profile snapshot is incomplete");
        }
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
