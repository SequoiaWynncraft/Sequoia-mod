package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.RaidBuild;
import com.seqwawa.seq.model.RaidCatalog;
import com.seqwawa.seq.model.RaidProfilesResponse;
import com.seqwawa.seq.model.RaidTeamProfile;
import com.seqwawa.seq.model.RaidType;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Holds the client to the payload the reference backend in {@code backend/}
 * actually produces.
 * <p>
 * The fixture is a real captured response from that service, not a hand-written
 * one, so the two cannot drift apart quietly: change the backend's output shape
 * and this test is what tells you the client stopped understanding it.
 */
class RaidProfilesBackendContractTest {

    private static RaidProfilesResponse response() throws Exception {
        try (InputStream stream = RaidProfilesBackendContractTest.class.getResourceAsStream(
                "/raid-profiles-backend-response.json")) {
            if (stream == null) {
                throw new IllegalStateException("captured backend response is missing");
            }
            String raw = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return RaidProfileStore.parseProfilesResponse(raw).orElseThrow();
        }
    }

    @Test
    void theClientParsesWhatTheBackendSends() throws Exception {
        RaidProfilesResponse response = response();

        assertEquals(RaidProfilesResponse.CURRENT_SCHEMA_VERSION, response.schemaVersion());
        assertEquals(2, response.profiles().size());
    }

    @Test
    void theGuildMetaArrivesWithTheProfiles() throws Exception {
        RaidCatalog catalog = response().toCatalog();

        assertEquals(
                List.of("ASCENDANCY", "CSPRING", "RESONANCE", "HALCYON", "CATACLYSM", "TCRACK", "HERO", "HADAL"),
                catalog.builds().stream().map(RaidBuild::key).toList());
        assertEquals(
                List.of("TNA", "TCC", "NOTG", "NOL", "WTP"),
                catalog.raids().stream().map(RaidType::key).toList());
        assertEquals("Cspring", catalog.labelFor("CSPRING"));
        assertEquals(
                Set.of("ASCENDANCY", "CSPRING", "RESONANCE"), catalog.raid("TNA").buildKeys());
    }

    @Test
    void theRaidNamesMatchWhatWynncraftKeysClearCountsBy() throws Exception {
        RaidCatalog catalog = response().toCatalog();

        // These strings are what appears under globalData.currentGuildRaids.list.
        assertEquals("TNA", catalog.raidByApiName("The Nameless Anomaly").key());
        assertEquals("TCC", catalog.raidByApiName("The Canyon Colossus").key());
        assertEquals("NOTG", catalog.raidByApiName("Nest of the Grootslangs").key());
        assertEquals("NOL", catalog.raidByApiName("Orphion's Nexus of Light").key());
        assertEquals("WTP", catalog.raidByApiName("The Wartorn Palace").key());
    }

    @Test
    void everyFieldSurvivesTheRoundTrip() throws Exception {
        Map<String, RaidTeamProfile> profiles = response().toDomain();

        RaidTeamProfile arc = profiles.get("66efb97531b4499e9b46a34980edd8ee");
        assertEquals(Set.of("ASCENDANCY", "CSPRING", "RESONANCE"), arc.buildKeys());
        assertTrue(arc.canBringAuras());
        assertEquals(PartyRegion.EU, arc.region());
        assertEquals("I am the goat", arc.status());
        assertTrue(arc.isComplete());
    }

    @Test
    void theOptionalFieldsTheBackendSendsAsNullAreHandled() throws Exception {
        Map<String, RaidTeamProfile> profiles = response().toDomain();

        // The backend sends explicit null for region and status rather than
        // omitting them, and an empty array for a member who ticked nothing.
        RaidTeamProfile blousy = profiles.get("10000000000000000000000000000002");
        assertTrue(blousy.buildKeys().isEmpty());
        assertFalse(blousy.canBringAuras());
        assertNull(blousy.region());
        assertFalse(blousy.hasStatus());
        assertTrue(blousy.isComplete(), "ticking nothing is still a shared profile");
    }

    @Test
    void everyProfileCarriesAUuidSoNothingIsMatchedByName() throws Exception {
        RaidProfilesResponse response = response();

        response.profiles()
                .forEach(profile -> assertNotNull(
                        RaidProfilesResponse.identityKey(profile),
                        profile.minecraft().username() + " arrived without a uuid"));
        assertEquals(response.profiles().size(), response.toDomain().size());
    }

    @Test
    void buildsComeBackInCatalogOrderWhateverOrderTheyWereSentIn() throws Exception {
        RaidProfilesResponse response = response();
        List<String> catalogOrder =
                response.toCatalog().builds().stream().map(RaidBuild::key).toList();

        response.profiles().forEach(profile -> {
            List<String> declared = profile.builds();
            List<String> expected = catalogOrder.stream().filter(declared::contains).toList();
            assertEquals(expected, declared, profile.minecraft().username() + " is not in catalog order");
        });
    }

    @Test
    void theNameIndexPointsAtTheSameProfileAsTheUuid() throws Exception {
        RaidProfilesResponse response = response();

        assertEquals(
                "66efb97531b4499e9b46a34980edd8ee", response.uuidByUsername().get("arcleretour"));
        assertEquals(
                response.toDomain().get("66efb97531b4499e9b46a34980edd8ee"),
                response.toDomain().get(response.uuidByUsername().get("arcleretour")));
    }

    @Test
    void everyDeclaredBuildKeyExistsInTheCatalogTheSameResponseCarries() throws Exception {
        RaidProfilesResponse response = response();
        RaidCatalog catalog = response.toCatalog();

        response.toDomain(catalog).forEach((username, profile) -> profile.buildKeys()
                .forEach(key -> assertTrue(
                        catalog.hasBuild(key), username + " declares " + key + ", which the catalog does not list")));
    }

    @Test
    void everyRaidPointsAtBuildsTheCatalogAlsoLists() throws Exception {
        RaidCatalog catalog = response().toCatalog();

        for (RaidType raid : catalog.raids()) {
            assertFalse(raid.buildKeys().isEmpty(), raid.key() + " has no meta build");
            raid.buildKeys()
                    .forEach(key -> assertTrue(
                            catalog.hasBuild(key), raid.key() + " points at " + key + ", which is not a build"));
        }
    }
}
