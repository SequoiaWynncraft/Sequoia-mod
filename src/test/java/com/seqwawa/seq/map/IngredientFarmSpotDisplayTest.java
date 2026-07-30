package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.model.IngredientGuideEntry;
import com.seqwawa.seq.model.IngredientGuideEntry.Icon;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IngredientFarmSpotDisplayTest {
    @Test
    void sortsIngredientsByTierAscendingAndKeepsUnknownEntries() {
        IngredientFarmSpot spot = spot(List.of("Tier Three", "Unknown", "Tier Zero", "Tier One"));
        Map<String, IngredientGuideEntry> ingredients = Map.of(
                "Tier Three", ingredient("Tier Three", 3, 90, List.of("scribing")),
                "Tier Zero", ingredient("Tier Zero", 0, 5, List.of("cooking")),
                "Tier One", ingredient("Tier One", 1, 30, List.of("armouring")));

        List<IngredientFarmSpotDisplay.Entry> resolved =
                IngredientFarmSpotDisplay.resolve(spot, ingredients::get);

        assertEquals(
                List.of("Tier Zero", "Tier One", "Tier Three", "Unknown"),
                resolved.stream().map(IngredientFarmSpotDisplay.Entry::name).toList());
    }

    @Test
    void formatsProfessionTierAndLevelOnIngredientLine() {
        IngredientFarmSpotDisplay.Entry entry = new IngredientFarmSpotDisplay.Entry(
                "Sample Ingredient",
                ingredient("Sample Ingredient", 2, 67, List.of("armouring", "tailoring")));

        assertEquals("Armouring, Tailoring · Tier 2 · Lv. 67", entry.metadata());
        assertEquals(
                "Sample Ingredient · Armouring, Tailoring · Tier 2 · Lv. 67",
                entry.displayLine());
        assertEquals(0xFFFFFF00, entry.tierColor());
    }

    private static IngredientFarmSpot spot(List<String> ingredients) {
        return new IngredientFarmSpot("test", "Test", 0, 0, 0, 0, ingredients, List.of(), "");
    }

    private static IngredientGuideEntry ingredient(
            String name, int tier, int level, List<String> skills) {
        return new IngredientGuideEntry(
                name,
                name,
                tier,
                level,
                skills,
                Icon.unavailable(),
                List.of());
    }
}
