package com.seqwawa.seq.wynnbuilder.calc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
import com.seqwawa.seq.wynnbuilder.data.WynnDataFile;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemDetailsTest {

    private static final String ITEMS =
            """
            {"items": [
              {"name": "Rolled", "displayName": "Rolled", "category": "accessory", "type": "ring",
               "tier": "Legendary", "lvl": 100, "id": 1, "sdPct": 10, "hpBonus": -100, "spRaw3": -10,
               "dexReq": 40},
              {"name": "Fixed", "displayName": "Fixed", "category": "accessory", "type": "ring",
               "tier": "Unique", "lvl": 50, "id": 2, "sdPct": 25, "fixID": true},
              {"name": "Plate", "displayName": "Plate", "category": "armor", "type": "chestplate",
               "tier": "Rare", "lvl": 80, "id": 3, "hp": 2000, "eDef": 100, "fDef": -50, "slots": 3,
               "majorIds": ["TAUNT"]}
            ]}
            """;

    private static final String RECIPES =
            """
            {"recipes": [
              {"type": "BOOTS", "skill": "TAILORING", "id": 43, "name": "Boots-1-3",
               "healthOrDamage": {"minimum": 100, "maximum": 140},
               "durability": {"minimum": 175, "maximum": 182},
               "lvl": {"minimum": 100, "maximum": 103},
               "materials": [{"item": "A", "amount": 1}, {"item": "B", "amount": 1}]}
            ]}
            """;

    private static final String INGREDIENTS =
            """
            {"ingredients": [
              {"displayName": "Bob's Tear", "name": "Bob's Tear", "type": "ingredient",
               "tier": 3, "lvl": 100, "id": 700, "skills": ["TAILORING"],
               "ids": {"sdPct": {"minimum": 10, "maximum": 20}},
               "consumableIDs": {"dura": 0, "charges": 0},
               "posMods": {"left": 0, "right": 0, "above": 0, "under": 0, "touching": 0, "notTouching": 0},
               "itemIDs": {"dura": -100, "strReq": 15}}
            ]}
            """;

    private static WynnDataSet data() {
        Map<WynnDataFile, String> contents = new EnumMap<>(WynnDataFile.class);
        contents.put(WynnDataFile.ITEMS, ITEMS);
        contents.put(WynnDataFile.RECIPES, RECIPES);
        contents.put(WynnDataFile.INGREDIENTS, INGREDIENTS);
        return WynnDataSet.parse("test", contents);
    }

    private static String valueFor(List<ItemDetails.Line> lines, String label) {
        return lines.stream()
                .filter(line -> line.label().equals(label))
                .map(ItemDetails.Line::value)
                .findFirst()
                .orElse(null);
    }

    @Test
    void rolledStatsAreShownAsARange() {
        WynnItem item = data().itemByName("Rolled");

        List<ItemDetails.Line> lines = ItemDetails.forItem(item);

        assertEquals("+3 to +13%", valueFor(lines, "Spell Damage %"));
    }

    @Test
    void drawbacksReadInNumericOrder() {
        WynnItem item = data().itemByName("Rolled");

        List<ItemDetails.Line> lines = ItemDetails.forItem(item);

        // -130 to -70, not "-70 to -130": the display order is numeric even though the best roll
        // is the larger one.
        assertEquals("-130 to -70", valueFor(lines, "Health Bonus"));
    }

    @Test
    void spellCostReductionsReadInNumericOrderToo() {
        WynnItem item = data().itemByName("Rolled");

        List<ItemDetails.Line> lines = ItemDetails.forItem(item);

        assertEquals("-13 to -3", valueFor(lines, "Spell 3 Cost"));
    }

    @Test
    void fixedItemsShowASingleValue() {
        WynnItem item = data().itemByName("Fixed");

        List<ItemDetails.Line> lines = ItemDetails.forItem(item);

        assertEquals("+25%", valueFor(lines, "Spell Damage %"));
        assertTrue(lines.stream().anyMatch(line -> line.label().contains("fixed")));
    }

    @Test
    void structuralFactsAreListedSeparatelyFromRolls() {
        WynnItem item = data().itemByName("Plate");

        List<ItemDetails.Line> lines = ItemDetails.forItem(item);

        assertEquals("2000", valueFor(lines, "Health"));
        assertEquals("+100", valueFor(lines, "Earth defence"));
        assertEquals("-50", valueFor(lines, "Fire defence"));
        assertEquals("3", valueFor(lines, "Powder slots"));
        assertTrue(lines.stream().anyMatch(line -> line.label().equals("TAUNT")));
    }

    @Test
    void requirementsAreNamed() {
        List<ItemDetails.Line> lines = ItemDetails.forItem(data().itemByName("Rolled"));

        assertEquals("40", valueFor(lines, "Dexterity"));
    }

    @Test
    void craftsDescribeTheirRecipeAndResultingRanges() {
        WynnDataSet data = data();
        CraftedItem craft = new CraftedItem(43,
                List.of(700, CraftedItem.NO_INGREDIENT, CraftedItem.NO_INGREDIENT,
                        CraftedItem.NO_INGREDIENT, CraftedItem.NO_INGREDIENT, CraftedItem.NO_INGREDIENT),
                3, 3, CraftedItem.AttackSpeed.SLOW);

        List<ItemDetails.Line> lines = ItemDetails.forCraft(craft, data);

        assertEquals("Tailoring", valueFor(lines, "Profession"));
        // Spelled out: the UI font has no star glyph, so it rendered as an empty box.
        assertEquals("Tier 3 and tier 3", valueFor(lines, "Materials"));
        // What the ingredients produce is shown; the grid itself belongs to the crafter.
        assertEquals("+10 to +20%", valueFor(lines, "Spell Damage %"));
        // Requirements introduced by the ingredient carry through.
        assertEquals("15", valueFor(lines, "Strength"));
        assertFalse(lines.stream().anyMatch(line -> line.label().equals("Bob's Tear")),
                "the ingredient list is left to the crafter");
    }

    @Test
    void armourCraftsDoNotReportConsumableWarnings() {
        // Duration is a consumable concern; a helmet recipe has none, so warning about it would be
        // reporting on a value the recipe never had.
        List<ItemDetails.Line> lines = ItemDetails.forCraft(CraftedItem.empty(43), data());

        assertFalse(lines.stream().anyMatch(line -> line.label().contains("Duration")),
                "armour must not report a duration warning");
    }

    @Test
    void materialTiersChangeTheCraftedHealth() {
        WynnDataSet data = data();
        List<ItemDetails.Line> lowTier = ItemDetails.forCraft(CraftedItem.empty(43).withMaterialTiers(1, 1), data);
        List<ItemDetails.Line> highTier = ItemDetails.forCraft(CraftedItem.empty(43).withMaterialTiers(3, 3), data);

        assertFalse(valueFor(lowTier, "Health").equals(valueFor(highTier, "Health")),
                "better materials must produce a better craft");
    }

    @Test
    void missingItemsProduceNoLinesRatherThanFailing() {
        assertTrue(ItemDetails.forItem(null).isEmpty());
        assertTrue(ItemDetails.forCraft(null, data()).isEmpty());
    }
}
