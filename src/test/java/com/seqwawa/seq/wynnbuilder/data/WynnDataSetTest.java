package com.seqwawa.seq.wynnbuilder.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Parser tests over fixtures written in the upstream schema.
 *
 * <p>The fixtures are hand-written rather than copied from the WynnBuilder repository, which is
 * GPL-3 while this mod is MIT.
 */
class WynnDataSetTest {

    private static final String ITEMS =
            """
            {"items": [
              {"name": "Atlas", "displayName": "Atlas", "category": "accessory", "type": "bracelet",
               "tier": "Rare", "lvl": 94, "id": 167, "ls": 160, "ms": 6, "strReq": 0, "dexReq": 40},
              {"name": "Amalgamation", "displayName": "Amalgamation", "category": "weapon", "type": "wand",
               "tier": "Rare", "lvl": 120, "id": 900, "atkSpd": "FAST", "slots": 5, "classReq": "mage",
               "nDam": "0-0", "eDam": "18-27", "tDam": "14-31", "wDam": "19-27", "fDam": "20-25", "aDam": "15-31",
               "hpBonus": -1800, "maxMana": -28, "spRaw3": -3, "majorIds": ["TAUNT"]},
              {"name": "Guardian", "displayName": "Guardian", "category": "armor", "type": "chestplate",
               "tier": "Legendary", "lvl": 100, "id": 42, "hp": 4000, "eDef": 150, "fDef": -80, "set": "Guardian"},
              {"name": "Old Name", "displayName": "Old Name", "category": "accessory", "type": "ring",
               "tier": "Unique", "lvl": 50, "id": 5141, "remapID": 4602},
              {"name": "Espionage", "displayName": "Espionage", "category": "accessory", "type": "necklace",
               "tier": "Legendary", "lvl": 103, "id": 4602, "spd": 9}
            ], "version": "test"}
            """;

    private static final String RECIPES =
            """
            {"recipes": [
              {"type": "BOOTS", "skill": "TAILORING", "id": 43, "name": "Boots-1-3",
               "healthOrDamage": {"minimum": 9, "maximum": 11},
               "durability": {"minimum": 175, "maximum": 182},
               "lvl": {"minimum": 1, "maximum": 3}},
              {"type": "BOW", "skill": "WOODWORKING", "id": 500, "name": "Bow-100-103",
               "healthOrDamage": {"minimum": 200, "maximum": 260},
               "durability": {"minimum": 900, "maximum": 1100},
               "lvl": {"minimum": 100, "maximum": 103}}
            ]}
            """;

    private static final String INGREDIENTS =
            """
            {"ingredients": [
              {"displayName": "Rapid-Fire Mechanism", "name": "Rapid-Fire Mechanism", "type": "ingredient",
               "tier": 3, "lvl": 119, "id": 700, "skills": ["WEAPONSMITHING"],
               "ids": {"atkTier": {"minimum": 1, "maximum": 1}, "eMdRaw": {"minimum": 65, "maximum": 75}},
               "consumableIDs": {"dura": 0, "charges": 0},
               "posMods": {"left": 0, "right": 10, "above": 0, "under": -5, "touching": 0, "notTouching": 0},
               "itemIDs": {"dura": -184, "strReq": 25, "dexReq": 0}}
            ]}
            """;

    private static final String TOMES =
            """
            {"tomes": [
              {"name": "Tome of Weapon Mastery", "displayName": "Tome of Weapon Mastery", "type": "weaponTome",
               "tier": "Rare", "lvl": 105, "id": 12, "sdPct": 6, "mdPct": 6, "category": "tome"},
              {"name": "Tome of Dungeoneering", "displayName": "Tome of Dungeoneering", "type": "dungeonXpTome",
               "tier": "Unique", "lvl": 90, "id": 30, "xpb": 10, "category": "tome"},
              {"name": "Retired Tome", "displayName": "Retired Tome", "type": "armorTome",
               "tier": "Unique", "lvl": 1, "id": 99, "remapID": 12, "category": "tome"}
            ]}
            """;

