package com.seqwawa.seq.wynnbuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.wynnbuilder.codec.BuildCodec;
import com.seqwawa.seq.wynnbuilder.data.BuildEquipment;
import com.seqwawa.seq.wynnbuilder.data.CraftedItem;
import com.seqwawa.seq.wynnbuilder.data.EncodingConsts;
import com.seqwawa.seq.wynnbuilder.data.EquipmentSlot;
import com.seqwawa.seq.wynnbuilder.data.Powder;
import com.seqwawa.seq.wynnbuilder.data.WynnBuild;
import com.seqwawa.seq.wynnbuilder.data.WynnDataFile;
import com.seqwawa.seq.wynnbuilder.data.WynnDataSet;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** End-to-end import: pasted text in, working build out. */
class BuildLinkImporterTest {

    private static final String ITEMS =
            """
            {"items": [
              {"name": "Idol", "displayName": "Idol", "category": "weapon", "type": "bow",
               "tier": "Mythic", "lvl": 103, "id": 100, "atkSpd": "SLOW", "slots": 3,
               "nDam": "50-90", "eDam": "0-0", "tDam": "120-160", "wDam": "0-0", "fDam": "0-0", "aDam": "0-0"},
              {"name": "Aftershock", "displayName": "Aftershock", "category": "armor", "type": "chestplate",
               "tier": "Mythic", "lvl": 100, "id": 200, "hp": 3500, "slots": 4},
              {"name": "Dissonance", "displayName": "Dissonance", "category": "armor", "type": "helmet",
               "tier": "Legendary", "lvl": 95, "id": 300, "hp": 2000, "slots": 3},
              {"name": "Diamond Ring", "displayName": "Diamond Ring", "category": "accessory", "type": "ring",
               "tier": "Rare", "lvl": 90, "id": 400, "sdPct": 8}
            ]}
            """;

    private static final String RECIPES =
            """
            {"recipes": [
              {"type": "BOOTS", "skill": "TAILORING", "id": 43, "name": "Boots-1-3",
               "healthOrDamage": {"minimum": 9, "maximum": 11},
               "durability": {"minimum": 175, "maximum": 182},
               "lvl": {"minimum": 1, "maximum": 3},
               "materials": [{"item": "A", "amount": 1}, {"item": "B", "amount": 2}]},
              {"type": "BOW", "skill": "WOODWORKING", "id": 500, "name": "Bow-100-103",
               "healthOrDamage": {"minimum": 200, "maximum": 260},
               "durability": {"minimum": 900, "maximum": 1100},
               "lvl": {"minimum": 100, "maximum": 103},
               "materials": [{"item": "A", "amount": 2}, {"item": "B", "amount": 1}]}
            ]}
            """;

    private static WynnDataSet data() {
        Map<WynnDataFile, String> contents = new EnumMap<>(WynnDataFile.class);
        contents.put(WynnDataFile.ITEMS, ITEMS);
        contents.put(WynnDataFile.RECIPES, RECIPES);
        return WynnDataSet.parse("2.2.3.0", contents);
    }

    private static String encode(WynnBuild build, WynnDataSet data) {
        return BuildCodec.encode(build, EncodingConsts.DEFAULT, data::recipeType);
    }

    private static WynnBuild newBuild() {
        return new WynnBuild(33, EncodingConsts.DEFAULT.maxLevel(),
                EncodingConsts.DEFAULT.tomeCount(), EncodingConsts.DEFAULT.aspectCount());
    }

    @Test
    void importsAFullBuilderUrl() {
        WynnDataSet data = data();
        WynnBuild original = newBuild();
        original.setEquipment(EquipmentSlot.WEAPON, new BuildEquipment.Normal(100));
        original.setEquipment(EquipmentSlot.CHESTPLATE, new BuildEquipment.Normal(200));
        original.setLevel(103);
        String url = "https://wynnbuilder.github.io/builder/#" + encode(original, data);

        BuildLinkImporter.Result result = BuildLinkImporter.importBuild(url, EncodingConsts.DEFAULT, data);

        assertTrue(result.success(), result.message());
        assertEquals(new BuildEquipment.Normal(100), result.build().equipment(EquipmentSlot.WEAPON));
        assertEquals(new BuildEquipment.Normal(200), result.build().equipment(EquipmentSlot.CHESTPLATE));
        assertEquals(103, result.build().level());
    }

    @Test
    void importsABareHashWithoutAUrl() {
        WynnDataSet data = data();
        WynnBuild original = newBuild();
        original.setEquipment(EquipmentSlot.HELMET, new BuildEquipment.Normal(300));

        BuildLinkImporter.Result result =
                BuildLinkImporter.importBuild(encode(original, data), EncodingConsts.DEFAULT, data);

        assertTrue(result.success(), result.message());
        assertEquals(new BuildEquipment.Normal(300), result.build().equipment(EquipmentSlot.HELMET));
    }

    @Test
    void importsAUrlWithSurroundingWhitespace() {
        WynnDataSet data = data();
        WynnBuild original = newBuild();
        original.setEquipment(EquipmentSlot.RING1, new BuildEquipment.Normal(400));
        String url = "  https://wynnbuilder.github.io/builder/#" + encode(original, data) + "  ";

        BuildLinkImporter.Result result = BuildLinkImporter.importBuild(url, EncodingConsts.DEFAULT, data);

        assertTrue(result.success(), result.message());
        assertEquals(new BuildEquipment.Normal(400), result.build().equipment(EquipmentSlot.RING1));
    }

