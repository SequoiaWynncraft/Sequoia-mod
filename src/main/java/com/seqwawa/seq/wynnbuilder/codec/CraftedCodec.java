package com.seqwawa.seq.wynnbuilder.codec;

import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * Section B of the encoding spec: crafted items.
 *
 * <p>Crafted hashes are deliberately independent of the data versioning used by builds, so the
 * field widths here are fixed constants rather than values read from {@code encoding_consts.json}.
 * The same routine serves the standalone crafter link and the crafted blobs embedded inside a build.
 */
public final class CraftedCodec {
    /** Encoder version written into new hashes. */
    public static final int ENCODING_VERSION = 2;

    private static final int VERSION_BITS = 7;
    private static final int INGREDIENT_ID_BITS = 12;
    private static final int RECIPE_ID_BITS = 12;
    private static final int MATERIAL_TIER_BITS = 3;
    private static final int MATERIAL_COUNT = 2;
    private static final int ATTACK_SPEED_BITS = 4;

    private CraftedCodec() {}

    /** Encodes a craft into its own Base64 hash. */
    public static String encodeToBase64(CraftedItem craft, boolean weaponRecipe) {
        BitVector vector = new BitVector();
        encode(vector, craft, weaponRecipe);
        return vector.toBase64();
    }

    /**
     * Appends a craft to an existing vector, padding it to a 6-bit boundary.
     *
     * <p>The padding rule is copied from upstream exactly: it always writes between one and six zero
     * bits, never zero. An aligned vector still gets six bits of padding, and a decoder that skips a
     * "smarter" amount desynchronises on every already-aligned craft.
     */
    public static void encode(BitVector vector, CraftedItem craft, boolean weaponRecipe) {
        int start = vector.length();
        vector.append(0, 1); // binary, not legacy
        vector.append(ENCODING_VERSION, VERSION_BITS);
        for (int ingredientId : craft.ingredientIds()) {
            vector.append(ingredientId, INGREDIENT_ID_BITS);
        }
        vector.append(craft.recipeId(), RECIPE_ID_BITS);
        vector.append(craft.materialTier1() - 1, MATERIAL_TIER_BITS);
        vector.append(craft.materialTier2() - 1, MATERIAL_TIER_BITS);
        if (weaponRecipe) {
            vector.append(craft.attackSpeed().ordinal(), ATTACK_SPEED_BITS);
        }
        vector.append(0, 6 - ((vector.length() - start) % 6));
    }

    /**
     * Reads a craft from the current cursor position, consuming its padding.
     *
     * @param recipeTypeLookup resolves a recipe ID to its type, needed to know whether an attack
     *     speed field follows; may return {@code null} for unknown recipes
     * @return the decoded craft, or {@code null} when the blob is in the legacy format
     */
    public static CraftedItem decode(BitVector vector, IntFunction<String> recipeTypeLookup) {
        int start = vector.position();
        if (vector.readBit()) {
            return null; // legacy crafted hash, handled by the caller
        }
        vector.read(VERSION_BITS);

        List<Integer> ingredients = new ArrayList<>(CraftedItem.INGREDIENT_SLOTS);
        for (int i = 0; i < CraftedItem.INGREDIENT_SLOTS; i++) {
            ingredients.add(vector.readInt(INGREDIENT_ID_BITS));
        }
        int recipeId = vector.readInt(RECIPE_ID_BITS);

        int[] materialTiers = new int[MATERIAL_COUNT];
        for (int i = 0; i < MATERIAL_COUNT; i++) {
            materialTiers[i] = vector.readInt(MATERIAL_TIER_BITS) + 1;
        }

        CraftedItem.AttackSpeed attackSpeed = CraftedItem.AttackSpeed.SLOW;
        String recipeType = recipeTypeLookup == null ? null : recipeTypeLookup.apply(recipeId);
        if (CraftedItem.isWeaponType(recipeType)) {
            attackSpeed = CraftedItem.AttackSpeed.byIndex(vector.readInt(ATTACK_SPEED_BITS));
        }

        vector.seek(vector.position() + (6 - ((vector.position() - start) % 6)));
        return new CraftedItem(recipeId, ingredients, materialTiers[0], materialTiers[1], attackSpeed);
    }

    /** Decodes a standalone crafted hash, as used by the crafter page. */
    public static CraftedItem decodeBase64(String hash, IntFunction<String> recipeTypeLookup) {
        return decode(BitVector.fromBase64(hash), recipeTypeLookup);
    }

    /**
     * Legacy crafted hashes (the {@code CR-1...} form) are fixed-width Base64 fields.
     *
     * @return the decoded craft, or {@code null} when the text is not a legacy craft
     */
    public static CraftedItem decodeLegacy(String hash) {
        String text = hash;
        if (text.startsWith("CR-")) {
            text = text.substring(3);
        }
        if (text.length() < 16 || text.charAt(0) != '1') {
            return null;
        }
        text = text.substring(1);
        List<Integer> ingredients = new ArrayList<>(CraftedItem.INGREDIENT_SLOTS);
        for (int i = 0; i < CraftedItem.INGREDIENT_SLOTS; i++) {
            ingredients.add(WynnBase64.toInt(text.substring(2 * i, 2 * i + 2)));
        }
        int recipeId = WynnBase64.toInt(text.substring(12, 14));
        int tierNumber = WynnBase64.toInt(text.substring(14, 15));
        // Legacy packs both tiers as a + 3b.
        int tier1 = tierNumber % 3 == 0 ? 3 : tierNumber % 3;
        int tier2 = (tierNumber - tier1) / 3 + 1;
        CraftedItem.AttackSpeed attackSpeed = text.length() >= 16
                ? CraftedItem.AttackSpeed.byIndex(WynnBase64.toInt(text.substring(15, 16)))
                : CraftedItem.AttackSpeed.SLOW;
        return new CraftedItem(recipeId, ingredients, tier1, tier2, attackSpeed);
    }
}