    private static final String ASPECTS =
            """
            {"Archer": [
              {"displayName": "Aspect of Chaotic Demolition", "id": 0, "tier": "Fabled",
               "tiers": [{"threshold": 1, "description": "Increase your maximum Traps by +2.",
                          "abilities": [{"base_abil": 24, "properties": {"traps": 2}, "effects": []}]},
                         {"threshold": 15, "description": "Increase your maximum Traps by +3.", "abilities": []}]}
            ]}
            """;

    private static WynnDataSet parse() {
        Map<WynnDataFile, String> contents = new EnumMap<>(WynnDataFile.class);
        contents.put(WynnDataFile.ITEMS, ITEMS);
        contents.put(WynnDataFile.RECIPES, RECIPES);
        contents.put(WynnDataFile.INGREDIENTS, INGREDIENTS);
        contents.put(WynnDataFile.TOMES, TOMES);
        contents.put(WynnDataFile.ASPECTS, ASPECTS);
        return WynnDataSet.parse("test", contents);
    }

    @Test
    void itemsAreIndexedByIdAndName() {
        WynnDataSet data = parse();

        WynnItem atlas = data.item(167);
        assertNotNull(atlas);
        assertEquals("Atlas", atlas.displayName());
        assertEquals("bracelet", atlas.type());
        assertEquals(WynnItem.Tier.RARE, atlas.tier());
        assertEquals(94, atlas.level());
        assertEquals(atlas, data.itemByName("Atlas"));
        assertEquals(atlas, data.itemByName("atlas"), "name lookup should ignore case");
    }

    @Test
    void identificationsAreSeparatedFromStructuralFields() {
        WynnItem atlas = parse().item(167);

        assertEquals(Map.of("ls", 160, "ms", 6), atlas.identifications());
        // Requirements and level must not leak into the rolled identifications.
        assertEquals(40, atlas.requirements().get("dexReq"));
        assertFalse(atlas.identifications().containsKey("lvl"));
        assertFalse(atlas.identifications().containsKey("dexReq"));
    }

    @Test
    void weaponDamageRangesAndPowderSlotsAreParsed() {
        WynnItem weapon = parse().itemByName("Amalgamation");

        assertNotNull(weapon);
        assertTrue(weapon.isWeapon());
        assertEquals(5, weapon.powderSlots());
        assertEquals("FAST", weapon.attackSpeed());
        assertArrayEqualsRange(new int[] {18, 27}, weapon.damages().get("eDam"));
        assertArrayEqualsRange(new int[] {0, 0}, weapon.damages().get("nDam"));
        assertEquals(-1800, weapon.identifications().get("hpBonus"), "negative rolls must survive");
        assertEquals(java.util.List.of("TAUNT"), weapon.majorIds());
    }

    @Test
    void baseHealthAndDefencesAreStructuralNotRolled() {
        WynnItem chestplate = parse().itemByName("Guardian");

        assertEquals(4000, chestplate.baseHealth());
        assertEquals(150, chestplate.baseDefences().get("eDef"));
        assertEquals(-80, chestplate.baseDefences().get("fDef"));
        assertFalse(chestplate.identifications().containsKey("hp"));
        assertEquals("Guardian", chestplate.setName());
    }

    @Test
    void renamedItemsFollowTheirRedirect() {
        WynnDataSet data = parse();

        // Without following remapID an old shared link would resolve to nothing.
        WynnItem redirected = data.item(5141);
        assertNotNull(redirected);
        assertEquals("Espionage", redirected.displayName());
        assertEquals(4602, redirected.id());
    }

    @Test
    void syntheticEmptySlotItemsExistForLegacyLinks() {
        WynnDataSet data = parse();

        WynnItem noHelmet = data.item(10000);
        assertNotNull(noHelmet, "legacy hashes reference the empty-slot items by ID");
        assertEquals("No Helmet", noHelmet.displayName());
        assertTrue(noHelmet.isNoneItem());
        assertEquals("No Weapon", data.item(10008).displayName());
        // They must not pollute the pickers.
        assertFalse(data.itemsForSlot(EquipmentSlot.HELMET).contains(noHelmet));
    }

