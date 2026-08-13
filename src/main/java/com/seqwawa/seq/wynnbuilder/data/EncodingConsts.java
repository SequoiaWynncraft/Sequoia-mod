package com.seqwawa.seq.wynnbuilder.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Per-version bit widths and enum values for the V12 encoding, loaded from
 * {@code data/<version>/encoding_consts.json}.
 *
 * <p>These are generated upstream from the data of each version, so a build must be decoded with
 * the constants of the version named in its header rather than the newest ones. Values fall back to
 * the current version's defaults when a field is missing, which keeps older files readable.
 */
public record EncodingConsts(
        int equipmentKindBits,
        int equipmentKindNormal,
        int equipmentKindCrafted,
        int equipmentKindCustom,
        int equipmentCount,
        int powderableEquipmentCount,
        int powderElements,
        int powderTiers,
        int powderIdBits,
        int powderWrapperBits,
        int itemIdBits,
        int tomeIdBits,
        int tomeCount,
        int aspectIdBits,
        int aspectCount,
        int maxSkillPointBits,
        int skillPointTypes,
        int levelBits,
        int maxLevel) {

    /** Constants for data version 2.2.3.0, used when a file cannot be read. */
    public static final EncodingConsts DEFAULT = new EncodingConsts(
            2, 0, 1, 2,
            9, 5,
            5, 7, 6, 2,
            13, 8, 14, 5, 5,
            12, 5,
            7, 121);

    // Flag values. These are stable across every published version, so they are not parsed.
    public static final int NO_POWDERS = 0;
    public static final int HAS_POWDERS = 1;
    public static final int POWDER_REPEAT = 0;
    public static final int POWDER_NO_REPEAT = 1;
    public static final int POWDER_REPEAT_TIER = 0;
    public static final int POWDER_CHANGE_POWDER = 1;
    public static final int POWDER_NEW_POWDER = 0;
    public static final int POWDER_NEW_ITEM = 1;
    public static final int NO_TOMES = 0;
    public static final int HAS_TOMES = 1;
    public static final int SLOT_UNUSED = 0;
    public static final int SLOT_USED = 1;
    public static final int NO_ASPECTS = 0;
    public static final int HAS_ASPECTS = 1;
    public static final int SP_ASSIGNED = 0;
    public static final int SP_AUTOMATIC = 1;
    public static final int SP_ELEMENT_UNASSIGNED = 0;
    public static final int SP_ELEMENT_ASSIGNED = 1;
    public static final int LEVEL_MAX = 0;
    public static final int LEVEL_OTHER = 1;

    public static EncodingConsts parse(String json) {
        JsonElement root = JsonParser.parseString(json);
        if (root == null || !root.isJsonObject()) {
            return DEFAULT;
        }
        JsonObject object = root.getAsJsonObject();
        JsonObject equipmentKind = optionalObject(object, "EQUIPMENT_KIND");
        return new EncodingConsts(
                nested(equipmentKind, "BITLEN", DEFAULT.equipmentKindBits),
                nested(equipmentKind, "NORMAL", DEFAULT.equipmentKindNormal),
                nested(equipmentKind, "CRAFTED", DEFAULT.equipmentKindCrafted),
                nested(equipmentKind, "CUSTOM", DEFAULT.equipmentKindCustom),
                integer(object, "EQUIPMENT_NUM", DEFAULT.equipmentCount),
                integer(object, "POWDERABLE_EQUIPMENT_NUM", DEFAULT.powderableEquipmentCount),
                arraySize(object, "POWDER_ELEMENTS", DEFAULT.powderElements),
                integer(object, "POWDER_TIERS", DEFAULT.powderTiers),
                integer(object, "POWDER_ID_BITLEN", DEFAULT.powderIdBits),
                integer(object, "POWDER_WRAPPER_BITLEN", DEFAULT.powderWrapperBits),
                integer(object, "ITEM_ID_BITLEN", DEFAULT.itemIdBits),
                integer(object, "TOME_ID_BITLEN", DEFAULT.tomeIdBits),
                integer(object, "TOME_NUM", DEFAULT.tomeCount),
                integer(object, "ASPECT_ID_BITLEN", DEFAULT.aspectIdBits),
                integer(object, "NUM_ASPECTS", DEFAULT.aspectCount),
                integer(object, "MAX_SP_BITLEN", DEFAULT.maxSkillPointBits),
                integer(object, "SP_TYPES", DEFAULT.skillPointTypes),
                integer(object, "LEVEL_BITLEN", DEFAULT.levelBits),
                integer(object, "MAX_LEVEL", DEFAULT.maxLevel));
    }

    private static JsonObject optionalObject(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static int nested(JsonObject object, String key, int fallback) {
        return object == null ? fallback : integer(object, key, fallback);
    }

    private static int integer(JsonObject object, String key, int fallback) {
        JsonElement element = object == null ? null : object.get(key);
        if (element == null || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int arraySize(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray().size() : fallback;
    }
}
