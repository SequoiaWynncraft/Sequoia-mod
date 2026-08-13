package com.seqwawa.seq.wynnbuilder.atree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * One ability in a class tree.
 *
 * <p>Effects come in exactly four kinds, which is what makes the tree tractable to evaluate:
 * {@code raw_stat} and {@code stat_scaling} contribute to build statistics, while
 * {@code replace_spell} and {@code add_spell_prop} shape the spells.
 */
public record AbilityNode(
        int id,
        String displayName,
        String description,
        List<Integer> parentIds,
        List<Integer> dependencyIds,
        List<Integer> blockerIds,
        int cost,
        int row,
        int column,
        String icon,
        String archetype,
        int archetypeRequirement,
        Integer baseAbility,
        Map<String, Double> properties,
        List<Effect> effects) {

    public AbilityNode {
        parentIds = List.copyOf(parentIds);
        properties = Map.copyOf(properties);
        dependencyIds = List.copyOf(dependencyIds);
        blockerIds = List.copyOf(blockerIds);
        effects = List.copyOf(effects);
    }

    /** An ability effect, kept as its parsed JSON so the engine can interpret each kind. */
    public record Effect(String type, JsonObject raw) {}

    /** The tree root is the only node without parents. */
    public boolean isRoot() {
        return parentIds.isEmpty();
    }

    public static AbilityNode parse(JsonObject object) {
        int id = integer(object, "id", -1);
        if (id < 0) {
            return null;
        }
        JsonObject display = object.getAsJsonObject("display");
        List<Effect> effects = new ArrayList<>();
        JsonElement effectsElement = object.get("effects");
        if (effectsElement != null && effectsElement.isJsonArray()) {
            for (JsonElement element : effectsElement.getAsJsonArray()) {
                if (element != null && element.isJsonObject()) {
                    JsonObject effect = element.getAsJsonObject();
                    JsonElement type = effect.get("type");
                    if (type != null && type.isJsonPrimitive()) {
                        effects.add(new Effect(type.getAsString(), effect));
                    }
                }
            }
        }
        return new AbilityNode(
                id,
                string(object, "display_name", "Ability " + id),
                string(object, "desc", ""),
                integers(object.get("parents")),
                integers(object.get("dependencies")),
                integers(object.get("blockers")),
                integer(object, "cost", 1),
                display == null ? 0 : integer(display, "row", 0),
                display == null ? 0 : integer(display, "col", 0),
                display == null ? "" : string(display, "icon", ""),
                string(object, "archetype", null),
                integer(object, "archetype_req", 0),
                object.has("base_abil") ? integer(object, "base_abil", -1) : null,
                properties(object.get("properties")),
                effects);
    }

    /** Numeric properties an ability exposes, such as a duration or a projectile count. */
    private static Map<String, Double> properties(JsonElement element) {
        Map<String, Double> properties = new java.util.LinkedHashMap<>();
        if (element != null && element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                if (entry.getValue() != null && entry.getValue().isJsonPrimitive()) {
                    try {
                        properties.put(entry.getKey(), entry.getValue().getAsDouble());
                    } catch (RuntimeException ignored) {
                        // Non-numeric properties are descriptive and not referenced by hit counts.
                    }
                }
            }
        }
        return properties;
    }

    private static List<Integer> integers(JsonElement element) {
        List<Integer> values = new ArrayList<>();
        if (element != null && element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            for (JsonElement entry : array) {
                if (entry != null && entry.isJsonPrimitive()) {
                    try {
                        values.add(entry.getAsInt());
                    } catch (RuntimeException ignored) {
                        // Skip malformed references rather than dropping the whole node.
                    }
                }
            }
        }
        return values;
    }

    private static String string(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        String value = element.getAsString();
        return value.isEmpty() ? fallback : value;
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }
}
