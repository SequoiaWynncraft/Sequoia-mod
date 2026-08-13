package com.seqwawa.seq.wynnbuilder.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Name matching between the game and the data files.
 *
 * <p>These are the differences that silently stop an in-game item resolving: the client renders
 * typographic punctuation and colour codes, and powdered items carry a bracketed suffix.
 */
class ItemNamesTest {

    private static final String ITEMS =
            """
            {"items": [
              {"name": "Honoured Commander's Medal", "displayName": "Honoured Commander's Medal",
               "category": "accessory", "type": "necklace", "tier": "Legendary", "lvl": 100, "id": 1},
              {"name": "Dissonance", "displayName": "Dissonance", "category": "armor", "type": "helmet",
               "tier": "Legendary", "lvl": 95, "id": 2},
              {"name": "Warp", "displayName": "Warp", "category": "accessory", "type": "ring",
               "tier": "Rare", "lvl": 90, "id": 3}
            ]}
            """;

    private static WynnDataSet data() {
        Map<WynnDataFile, String> contents = new EnumMap<>(WynnDataFile.class);
        contents.put(WynnDataFile.ITEMS, ITEMS);
        return WynnDataSet.parse("test", contents);
    }

    @Test
    void typographicApostrophesMatchAsciiOnes() {
        // The client renders U+2019 where the data file has a plain apostrophe.
        assertEquals(
                ItemNames.normalise("Honoured Commander's Medal"),
                ItemNames.normalise("Honoured Commander’s Medal"));
    }

    @Test
    void lookupSucceedsForATypographicApostrophe() {
        WynnDataSet data = data();

        WynnItem item = data.itemByName("Honoured Commander’s Medal");

        assertNotNull(item, "an in-game name with a curly apostrophe must still resolve");
        assertEquals(1, item.id());
    }

    @Test
    void colourCodesAreIgnored() {
        WynnDataSet data = data();

        assertNotNull(data.itemByName("§5Dissonance"));
        assertSame(data.itemByName("Dissonance"), data.itemByName("§5§lDissonance"));
    }

    @Test
    void powderSuffixesAreStripped() {
        WynnDataSet data = data();

        assertNotNull(data.itemByName("Dissonance [✤✤✦]"));
        assertNotNull(data.itemByName("Dissonance [E6 T6]"));
        assertEquals("Dissonance", ItemNames.stripBracketedSuffix("Dissonance [✤✤✦]"));
    }

    @Test
    void repeatedAndNonBreakingWhitespaceCollapses() {
        assertEquals(ItemNames.normalise("Warp"), ItemNames.normalise("  Warp  "));
        assertEquals(ItemNames.normalise("Honoured Commander's Medal"),
                ItemNames.normalise("Honoured  Commander's Medal"));
    }

    @Test
    void dashVariantsAreUnified() {
        assertEquals(ItemNames.normalise("Anima-Infused Helmet"), ItemNames.normalise("Anima–Infused Helmet"));
    }

    @Test
    void caseIsIgnored() {
        assertNotNull(data().itemByName("dISSONANCE"));
    }

    @Test
    void anItemWhoseRealNameEndsInBracketsIsNotTruncatedAway() {
        // Only a trailing bracket group is treated as a powder suffix, and the name still has to be
        // matched afterwards, so nothing is lost for items without one.
        assertEquals("Dissonance", ItemNames.stripBracketedSuffix("Dissonance"));
        assertEquals("", ItemNames.stripBracketedSuffix("[✤✤]"));
    }

    @Test
    void nullAndBlankNamesAreSafe() {
        assertEquals("", ItemNames.normalise(null));
        assertEquals("", ItemNames.normalise("   "));
    }
}
