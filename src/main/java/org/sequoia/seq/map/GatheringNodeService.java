package org.sequoia.seq.map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.sequoia.seq.client.SeqClient;

public final class GatheringNodeService {
    private static final String STATIC_NODES_RESOURCE = "assets/seq/map/gathering-nodes.json";

    private static GatheringNodeService instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "seq-gathering-nodes");
        thread.setDaemon(true);
        return thread;
    });

    private volatile List<GatheringNode> nodes = List.of();
    private volatile boolean loadRequested;

    public static GatheringNodeService getInstance() {
        if (instance == null) {
            instance = new GatheringNodeService();
        }
        return instance;
    }

    private GatheringNodeService() {}

    public List<GatheringNode> nodes() {
        return nodes;
    }

    public void loadBundledNodes() {
        if (loadRequested) {
            return;
        }
        loadRequested = true;

        CompletableFuture.runAsync(this::loadStaticResource, executor);
    }

    private void loadStaticResource() {
        try (InputStream input = GatheringNodeService.class.getClassLoader()
                .getResourceAsStream(STATIC_NODES_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing bundled node resource " + STATIC_NODES_RESOURCE);
            }

            String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            List<GatheringNode> bundledNodes = parseNodes(body);
            nodes = bundledNodes;
        } catch (Exception exception) {
            SeqClient.LOGGER.warn("[GatheringMap] Failed to load bundled gathering nodes.", exception);
        }
    }

    private List<GatheringNode> parseNodes(String body) {
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
        if (parsed.isEmpty() && array.size() > 0) {
            throw new IllegalArgumentException("Bundled gathering nodes did not contain any valid nodes.");
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
        throw new IllegalArgumentException("Bundled gathering nodes root must be an array or object with data array.");
    }

    private static int readInt(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing integer field " + key);
        }
        return element.getAsInt();
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
