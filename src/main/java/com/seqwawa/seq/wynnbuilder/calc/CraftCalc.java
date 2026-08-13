package com.seqwawa.seq.wynnbuilder.calc;

import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
import com.seqwawa.seq.wynnbuilder.data.Identifications;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import com.seqwawa.seq.wynnbuilder.data.WynnIngredient;
import com.seqwawa.seq.wynnbuilder.data.WynnRecipe;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the result of a craft: the item a recipe, material tiers and six ingredients produce.
 *
 * <p>The ingredient grid is two columns by three rows, filled left to right and top to bottom, and
 * an ingredient's position matters: some grant bonuses to their neighbours. Those positional
 * modifiers produce an effectiveness percentage per cell, which scales that ingredient's
 * identifications and requirement changes — but never durability, duration or charges.
 */
public final class CraftCalc {

    private static final int ROWS = 3;
    private static final int COLUMNS = 2;

    /** Material tier multipliers, indexed by tier. Tier 0 is unused. */
    private static final double[] TIER_MULTIPLIER = {0, 1, 1.25, 1.4};

    private CraftCalc() {}

    /** The computed craft. Ranges are {@code [low, high]}. */
    public record Result(
            String type,
            String category,
            int levelMin,
            int levelMax,
            int[] healthOrDamage,
            int[] durability,
            int[] duration,
            int charges,
            int powderSlots,
            int[] neutralDamage,
            Map<String, int[]> identificationRanges,
            Map<String, Integer> requirements,
            int[] effectiveness,
            List<String> warnings) {

        public Result {
            healthOrDamage = healthOrDamage.clone();
            durability = durability.clone();
            duration = duration.clone();
            neutralDamage = neutralDamage.clone();
            identificationRanges = Map.copyOf(identificationRanges);
            requirements = Map.copyOf(requirements);
            effectiveness = effectiveness.clone();
            warnings = List.copyOf(warnings);
        }

        public boolean isWeapon() {
            return "weapon".equals(category);
        }

        public boolean isArmour() {
            return "armor".equals(category);
        }

        public boolean isConsumable() {
            return "consumable".equals(category);
        }
    }

    /**
     * Computes the effectiveness percentage of each of the six ingredient positions.
     *
     * <p>Positions are numbered {@code row * 2 + column}. An ingredient's {@code above}/{@code under}
     * bonuses apply to its whole column, {@code left}/{@code right} to its row neighbour, and
     * {@code touching}/{@code notTouching} to cells by grid adjacency.
     */
    public static int[] effectiveness(List<WynnIngredient> ingredients) {
        int[][] grid = new int[ROWS][COLUMNS];
        for (int[] row : grid) {
            java.util.Arrays.fill(row, 100);
        }

        for (int index = 0; index < ingredients.size() && index < ROWS * COLUMNS; index++) {
            WynnIngredient ingredient = ingredients.get(index);
            if (ingredient == null) {
                continue;
            }
            WynnIngredient.PositionModifiers modifiers = ingredient.positionModifiers();
            if (modifiers.isEmpty()) {
                continue;
            }
            int row = index / COLUMNS;
            int column = index % COLUMNS;

            for (int k = row - 1; k >= 0; k--) {
                grid[k][column] += modifiers.above();
            }
            for (int k = row + 1; k < ROWS; k++) {
                grid[k][column] += modifiers.under();
            }
            if (column == 1) {
                grid[row][0] += modifiers.left();
            }
            if (column == 0) {
                grid[row][1] += modifiers.right();
            }
            if (modifiers.touching() != 0 || modifiers.notTouching() != 0) {
                for (int k = 0; k < ROWS; k++) {
                    for (int l = 0; l < COLUMNS; l++) {
                        int rowDistance = Math.abs(k - row);
                        int columnDistance = Math.abs(l - column);
                        boolean touching = (rowDistance == 1 && columnDistance == 0)
                                || (rowDistance == 0 && columnDistance == 1);
                        boolean notTouching = rowDistance > 1 || (rowDistance == 1 && columnDistance == 1);
                        if (touching) {
                            grid[k][l] += modifiers.touching();
                        }
                        if (notTouching) {
                            grid[k][l] += modifiers.notTouching();
                        }
                    }
                }
            }
        }

        int[] flattened = new int[ROWS * COLUMNS];
        for (int row = 0; row < ROWS; row++) {
            System.arraycopy(grid[row], 0, flattened, row * COLUMNS, COLUMNS);
        }
        return flattened;
    }

