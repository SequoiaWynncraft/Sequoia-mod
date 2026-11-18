package com.seqwawa.seq.managers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.Clocks;
import com.seqwawa.seq.client.Refreshable;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.IngredientGuideEntry;
import com.seqwawa.seq.model.IngredientGuideEntry.CraftingModifiers;
import com.seqwawa.seq.model.IngredientGuideEntry.DropSource;
import com.seqwawa.seq.model.IngredientGuideEntry.Effect;
import com.seqwawa.seq.model.IngredientGuideEntry.Icon;
import com.seqwawa.seq.model.IngredientGuideEntry.Modifier;
import com.seqwawa.seq.model.IngredientGuideEntry.SpawnLocation;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.LongSupplier;

public final class IngredientGuideManager {
    static final URI DEFAULT_ENDPOINT = URI.create("https://api.wynncraft.com/v3/item/search?fullResult");
    static final long REFRESH_INTERVAL_MS = Duration.ofHours(1).toMillis();
    /** After a failed load, even the first, the guide waits this long before asking again. */
    static final long RETRY_INTERVAL_MS = Duration.ofMinutes(1).toMillis();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final String SEARCH_BODY = "{\"type\":\"ingredient\"}";
    private static final IngredientGuideManager INSTANCE = new IngredientGuideManager();

    private final HttpClient httpClient;
    private final URI endpoint;
    /** A failed refresh keeps the ingredients from the last good one. */
    private final Refreshable<List<IngredientGuideEntry>> ingredients;

    public static IngredientGuideManager getInstance() {
        return INSTANCE;
    }

    private IngredientGuideManager() {
        this(HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .executor(Executors.newSingleThreadExecutor(runnable -> {
                            Thread thread = new Thread(runnable, "seq-ingredient-guide");
                            thread.setDaemon(true);
                            return thread;
                        }))
                        .build(),
                DEFAULT_ENDPOINT,
                Clocks::monotonicMillis,
                ClientThread.executor());
    }

    IngredientGuideManager(HttpClient httpClient, URI endpoint, LongSupplier clock, Executor handBack) {
        this.httpClient = httpClient;
        this.endpoint = endpoint;
        this.ingredients = Refreshable.<List<IngredientGuideEntry>>builder("ingredient guide", this::fetch)
                .clock(clock)
                .handBack(handBack)
                .refreshEveryMs(REFRESH_INTERVAL_MS)
                .retryAfterMs(RETRY_INTERVAL_MS)
                .emptyValue(List.of())
                .whenFailed(failure -> SeqClient.LOGGER.warn(
                        "[IngredientGuide] Failed to refresh Wynncraft ingredients.", failure))
                .build();
    }

    public Snapshot snapshot() {
        Refreshable.State<List<IngredientGuideEntry>> state = ingredients.state();
        return new Snapshot(state.value(), state.version());
    }

    public String status() {
        Refreshable.State<List<IngredientGuideEntry>> state = ingredients.state();
        if (state.loading()) return state.hasValue() ? "Refreshing ingredients..." : "Loading ingredients...";
        if (state.failure() != null) return "Refresh failed";
        return state.hasValue() ? "Loaded " + state.value().size() + " ingredients" : "Not loaded";
    }

    public boolean isLoading() {
        return ingredients.isLoading();
    }

    /** Loads unless a load is running or the last began too recently. Game thread only. */
    public boolean requestRefresh() {
        return ingredients.request();
    }

    /** With {@code force}, loads now whatever the interval, unless a load is running. */
    public boolean requestRefresh(boolean force) {
        return force ? ingredients.force() : ingredients.request();
    }

