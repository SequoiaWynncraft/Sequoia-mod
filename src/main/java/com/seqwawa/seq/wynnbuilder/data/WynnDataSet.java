package com.seqwawa.seq.wynnbuilder.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every parsed data file for one WynnBuilder data version, indexed for lookup.
 *
 * <p>Instances are immutable and shared across screens. Parsing happens off the render thread; a
 * screen only ever reads a finished set.
 */
public final class WynnDataSet {
    private final String version;
    private final EncodingConsts encodingConsts;

    private final List<WynnItem> items;
    private final Map<Integer, WynnItem> itemsById;
    private final Map<String, WynnItem> itemsByName;
    private final Map<Integer, Integer> itemRemaps;

    private final List<WynnIngredient> ingredients;
    private final Map<Integer, WynnIngredient> ingredientsById;
    private final Map<String, WynnIngredient> ingredientsByName;

    private final List<WynnRecipe> recipes;
    private final Map<Integer, WynnRecipe> recipesById;

    private final List<WynnTome> tomes;
    private final Map<Integer, WynnTome> tomesById;

    private final Map<String, List<WynnAspect>> aspectsByClass;
    private final Map<String, com.seqwawa.seq.wynnbuilder.atree.AbilityTree> abilityTrees;
    private final Map<String, WynnSet> sets;
    private final MajorIds majorIds;

    private WynnDataSet(
            String version,
            EncodingConsts encodingConsts,
            List<WynnItem> items,
            Map<Integer, Integer> itemRemaps,
            List<WynnIngredient> ingredients,
            List<WynnRecipe> recipes,
            List<WynnTome> tomes,
            Map<String, List<WynnAspect>> aspectsByClass,
            Map<String, com.seqwawa.seq.wynnbuilder.atree.AbilityTree> abilityTrees,
            Map<String, WynnSet> sets,
            MajorIds majorIds) {
        this.version = version;
        this.encodingConsts = encodingConsts;
        this.abilityTrees = Map.copyOf(abilityTrees);
        this.sets = Map.copyOf(sets);
        this.majorIds = majorIds;
        this.items = List.copyOf(items);
        this.itemRemaps = Map.copyOf(itemRemaps);
        this.ingredients = List.copyOf(ingredients);
        this.recipes = List.copyOf(recipes);
        this.tomes = List.copyOf(tomes);
        this.aspectsByClass = Map.copyOf(aspectsByClass);

        Map<Integer, WynnItem> byId = new HashMap<>(items.size());
        Map<String, WynnItem> byName = new HashMap<>(items.size());
        for (WynnItem item : items) {
            byId.put(item.id(), item);
            byName.put(normaliseName(item.displayName()), item);
            byName.putIfAbsent(normaliseName(item.name()), item);
        }
        this.itemsById = Map.copyOf(byId);
        this.itemsByName = Map.copyOf(byName);

        Map<Integer, WynnIngredient> ingredientsIndex = new HashMap<>(ingredients.size());
        Map<String, WynnIngredient> ingredientNames = new HashMap<>(ingredients.size());
        for (WynnIngredient ingredient : ingredients) {
            ingredientsIndex.put(ingredient.id(), ingredient);
            ingredientNames.put(normaliseName(ingredient.displayName()), ingredient);
        }
        this.ingredientsById = Map.copyOf(ingredientsIndex);
        this.ingredientsByName = Map.copyOf(ingredientNames);

        Map<Integer, WynnRecipe> recipesIndex = new HashMap<>(recipes.size());
        for (WynnRecipe recipe : recipes) {
            recipesIndex.put(recipe.id(), recipe);
        }
        this.recipesById = Map.copyOf(recipesIndex);

        Map<Integer, WynnTome> tomesIndex = new HashMap<>(tomes.size());
        for (WynnTome tome : tomes) {
            tomesIndex.put(tome.id(), tome);
        }
        this.tomesById = Map.copyOf(tomesIndex);
    }

    public String version() {
        return version;
    }

    public EncodingConsts encodingConsts() {
        return encodingConsts;
    }

    public List<WynnItem> items() {
        return items;
    }

    /**
     * Resolves an item ID, following the rename redirects.
     *
     * <p>A handful of items were merged or renamed upstream; without following the redirect an older
     * link resolves to nothing.
     */
    public WynnItem item(int id) {
        Integer target = itemRemaps.get(id);
        return itemsById.get(target != null ? target : id);
    }

    public WynnItem itemByName(String name) {
        return name == null ? null : itemsByName.get(normaliseName(name));
    }

    public List<WynnIngredient> ingredients() {
        return ingredients;
    }