    public static Result compute(CraftedItem craft, WynnDataSet data) {
        WynnRecipe recipe = data.recipe(craft.recipeId());
        if (recipe == null) {
            return new Result("", "", 0, 0, new int[2], new int[2], new int[2], 0, 0, new int[2],
                    Map.of(), Map.of(), new int[6], List.of("Unknown recipe"));
        }

        List<WynnIngredient> ingredients = new ArrayList<>(CraftedItem.INGREDIENT_SLOTS);
        boolean anyIngredient = false;
        for (int ingredientId : craft.ingredientIds()) {
            WynnIngredient ingredient = ingredientId == CraftedItem.NO_INGREDIENT ? null : data.ingredient(ingredientId);
            ingredients.add(ingredient);
            if (ingredient != null) {
                anyIngredient = true;
            }
        }

        String type = recipe.type() == null ? "" : recipe.type().toLowerCase(java.util.Locale.ROOT);
        String category = categoryOf(recipe);
        List<String> warnings = new ArrayList<>();

        double materialMultiplier = materialMultiplier(craft, recipe);

        int[] healthOrDamage = {
            (int) Math.floor(recipe.healthOrDamageMin() * materialMultiplier),
            (int) Math.floor(recipe.healthOrDamageMax() * materialMultiplier)
        };

        int[] durability = {
            (int) Math.round(recipe.durabilityMin() * materialMultiplier),
            (int) Math.round(recipe.durabilityMax() * materialMultiplier)
        };

        // A consumable with no ingredients keeps the recipe's basic duration instead of the scaled one.
        int[] duration = anyIngredient
                ? new int[] {
                    (int) Math.round(recipe.durationMin() * materialMultiplier),
                    (int) Math.round(recipe.durationMax() * materialMultiplier)
                }
                : new int[] {recipe.basicDurationMin(), recipe.basicDurationMax()};

        int charges = "consumable".equals(category) ? (anyIngredient ? tierBySize(recipe.minLevel()) : 3) : 0;
        int powderSlots = "consumable".equals(category) ? 0 : tierBySize(recipe.minLevel());

        int[] neutralDamage = new int[2];
        if ("weapon".equals(category)) {
            double ratio = attackSpeedRatio(craft.attackSpeed());
            neutralDamage[0] = (int) Math.floor(Math.floor(healthOrDamage[0]) * ratio);
            neutralDamage[1] = (int) Math.floor(Math.floor(healthOrDamage[1]) * ratio);
        }

        int[] effectiveness = effectiveness(ingredients);

        Map<String, int[]> identificationRanges = new LinkedHashMap<>();
        Map<String, Integer> requirements = new LinkedHashMap<>();
        for (String key : Identifications.REQUIREMENT_KEYS) {
            requirements.put(key, 0);
        }

        for (int index = 0; index < ingredients.size(); index++) {
            WynnIngredient ingredient = ingredients.get(index);
            if (ingredient == null) {
                continue;
            }
            double multiplier = effectiveness[index] / 100.0;

            // Durability, duration and charges bypass effectiveness entirely.
            durability[0] += ingredient.itemModifiers().durability();
            durability[1] += ingredient.itemModifiers().durability();
            duration[0] += ingredient.consumableModifiers().duration();
            duration[1] += ingredient.consumableModifiers().duration();
            charges += ingredient.consumableModifiers().charges();

            if (!"consumable".equals(category)) {
                for (Map.Entry<String, Integer> entry : ingredient.itemModifiers().requirements().entrySet()) {
                    int scaled = (int) Math.round(entry.getValue() * multiplier + 1e-9);
                    requirements.merge(entry.getKey(), scaled, Integer::sum);
                }
            }

            for (Map.Entry<String, int[]> entry : ingredient.identificationRanges().entrySet()) {
                int low = (int) Math.floor(entry.getValue()[0] * multiplier);
                int high = (int) Math.floor(entry.getValue()[1] * multiplier);
                int minimum = Math.min(low, high);
                int maximum = Math.max(low, high);
                identificationRanges.merge(entry.getKey(), new int[] {minimum, maximum}, (existing, added) ->
                        new int[] {existing[0] + added[0], existing[1] + added[1]});
            }
        }

        boolean consumable = "consumable".equals(category);
        // Durability applies to gear, duration and charges to consumables. Checking the wrong one
        // reports a warning for a value the recipe never had, since it defaults to zero.
        if (!consumable) {
            for (int i = 0; i < durability.length; i++) {
                if (durability[i] < 1) {
                    durability[i] = 0;
                    if (!warnings.contains("Durability falls below zero")) {
                        warnings.add("Durability falls below zero");
                    }
                }
            }
        } else {
            if (anyIngredient) {
                for (int i = 0; i < duration.length; i++) {
                    if (duration[i] < 1) {
                        duration[i] = 1;
                        if (!warnings.contains("Duration falls below one")) {
                            warnings.add("Duration falls below one");
                        }
                    }
                }
            }
            if (charges < 1) {
                charges = 1;
            }
        }

        return new Result(
                type,
                category,
                recipe.minLevel(),
                recipe.maxLevel(),
                healthOrDamage,
                durability,
                duration,
                charges,
                powderSlots,
                neutralDamage,
                identificationRanges,
                requirements,
                effectiveness,
                warnings);
    }

    /** Weighted by how much of each material the recipe uses. */
    private static double materialMultiplier(CraftedItem craft, WynnRecipe recipe) {
        List<Integer> amounts = recipe.materialAmounts();
        int firstAmount = amounts.size() > 0 ? amounts.get(0) : 1;
        int secondAmount = amounts.size() > 1 ? amounts.get(1) : 1;
        double first = TIER_MULTIPLIER[clampTier(craft.materialTier1())] * firstAmount;
        double second = TIER_MULTIPLIER[clampTier(craft.materialTier2())] * secondAmount;
        int total = firstAmount + secondAmount;
        return total == 0 ? 1 : (first + second) / total;
    }

    private static int clampTier(int tier) {
        return Math.max(1, Math.min(tier, 3));
    }

    /** Both powder slots and consumable charges follow the same level thresholds. */
    private static int tierBySize(int minLevel) {
        if (minLevel < 30) {
            return 1;
        }
        return minLevel < 70 ? 2 : 3;
    }

    private static double attackSpeedRatio(CraftedItem.AttackSpeed attackSpeed) {
        return switch (attackSpeed) {
            case SLOW -> 2.05 / 1.5;
            case NORMAL -> 1.0;
            case FAST -> 2.05 / 2.5;
        };
    }

    private static String categoryOf(WynnRecipe recipe) {
        if (recipe.isConsumable()) {
            return "consumable";
        }
        if (recipe.isWeapon()) {
            return "weapon";
        }
        if (recipe.isArmour()) {
            return "armor";
        }
        if (recipe.isAccessory()) {
            return "accessory";
        }
        return "";
    }
}