    @Test
    void slotFilteringUsesTheAcceptedItemType() {
        WynnDataSet data = parse();

        assertEquals(1, data.itemsForSlot(EquipmentSlot.WEAPON).size());
        assertEquals(1, data.itemsForSlot(EquipmentSlot.CHESTPLATE).size());
        assertEquals(1, data.itemsForSlot(EquipmentSlot.BRACELET).size());
        assertTrue(data.itemsForSlot(EquipmentSlot.BOOTS).isEmpty());
    }

    @Test
    void recipesExposeTheTypeTheCraftedCodecNeeds() {
        WynnDataSet data = parse();

        assertEquals("BOOTS", data.recipeType(43));
        assertFalse(data.recipe(43).isWeapon());
        assertTrue(data.recipe(500).isWeapon(), "a bow recipe encodes an attack speed");
        assertEquals(1, data.recipe(43).minLevel());
        assertEquals(3, data.recipe(43).maxLevel());
    }

    @Test
    void ingredientModifiersAreParsed() {
        WynnIngredient ingredient = parse().ingredientByName("Rapid-Fire Mechanism");

        assertNotNull(ingredient);
        assertEquals(3, ingredient.tier());
        assertArrayEqualsRange(new int[] {65, 75}, ingredient.identificationRanges().get("eMdRaw"));
        assertEquals(-184, ingredient.itemModifiers().durability());
        assertEquals(25, ingredient.itemModifiers().requirements().get("strReq"));
        assertEquals(10, ingredient.positionModifiers().right());
        assertEquals(-5, ingredient.positionModifiers().under());
        assertFalse(ingredient.positionModifiers().isEmpty());
    }

    @Test
    void tomesAreFilteredBySlotTypeAndRedirectsAreDropped() {
        WynnDataSet data = parse();

        assertEquals(1, data.tomesForSlot(WynnTome.Slot.WEAPON_1).size());
        assertEquals(1, data.tomesForSlot(WynnTome.Slot.DUNGEON_XP_1).size());
        assertTrue(data.tomesForSlot(WynnTome.Slot.ARMOUR_1).isEmpty(), "remapped tomes are not offered");
        assertNull(data.tome(99));
        assertEquals(Map.of("sdPct", 6, "mdPct", 6), data.tome(12).identifications());
    }

    @Test
    void tomeSlotOrderMatchesTheEncoding() {
        // The link writes slots in this order; a swap silently moves tomes between slots.
        assertEquals(
                java.util.List.of(
                        "weaponTome", "weaponTome", "armorTome", "armorTome", "armorTome", "armorTome",
                        "guildTome", "lootrunTome", "gatherXpTome", "gatherXpTome",
                        "dungeonXpTome", "dungeonXpTome", "mobXpTome", "mobXpTome"),
                WynnTome.Slot.encodingOrder().stream().map(WynnTome.Slot::tomeType).toList());
    }

    @Test
    void aspectsAreGroupedByClassWithTheirTiers() {
        WynnDataSet data = parse();

        assertEquals(1, data.aspects("Archer").size());
        WynnAspect aspect = data.aspect("Archer", 0);
        assertNotNull(aspect);
        assertEquals(2, aspect.tiers().size());
        assertEquals(15, aspect.tiers().get(1).threshold());
        assertTrue(aspect.descriptionForTier(1).contains("+2"));
        assertTrue(aspect.descriptionForTier(2).contains("+3"));
        assertTrue(data.aspects("Mage").isEmpty());
    }

    private static void assertArrayEqualsRange(int[] expected, int[] actual) {
        assertNotNull(actual);
        assertEquals(expected[0], actual[0]);
        assertEquals(expected[1], actual[1]);
    }
}
