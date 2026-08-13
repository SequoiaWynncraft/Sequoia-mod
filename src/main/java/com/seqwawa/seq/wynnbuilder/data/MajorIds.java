package com.seqwawa.seq.wynnbuilder.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Major identifications and what they do.
 *
 * <p>A major ID modifies abilities using the same effect vocabulary as the ability tree, so once
 * parsed they can be fed through the same engine rather than special-cased one by one.
 */
public record MajorIds(Map<String, Entry> byName) {

    public MajorIds {
        byName = Map.copyOf(byName);
    }

    /** One major ID: its description, and the ability changes it makes. */
    public record Entry(String name, String displayName, String description, List<JsonObject> abilities) {
        public Entry {
            abilities = List.copyOf(abilities);
        }
    }

    public static MajorIds empty() {
        return new MajorIds(Map.of());
    }

    public Entry get(String name) {
        return name == null ? null : byName.get(name.toUpperCase(Locale.ROOT));
    }

    /** A readable name for a major ID, falling back to the raw key. */
    public String displayName(String name) {
        Entry entry = get(name);
        return entry == null || entry.displayName() == null ? name : entry.displayName();
    }

    public String description(String name) {
        Entry entry = get(name);
        return entry == null ? "" : entry.description();
    }

    public static MajorIds parse(String json) {
        if (json == null || json.isBlank()) {
            return empty();
        }
        JsonElement root = JsonParser.parseString(json);
        if (root == null || !root.isJsonObject()) {
            return empty();
        }
        Map<String, Entry> entries = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> element : root.getAsJsonObject().entrySet()) {
            if (element.getValue() == null || !element.getValue().isJsonObject()) {
                continue;
            }
            JsonObject object = element.getValue().getAsJsonObject();
            List<JsonObject> abilities = new ArrayList<>();
            JsonElement abilitiesElement = object.get("abilities");
            if (abilitiesElement != null && abilitiesElement.isJsonArray()) {
                for (JsonElement ability : abilitiesElement.getAsJsonArray()) {
                    if (ability != null && ability.isJsonObject()) {
                        abilities.add(ability.getAsJsonObject());
                    }
                }
            }
            String key = element.getKey().toUpperCase(Locale.ROOT);
            entries.put(key, new Entry(
                    key,
                    WynnItem.Json.stringOrDefault(object, "displayName", element.getKey()),
                    WynnItem.Json.stringOrDefault(object, "description", ""),
                    abilities));
        }
        return new MajorIds(entries);
    }
}
