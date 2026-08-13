package com.seqwawa.seq.wynnbuilder.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An item set and the bonuses it grants as more of its pieces are worn.
 *
 * <p>{@code bonuses} is indexed by the number of pieces worn minus one, so the first entry is the
 * (empty) bonus for a single piece. Some sets list fewer bonus tiers than they have pieces, in which
 * case the last tier is the most a build can reach.
 */
public record WynnSet(String name, List<String> items, List<Map<String, Integer>> bonuses, boolean hidden, List<Boolean> illegal) {

    public WynnSet {
        items = List.copyOf(items);
        bonuses = List.copyOf(bonuses);
        illegal = List.copyOf(illegal);
    }

    /**
     * The bonus for wearing {@code count} pieces.
     *
     * <p>Counts beyond the listed tiers clamp to the highest one rather than granting nothing.
     */
    public Map<String, Integer> bonusFor(int count) {
        if (count < 1 || bonuses.isEmpty()) {
            return Map.of();
        }
        int index = Math.min(count, bonuses.size()) - 1;
        return bonuses.get(index);
    }

    /**
     * Whether wearing this many pieces is impossible in game.
     *
     * <p>Some sets contain several pieces for the same slot, so the data marks those tiers as
     * unreachable rather than listing a bonus.
     */
    public boolean isIllegalAt(int count) {
        if (count < 1 || illegal.isEmpty()) {
            return false;
        }
        int index = Math.min(count, illegal.size()) - 1;
        return illegal.get(index);
    }

    public static WynnSet parse(String name, JsonObject object) {
        List<String> items = new ArrayList<>();
        JsonElement itemsElement = object.get("items");
        if (itemsElement != null && itemsElement.isJsonArray()) {
            for (JsonElement element : itemsElement.getAsJsonArray()) {
                if (element != null && element.isJsonPrimitive()) {
                    items.add(element.getAsString());
                }
            }
        }

        List<Map<String, Integer>> bonuses = new ArrayList<>();
        List<Boolean> illegal = new ArrayList<>();
        JsonElement bonusesElement = object.get("bonuses");
        if (bonusesElement != null && bonusesElement.isJsonArray()) {
            for (JsonElement element : bonusesElement.getAsJsonArray()) {
                Map<String, Integer> bonus = new LinkedHashMap<>();
                boolean tierIllegal = false;
                if (element != null && element.isJsonObject()) {
                    for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                        if ("illegal".equals(entry.getKey())) {
                            tierIllegal = true;
                            continue;
                        }
                        Integer value = WynnItem.Json.optionalInteger(entry.getValue());
                        String key = Identifications.normalise(entry.getKey());
                        if (value != null && key != null && value != 0) {
                            bonus.put(key, value);
                        }
                    }
                }
                bonuses.add(Map.copyOf(bonus));
                illegal.add(tierIllegal);
            }
        }

        boolean hidden = object.has("hidden")
                && object.get("hidden").isJsonPrimitive()
                && object.get("hidden").getAsBoolean();
        return new WynnSet(name, items, bonuses, hidden, illegal);
    }
}
