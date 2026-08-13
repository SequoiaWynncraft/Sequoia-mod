package com.seqwawa.seq.wynnbuilder.data;

import java.util.List;
import java.util.Locale;

/**
 * A crafted item: a recipe, two material tiers, six ingredient slots and an attack speed.
 *
 * <p>Ingredient ID {@link #NO_INGREDIENT} marks an empty slot; IDs from {@link #POWDER_ID_BASE}
 * onwards are powders used as ingredients.
 */
public record CraftedItem(int recipeId, List<Integer> ingredientIds, int materialTier1, int materialTier2, AttackSpeed attackSpeed) {

    /** Ingredient slot count, fixed by the encoding. */
    public static final int INGREDIENT_SLOTS = 6;
    /** The "no ingredient" sentinel used in the encoding. */
    public static final int NO_INGREDIENT = 4000;
    /** Powders occupy IDs 4001-4030 when used as ingredients. */
    public static final int POWDER_ID_BASE = 4001;

    /** Recipe types that carry an attack speed in the encoded stream. */
    private static final List<String> WEAPON_TYPES = List.of("relik", "wand", "spear", "dagger", "bow");

    public CraftedItem {
        ingredientIds = List.copyOf(ingredientIds);
        if (ingredientIds.size() != INGREDIENT_SLOTS) {
            throw new IllegalArgumentException("A craft must have exactly " + INGREDIENT_SLOTS + " ingredient slots");
        }
        if (attackSpeed == null) {
            attackSpeed = AttackSpeed.SLOW;
        }
    }

    public enum AttackSpeed {
        SLOW("Slow"),
        NORMAL("Normal"),
        FAST("Fast");

        private final String label;

        AttackSpeed(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static AttackSpeed byIndex(int index) {
            AttackSpeed[] values = values();
            return index >= 0 && index < values.length ? values[index] : SLOW;
        }
    }

    /** An empty craft for the given recipe. */
    public static CraftedItem empty(int recipeId) {
        return new CraftedItem(
                recipeId,
                List.of(NO_INGREDIENT, NO_INGREDIENT, NO_INGREDIENT, NO_INGREDIENT, NO_INGREDIENT, NO_INGREDIENT),
                3,
                3,
                AttackSpeed.SLOW);
    }

    /** Whether an attack speed field is present for a recipe of this type. */
    public static boolean isWeaponType(String recipeType) {
        return recipeType != null && WEAPON_TYPES.contains(recipeType.toLowerCase(Locale.ROOT));
    }

    public CraftedItem withIngredient(int slot, int ingredientId) {
        List<Integer> updated = new java.util.ArrayList<>(ingredientIds);
        updated.set(slot, ingredientId);
        return new CraftedItem(recipeId, updated, materialTier1, materialTier2, attackSpeed);
    }

    public CraftedItem withRecipe(int newRecipeId) {
        return new CraftedItem(newRecipeId, ingredientIds, materialTier1, materialTier2, attackSpeed);
    }

    public CraftedItem withMaterialTiers(int tier1, int tier2) {
        return new CraftedItem(recipeId, ingredientIds, tier1, tier2, attackSpeed);
    }

    public CraftedItem withAttackSpeed(AttackSpeed speed) {
        return new CraftedItem(recipeId, ingredientIds, materialTier1, materialTier2, speed);
    }
}
