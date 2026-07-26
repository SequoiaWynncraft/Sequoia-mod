package com.seqwawa.seq.map;

import java.util.List;

public final class IngredientFarmSpotCatalog {
    /*
     * Curated mob-totem locations live here. A spot can target several
     * ingredients and mobs; the Ingredients map UI already consumes this list.
     */
    private static final List<IngredientFarmSpot> BUILT_IN_SPOTS = List.of();

    private IngredientFarmSpotCatalog() {}

    public static List<IngredientFarmSpot> all() {
        return BUILT_IN_SPOTS;
    }

    public static List<IngredientFarmSpot> forIngredient(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return List.of();
        }
        return BUILT_IN_SPOTS.stream()
                .filter(spot -> spot.farmsIngredient(ingredientName))
                .toList();
    }
}
