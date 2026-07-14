package com.seqwawa.seq.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GuildTerritoryService {
    private static final String STATIC_TERRITORIES_RESOURCE = "assets/seq/map/guild-territories.json";
    private static final GuildTerritoryService INSTANCE = new GuildTerritoryService();

    private volatile GuildTerritoryIndex index = GuildTerritoryIndex.EMPTY;
    private boolean loadRequested;

    private GuildTerritoryService() {}

    public static GuildTerritoryService getInstance() {
        return INSTANCE;
    }

    public GuildTerritoryIndex index() {
        return index;
    }

    public synchronized void loadBundledTerritories() {
        if (loadRequested) {
            return;
        }
        loadRequested = true;

        try (InputStream input = GuildTerritoryService.class.getClassLoader()
                .getResourceAsStream(STATIC_TERRITORIES_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled territory resource " + STATIC_TERRITORIES_RESOURCE);
            }
            String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            index = new GuildTerritoryIndex(parseTerritories(body));
        } catch (Exception exception) {
            SeqClient.LOGGER.warn("[WorldMap] Failed to load bundled guild territories.", exception);
        }
    }

    static List<GuildTerritory> parseTerritories(String body) {
        JsonElement root = JsonParser.parseString(body);
        JsonArray array = territoryArray(root);
        List<GuildTerritory> parsed = new ArrayList<>(array.size());
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            try {
                String name = readString(object, "name");
                JsonArray start = readCoordinate(object, "start");
                JsonArray end = readCoordinate(object, "end");
                parsed.add(GuildTerritory.fromCorners(
                        name,
                        start.get(0).getAsDouble(),
                        start.get(1).getAsDouble(),
                        end.get(0).getAsDouble(),
                        end.get(1).getAsDouble()));
            } catch (RuntimeException ignored) {
                // One malformed territory must not make the map unavailable.
            }
        }
        if (parsed.isEmpty() && array.size() > 0) {
            throw new IllegalArgumentException("Bundled territory data did not contain any valid territories.");
        }
        return parsed.stream().sorted(Comparator.comparing(GuildTerritory::name)).toList();
    }

    private static JsonArray territoryArray(JsonElement root) {
        if (root != null && root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (root != null && root.isJsonObject()) {
            JsonElement data = root.getAsJsonObject().get("data");
            if (data != null && data.isJsonArray()) {
                return data.getAsJsonArray();
            }
        }
        throw new IllegalArgumentException("Territory root must be an array or object with a data array.");
    }

    private static JsonArray readCoordinate(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonArray() || element.getAsJsonArray().size() != 2) {
            throw new IllegalArgumentException("Invalid territory coordinate " + key);
        }
        JsonArray coordinate = element.getAsJsonArray();
        coordinate.get(0).getAsDouble();
        coordinate.get(1).getAsDouble();
        return coordinate;
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing string field " + key);
        }
        String value = element.getAsString().trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Blank string field " + key);
        }
        return value;
    }
}
