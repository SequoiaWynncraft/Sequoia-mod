package com.seqwawa.seq.wynnbuilder.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/**
 * A class aspect and its tier thresholds.
 *
 * <p>Aspect IDs restart from zero for each class, so an ID only resolves alongside the class implied
 * by the equipped weapon.
 */
public record WynnAspect(int id, String displayName, WynnItem.Tier tier, String playerClass, List<Tier> tiers) {

    public WynnAspect {
        tiers = List.copyOf(tiers);
    }

    /** One tier of an aspect: the threshold that unlocks it and what it does. */
    public record Tier(int threshold, String description, List<AbilityModification> abilities) {
        public Tier {
            abilities = List.copyOf(abilities);
        }
    }

    /** An aspect tier modifying an existing ability, by property overrides or added effects. */
    public record AbilityModification(int baseAbilityId, JsonObject properties, List<JsonObject> effects) {
        public AbilityModification {
            effects = List.copyOf(effects);
        }
    }

    /** Number of aspect slots on a build. */
    public static final int SLOT_COUNT = 5;

    /** The description shown for a given tier, clamped to the available tiers. */
    public String descriptionForTier(int tier) {
        if (tiers.isEmpty()) {
            return "";
        }
        int index = Math.max(0, Math.min(tier - 1, tiers.size() - 1));
        return tiers.get(index).description();
    }

    public static WynnAspect parse(JsonObject object, String playerClass) {
        int id = WynnItem.Json.integer(object, "id", -1);
        if (id < 0) {
            return null;
        }
        List<Tier> tiers = new ArrayList<>();
        JsonElement tiersElement = object.get("tiers");
        if (tiersElement != null && tiersElement.isJsonArray()) {
            for (JsonElement element : tiersElement.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                JsonObject tierObject = element.getAsJsonObject();
                List<AbilityModification> abilities = new ArrayList<>();
                JsonElement abilitiesElement = tierObject.get("abilities");
                if (abilitiesElement != null && abilitiesElement.isJsonArray()) {
                    for (JsonElement abilityElement : abilitiesElement.getAsJsonArray()) {
                        if (abilityElement == null || !abilityElement.isJsonObject()) {
                            continue;
                        }
                        JsonObject ability = abilityElement.getAsJsonObject();
                        List<JsonObject> effects = new ArrayList<>();
                        JsonElement effectsElement = ability.get("effects");
                        if (effectsElement != null && effectsElement.isJsonArray()) {
                            for (JsonElement effect : effectsElement.getAsJsonArray()) {
                                if (effect != null && effect.isJsonObject()) {
                                    effects.add(effect.getAsJsonObject());
                                }
                            }
                        }
                        JsonElement properties = ability.get("properties");
                        abilities.add(new AbilityModification(
                                WynnItem.Json.integer(ability, "base_abil", -1),
                                properties != null && properties.isJsonObject() ? properties.getAsJsonObject() : new JsonObject(),
                                effects));
                    }
                }
                tiers.add(new Tier(
                        WynnItem.Json.integer(tierObject, "threshold", 1),
                        WynnItem.Json.stringOrDefault(tierObject, "description", ""),
                        abilities));
            }
        }
        return new WynnAspect(
                id,
                WynnItem.Json.stringOrDefault(object, "displayName", "Aspect " + id),
                WynnItem.Tier.parse(WynnItem.Json.string(object, "tier")),
                playerClass,
                tiers);
    }
}
