package com.seqwawa.seq.map;

import java.util.List;

public final class IngredientFarmSpotCatalog {
    /*
     * Curated mob-totem locations live here. A spot can target several
     * ingredients and mobs; the Ingredients map UI already consumes this list.
     */
    private static final List<IngredientFarmSpot> BUILT_IN_SPOTS = List.of(
            new IngredientFarmSpot(
                    "kaian-scroll-1070-160-negative-4693",
                    "Kaian Scroll Totem Spot",
                    1070,
                    160,
                    -4693,
                    0,
                    List.of("Kaian Scroll"),
                    List.of(),
                    ""),
            new IngredientFarmSpot(
                    "dragonling-demonic-909-59-negative-4686",
                    "Dragonling Egg & Demonic Blood Totem Spot",
                    909,
                    59,
                    -4686,
                    0,
                    List.of("Dragonling Egg", "Demonic Blood"),
                    List.of(),
                    ""));

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
