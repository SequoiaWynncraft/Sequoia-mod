package com.seqwawa.seq.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.Background;
import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.Clocks;
import com.seqwawa.seq.client.Refreshable;
import com.seqwawa.seq.client.SeqClient;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public final class GatheringNodeService {
    // The API's nodes with the Dernic and Molten kinds named. scripts/gathering_nodes.py regenerates it,
    // and its meta says from which API version and when.
    private static final String STATIC_NODES_RESOURCE = "assets/seq/map/gathering-nodes.json";
    static final URI DEFAULT_ENDPOINT = URI.create("https://api.wynncraft.com/v3/map/gathering-nodes");
    // Wynncraft caches this route for one hour.
    static final long REFRESH_INTERVAL_MS = Duration.ofHours(1).toMillis();
    static final long RETRY_INTERVAL_MS = Duration.ofMinutes(1).toMillis();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final GatheringNodeService INSTANCE = new GatheringNodeService();

    private final HttpClient httpClient;
    private final URI endpoint;
    private final Executor disk;
    private final Supplier<InputStream> bundledNodes;
    /** The bundled nodes once read: the static source shows them, and they name the API's nodes. */
    private List<GatheringNode> bundled;
    /**
     * One per source, each keeping its last good nodes. An equal answer keeps the old
     * list, so the map doesn't cluster the same nodes again.
     */
    private final Map<GatheringNodeSource, Refreshable<List<GatheringNode>>> sources =
            new EnumMap<>(GatheringNodeSource.class);

    public static GatheringNodeService getInstance() {
        return INSTANCE;
    }

    private GatheringNodeService() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), DEFAULT_ENDPOINT,
                Clocks::monotonicMillis, Background.disk(), ClientThread.executor());
    }

    GatheringNodeService(HttpClient httpClient, URI endpoint, LongSupplier clock, Executor disk, Executor handBack) {
        this(httpClient, endpoint, clock, disk, handBack,
                () -> GatheringNodeService.class.getClassLoader().getResourceAsStream(STATIC_NODES_RESOURCE));
    }

    GatheringNodeService(HttpClient httpClient, URI endpoint, LongSupplier clock, Executor disk, Executor handBack,
            Supplier<InputStream> bundledNodes) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.disk = disk;
        this.bundledNodes = bundledNodes;
        for (GatheringNodeSource source : GatheringNodeSource.values()) {
            sources.put(source, Refreshable.<List<GatheringNode>>builder(
                            "gathering nodes from " + source.label(),
                            () -> source == GatheringNodeSource.STATIC ? readBundled() : fetchFromApi())
                    .clock(clock)
                    .handBack(handBack)
                    // The bundled file never changes while the game runs, so it loads once.
                    .refreshEveryMs(source == GatheringNodeSource.STATIC ? Long.MAX_VALUE : REFRESH_INTERVAL_MS)
                    .retryAfterMs(RETRY_INTERVAL_MS)
                    .keepEqualValue()
                    .emptyValue(List.of())
                    .whenFailed(failure -> SeqClient.LOGGER.warn(
                            "[GatheringMap] Failed to load gathering nodes from {}.", source.label(), failure))
                    .build());
        }
    }

    public List<GatheringNode> nodes(GatheringNodeSource source) {
        return sources.get(source).value();
    }

    public String status(GatheringNodeSource source) {
        Refreshable.State<List<GatheringNode>> state = sources.get(source).state();
        boolean bundled = source == GatheringNodeSource.STATIC;
        if (state.loading()) {
            return bundled ? "Loading static nodes..." : state.hasValue() ? "Refreshing nodes..." : "Loading nodes...";
        }
        if (state.failure() != null) {
            return bundled ? "Static node load failed; retrying..."
                    : state.hasValue() ? "Refresh failed; using cached nodes" : "Node load failed; retrying...";
        }
        return state.hasValue() ? "Loaded " + state.value().size() + " nodes" : "Not loaded";
    }

    public boolean isLoading(GatheringNodeSource source) {
        return sources.get(source).isLoading();
    }

    /** Loads unless a load is running or the last began too recently. Neither source loads on the caller's thread. */
    public boolean requestRefresh(GatheringNodeSource source) {
        return sources.get(source).request();
    }

    /** Reads and parses the bundled nodes on the disk thread. */
    private CompletableFuture<List<GatheringNode>> readBundled() {
        return CompletableFuture.supplyAsync(this::bundled, disk);
    }

    /** The bundled nodes, read once on the first call that succeeds. */
    private synchronized List<GatheringNode> bundled() {
        if (bundled == null) {
            try (InputStream input = bundledNodes.get()) {
                if (input == null) {
                    throw new IllegalStateException("Missing bundled node resource " + STATIC_NODES_RESOURCE);
                }
                bundled = parseNodes(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }
        return bundled;
    }

    /** Gives the API's plain Dernic and Molten nodes the bundled file's names for them, on the disk thread. */
    private List<GatheringNode> nameByBundled(List<GatheringNode> nodes) {
        List<GatheringNode> reference;
        try {
            reference = bundled();
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[GatheringMap] Couldn't read the bundled nodes to name the API's Dernic and Molten "
                    + "nodes; they keep the API's names.", exception);
            reference = List.of();
        }
        return GatheringNodeNames.resolve(nodes, reference);
    }

    private CompletableFuture<List<GatheringNode>> fetchFromApi() {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Accept", "application/json")
                .header("User-Agent", "Sequoia-Mod")
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("API returned HTTP " + response.statusCode());
            }
            return parseNodes(response.body());
        }).thenApplyAsync(this::nameByBundled, disk);
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
