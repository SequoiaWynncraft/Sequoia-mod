package com.seqwawa.seq.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.Clocks;
import com.seqwawa.seq.client.Refreshable;
import com.seqwawa.seq.client.SeqClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

public final class WorldEventService {
    static final URI DEFAULT_ENDPOINT = URI.create("https://api.wynncraft.com/v3/map/world-events");
    static final long REFRESH_INTERVAL_MS = Duration.ofMinutes(2).toMillis();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);
    private static final WorldEventService INSTANCE = new WorldEventService();

    private final HttpClient httpClient;
    private final URI endpoint;
    /** A failed refresh keeps the events from the last good one. */
    private final Refreshable<List<WorldEventDefinition>> events;

    public static WorldEventService getInstance() {
        return INSTANCE;
    }

    private WorldEventService() {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .executor(Executors.newSingleThreadExecutor(runnable -> {
                            Thread thread = new Thread(runnable, "seq-world-events");
                            thread.setDaemon(true);
                            return thread;
                        }))
                        .build(),
                DEFAULT_ENDPOINT,
                Clocks::monotonicMillis,
                ClientThread.executor());
    }

    WorldEventService(HttpClient httpClient, URI endpoint, LongSupplier clock, Executor handBack) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.events = Refreshable.<List<WorldEventDefinition>>builder("world events", this::fetch)
                .clock(clock)
                .handBack(handBack)
                .refreshEveryMs(REFRESH_INTERVAL_MS)
                .emptyValue(List.of())
                .whenFailed(failure -> SeqClient.LOGGER.warn(
                        "[WorldEvents] Failed to refresh Wynncraft world events.", failure))
                .build();
    }

    public Snapshot snapshot() {
        Refreshable.State<List<WorldEventDefinition>> state = events.state();
        return new Snapshot(state.value(), state.version());
    }

    public String status() {
        Refreshable.State<List<WorldEventDefinition>> state = events.state();
        if (state.loading()) return state.hasValue() ? "Refreshing..." : "Loading...";
        if (state.failure() != null) return "Refresh failed";
        return state.hasValue() ? "Updated " + state.value().size() + " events" : "Not loaded";
    }

    /** Fetches unless a fetch is running or the last began under two minutes ago. Game thread only. */
    public boolean requestRefresh() {
        return events.request();
    }

    private CompletableFuture<List<WorldEventDefinition>> fetch() {
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
            return parseEvents(response.body());
        });
    }

    static List<WorldEventDefinition> parseEvents(String body) {
        JsonElement root = JsonParser.parseString(body);
        if (root == null || !root.isJsonArray()) {
            throw new IllegalArgumentException("World-event response must be an array.");
        }
        JsonArray array = root.getAsJsonArray();
        List<WorldEventDefinition> events = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            try {
                JsonObject object = element.getAsJsonObject();
                String name = requiredString(object, "name");
                String internalName = requiredString(object, "internalName");
                List<WorldEventLocation> locations = parseLocations(object.get("location"));
                if (locations.isEmpty()) {
                    continue;
                }
                events.add(new WorldEventDefinition(
                        name,
                        internalName,
                        optionalString(object, "lore"),
                        optionalString(object, "difficulty"),
                        optionalInteger(object, "level"),
                        optionalString(object, "length"),
                        locations,
                        optionalInstant(object, "schedule")));
            } catch (RuntimeException ignored) {
                // One malformed event must not make the remaining map data unavailable.
            }
        }
        if (events.isEmpty()) {
            throw new IllegalArgumentException("World-event response did not contain any valid events.");
        }
        return events.stream()
                .sorted(Comparator.comparing(WorldEventDefinition::name)
                        .thenComparing(WorldEventDefinition::internalName))
                .toList();
    }

    private static List<WorldEventLocation> parseLocations(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        List<WorldEventLocation> locations = new ArrayList<>();
        for (JsonElement locationElement : element.getAsJsonArray()) {
            if (locationElement == null || !locationElement.isJsonObject()) {
                continue;
            }
            try {
                JsonObject location = locationElement.getAsJsonObject();
                JsonObject event = location.getAsJsonObject("event");
                if (event == null) {
                    continue;
                }
                locations.add(new WorldEventLocation(
                        requiredDouble(event, "x"),
                        optionalDouble(event, "y", 0),
                        requiredDouble(event, "z"),
                        optionalDouble(location, "radius", 0),
                        optionalDouble(location, "spawnRadius", 0)));
            } catch (RuntimeException ignored) {
                // Keep valid spots for multi-location events.
            }
        }
        return List.copyOf(locations);
    }

    private static String requiredString(JsonObject object, String key) {
        String value = optionalString(object, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing string field " + key);
        }
        return value.trim();
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer optionalInteger(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Instant optionalInstant(JsonObject object, String key) {
        String value = optionalString(object, key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static double requiredDouble(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing number field " + key);
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Non-finite number field " + key);
        }
        return value;
    }

    private static double optionalDouble(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            double value = element.getAsDouble();
            return Double.isFinite(value) ? value : fallback;
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    /** The events, and a version that changes whenever they do, so readers can tell a new list. 0 before the first. */
    public record Snapshot(List<WorldEventDefinition> events, long version) {
        public Snapshot {
            events = List.copyOf(events);
        }
    }
}