    private CompletableFuture<List<IngredientGuideEntry>> fetch() {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Sequoia-Mod")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(SEARCH_BODY))
                .build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> {
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("API returned HTTP " + response.statusCode());
            }
            return parseIngredients(response.body());
        });
    }

    public static List<IngredientGuideEntry> filter(
            List<IngredientGuideEntry> ingredients, String query, SearchScope scope) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return List.copyOf(ingredients);
        }
        SearchScope resolvedScope = scope == null ? SearchScope.INGREDIENT : scope;
        List<String> terms = List.of(normalized.split("\\s+"));
        return ingredients.stream()
                .filter(ingredient -> {
                    String searchableText = searchableText(ingredient, resolvedScope);
                    return terms.stream().allMatch(searchableText::contains);
                })
                .toList();
    }

    public static List<IngredientGuideEntry> sort(
            List<IngredientGuideEntry> ingredients,
            SortKey primaryKey,
            SortDirection primaryDirection,
            SortKey secondaryKey,
            SortDirection secondaryDirection) {
        if (ingredients == null || ingredients.isEmpty()) {
            return List.of();
        }
        SortKey resolvedPrimary = primaryKey == null ? SortKey.LEVEL : primaryKey;
        SortDirection resolvedPrimaryDirection =
                primaryDirection == null ? SortDirection.ASCENDING : primaryDirection;
        SortKey resolvedSecondary = secondaryKey == null ? SortKey.RARITY : secondaryKey;
        SortDirection resolvedSecondaryDirection =
                secondaryDirection == null ? SortDirection.ASCENDING : secondaryDirection;

        Comparator<IngredientGuideEntry> comparator =
                directedComparator(resolvedPrimary, resolvedPrimaryDirection);
        if (resolvedPrimary != SortKey.ALPHABETICAL && resolvedSecondary != resolvedPrimary) {
            comparator = comparator.thenComparing(
                    directedComparator(resolvedSecondary, resolvedSecondaryDirection));
        }
        comparator = comparator
                .thenComparing(IngredientGuideEntry::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(IngredientGuideEntry::internalName, String.CASE_INSENSITIVE_ORDER);
        return ingredients.stream().sorted(comparator).toList();
    }

    private static Comparator<IngredientGuideEntry> directedComparator(
            SortKey key, SortDirection direction) {
        Comparator<IngredientGuideEntry> comparator = switch (key) {
            case LEVEL -> Comparator.comparingInt(IngredientGuideEntry::level);
            case RARITY -> Comparator.comparingInt(IngredientGuideEntry::tier);
            case ALPHABETICAL ->
                Comparator.comparing(IngredientGuideEntry::displayName, String.CASE_INSENSITIVE_ORDER);
        };
        return direction == SortDirection.DESCENDING ? comparator.reversed() : comparator;
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
                        parseEffects(object.get("identifications")),
                        parseCraftingModifiers(object),
                        parseDropSources(object.get("droppedBy"))));
            } catch (RuntimeException ignored) {
                // Preserve all valid entries if one API record is malformed.
            }
        }
        if (ingredients.isEmpty() && array.size() > 0) {
            throw new IllegalArgumentException("Ingredient response did not contain any valid ingredients.");
        }
        return sort(
                ingredients,
                SortKey.LEVEL,
                SortDirection.ASCENDING,
                SortKey.RARITY,
                SortDirection.ASCENDING);
    }

    private static String searchableText(IngredientGuideEntry ingredient, SearchScope scope) {
        StringBuilder text = new StringBuilder();
        switch (scope) {
            case INGREDIENT -> text
                    .append(ingredient.displayName()).append(' ')
                    .append(ingredient.internalName());
            case MOB -> ingredient.dropSources().forEach(source -> text.append(source.name()).append(' '));
            case PROFESSION -> ingredient.skills().forEach(skill -> text.append(skill).append(' '));
        }
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

    private static List<Effect> parseEffects(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return List.of();
        }
        List<Effect> effects = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject values = entry.getValue().getAsJsonObject();
            Integer raw = optionalInteger(values, "raw");
            Integer min = optionalInteger(values, "min");
            Integer max = optionalInteger(values, "max");
            if (raw == null && min == null && max == null) {
                continue;
            }
            int fallback = raw == null ? 0 : raw;
            int low = min == null ? fallback : min;
            int high = max == null ? fallback : max;
            effects.add(new Effect(entry.getKey(), Math.min(low, high), Math.max(low, high)));
        }
        return List.copyOf(effects);
    }

    private static CraftingModifiers parseCraftingModifiers(JsonObject ingredient) {
        JsonObject consumable = optionalObject(ingredient, "consumableOnlyIDs");
        JsonObject itemOnly = optionalObject(ingredient, "itemOnlyIDs");
        JsonObject positions = optionalObject(ingredient, "ingredientPositionModifiers");
        int duration = consumable == null ? 0 : optionalInt(consumable, "duration", 0);
        int charges = consumable == null ? 0 : optionalInt(consumable, "charges", 0);
        int durability = itemOnly == null ? 0 : optionalInt(itemOnly, "durabilityModifier", 0) / 1000;
        return new CraftingModifiers(
                duration,
                charges,
                durability,
                parseNonZeroModifiers(itemOnly, Set.of("durabilityModifier")),
                parseNonZeroModifiers(positions, Set.of()));
    }

    private static List<Modifier> parseNonZeroModifiers(JsonObject object, Set<String> excludedKeys) {
        if (object == null || object.isEmpty()) {
            return List.of();
        }
        List<Modifier> modifiers = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (excludedKeys.contains(entry.getKey())
                    || entry.getValue() == null
                    || !entry.getValue().isJsonPrimitive()) {
                continue;
            }
            try {
                int value = entry.getValue().getAsInt();
                if (value != 0) {
                    modifiers.add(new Modifier(entry.getKey(), value));
                }
            } catch (RuntimeException ignored) {
                // Ignore malformed modifier values while retaining the ingredient.
            }
        }
        return List.copyOf(modifiers);
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
        Integer value = optionalInteger(object, key);
        return value == null ? fallback : value;
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

    private static final class SourceAccumulator {
        private final String name;
        private final Set<SpawnLocation> locations = new LinkedHashSet<>();

        private SourceAccumulator(String name) {
            this.name = name;
        }
    }

    public enum SortKey {
        LEVEL("Level"),
        RARITY("Rarity"),
        ALPHABETICAL("Alphabetical");

        private final String label;

        SortKey(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum SortDirection {
        ASCENDING("↑", "Ascending"),
        DESCENDING("↓", "Descending");

        private final String symbol;
        private final String label;

        SortDirection(String symbol, String label) {
            this.symbol = symbol;
            this.label = label;
        }

        public String symbol() {
            return symbol;
        }

        public String label() {
            return label;
        }

        public SortDirection toggled() {
            return this == ASCENDING ? DESCENDING : ASCENDING;
        }
    }

    public enum SearchScope {
        INGREDIENT("Ingredient"),
        MOB("Mob"),
        PROFESSION("Profession");

        private final String label;

        SearchScope(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public SearchScope next() {
            SearchScope[] scopes = values();
            return scopes[(ordinal() + 1) % scopes.length];
        }
    }

    /** The ingredients, and a version that changes whenever they do, so screens can tell a new list. 0 before the first. */
    public record Snapshot(List<IngredientGuideEntry> ingredients, long version) {
        public Snapshot {
            ingredients = List.copyOf(ingredients);
        }
    }
}
