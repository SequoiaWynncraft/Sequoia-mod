package com.seqwawa.seq.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.IngredientGuideEntry;
import com.seqwawa.seq.model.IngredientGuideEntry.DropSource;
import com.seqwawa.seq.model.IngredientGuideEntry.Icon;
import com.seqwawa.seq.model.IngredientGuideEntry.SpawnLocation;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

public final class IngredientGuideManager {
    static final URI DEFAULT_ENDPOINT = URI.create("https://api.wynncraft.com/v3/item/search?fullResult");
    static final long REFRESH_INTERVAL_MS = Duration.ofHours(1).toMillis();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String SEARCH_BODY = "{\"type\":\"ingredient\"}";
    private static final IngredientGuideManager INSTANCE = new IngredientGuideManager();

    private final HttpClient httpClient;
    private final URI endpoint;
    private final LongSupplier currentTimeMillis;
    private volatile Snapshot snapshot = Snapshot.empty();
    private volatile String status = "Not loaded";
    private volatile long lastAttemptAtMs;
    private volatile boolean loading;

    public static IngredientGuideManager getInstance() {
        return INSTANCE;
    }

    private IngredientGuideManager() {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "seq-ingredient-guide");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .build();
        this.endpoint = DEFAULT_ENDPOINT;
        this.currentTimeMillis = System::currentTimeMillis;
    }

    IngredientGuideManager(HttpClient httpClient, URI endpoint, LongSupplier currentTimeMillis) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.currentTimeMillis = currentTimeMillis;
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    public String status() {
        return status;
    }

    public boolean isLoading() {
        return loading;
    }

    public synchronized boolean requestRefresh() {
        return requestRefresh(false);
    }

    public synchronized boolean requestRefresh(boolean force) {
        long now = currentTimeMillis.getAsLong();
        if (loading || (!force && snapshot.version() > 0 && now - lastAttemptAtMs < REFRESH_INTERVAL_MS)) {
            return false;
        }
        loading = true;
        lastAttemptAtMs = now;
        status = snapshot.version() == 0 ? "Loading ingredients..." : "Refreshing ingredients...";

        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Sequoia-Mod")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(SEARCH_BODY))
                .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .whenComplete(this::completeRefresh);
        return true;
    }

    private synchronized void completeRefresh(HttpResponse<String> response, Throwable throwable) {
        try {
            if (throwable != null) {
                throw new IllegalStateException("request failed", throwable);
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("API returned HTTP " + response.statusCode());
            }
            List<IngredientGuideEntry> ingredients = parseIngredients(response.body());
            snapshot = new Snapshot(ingredients, snapshot.version() + 1, Instant.now());
            status = "Loaded " + ingredients.size() + " ingredients";
        } catch (Exception exception) {
            status = "Refresh failed";
            SeqClient.LOGGER.warn("[IngredientGuide] Failed to refresh Wynncraft ingredients.", exception);
        } finally {
            loading = false;
        }
    }

    public static List<IngredientGuideEntry> filter(List<IngredientGuideEntry> ingredients, String query) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return List.copyOf(ingredients);
        }
        List<String> terms = List.of(normalized.split("\\s+"));
        return ingredients.stream()
                .filter(ingredient -> terms.stream().allMatch(term -> searchableText(ingredient).contains(term)))
                .toList();
    }

    static List<IngredientGuideEntry> parseIngredients(String body) {
        JsonElement root = JsonParser.parseString(body);
        JsonArray array = ingredientArray(root);
        List<IngredientGuideEntry> ingredients = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            try {
                JsonObject object = element.getAsJsonObject();
                if (!"ingredient".equalsIgnoreCase(optionalString(object, "type"))) {
                    continue;
                }
                String displayName = requiredString(object, "displayName");
                String internalName = optionalString(object, "internalName");
                if (internalName == null) {
                    internalName = displayName;
                }
                JsonObject requirements = optionalObject(object, "requirements");
                ingredients.add(new IngredientGuideEntry(
                        displayName,
                        internalName,
                        parseTier(optionalString(object, "tier")),
                        requirements == null ? 0 : optionalInt(requirements, "level", 0),
                        requirements == null ? List.of() : stringList(requirements.get("skills")),
                        parseIcon(object.get("icon")),
                        parseDropSources(object.get("droppedBy"))));
            } catch (RuntimeException ignored) {
                // Preserve all valid entries if one API record is malformed.
            }
        }
        if (ingredients.isEmpty() && array.size() > 0) {
            throw new IllegalArgumentException("Ingredient response did not contain any valid ingredients.");
        }
        return ingredients.stream()
                .sorted(Comparator.comparingInt(IngredientGuideEntry::level)
                        .thenComparing(IngredientGuideEntry::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String searchableText(IngredientGuideEntry ingredient) {
        StringBuilder text = new StringBuilder()
                .append(ingredient.displayName()).append(' ')
                .append(ingredient.internalName()).append(' ');
        ingredient.skills().forEach(skill -> text.append(skill).append(' '));
        ingredient.dropSources().forEach(source -> text.append(source.name()).append(' '));
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static JsonArray ingredientArray(JsonElement root) {
        if (root != null && root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (root != null && root.isJsonObject()) {
            JsonElement results = root.getAsJsonObject().get("results");
            if (results != null && results.isJsonArray()) {
                return results.getAsJsonArray();
            }
        }
        throw new IllegalArgumentException("Ingredient response must be an array or contain a results array.");
    }

    private static Icon parseIcon(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Icon.unavailable();
        }
        JsonObject icon = element.getAsJsonObject();
        String format = optionalString(icon, "format");
        JsonElement value = icon.get("value");
        if ("skin".equalsIgnoreCase(format) && value != null && value.isJsonPrimitive()) {
            String hash = value.getAsString();
            if (hash.matches("[0-9a-fA-F]{32,64}")) {
                return new Icon("skin", null, 0, hash.toLowerCase(Locale.ROOT));
            }
        }
        if ("attribute".equalsIgnoreCase(format) && value != null && value.isJsonObject()) {
            JsonObject attribute = value.getAsJsonObject();
            String itemId = optionalString(attribute, "id");
            JsonObject customModelData = optionalObject(attribute, "customModelData");
            JsonElement dispatch = customModelData == null ? null : customModelData.get("rangeDispatch");
            if (itemId != null && dispatch != null && dispatch.isJsonArray() && !dispatch.getAsJsonArray().isEmpty()) {
                return new Icon("attribute", itemId, dispatch.getAsJsonArray().get(0).getAsInt(), null);
            }
        }
        return Icon.unavailable();
    }

    private static List<DropSource> parseDropSources(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        Map<String, SourceAccumulator> sources = new LinkedHashMap<>();
        for (JsonElement sourceElement : element.getAsJsonArray()) {
            if (sourceElement == null || !sourceElement.isJsonObject()) {
                continue;
            }
            JsonObject source = sourceElement.getAsJsonObject();
            String name = optionalString(source, "name");
            if (name == null || invalidSourceName(name)) {
                continue;
            }
            String key = name.toLowerCase(Locale.ROOT);
            SourceAccumulator accumulator = sources.computeIfAbsent(key, ignored -> new SourceAccumulator(name));
            accumulator.locations.addAll(parseLocations(source.get("coords")));
        }
        return sources.values().stream()
                .map(source -> new DropSource(source.name, List.copyOf(source.locations)))
                .sorted(Comparator.comparing(DropSource::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static List<SpawnLocation> parseLocations(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonArray()) {
            return List.of();
        }
        JsonArray coordinates = element.getAsJsonArray();
        if (coordinates.isEmpty()) {
            return List.of();
        }
        if (coordinates.get(0).isJsonPrimitive()) {
            SpawnLocation location = parseLocation(coordinates);
            return location == null ? List.of() : List.of(location);
        }
        List<SpawnLocation> locations = new ArrayList<>();
        for (JsonElement coordinate : coordinates) {
            if (coordinate != null && coordinate.isJsonArray()) {
                SpawnLocation location = parseLocation(coordinate.getAsJsonArray());
                if (location != null) {
                    locations.add(location);
                }
            }
        }
        return locations;
    }

    private static SpawnLocation parseLocation(JsonArray coordinates) {
        if (coordinates.size() < 3) {
            return null;
        }
        try {
            int x = coordinates.get(0).getAsInt();
            int y = coordinates.get(1).getAsInt();
            int z = coordinates.get(2).getAsInt();
            int radius = coordinates.size() >= 4 ? Math.max(0, coordinates.get(3).getAsInt()) : 0;
            return new SpawnLocation(x, y, z, radius);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static boolean invalidSourceName(String name) {
        return "null".equalsIgnoreCase(name) || "error".equalsIgnoreCase(name);
    }

    private static int parseTier(String tier) {
        if (tier == null) {
            return 0;
        }
        try {
            return Math.max(0, Math.min(3, Integer.parseInt(tier.replace("TIER_", ""))));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static List<String> stringList(JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return List.of();
        }
        Set<String> values = new LinkedHashSet<>();
        for (JsonElement value : element.getAsJsonArray()) {
            if (value != null && value.isJsonPrimitive()) {
                String string = value.getAsString().trim();
                if (!string.isEmpty()) {
                    values.add(string);
                }
            }
        }
        return List.copyOf(values);
    }

    private static JsonObject optionalObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static String requiredString(JsonObject object, String key) {
        String value = optionalString(object, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing string field " + key);
        }
        return value;
    }

    private static String optionalString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString().trim();
        return value.isEmpty() ? null : value;
    }

    private static int optionalInt(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static final class SourceAccumulator {
        private final String name;
        private final Set<SpawnLocation> locations = new LinkedHashSet<>();

        private SourceAccumulator(String name) {
            this.name = name;
        }
    }

    public record Snapshot(List<IngredientGuideEntry> ingredients, long version, Instant fetchedAt) {
        public Snapshot {
            ingredients = List.copyOf(ingredients);
        }

        private static Snapshot empty() {
            return new Snapshot(List.of(), 0, null);
        }
    }
}
