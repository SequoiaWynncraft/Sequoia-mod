package com.seqwawa.seq.map;

import java.util.List;
import java.util.Objects;

public record IngredientFarmSpot(
        String id,
        String name,
        int x,
        int y,
        int z,
        int radius,
        List<String> ingredients,
        List<String> mobs,
        String notes) {
    public IngredientFarmSpot {
        id = requireText(id, "id");
        name = requireText(name, "name");
        radius = Math.max(0, radius);
        ingredients = cleanList(ingredients);
        mobs = cleanList(mobs);
        notes = notes == null ? "" : notes.trim();
    }

    public boolean farmsIngredient(String ingredientName) {
        if (ingredientName == null || ingredientName.isBlank()) {
            return false;
        }
        return ingredients.stream().anyMatch(ingredient -> ingredient.equalsIgnoreCase(ingredientName.trim()));
    }

    public String coordinates() {
        return x + ", " + y + ", " + z;
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return normalized;
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .toList();
    }
}