    public WynnIngredient ingredient(int id) {
        return ingredientsById.get(id);
    }

    public WynnIngredient ingredientByName(String name) {
        return name == null ? null : ingredientsByName.get(normaliseName(name));
    }

    public List<WynnRecipe> recipes() {
        return recipes;
    }

    public WynnRecipe recipe(int id) {
        return recipesById.get(id);
    }

    /** The recipe type for an ID, which the crafted codec needs to know about attack speed. */
    public String recipeType(int id) {
        WynnRecipe recipe = recipesById.get(id);
        return recipe == null ? null : recipe.type();
    }

    public List<WynnTome> tomes() {
        return tomes;
    }

    public WynnTome tome(int id) {
        return tomesById.get(id);
    }

    /** Tomes that fit a given slot. */
    public List<WynnTome> tomesForSlot(WynnTome.Slot slot) {
        List<WynnTome> matching = new ArrayList<>();
        for (WynnTome tome : tomes) {
            if (slot.tomeType().equalsIgnoreCase(tome.type())) {
                matching.add(tome);
            }
        }
        return matching;
    }

    public List<WynnAspect> aspects(String playerClass) {
        return aspectsByClass.getOrDefault(playerClass, List.of());
    }

    /** The ability tree for a class, or {@code null} when the tree data is unavailable. */
    public com.seqwawa.seq.wynnbuilder.atree.AbilityTree abilityTree(String playerClass) {
        return playerClass == null ? null : abilityTrees.get(playerClass);
    }

    /** An item set by name, or {@code null} when the data has no such set. */
    public WynnSet set(String name) {
        return name == null ? null : sets.get(name);
    }

    public Map<String, WynnSet> sets() {
        return sets;
    }

    /** Major identification definitions, empty when that file was unavailable. */
    public MajorIds majorIds() {
        return majorIds;
    }

    public boolean hasAbilityTrees() {
        return !abilityTrees.isEmpty();
    }

    public WynnAspect aspect(String playerClass, int id) {
        for (WynnAspect aspect : aspects(playerClass)) {
            if (aspect.id() == id) {
                return aspect;
            }
        }
        return null;
    }

    /** Items that can go in a slot, filtered by the slot's accepted type. */
    public List<WynnItem> itemsForSlot(EquipmentSlot slot) {
        List<WynnItem> matching = new ArrayList<>();
        for (WynnItem item : items) {
            if (item.isNoneItem()) {
                continue;
            }
            if (slot == EquipmentSlot.WEAPON) {
                if (item.isWeapon()) {
                    matching.add(item);
                }
            } else if (slot.itemType() != null && slot.itemType().equalsIgnoreCase(item.type())) {
                matching.add(item);
            }
        }
        return matching;
    }

    /** Both the index and every lookup go through the same normalisation, so they cannot drift. */
    private static String normaliseName(String name) {
        return ItemNames.normalise(name);
    }

    /** Parses a complete data set from the raw file contents. */
    public static WynnDataSet parse(String version, Map<WynnDataFile, String> contents) {
        EncodingConsts consts = contents.containsKey(WynnDataFile.ENCODING_CONSTS)
                ? EncodingConsts.parse(contents.get(WynnDataFile.ENCODING_CONSTS))
                : EncodingConsts.DEFAULT;

        List<WynnItem> items = new ArrayList<>();
        Map<Integer, Integer> remaps = new LinkedHashMap<>();
        for (JsonObject object : arrayOf(contents.get(WynnDataFile.ITEMS), "items")) {
            WynnItem item = WynnItem.parse(object);
            if (item == null) {
                continue;
            }
            if (item.remapId() != null && item.remapId() >= 0) {
                remaps.put(item.id(), item.remapId());
            } else {
                items.add(item);
            }
        }
        items.addAll(syntheticNoneItems());

        List<WynnIngredient> ingredients = new ArrayList<>();
        for (JsonObject object : arrayOf(contents.get(WynnDataFile.INGREDIENTS), "ingredients")) {
            WynnIngredient ingredient = WynnIngredient.parse(object);
            if (ingredient != null) {
                ingredients.add(ingredient);
            }
        }

        List<WynnRecipe> recipes = new ArrayList<>();
        for (JsonObject object : arrayOf(contents.get(WynnDataFile.RECIPES), "recipes")) {
            WynnRecipe recipe = WynnRecipe.parse(object);
            if (recipe != null) {
                recipes.add(recipe);
            }
        }

        List<WynnTome> tomes = new ArrayList<>();
        for (JsonObject object : arrayOf(contents.get(WynnDataFile.TOMES), "tomes")) {
            WynnTome tome = WynnTome.parse(object);
            if (tome != null && tome.remapId() == null) {
                tomes.add(tome);
            }
        }

        Map<String, List<WynnAspect>> aspects = new LinkedHashMap<>();
        String aspectsJson = contents.get(WynnDataFile.ASPECTS);
        if (aspectsJson != null) {
            JsonElement root = JsonParser.parseString(aspectsJson);
            if (root != null && root.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject().entrySet()) {
                    if (entry.getValue() == null || !entry.getValue().isJsonArray()) {
                        continue;
                    }
                    List<WynnAspect> classAspects = new ArrayList<>();
                    for (JsonElement element : entry.getValue().getAsJsonArray()) {
                        if (element != null && element.isJsonObject()) {
                            WynnAspect aspect = WynnAspect.parse(element.getAsJsonObject(), entry.getKey());
                            if (aspect != null) {
                                classAspects.add(aspect);
                            }
                        }
                    }
                    aspects.put(entry.getKey(), List.copyOf(classAspects));
                }
            }
        }

