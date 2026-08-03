package com.seqwawa.seq.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.ItemScale;
import com.seqwawa.seq.model.ItemScalesResponse;
import com.seqwawa.seq.network.ApiClient;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Holds the community stat weights used to score item rolls, indexed by item name.
 * <p>
 * Backed by {@code /v1/item-scales} and cached on disk, so tooltips keep working
 * offline and before the first refresh of a session lands.
 */
public final class ItemScaleService {
    private static final long REFRESH_INTERVAL_MS = 30 * 60 * 1000L;

    private static ItemScaleService instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private volatile Map<String, ItemScale> scales = Map.of();
    private volatile boolean cacheLoaded;
    private volatile boolean refreshInFlight;
    private volatile long lastRefreshAttemptMs;
    private volatile Instant lastSuccessfulRefresh;
    private volatile String status = "not loaded";

    private ItemScaleService() {
        loadCache();
    }

    public static synchronized ItemScaleService getInstance() {
        if (instance == null) {
            instance = new ItemScaleService();
        }
        return instance;
    }

    /** True once at least one weighted item is known, i.e. scoring can do something useful. */
    public boolean hasScales() {
        return !scales.isEmpty();
    }

    public ItemScale scaleFor(String itemName) {
        String key = normalizeKey(itemName);
        return key == null ? null : scales.get(key);
    }

    public int size() {
        return scales.size();
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (!cacheLoaded || now - lastRefreshAttemptMs >= REFRESH_INTERVAL_MS) {
            refreshAsync();
        }
    }

    public CompletableFuture<String> refreshAsync() {
        if (refreshInFlight) {
            return CompletableFuture.completedFuture("Item scale refresh already running.");
        }
        refreshInFlight = true;
        lastRefreshAttemptMs = System.currentTimeMillis();
        status = "refreshing";

        return ApiClient.getInstance()
                .getItemScales()
                .thenApply(response -> {
                    Map<String, ItemScale> parsed = parseScales(response);
                    scales = parsed;
                    writeCache(response);
                    lastSuccessfulRefresh = Instant.now();
                    status = "loaded " + parsed.size() + " weighted items";
                    return "Item scales refreshed: " + parsed.size() + " weighted items.";
                })
                .exceptionally(throwable -> {
                    status = "refresh failed";
                    SeqClient.LOGGER.debug("[ItemScale] Failed to refresh item scales: {}", rootMessage(throwable));
                    return "Item scale refresh failed; using cached scales. Cause: " + rootMessage(throwable);
                })
                .whenComplete((ignored, throwable) -> refreshInFlight = false);
    }

    public String status() {
        String refreshed = lastSuccessfulRefresh == null ? "never" : lastSuccessfulRefresh.toString();
        return "Item scales: " + scales.size() + " weighted items | status=" + status + " | last refresh=" + refreshed;
    }

    private void loadCache() {
        cacheLoaded = true;
        Path cachePath = cachePath();
        if (!Files.isRegularFile(cachePath)) {
            status = "no cache";
            return;
        }
        try {
            ItemScalesResponse response = gson.fromJson(Files.readString(cachePath), ItemScalesResponse.class);
            scales = parseScales(response);
            status = "loaded " + scales.size() + " cached weighted items";
        } catch (IOException | RuntimeException exception) {
            status = "cache load failed";
            SeqClient.LOGGER.warn("[ItemScale] Failed to load cached item scales.", exception);
        }
    }

    // ── Parsing ──

    /**
     * Items whose weights are all zero are dropped: they carry no ranking signal, and
     * keeping them would show an empty scale block on a tooltip for no reason.
     */
    static Map<String, ItemScale> parseScales(ItemScalesResponse response) {
        if (response == null || response.schemaVersion() != 1) {
            throw new IllegalArgumentException("Unsupported or incomplete item scale response");
        }
        if (response.scales() == null) {
            return Map.of();
        }

        Map<String, ItemScale> parsed = new HashMap<>();
        for (ItemScalesResponse.ScaleDefinition definition : response.scales()) {
            if (definition == null) {
                continue;
            }
            String key = normalizeKey(definition.itemName());
            if (key == null) {
                continue;
            }
            Map<String, Double> weights = nonZeroWeights(definition.weights());
            if (!weights.isEmpty()) {
                parsed.put(key, new ItemScale(definition.itemName().trim(), weights));
            }
        }
        return Map.copyOf(parsed);
    }

    private static Map<String, Double> nonZeroWeights(Map<String, Double> weights) {
        if (weights == null) {
            return Map.of();
        }
        Map<String, Double> kept = new HashMap<>();
        for (Map.Entry<String, Double> entry : weights.entrySet()) {
            String stat = entry.getKey();
            Double weight = entry.getValue();
            if (stat != null && !stat.isBlank() && weight != null && weight != 0.0) {
                kept.put(stat.trim(), weight);
            }
        }
        return kept;
    }

    private static String normalizeKey(String itemName) {
        if (itemName == null) {
            return null;
        }
        String normalized = itemName.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    // ── Cache ──

    private void writeCache(ItemScalesResponse response) {
        try {
            Path cachePath = cachePath();
            Files.createDirectories(cachePath.getParent());
            Path temp = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
            Files.writeString(temp, gson.toJson(response));
            try {
                Files.move(temp, cachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[ItemScale] Failed to write item scale cache.", exception);
        }
    }

    private static Path cachePath() {
        return FabricLoader.getInstance()
                .getGameDir()
                .resolve("config")
                .resolve("sequoia")
                .resolve("cache")
                .resolve("item-scales.json");
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
