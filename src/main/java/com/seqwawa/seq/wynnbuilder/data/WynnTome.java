package com.seqwawa.seq.wynnbuilder.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tome. Structurally a simplified item: no damage, no powders, just identifications.
 *
 * <p>The fourteen tome slots on a build are typed, so {@link Slot} maps each slot index to the tome
 * category it accepts.
 */
public record WynnTome(
        int id,
        String name,
        String displayName,
        String type,
        WynnItem.Tier tier,
        int level,
        Map<String, Integer> identifications,
        Integer remapId) {

    public WynnTome {
        identifications = Map.copyOf(identifications);
    }

    /**
     * The fourteen tome slots in encoding order, with the tome type each accepts.
     *
     * <p>Order matters: it is the order the slots are written to the link.
     */
    public enum Slot {
        WEAPON_1("weaponTome", "Weapon Tome"),
        WEAPON_2("weaponTome", "Weapon Tome"),
        ARMOUR_1("armorTome", "Armour Tome"),
        ARMOUR_2("armorTome", "Armour Tome"),
        ARMOUR_3("armorTome", "Armour Tome"),
        ARMOUR_4("armorTome", "Armour Tome"),
        GUILD("guildTome", "Guild Tome"),
        LOOTRUN("lootrunTome", "Lootrun Tome"),
        GATHER_XP_1("gatherXpTome", "Gathering XP Tome"),
        GATHER_XP_2("gatherXpTome", "Gathering XP Tome"),
        DUNGEON_XP_1("dungeonXpTome", "Dungeon XP Tome"),
        DUNGEON_XP_2("dungeonXpTome", "Dungeon XP Tome"),
        MOB_XP_1("mobXpTome", "Mob XP Tome"),
        MOB_XP_2("mobXpTome", "Mob XP Tome");

        private static final List<Slot> ORDER = List.of(values());

        private final String tomeType;
        private final String label;

        Slot(String tomeType, String label) {
            this.tomeType = tomeType;
            this.label = label;
        }

        public String tomeType() {
            return tomeType;
        }

        public String label() {
            return label;
        }

        public static List<Slot> encodingOrder() {
            return ORDER;
        }
    }

    public static WynnTome parse(JsonObject object) {
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

        Map<String, Integer> identifications = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String key = Identifications.normalise(entry.getKey());
            if (key == null || !Identifications.isIdentification(key)) {
                continue;
            }
            Integer value = WynnItem.Json.optionalInteger(entry.getValue());
            if (value != null && value != 0) {
                identifications.merge(key, value, Integer::sum);
            }
        }

        return new WynnTome(
                id,
                name,
                displayName,
                WynnItem.Json.stringOrDefault(object, "type", ""),
                WynnItem.Tier.parse(WynnItem.Json.string(object, "tier")),
                WynnItem.Json.integer(object, "lvl", 0),
                identifications,
                object.has("remapID") ? WynnItem.Json.integer(object, "remapID", -1) : null);
    }
}