        Map<String, com.seqwawa.seq.wynnbuilder.atree.AbilityTree> abilityTrees =
                com.seqwawa.seq.wynnbuilder.atree.AbilityTree.parseAll(contents.get(WynnDataFile.ABILITY_TREE));

        Map<String, WynnSet> sets = parseSets(contents.get(WynnDataFile.ITEMS));
        MajorIds majorIds = MajorIds.parse(contents.get(WynnDataFile.MAJOR_IDS));

        return new WynnDataSet(
                version, consts, items, remaps, ingredients, recipes, tomes, aspects, abilityTrees, sets, majorIds);
    }

    /**
     * The "No Helmet" style placeholders, which live only in code upstream rather than in the data.
     *
     * <p>Legacy hashes reference them by ID, so they must exist for those links to resolve.
     */
    private static List<WynnItem> syntheticNoneItems() {
        List<String[]> definitions = List.of(
                new String[] {"armor", "helmet", "No Helmet"},
                new String[] {"armor", "chestplate", "No Chestplate"},
                new String[] {"armor", "leggings", "No Leggings"},
                new String[] {"armor", "boots", "No Boots"},
                new String[] {"accessory", "ring", "No Ring 1"},
                new String[] {"accessory", "ring", "No Ring 2"},
                new String[] {"accessory", "bracelet", "No Bracelet"},
                new String[] {"accessory", "necklace", "No Necklace"},
                new String[] {"weapon", "dagger", "No Weapon"});
        List<WynnItem> none = new ArrayList<>(definitions.size());
        for (int i = 0; i < definitions.size(); i++) {
            String[] definition = definitions.get(i);
            none.add(new WynnItem(
                    LegacyNoneItems.BASE_ID + i,
                    definition[2],
                    definition[2],
                    definition[0],
                    definition[1],
                    WynnItem.Tier.NORMAL,
                    0,
                    null,
                    "NORMAL",
                    0,
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    0,
                    Map.of(),
                    List.of(),
                    null,
                    true,
                    null,
                    null));
        }
        return none;
    }

    /** Where the synthetic empty-slot items live in the ID space. */
    public static final class LegacyNoneItems {
        public static final int BASE_ID = 10000;

        private LegacyNoneItems() {}
    }

    /** Set definitions sit alongside the items in the same file. */
    private static Map<String, WynnSet> parseSets(String json) {
        Map<String, WynnSet> sets = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return sets;
        }
        JsonElement root = JsonParser.parseString(json);
        if (root == null || !root.isJsonObject()) {
            return sets;
        }
        JsonElement setsElement = root.getAsJsonObject().get("sets");
        if (setsElement == null || !setsElement.isJsonObject()) {
            return sets;
        }
        for (Map.Entry<String, JsonElement> entry : setsElement.getAsJsonObject().entrySet()) {
            if (entry.getValue() != null && entry.getValue().isJsonObject()) {
                sets.put(entry.getKey(), WynnSet.parse(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
        }
        return sets;
    }

    private static List<JsonObject> arrayOf(String json, String key) {
        List<JsonObject> objects = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return objects;
        }
        JsonElement root = JsonParser.parseString(json);
        JsonElement array = null;
        if (root != null && root.isJsonArray()) {
            array = root;
        } else if (root != null && root.isJsonObject()) {
            array = root.getAsJsonObject().get(key);
        }
        if (array == null || !array.isJsonArray()) {
            return objects;
        }
        for (JsonElement element : array.getAsJsonArray()) {
            if (element != null && element.isJsonObject()) {
                objects.add(element.getAsJsonObject());
            }
        }
        return objects;
    }
}
