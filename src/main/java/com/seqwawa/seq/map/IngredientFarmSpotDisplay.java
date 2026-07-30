package com.seqwawa.seq.map;

import com.seqwawa.seq.model.IngredientGuideEntry;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

public final class IngredientFarmSpotDisplay {
    private static final int[] TIER_COLORS = {
        0xFF999999,
        0xFFFFF799,
        0xFFFFFF00,
        0xFFE64D00
    };

    private IngredientFarmSpotDisplay() {}

    public static List<Entry> resolve(
            IngredientFarmSpot spot, Function<String, IngredientGuideEntry> ingredientResolver) {
        Objects.requireNonNull(spot, "spot");
        Objects.requireNonNull(ingredientResolver, "ingredientResolver");
        return spot.ingredients().stream()
                .map(name -> new Entry(name, ingredientResolver.apply(name)))
                .sorted(Comparator.comparingInt(Entry::tierSortValue))
                .toList();
    }

    public record Entry(String name, IngredientGuideEntry ingredient) {
        public Entry {
            name = Objects.requireNonNull(name, "name").trim();
        }

        public String metadata() {
            if (ingredient == null) {
                return "Ingredient data unavailable";
            }
            String professions = professions();
            return professions + " · Tier " + ingredient.tier() + " · Lv. " + ingredient.level();
        }

        public String professions() {
            if (ingredient == null) {
                return "Unknown professions";
            }
            return ingredient.skills().isEmpty()
                    ? "No professions listed"
                    : ingredient.skills().stream()
                            .map(IngredientFarmSpotDisplay::titleCase)
                            .reduce((first, second) -> first + ", " + second)
                            .orElse("");
        }

        public String displayLine() {
            return name + " · " + metadata();
        }

        public int tierColor() {
            int tier = ingredient == null ? 0 : ingredient.tier();
            return TIER_COLORS[Math.max(0, Math.min(TIER_COLORS.length - 1, tier))];
        }

        private int tierSortValue() {
            return ingredient == null ? Integer.MAX_VALUE : ingredient.tier();
        }
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
