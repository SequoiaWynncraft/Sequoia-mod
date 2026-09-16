package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;
import com.seqwawa.seq.model.WynnClassType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WynnWeaponClassIndexTest {
    @Test
    void readsTheClassOutOfWeaponModelPaths() {
        assertEquals(
                WynnClassType.SHAMAN,
                WynnWeaponClassIndex.classForModelPath("item/wynn/weapon/shaman/relik_basic_gold"));
        assertEquals(
                WynnClassType.WARRIOR,
                WynnWeaponClassIndex.classForModelPath("minecraft:item/wynn/weapon/warrior/spear_fire_a"));
    }

    @Test
    void readsTheClassOutOfWeaponSkinPaths() {
        assertEquals(
                WynnClassType.SHAMAN,
                WynnWeaponClassIndex.classForModelPath("item/wynn/skin/relik/acolyte_scepter"));
        assertEquals(
                WynnClassType.ASSASSIN,
                WynnWeaponClassIndex.classForModelPath("item/wynn/skin/dagger/candy_dagger"));
    }

    @Test
    void ignoresModelPathsThatAreNotWeapons() {
        assertNull(WynnWeaponClassIndex.classForModelPath("item/wynn/gui/trade_market/filter/relik"));
        assertNull(WynnWeaponClassIndex.classForModelPath("item/wynn/skin/hat/relik_crown"));
        assertNull(WynnWeaponClassIndex.classForModelPath("item/wynn/gui/layer/durability/1"));
        assertNull(WynnWeaponClassIndex.classForModelPath(null));
    }

    @Test
    void indexesWeaponThresholdsOutOfAnItemDefinition() {
        Map<Integer, WynnClassType> index = new HashMap<>();

        WynnWeaponClassIndex.indexModels(index, JsonParser.parseString("""
                {"model":{"entries":[
                  {"model":{"model":"item/wynn/gui/layer/durability/1","type":"minecraft:model"},"threshold":1},
                  {"model":{"model":"item/wynn/weapon/shaman/relik_basic_gold","type":"minecraft:model"},
                   "threshold":3649},
                  {"model":{"model":"item/wynn/skin/relik/bonfire_relik","type":"minecraft:model"},
                   "threshold":3652},
                  {"model":{"model":"item/wynn/weapon/mage/wand_basic_gold","type":"minecraft:model"},
                   "threshold":4001}],
                 "index":0,"property":"minecraft:custom_model_data","type":"minecraft:range_dispatch"}}
                """));

        assertEquals(
                Map.of(
                        3649, WynnClassType.SHAMAN,
                        3652, WynnClassType.SHAMAN,
                        4001, WynnClassType.MAGE),
                index);
    }

    @Test
    void foldsAnimatedModelsBackOntoTheirBaseModel() {
        assertEquals(3649, WynnWeaponClassIndex.normalizeAnimatedModel(3649f));
        assertEquals(3649, WynnWeaponClassIndex.normalizeAnimatedModel(65536f + 3649f));
    }

    @Test
    void readsNoClassBeforeTheResourcePackIsIndexed() {
        WynnWeaponClassIndex.reset();

        assertNull(WynnWeaponClassIndex.classOfModels(List.of(3649f)));
    }
}
