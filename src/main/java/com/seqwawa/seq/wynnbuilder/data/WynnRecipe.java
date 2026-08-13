package com.seqwawa.seq.wynnbuilder.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A crafting recipe: what can be made, at which levels, and the base rolls it contributes.
 *
 * <p>{@code healthOrDamage} carries health for armour and damage for weapons; which one it means
 * follows from the recipe type.
 */
public record WynnRecipe(
        int id,
        String name,
        String type,
        String profession,
        int minLevel,
        int maxLevel,
        int healthOrDamageMin,
        int healthOrDamageMax,
        int durabilityMin,
        int durabilityMax,
        int durationMin,
        int durationMax,
        int basicDurationMin,
        int basicDurationMax,
        List<Integer> materialAmounts) {

    public WynnRecipe {
        materialAmounts = List.copyOf(materialAmounts);
    }

    /** Whether crafting this recipe produces a weapon, which also means it encodes an attack speed. */
    public boolean isWeapon() {
        return CraftedItem.isWeaponType(type);
    }

    public boolean isConsumable() {
        String upper = type == null ? "" : type.toUpperCase(Locale.ROOT);
        return upper.equals("POTION") || upper.equals("SCROLL") || upper.equals("FOOD");
    }

    public boolean isArmour() {
        String upper = type == null ? "" : type.toUpperCase(Locale.ROOT);
        return upper.equals("HELMET") || upper.equals("CHESTPLATE") || upper.equals("LEGGINGS") || upper.equals("BOOTS");
    }

    public boolean isAccessory() {
        String upper = type == null ? "" : type.toUpperCase(Locale.ROOT);
        return upper.equals("RING") || upper.equals("NECKLACE") || upper.equals("BRACELET");
    }

    /** Label such as {@code Boots (Lv. 1-3)} for the recipe picker. */
    public String displayLabel() {
        String label = type == null ? "?" : type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT);
        return label + " (Lv. " + minLevel + "-" + maxLevel + ")";
    }

    public static WynnRecipe parse(JsonObject object) {
        int id = WynnItem.Json.integer(object, "id", -1);
        if (id < 0) {
            return null;
        }
        int[] level = range(object.get("lvl"));
        int[] healthOrDamage = range(object.get("healthOrDamage"));
        int[] durability = range(object.get("durability"));
        int[] duration = range(object.get("duration"));
        int[] basicDuration = range(object.get("basicDuration"));
        return new WynnRecipe(
                id,
                WynnItem.Json.stringOrDefault(object, "name", "Recipe " + id),
                WynnItem.Json.stringOrDefault(object, "type", ""),
                WynnItem.Json.stringOrDefault(object, "skill", ""),
                level[0],
                level[1],
                healthOrDamage[0],
                healthOrDamage[1],
                durability[0],
                durability[1],
                duration[0],
                duration[1],
                basicDuration[0],
                basicDuration[1],
                materialAmounts(object.get("materials")));
    }

    /** Material quantities, which weight the material tier multiplier. */
    private static List<Integer> materialAmounts(JsonElement element) {
        List<Integer> amounts = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return amounts;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry != null && entry.isJsonObject()) {
                amounts.add(WynnItem.Json.integer(entry.getAsJsonObject(), "amount", 1));
            }
        }
        return amounts;
    }

    private static int[] range(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return new int[] {0, 0};
        }
        JsonObject object = element.getAsJsonObject();
        int minimum = WynnItem.Json.integer(object, "minimum", 0);
        int maximum = WynnItem.Json.integer(object, "maximum", minimum);
        return new int[] {minimum, maximum};
    }
}
