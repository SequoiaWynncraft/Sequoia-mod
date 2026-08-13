package com.seqwawa.seq.wynnbuilder.codec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.seqwawa.seq.wynnbuilder.codec.LegacyBuildCodec.LegacyBuild;
import com.seqwawa.seq.wynnbuilder.data.Powder;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Validates the legacy decoder against the upstream regression corpus.
 *
 * <p>These 160 hashes were produced by WynnBuilder itself, so unlike a round-trip test they are real
 * ground truth for the field layout: a wrong offset in any section desynchronises everything after
 * it and shows up as a truncation error or an absurd value.
 */
class LegacyBuildCodecTest {

    private static List<String> corpus() {
        try (InputStream stream =
                LegacyBuildCodecTest.class.getResourceAsStream("/wynnbuilder/legacy-build-links.json")) {
            assertNotNull(stream, "legacy link corpus resource is missing");
            JsonArray array = JsonParser.parseReader(
                            new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonArray();
            List<String> hashes = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                hashes.add(element.getAsString());
            }
            return hashes;
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read the legacy link corpus", exception);
        }
    }

    @Test
    void corpusIsPresentAndRecognisedAsLegacy() {
        List<String> hashes = corpus();
        assertEquals(160, hashes.size());
        for (String hash : hashes) {
            assertTrue(LegacyBuildCodec.isLegacy(hash), "should be recognised as legacy: " + hash);
            assertFalse(BuildCodec.isBinary(hash), "legacy hash must not be taken for binary: " + hash);
        }
    }

    @Test
    void everyUpstreamHashDecodesWithPlausibleContents() {
        List<String> failures = new ArrayList<>();

        for (String hash : corpus()) {
            try {
                LegacyBuild build = LegacyBuildCodec.decode(hash);

                if (build.equipment().size() != 9) {
                    failures.add(hash + " -> " + build.equipment().size() + " equipment slots");
                    continue;
                }
                // A desynchronised stream shows up here first: levels and powder tiers go out of range.
                if (build.level() < 1 || build.level() > 200) {
                    failures.add(hash + " -> implausible level " + build.level());
                }
                for (List<Powder> slotPowders : build.powders()) {
                    for (Powder powder : slotPowders) {
                        if (powder.tier() < 1 || powder.tier() > 7) {
                            failures.add(hash + " -> implausible powder tier " + powder.tier());
                        }
                    }
                }
                for (Integer skillPoint : build.skillPoints()) {
                    if (skillPoint != null && (skillPoint < -2048 || skillPoint > 2047)) {
                        failures.add(hash + " -> implausible skill points " + skillPoint);
                    }
                }
            } catch (RuntimeException exception) {
                failures.add(hash + " -> " + exception);
            }
        }

        assertTrue(failures.isEmpty(), "Failed to decode:\n" + String.join("\n", failures));
    }

    @Test
    void decodesTheDocumentedVersionZeroExample() {
        // From the upstream regression list; nine three-character item IDs and nothing else.
        LegacyBuild build = LegacyBuildCodec.decode("0_0K30oY09X2SJ2SK2SL2SM2SN0QQ");

        assertEquals(0, build.version());
        assertEquals(9, build.equipment().size());
        assertEquals(1283, build.equipment().get(0).itemId());
        // 2SJ..2SN are the synthetic "no accessory" entries.
        assertTrue(build.equipment().get(3).isNone(), "boots slot should be empty");
        assertEquals(106, build.level(), "pre-v3 hashes carry no level and default to 106");
    }

    @Test
    void versionElevenCarriesTomesAspectsAndAbilityTree() {
        List<String> elevens = corpus().stream().filter(hash -> hash.startsWith("11_")).toList();
        assertFalse(elevens.isEmpty(), "corpus should contain version 11 hashes");

        int withTree = 0;
        for (String hash : elevens) {
            LegacyBuild build = LegacyBuildCodec.decode(hash);
            assertEquals(11, build.version());
            assertEquals(14, build.tomeIds().size(), "v11 has fourteen tome slots");
            assertEquals(5, build.aspects().size());
            // The tree is whatever is left, so its size proves the earlier offsets landed correctly:
            // a mis-parsed section leaves a ragged tail instead of a whole number of characters.
            assertEquals(0, build.abilityTreeBits().length() % 6, "tree must consume whole characters: " + hash);
            // Real builds leave at most ten characters of tree here; more means we under-consumed.
            assertTrue(build.abilityTreeBits().length() <= 10 * 6, "unexpected trailing data in " + hash);
            if (build.abilityTreeBits().length() > 0) {
                withTree++;
            }
        }
        // A build with no abilities selected is legitimate, but most of the corpus has a tree.
        assertTrue(withTree >= elevens.size() - 2, "expected most v11 builds to carry a tree");
    }

    @Test
    void versionNineHasEightTomeSlots() {
        List<String> nines = corpus().stream().filter(hash -> hash.startsWith("9_")).toList();
        assertFalse(nines.isEmpty(), "corpus should contain version 9 hashes");

        for (String hash : nines) {
            LegacyBuild build = LegacyBuildCodec.decode(hash);
            assertEquals(8, build.tomeIds().size());
            assertTrue(build.aspects().isEmpty(), "aspects only exist from v11");
        }
    }

    @Test
    void binaryHashesAreNotMistakenForLegacy() {
        assertFalse(LegacyBuildCodec.isLegacy("CA00000"));
        assertTrue(BuildCodec.isBinary("CA00000"));
    }
}
