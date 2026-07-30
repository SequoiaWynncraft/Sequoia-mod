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
                    "Kaian Scroll & Gravitation Crystal Totem Spot",
                    1070,
                    160,
                    -4693,
                    0,
                    List.of("Kaian Scroll", "Gravitation Crystal"),
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
                    ""),
            new IngredientFarmSpot(
                    "mucoid-matter-negative-1988-33-negative-692",
                    "Mucoid Matter Totem Spot",
                    -1988,
                    33,
                    -692,
                    0,
                    List.of("Mucoid Matter"),
                    List.of(),
                    ""),
            new IngredientFarmSpot(
                    "infected-mass-1311-92-negative-1036",
                    "Infected Mass Totem Spot",
                    1311,
                    92,
                    -1036,
                    0,
                    List.of("Infected Mass"),
                    List.of(),
                    ""),
            new IngredientFarmSpot(
                    "void-valley-1373-143-negative-999",
                    "Void Valley",
                    1373,
                    143,
                    -999,
                    0,
                    List.of(
                            "Leg Eater Tooth",
                            "Insanity Star",
                            "Expelled Shrapnel",
                            "Soul Essence",
                            "Toxxulous Ripper's Legs",
                            "Myocardial Leg",
                            "Glow Bulb Seeds"),
                    List.of(),
                    ""),
            new IngredientFarmSpot(
                    "rotten-teeth-1259-20-negative-5536",
                    "Rotten Teeth",
                    1259,
                    20,
                    -5536,
                    0,
                    List.of(
                            "Rotten Teeth",
                            "Ashen Hide",
                            "Infernal Flesh",
                            "Serpent Tongue"),
                    List.of(),
                    "Don't expect a LOT of serpent tongues."),
            new IngredientFarmSpot(
                    "silent-road-664-75-negative-1040",
                    "Silent Road",
                    664,
                    75,
                    -1040,
                    0,
                    List.of(
                            "Blighted Skull",
                            "Ominous Pearl",
                            "Forgotten Pickaxe",
                            "Sought-After Ore",
                            "Contorted Stone"),
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
