package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.seqwawa.seq.model.ItemScale;
import com.seqwawa.seq.model.ItemScalesResponse;
import com.seqwawa.seq.model.ItemScalesResponse.ScaleDefinition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemScaleServiceTest {

    @Test
    void weightsAreIndexedByLowercasedItemName() {
        ItemScalesResponse response = response(new ScaleDefinition("Ascendancy", "weapon", Map.of("rawHealth", 35.0)));

        Map<String, ItemScale> parsed = ItemScaleService.parseScales(response);

        ItemScale scale = parsed.get("ascendancy");
        assertEquals(Map.of("rawHealth", 35.0), scale.weights());
        assertEquals("Ascendancy", scale.itemName());
    }

    @Test
    void lookupToleratesCasingAndPaddingFromTheItemName() {
        ItemScalesResponse response =
                response(new ScaleDefinition("  Crusade   Sabatons ", "armour", Map.of("rawHealth", 85.0)));

        Map<String, ItemScale> parsed = ItemScaleService.parseScales(response);

        assertNotNull(parsed.get("crusade sabatons"));
        assertEquals("Crusade   Sabatons", parsed.get("crusade sabatons").itemName());
    }

    @Test
    void zeroWeightsAreDroppedAndItemsLeftWithNoneAreSkipped() {
        ItemScalesResponse response = response(
                new ScaleDefinition("Idol", "weapon", Map.of("walkSpeed", 12.5, "reflection", 0.0)),
                new ScaleDefinition("Boreal", "armour", Map.of("airDefence", 0.0)));

        Map<String, ItemScale> parsed = ItemScaleService.parseScales(response);

        assertEquals(Map.of("walkSpeed", 12.5), parsed.get("idol").weights());
        assertNull(parsed.get("boreal"));
        assertEquals(1, parsed.size());
    }

    @Test
    void negativeWeightsSurviveParsing() {
        ItemScalesResponse response =
                response(new ScaleDefinition("Fantasia", "weapon", Map.of("1stSpellCost", -7.0)));

        assertEquals(Map.of("1stSpellCost", -7.0), ItemScaleService.parseScales(response).get("fantasia").weights());
    }

    @Test
    void malformedEntriesAreSkippedRatherThanFailingTheWholePayload() {
        Map<String, Double> holes = new HashMap<>();
        holes.put("rawHealth", null);
        holes.put("  ", 10.0);
        holes.put("walkSpeed", 20.0);

        ItemScalesResponse response = response(
                null,
                new ScaleDefinition(null, "weapon", Map.of("rawHealth", 5.0)),
                new ScaleDefinition("Hero", "weapon", holes),
                new ScaleDefinition("Warp", "weapon", null));

        Map<String, ItemScale> parsed = ItemScaleService.parseScales(response);

        assertEquals(Map.of("walkSpeed", 20.0), parsed.get("hero").weights());
        assertEquals(1, parsed.size());
    }

    @Test
    void unknownSchemaVersionsAreRejectedSoTheCachedPayloadIsKept() {
        assertThrows(IllegalArgumentException.class, () -> ItemScaleService.parseScales(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ItemScaleService.parseScales(new ItemScalesResponse(2, List.of())));
    }

    @Test
    void missingScaleListParsesToAnEmptyIndex() {
        assertTrue(ItemScaleService.parseScales(new ItemScalesResponse(1, null)).isEmpty());
    }

    /** Guards the payload in docs/ against the parser that will consume it from the endpoint. */
    @Test
    void bundledEndpointPayloadParsesAndCoversEveryMythic() throws IOException {
        Path payload = Path.of("docs", "item-scales.json");
        assertTrue(Files.isRegularFile(payload), "docs/item-scales.json is missing");

        ItemScalesResponse response = new Gson().fromJson(Files.readString(payload), ItemScalesResponse.class);
        assertEquals(66, response.scales().size(), "expected every mythic weapon and armour piece");

        Map<String, ItemScale> parsed = ItemScaleService.parseScales(response);
        assertEquals(66, parsed.size(), "every listed mythic should carry at least one non-zero weight");

        ItemScale ascendancy = parsed.get("ascendancy");
        assertEquals(35.0, ascendancy.weights().get("rawHealth"));
        assertEquals(35.0, ascendancy.weights().get("rawSpellDamage"));
    }

    private static ItemScalesResponse response(ScaleDefinition... definitions) {
        return new ItemScalesResponse(1, Arrays.asList(definitions));
    }
}
