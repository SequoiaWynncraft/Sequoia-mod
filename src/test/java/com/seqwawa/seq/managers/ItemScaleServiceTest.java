package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.seqwawa.seq.model.ItemScale;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemScaleServiceTest {

    private static final Type PAYLOAD_TYPE = new TypeToken<Map<String, Map<String, Double>>>() {}.getType();

    @Test
    void weightsAreIndexedByLowercasedItemName() {
        Map<String, ItemScale> parsed =
                ItemScaleService.parseScales(Map.of("Ascendancy", Map.of("rawHealth", 45.0)));

        ItemScale scale = parsed.get("ascendancy");
        assertEquals(Map.of("rawHealth", 45.0), scale.weights());
        assertEquals("Ascendancy", scale.itemName());
    }

    @Test
    void lookupToleratesCasingAndPaddingFromTheItemName() {
        Map<String, ItemScale> parsed =
                ItemScaleService.parseScales(Map.of("  Crusade   Sabatons ", Map.of("rawHealth", 85.0)));

        assertNotNull(parsed.get("crusade sabatons"));
        assertEquals("Crusade   Sabatons", parsed.get("crusade sabatons").itemName());
    }

    @Test
    void zeroWeightsAreDroppedAndItemsLeftWithNoneAreSkipped() {
        Map<String, ItemScale> parsed = ItemScaleService.parseScales(Map.of(
                "Resonance", Map.of("manaRegen", 35.0, "sprint", 0.0),
                "Boreal", Map.of("airDefence", 0.0)));

        assertEquals(Map.of("manaRegen", 35.0), parsed.get("resonance").weights());
        assertNull(parsed.get("boreal"));
        assertEquals(1, parsed.size());
    }

    @Test
    void negativeWeightsSurviveParsing() {
        Map<String, ItemScale> parsed = ItemScaleService.parseScales(Map.of("Fantasia", Map.of("1stSpellCost", -7.0)));

        assertEquals(Map.of("1stSpellCost", -7.0), parsed.get("fantasia").weights());
    }

    @Test
    void malformedEntriesAreSkippedRatherThanFailingTheWholePayload() {
        Map<String, Double> holes = new HashMap<>();
        holes.put("rawHealth", null);
        holes.put("  ", 10.0);
        holes.put("walkSpeed", 20.0);

        Map<String, Map<String, Double>> payload = new HashMap<>();
        payload.put("Hero", holes);
        payload.put("Warp", null);
        payload.put("   ", Map.of("rawHealth", 5.0));

        Map<String, ItemScale> parsed = ItemScaleService.parseScales(payload);

        assertEquals(Map.of("walkSpeed", 20.0), parsed.get("hero").weights());
        assertEquals(1, parsed.size());
    }

    @Test
    void aMissingPayloadIsRejectedSoTheCachedScalesAreKept() {
        assertThrows(IllegalArgumentException.class, () -> ItemScaleService.parseScales(null));
    }

    @Test
    void anEmptyPayloadParsesToAnEmptyIndex() {
        assertTrue(ItemScaleService.parseScales(Map.of()).isEmpty());
    }

    /** Guards the payload in docs/ against the parser that consumes it from the endpoint. */
    @Test
    void endpointPayloadParsesAndCoversEveryMythic() throws IOException {
        Path payloadFile = Path.of("docs", "item-scales.json");
        assertTrue(Files.isRegularFile(payloadFile), "docs/item-scales.json is missing");

        Map<String, Map<String, Double>> payload =
                new Gson().fromJson(Files.readString(payloadFile), PAYLOAD_TYPE);
        assertEquals(66, payload.size(), "expected every mythic weapon and armour piece");

        Map<String, ItemScale> parsed = ItemScaleService.parseScales(payload);
        assertEquals(66, parsed.size(), "every listed mythic should carry at least one non-zero weight");
        assertEquals(45.0, parsed.get("ascendancy").weights().get("rawHealth"));
    }
}
