package com.seqwawa.seq.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class GatheringNodeService {
    private static final String STATIC_NODES_RESOURCE = "assets/seq/map/gathering-nodes.json";
    static final URI DEFAULT_ENDPOINT = URI.create("https://api.wynncraft.com/v3/map/gathering-nodes");
    // Wynncraft caches this route for one hour.
    static final long REFRESH_INTERVAL_MS = Duration.ofHours(1).toMillis();
    static final long RETRY_INTERVAL_MS = Duration.ofMinutes(1).toMillis();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final GatheringNodeService INSTANCE = new GatheringNodeService();

    private final HttpClient httpClient;
    private final URI endpoint;
    private final LongSupplier currentTimeMillis;
    private final Executor executor;
    private final Supplier<InputStream> bundledNodes;
    private final Map<GatheringNodeSource, Cache> caches = Map.of(
            GatheringNodeSource.STATIC, new Cache(), GatheringNodeSource.WYNN_API, new Cache());

    public static GatheringNodeService getInstance() {
        return INSTANCE;
    }

    private GatheringNodeService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                DEFAULT_ENDPOINT, System::currentTimeMillis,
                // A slow API request must not hold up switching to the local file.
                Executors.newFixedThreadPool(2, runnable -> {
                    Thread thread = new Thread(runnable, "seq-gathering-nodes");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    GatheringNodeService(HttpClient httpClient, URI endpoint, LongSupplier currentTimeMillis, Executor executor) {
        this(httpClient, endpoint, currentTimeMillis, executor,
                () -> GatheringNodeService.class.getClassLoader().getResourceAsStream(STATIC_NODES_RESOURCE));
    }

    GatheringNodeService(HttpClient httpClient, URI endpoint, LongSupplier currentTimeMillis, Executor executor,
            Supplier<InputStream> bundledNodes) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.currentTimeMillis = currentTimeMillis;
        this.executor = executor;
        this.bundledNodes = bundledNodes;
    }

    public List<GatheringNode> nodes(GatheringNodeSource source) {
        return caches.get(source).nodes;
    }

    public String status(GatheringNodeSource source) {
        return caches.get(source).status;
    }

    public boolean isLoading(GatheringNodeSource source) {
        return caches.get(source).loading;
    }

    public synchronized boolean requestRefresh(GatheringNodeSource source) {
        Cache cache = caches.get(source);
        long now = currentTimeMillis.getAsLong();
        if (cache.loading || (source == GatheringNodeSource.STATIC && !cache.nodes.isEmpty())
                || (cache.attempted && now - cache.lastAttemptAtMs < cache.refreshIntervalMs)) {
            return false;
        }
        cache.attempted = true;
        cache.lastAttemptAtMs = now;
        cache.loading = true;
        cache.status = source == GatheringNodeSource.STATIC ? "Loading static nodes..."
                : cache.nodes.isEmpty() ? "Loading nodes..." : "Refreshing nodes...";
        try {
            // Both loading and parsing stay off the render thread. The source is
            // captured here, so completion only updates that source's cache.
            executor.execute(() -> fetchNodes(source));
            return true;
        } catch (RuntimeException exception) {
            failRefresh(source, exception);
            return false;
        }
    }

    private void fetchNodes(GatheringNodeSource source) {
        try {
            List<GatheringNode> fetchedNodes;
            if (source == GatheringNodeSource.STATIC) {
                try (InputStream input = bundledNodes.get()) {
                    if (input == null) {
                        throw new IllegalStateException("Missing bundled node resource " + STATIC_NODES_RESOURCE);
                    }
                    fetchedNodes = parseNodes(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                }
            } else {
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                        .header("Accept", "application/json")
                        .header("User-Agent", "Sequoia-Mod")
                        .timeout(REQUEST_TIMEOUT)
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("API returned HTTP " + response.statusCode());
                }
                fetchedNodes = parseNodes(response.body());
            }
            completeRefresh(source, fetchedNodes);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            failRefresh(source, exception);
        }
    }

    private synchronized void completeRefresh(GatheringNodeSource source, List<GatheringNode> fetchedNodes) {
        Cache cache = caches.get(source);
        if (!cache.nodes.equals(fetchedNodes)) {
            cache.nodes = fetchedNodes;
        }
        cache.refreshIntervalMs = REFRESH_INTERVAL_MS;
        cache.status = "Loaded " + cache.nodes.size() + " nodes";
        cache.loading = false;
    }

    private synchronized void failRefresh(GatheringNodeSource source, Exception exception) {
        Cache cache = caches.get(source);
        cache.refreshIntervalMs = RETRY_INTERVAL_MS;
        cache.status = source == GatheringNodeSource.STATIC ? "Static node load failed; retrying..."
                : cache.nodes.isEmpty() ? "Node load failed; retrying..." : "Refresh failed; using cached nodes";
        cache.loading = false;
        SeqClient.LOGGER.warn("[GatheringMap] Failed to load gathering nodes from {}.", source.label(), exception);
    }

    private static final class Cache {
        private volatile List<GatheringNode> nodes = List.of();
        private volatile String status = "Not loaded";
        private volatile boolean loading;
        private boolean attempted;
        private long lastAttemptAtMs;
        private long refreshIntervalMs;
    }

    static List<GatheringNode> parseNodes(String body) {
        JsonElement root = JsonParser.parseString(body);
        JsonArray array = nodeArray(root);
        List<GatheringNode> parsed = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            try {
                parsed.add(new GatheringNode(
                        readInt(object, "x"),
                        readInt(object, "y"),
                        readInt(object, "z"),
                        readInt(object, "angle"),
                        readString(object, "type").toUpperCase(Locale.ROOT),
                        readString(object, "resource").toUpperCase(Locale.ROOT),
                        readInt(object, "level")));
            } catch (RuntimeException ignored) {
                // Skip malformed nodes while keeping the rest of the map usable.
            }
        }
        if (parsed.isEmpty()) {
            throw new IllegalArgumentException("Gathering-node response did not contain any valid nodes.");
        }
        return List.copyOf(parsed);
    }

    private static JsonArray nodeArray(JsonElement root) {
        if (root != null && root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (root != null && root.isJsonObject()) {
            JsonElement data = root.getAsJsonObject().get("data");
            if (data != null && data.isJsonArray()) {
                return data.getAsJsonArray();
            }
        }
        throw new IllegalArgumentException("Gathering nodes must be an array or an object with a data array.");
    }

    private static int readInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing integer field " + key);
        }
        return element.getAsBigDecimal().intValueExact();
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing string field " + key);
        }
        String value = element.getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Blank string field " + key);
        }
        return value;
    }
}
