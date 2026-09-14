package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RaidProfilesResponseTest {

    private static RaidProfilesResponse.Catalog catalog() {
        return new RaidProfilesResponse.Catalog(
                List.of(
                        new RaidProfilesResponse.Build("ASCENDANCY", "Ascendancy", 1),
                        new RaidProfilesResponse.Build("HADAL", "Hadal", 8),
                        new RaidProfilesResponse.Build("HERO", "Hero", 7)),
                List.of(
                        new RaidProfilesResponse.Raid(
                                "TNA", "TNA", "The Nameless Anomaly", 1, List.of("ASCENDANCY")),
                        new RaidProfilesResponse.Raid(
                                "NOTG", "NOTG", "Nest of the Grootslangs", 3, List.of("HADAL", "HERO"))));
    }

    /** A stable, dash-shaped uuid per name, the way the backend sends them. */
    private static String uuidFor(String username) {
        return String.format("%08x-0000-0000-0000-000000000000", Math.abs(username.hashCode()));
    }

    private static String keyFor(String username) {
        return RaidProfilesResponse.normalizeUuid(uuidFor(username));
    }

    private static RaidProfilesResponse.Profile profile(
            String username, List<String> builds, boolean auras, String region, String status, Instant updatedAt) {
        return new RaidProfilesResponse.Profile(
                new RaidProfilesResponse.MinecraftIdentity(uuidFor(username), username),
                builds,
                auras,
                region,
                status,
                updatedAt);
    }

    @Test
    void theCatalogBecomesTheOneTheScreensRead() {
        RaidCatalog parsed = new RaidProfilesResponse(1, catalog(), List.of()).toCatalog();

        assertEquals(
                List.of("ASCENDANCY", "HERO", "HADAL"),
                parsed.builds().stream().map(RaidBuild::key).toList(),
                "ordered by the position the backend gave, not by the order in the array");
        assertEquals(List.of("TNA", "NOTG"), parsed.raids().stream().map(RaidType::key).toList());
        assertEquals("The Nameless Anomaly", parsed.raid("TNA").apiName());
        assertEquals(Set.of("HADAL", "HERO"), parsed.raid("NOTG").buildKeys());
    }

    @Test
    void aResponseWithNoCatalogYieldsAnEmptyOne() {
        assertTrue(new RaidProfilesResponse(1, null, List.of()).toCatalog().isEmpty());
    }

    @Test
    void convertsAWholeResponseKeyedByLowercaseUsername() {
        RaidProfilesResponse response = new RaidProfilesResponse(
                1,
                catalog(),
                List.of(profile(
                        "GaztheCat",
                        List.of("ASCENDANCY"),
                        true,
                        "EU",
                        "down for tna",
                        Instant.parse("2026-09-04T18:12:00Z"))));

        Map<String, RaidTeamProfile> domain = response.toDomain();

        assertEquals(Set.of(keyFor("GaztheCat")), domain.keySet(), "keyed by uuid, never by name");
        RaidTeamProfile profile = domain.get(keyFor("GaztheCat"));
        assertEquals(Set.of("ASCENDANCY"), profile.buildKeys());
        assertTrue(profile.canBringAuras());
        assertEquals(PartyRegion.EU, profile.region());
        assertEquals("down for tna", profile.status());
        assertEquals(Instant.parse("2026-09-04T18:12:00Z").toEpochMilli(), profile.updatedAtEpochMs());
        assertTrue(profile.isComplete());
    }

    @Test
    void buildKeysTheCatalogDoesNotKnowAreSkipped() {
        Map<String, RaidTeamProfile> domain = new RaidProfilesResponse(
                        1,
                        catalog(),
                        List.of(profile("alpha", List.of("HADAL", "SOME_FUTURE_BUILD"), false, null, null, null)))
                .toDomain();

        assertEquals(Set.of("HADAL"), domain.get(keyFor("alpha")).buildKeys());
    }

    @Test
    void withoutACatalogTheKeysAreKeptAsSentRatherThanAllDropped() {
        // The catalog has not arrived yet, so dropping every key would make each
        // profile look empty instead of unloaded.
        Map<String, RaidTeamProfile> domain = new RaidProfilesResponse(
                        1, null, List.of(profile("alpha", List.of("HADAL", "ANYTHING"), false, null, null, null)))
                .toDomain();

        assertEquals(Set.of("HADAL", "ANYTHING"), domain.get(keyFor("alpha")).buildKeys());
    }

    @Test
    void anUnknownRegionReadsAsNoRegion() {
        Map<String, RaidTeamProfile> domain = new RaidProfilesResponse(
                        1,
                        catalog(),
                        List.of(
                                profile("alpha", List.of(), false, "OCE", null, null),
                                profile("bravo", List.of(), false, "  na  ", null, null)))
                .toDomain();

        assertNull(domain.get(keyFor("alpha")).region());
        assertEquals(PartyRegion.NA, domain.get(keyFor("bravo")).region());
    }

    @Test
    void anEmptyBuildListStillCountsAsASharedProfile() {
        RaidTeamProfile profile = new RaidProfilesResponse(
                        1,
                        catalog(),
                        List.of(profile("alpha", List.of(), false, null, null, Instant.parse("2026-01-01T00:00:00Z"))))
                .toDomain()
                .get(keyFor("alpha"));

        assertTrue(profile.buildKeys().isEmpty());
        assertTrue(
                profile.isComplete(),
                "someone who deliberately ticked nothing has still answered, and must not read as absent");
    }

    @Test
    void aMissingTimestampStillMarksTheProfileComplete() {
        RaidTeamProfile profile = new RaidProfilesResponse(
                        1, catalog(), List.of(profile("alpha", List.of("HERO"), false, null, null, null)))
                .toDomain()
                .get(keyFor("alpha"));

        assertTrue(profile.isComplete());
        assertTrue(profile.updatedAtEpochMs() > 0L);
    }

    @Test
    void theNameIndexMapsBackToTheSameUuid() {
        RaidProfilesResponse response = new RaidProfilesResponse(
                1, catalog(), List.of(profile("GaztheCat", List.of("ASCENDANCY"), true, "EU", null, null)));

        assertEquals(keyFor("GaztheCat"), response.uuidByUsername().get("gazthecat"));
        assertTrue(response.uuidByUsername().containsKey("gazthecat"));
    }

    @Test
    void entriesWithNoUuidAreDroppedBecauseTheyCannotBeKeyed() {
        RaidProfilesResponse response = new RaidProfilesResponse(
                1,
                catalog(),
                List.of(new RaidProfilesResponse.Profile(
                        new RaidProfilesResponse.MinecraftIdentity(null, "Nameless"),
                        List.of("HERO"),
                        false,
                        null,
                        null,
                        null)));

        assertTrue(response.toDomain().isEmpty());
        assertTrue(response.uuidByUsername().isEmpty());
    }

    @Test
    void uuidsCompareWithoutDashesAndWithoutCase() {
        assertEquals(
                RaidProfilesResponse.normalizeUuid("66EFB975-31B4-499E-9B46-A34980EDD8EE"),
                RaidProfilesResponse.normalizeUuid("66efb97531b4499e9b46a34980edd8ee"));
        assertNull(RaidProfilesResponse.normalizeUuid("  "));
        assertNull(RaidProfilesResponse.normalizeUuid(null));
    }

    @Test
    void entriesWithNoUsernameStillLandBecauseTheUuidIsWhatKeysThem() {
        RaidProfilesResponse response = new RaidProfilesResponse(
                1,
                catalog(),
                java.util.Arrays.asList(
                        new RaidProfilesResponse.Profile(
                                new RaidProfilesResponse.MinecraftIdentity(
                                        "aaaaaaaa-0000-0000-0000-000000000000", null),
                                List.of("HERO"),
                                false,
                                null,
                                null,
                                null),
                        new RaidProfilesResponse.Profile(null, List.of("HERO"), false, null, null, null),
                        null));

        assertEquals(1, response.toDomain().size(), "a nameless entry is still keyable");
        assertTrue(response.uuidByUsername().isEmpty(), "but it contributes nothing to the name index");
    }

    @Test
    void anEmptyResponseIsHandled() {
        assertTrue(new RaidProfilesResponse(1, catalog(), null).toDomain().isEmpty());
        assertTrue(new RaidProfilesResponse(1, catalog(), List.of()).profiles().isEmpty());
        assertFalse(new RaidProfilesResponse(1, catalog(), List.of()).toDomain().containsKey("anyone"));
    }
}