    @Test
    void importsBuildsContainingCraftedItems() {
        // The case that shows up as "CR-..." names on the website.
        WynnDataSet data = data();
        WynnBuild original = newBuild();
        CraftedItem craft = new CraftedItem(500, List.of(10, 20, 4000, 4000, 4000, 4000), 3, 2,
                CraftedItem.AttackSpeed.FAST);
        original.setEquipment(EquipmentSlot.WEAPON, new BuildEquipment.Crafted(craft));
        original.setEquipment(EquipmentSlot.HELMET, new BuildEquipment.Normal(300));
        String url = "https://wynnbuilder.github.io/builder/#" + encode(original, data);

        BuildLinkImporter.Result result = BuildLinkImporter.importBuild(url, EncodingConsts.DEFAULT, data);

        assertTrue(result.success(), result.message());
        BuildEquipment.Crafted imported =
                assertInstanceOf(BuildEquipment.Crafted.class, result.build().equipment(EquipmentSlot.WEAPON));
        assertEquals(craft, imported.craft());
        assertEquals(new BuildEquipment.Normal(300), result.build().equipment(EquipmentSlot.HELMET));
    }

    @Test
    void importsPowdersAndSkillPoints() {
        WynnDataSet data = data();
        WynnBuild original = newBuild();
        original.setEquipment(EquipmentSlot.WEAPON, new BuildEquipment.Normal(100));
        original.setPowders(EquipmentSlot.WEAPON, List.of(
                new Powder(Powder.PowderElement.THUNDER, 6),
                new Powder(Powder.PowderElement.THUNDER, 6),
                new Powder(Powder.PowderElement.WATER, 6)));
        original.setAssignedSkillPoint(1, 120);
        original.setAssignedSkillPoint(2, -30);

        BuildLinkImporter.Result result =
                BuildLinkImporter.importBuild(encode(original, data), EncodingConsts.DEFAULT, data);

        assertTrue(result.success(), result.message());
        assertEquals(3, result.build().powders(EquipmentSlot.WEAPON).size());
        assertEquals(120, result.build().assignedSkillPoint(1));
        assertEquals(-30, result.build().assignedSkillPoint(2));
    }

    @Test
    void inspectReportsTheDataVersionSoConstantsCanBeFetched() {
        WynnDataSet data = data();
        WynnBuild original = newBuild();

        BuildLinkImporter.LinkTarget target = BuildLinkImporter.inspect(encode(original, data));

        assertNotNull(target);
        assertFalse(target.legacy());
        assertEquals(33, target.versionIndex());
    }

    @Test
    void legacyLinksAreRecognisedAndImported() {
        // A version 0 hash from the upstream regression corpus: nine three-character item IDs.
        String url = "https://wynnbuilder.github.io/#0_0K30oY09X2SJ2SK2SL2SM2SN0QQ";

        BuildLinkImporter.LinkTarget target = BuildLinkImporter.inspect(url);
        assertNotNull(target);
        assertTrue(target.legacy());

        BuildLinkImporter.Result result = BuildLinkImporter.importBuild(url, EncodingConsts.DEFAULT, data());
        assertTrue(result.success(), result.message());
        assertEquals(106, result.build().level(), "pre-v3 links default to level 106");
    }

    @Test
    void unknownItemIdsDoNotAbortTheImport() {
        // A link referencing items this data set has never heard of should still open, with the
        // recognised slots filled, rather than failing outright.
        WynnDataSet data = data();
        WynnBuild original = newBuild();
        original.setEquipment(EquipmentSlot.HELMET, new BuildEquipment.Normal(4321));
        original.setEquipment(EquipmentSlot.WEAPON, new BuildEquipment.Normal(100));

        BuildLinkImporter.Result result =
                BuildLinkImporter.importBuild(encode(original, data), EncodingConsts.DEFAULT, data);

        assertTrue(result.success(), result.message());
        assertEquals(new BuildEquipment.Normal(4321), result.build().equipment(EquipmentSlot.HELMET));
        assertEquals(new BuildEquipment.Normal(100), result.build().equipment(EquipmentSlot.WEAPON));
    }

    @Test
    void garbageIsRejectedWithAClearMessage() {
        WynnDataSet data = data();

        assertNull(BuildLinkImporter.inspect("hello world"));
        assertNull(BuildLinkImporter.inspect(""));
        assertNull(BuildLinkImporter.inspect("https://example.com/nothing-here"));

        BuildLinkImporter.Result result = BuildLinkImporter.importBuild("hello world", EncodingConsts.DEFAULT, data);
        assertFalse(result.success());
        assertTrue(result.message().contains("WynnBuilder"));
    }

    @Test
    void importWithoutDataFailsGracefully() {
        BuildLinkImporter.Result result = BuildLinkImporter.importBuild("CA000", EncodingConsts.DEFAULT, null);
        assertFalse(result.success());
        assertTrue(result.message().contains("loading"));
    }
}
