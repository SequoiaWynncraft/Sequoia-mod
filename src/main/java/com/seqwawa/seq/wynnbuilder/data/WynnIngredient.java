package com.seqwawa.seq.wynnbuilder.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A crafting ingredient.
 *
 * <p>{@code ids} are the identifications granted to the crafted item, {@code itemIds} adjust the
 * item itself (durability and requirements) and {@code posMods} are the positional bonuses that
 * make ingredient placement in the 2x3 grid matter.
 */
public record WynnIngredient(
        int id,
        String name,
        String displayName,
        int tier,
        int level,
        List<String> professions,
        Map<String, int[]> identificationRanges,
        PositionModifiers positionModifiers,
        ItemModifiers itemModifiers,
        ConsumableModifiers consumableModifiers) {

    public WynnIngredient {
        professions = List.copyOf(professions);
        identificationRanges = Map.copyOf(identificationRanges);
    }

    /** Bonuses applied to neighbouring ingredients, as a percentage of their effectiveness. */
    public record PositionModifiers(int left, int right, int above, int under, int touching, int notTouching) {
        public static final PositionModifiers NONE = new PositionModifiers(0, 0, 0, 0, 0, 0);

        public boolean isEmpty() {
            return left == 0 && right == 0 && above == 0 && under == 0 && touching == 0 && notTouching == 0;
        }
    }

    /** Durability and requirement changes applied to the crafted item. */
    public record ItemModifiers(int durability, Map<String, Integer> requirements) {
        public static final ItemModifiers NONE = new ItemModifiers(0, Map.of());

        public ItemModifiers {
            requirements = Map.copyOf(requirements);
        }
    }

    /** Duration and charge changes applied to crafted consumables. */
    public record ConsumableModifiers(int duration, int charges) {
        public static final ConsumableModifiers NONE = new ConsumableModifiers(0, 0);
    }

    /** The sentinel used by the encoding for an empty ingredient slot. */
    public boolean isNone() {
        return id == CraftedItem.NO_INGREDIENT;
    }

    public static WynnIngredient parse(JsonObject object) {
        int id = WynnItem.Json.integer(object, "id", -1);
        if (id < 0) {
            return null;
        }
        String name = WynnItem.Json.string(object, "name");
        String displayName = WynnItem.Json.stringOrDefault(object, "displayName", name);
        if (name == null) {
            name = displayName;
        }
        if (name == null) {
            return null;
        }

        List<String> professions = new ArrayList<>();
        JsonElement skills = object.get("skills");
        if (skills != null && skills.isJsonArray()) {
            skills.getAsJsonArray().forEach(element -> {
                if (element != null && element.isJsonPrimitive()) {
                    professions.add(element.getAsString());
                }
            });
        }

        Map<String, int[]> ranges = new LinkedHashMap<>();
        JsonElement ids = object.get("ids");
        if (ids != null && ids.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : ids.getAsJsonObject().entrySet()) {
                if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject range = entry.getValue().getAsJsonObject();
                int minimum = WynnItem.Json.integer(range, "minimum", 0);
                int maximum = WynnItem.Json.integer(range, "maximum", minimum);
                String key = Identifications.normalise(entry.getKey());
                if (key != null && (minimum != 0 || maximum != 0)) {
                    ranges.put(key, new int[] {minimum, maximum});
                }
            }
        }

        return new WynnIngredient(
                id,
                name,
                displayName,
                WynnItem.Json.integer(object, "tier", 0),
                WynnItem.Json.integer(object, "lvl", 0),
                professions,
                ranges,
                parsePositionModifiers(object.get("posMods")),
                parseItemModifiers(object.get("itemIDs")),
                parseConsumableModifiers(object.get("consumableIDs")));
    }

    private static PositionModifiers parsePositionModifiers(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return PositionModifiers.NONE;
        }
        JsonObject object = element.getAsJsonObject();
        return new PositionModifiers(
                WynnItem.Json.integer(object, "left", 0),
                WynnItem.Json.integer(object, "right", 0),
                WynnItem.Json.integer(object, "above", 0),
                WynnItem.Json.integer(object, "under", 0),
                WynnItem.Json.integer(object, "touching", 0),
                WynnItem.Json.integer(object, "notTouching", 0));
    }

    private static ItemModifiers parseItemModifiers(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return ItemModifiers.NONE;
        }
        JsonObject object = element.getAsJsonObject();
        Map<String, Integer> requirements = new LinkedHashMap<>();
        for (String key : Identifications.REQUIREMENT_KEYS) {
            int value = WynnItem.Json.integer(object, key, 0);
            if (value != 0) {
                requirements.put(key, value);
            }
        }
        return new ItemModifiers(WynnItem.Json.integer(object, "dura", 0), requirements);
    }

    private static ConsumableModifiers parseConsumableModifiers(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return ConsumableModifiers.NONE;
        }
        JsonObject object = element.getAsJsonObject();
        return new ConsumableModifiers(
                WynnItem.Json.integer(object, "dura", 0), WynnItem.Json.integer(object, "charges", 0));
    }
}
