package com.seqwawa.seq.wynnbuilder.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A Wynncraft item as published by WynnBuilder.
 *
 * <p>Structural fields are typed; rolled identifications live in a map so the model does not need a
 * field per stat and survives new stats being added upstream.
 */
public record WynnItem(
        int id,
        String name,
        String displayName,
        String category,
        String type,
        Tier tier,
        int level,
        String classRequirement,
        String attackSpeed,
        int powderSlots,
        Map<String, Integer> requirements,
        Map<String, int[]> damages,
        Map<String, Integer> baseDefences,
        int baseHealth,
        Map<String, Integer> identifications,
        List<String> majorIds,
        String setName,
        boolean fixedIds,
        String restriction,
        Integer remapId) {

    public WynnItem {
        requirements = Map.copyOf(requirements);
        baseDefences = Map.copyOf(baseDefences);
        identifications = Map.copyOf(identifications);
        majorIds = List.copyOf(majorIds);
    }

    /** Item rarities, ordered from most common to rarest. */
    public enum Tier {
        NORMAL("Normal"),
        SET("Set"),
        UNIQUE("Unique"),
        RARE("Rare"),
        LEGENDARY("Legendary"),
        FABLED("Fabled"),
        MYTHIC("Mythic"),
        CRAFTED("Crafted");

        private final String label;

        Tier(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static Tier parse(String text) {
            if (text == null) {
                return NORMAL;
            }
            for (Tier tier : values()) {
                if (tier.label.equalsIgnoreCase(text)) {
                    return tier;
                }
            }
            return NORMAL;
        }
    }

    /** Whether this is one of the synthetic "no item" entries used by legacy hashes. */
    public boolean isNoneItem() {
        return id >= 10000;
    }

    public boolean isWeapon() {
        return "weapon".equalsIgnoreCase(category);
    }

    public static WynnItem parse(JsonObject object) {
        int id = Json.integer(object, "id", -1);
        if (id < 0) {
            return null;
        }
        String name = Json.string(object, "name");
        String displayName = Json.string(object, "displayName");
        if (displayName == null) {
            displayName = name;
        }
        if (name == null) {
            name = displayName;
        }
        if (name == null) {
            return null;
        }

        Map<String, Integer> requirements = new LinkedHashMap<>();
        for (String key : Identifications.REQUIREMENT_KEYS) {
            requirements.put(key, Json.integer(object, key, 0));
        }

        Map<String, int[]> damages = new LinkedHashMap<>();
        for (String key : Identifications.DAMAGE_KEYS) {
            int[] range = Json.damageRange(object, key);
            if (range != null) {
                damages.put(key, range);
            }
        }

        Map<String, Integer> defences = new LinkedHashMap<>();
        for (String key : Identifications.DEFENCE_KEYS) {
            int value = Json.integer(object, key, 0);
            if (value != 0) {
                defences.put(key, value);
            }
        }

        Map<String, Integer> identifications = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = Identifications.normalise(entry.getKey());
            if (key == null || !Identifications.isIdentification(key)) {
                continue;
            }
            Integer value = Json.optionalInteger(entry.getValue());
            if (value != null && value != 0) {
                identifications.merge(key, value, Integer::sum);
            }
        }

        List<String> majorIds = new ArrayList<>();
        JsonElement majorIdsElement = object.get("majorIds");
        if (majorIdsElement != null && majorIdsElement.isJsonArray()) {
            majorIdsElement.getAsJsonArray().forEach(element -> {
                if (element != null && element.isJsonPrimitive()) {
                    majorIds.add(element.getAsString());
                }
            });
        }

        return new WynnItem(
                id,
                name,
                displayName,
                Json.stringOrDefault(object, "category", ""),
                Json.stringOrDefault(object, "type", "").toLowerCase(Locale.ROOT),
                Tier.parse(Json.string(object, "tier")),
                Json.integer(object, "lvl", 0),
                Json.string(object, "classReq"),
                Json.stringOrDefault(object, "atkSpd", "NORMAL"),
                Json.integer(object, "slots", 0),
                requirements,
                damages,
                defences,
                Json.integer(object, "hp", 0),
                identifications,
                majorIds,
                Json.string(object, "set"),
                Json.bool(object, "fixID"),
                Json.string(object, "restrict"),
                object.has("remapID") ? Json.integer(object, "remapID", -1) : null);
    }

    /** Small JSON helpers shared by the data models. */
    static final class Json {
        private Json() {}

        static String string(JsonObject object, String key) {
            JsonElement element = object.get(key);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                return null;
            }
            String value = element.getAsString().trim();
            return value.isEmpty() ? null : value;
        }

        static String stringOrDefault(JsonObject object, String key, String fallback) {
            String value = string(object, key);
            return value == null ? fallback : value;
        }

        static boolean bool(JsonObject object, String key) {
            JsonElement element = object.get(key);
            if (element == null || !element.isJsonPrimitive()) {
                return false;
            }
            try {
                return element.getAsBoolean();
            } catch (RuntimeException ignored) {
                return false;
            }
        }

        static int integer(JsonObject object, String key, int fallback) {
            Integer value = optionalInteger(object.get(key));
            return value == null ? fallback : value;
        }

        static Integer optionalInteger(JsonElement element) {
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                return null;
            }
            try {
                return (int) Math.round(element.getAsDouble());
            } catch (RuntimeException ignored) {
                return null;
            }
        }

        /** Parses the {@code "min-max"} damage form, tolerating a plain number. */
        static int[] damageRange(JsonObject object, String key) {
            JsonElement element = object.get(key);
            if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
                return null;
            }
            String text = element.getAsString().trim();
            if (text.isEmpty()) {
                return null;
            }
            int separator = text.indexOf('-', 1);
            try {
                if (separator < 0) {
                    int single = Integer.parseInt(text);
                    return new int[] {single, single};
                }
                return new int[] {
                    Integer.parseInt(text.substring(0, separator).trim()),
                    Integer.parseInt(text.substring(separator + 1).trim())
                };
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
