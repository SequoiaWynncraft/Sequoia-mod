package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IngredientFarmSpotTest {
    @Test
    void supportsSeveralIngredientAndMobTargets() {
        List<String> ingredients = new ArrayList<>(List.of("Dead Bee", "Sturdy Flesh"));
        IngredientFarmSpot spot = new IngredientFarmSpot(
                "example",
                "Example Totem Spot",
                10,
                64,
                -20,
                25,
                ingredients,
                List.of("Bee", "Zombie"),
                "Example");
        ingredients.clear();

        assertTrue(spot.farmsIngredient("dead bee"));
        assertTrue(spot.farmsIngredient("Sturdy Flesh"));
        assertFalse(spot.farmsIngredient("Coastal Sand"));
        assertEquals(List.of("Dead Bee", "Sturdy Flesh"), spot.ingredients());
        assertEquals("10, 64, -20", spot.coordinates());
        assertThrows(UnsupportedOperationException.class, () -> spot.mobs().add("Spider"));
    }

    @Test
    void returnsNoCuratedSpotsForAnUnknownIngredient() {
        assertTrue(IngredientFarmSpotCatalog.forIngredient("Definitely Not An Ingredient").isEmpty());
    }
}
