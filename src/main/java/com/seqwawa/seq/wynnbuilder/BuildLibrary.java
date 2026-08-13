package com.seqwawa.seq.wynnbuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.client.SeqClient;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the player has saved, kept on disk between sessions.
 *
 * <p>Only the link hash is stored, not the resolved build: the hash is the canonical form, survives
 * data updates, and can be pasted into the website unchanged.
 */
public final class BuildLibrary {

    private static final Path FILE = Path.of("config", "sequoia", "wynnbuilder", "builds.json");
    private static final int MAX_ENTRIES = 100;

    private static final BuildLibrary INSTANCE = new BuildLibrary();

    private final List<SavedBuild> builds = new ArrayList<>();
    private boolean loaded;

    /** One saved build. */
    public record SavedBuild(String name, String hash, long savedAt) {}

    public static BuildLibrary getInstance() {
        return INSTANCE;
    }

    private BuildLibrary() {}

    public synchronized List<SavedBuild> all() {
        ensureLoaded();
        return List.copyOf(builds);
    }

    /**
     * Saves a build under a name, replacing any existing entry with the same name.
     *
     * @return the name actually used, which is made unique when blank
     */
    public synchronized String save(String name, String hash) {
        ensureLoaded();
        String cleaned = name == null || name.isBlank() ? "Build " + (builds.size() + 1) : name.trim();
        builds.removeIf(build -> build.name().equalsIgnoreCase(cleaned));
        builds.add(0, new SavedBuild(cleaned, hash, System.currentTimeMillis()));
        while (builds.size() > MAX_ENTRIES) {
            builds.remove(builds.size() - 1);
        }
        persist();
        return cleaned;
    }

    public synchronized void delete(String name) {
        ensureLoaded();
        if (builds.removeIf(build -> build.name().equalsIgnoreCase(name))) {
            persist();
        }
    }

    public synchronized SavedBuild byName(String name) {
        ensureLoaded();
        return builds.stream().filter(build -> build.name().equalsIgnoreCase(name)).findFirst().orElse(null);
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            if (!Files.isRegularFile(FILE)) {
                return;
            }
            JsonElement root = JsonParser.parseString(Files.readString(FILE, StandardCharsets.UTF_8));
            if (root == null || !root.isJsonArray()) {
                return;
            }
            for (JsonElement element : root.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                String name = optional(object, "name");
                String hash = optional(object, "hash");
                if (name != null && hash != null) {
                    long savedAt = object.has("savedAt") ? object.get("savedAt").getAsLong() : 0;
                    builds.add(new SavedBuild(name, hash, savedAt));
                }
            }
        } catch (IOException | RuntimeException exception) {
            SeqClient.LOGGER.warn("[WynnBuilder] Could not read saved builds.", exception);
        }
    }

    private void persist() {
        JsonArray array = new JsonArray();
        for (SavedBuild build : builds) {
            JsonObject object = new JsonObject();
            object.addProperty("name", build.name());
            object.addProperty("hash", build.hash());
            object.addProperty("savedAt", build.savedAt());
            array.add(object);
        }
        try {
            Files.createDirectories(FILE.getParent());
            // Written beside the target and moved, so an interrupted write cannot destroy the list.
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(temporary, array.toString(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[WynnBuilder] Could not save builds.", exception);
        }
    }

    private static String optional(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return null;
        }
        String value = element.getAsString().trim();
        return value.isEmpty() ? null : value;
    }
}
