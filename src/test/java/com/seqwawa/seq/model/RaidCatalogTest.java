package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The meta the backend publishes, and the questions the panel asks of it. */
class RaidCatalogTest {

    /** Sample guild meta for client catalog tests. */
    static RaidCatalog sequoiaCatalog() {
        return TestCatalogs.sequoia();
    }

    @Test
    void anEmptyCatalogIsARealStateRatherThanAFailure() {
        assertTrue(RaidCatalog.empty().isEmpty());
        assertTrue(new RaidCatalog(List.of(), List.of()).builds().isEmpty());
        // Builds without raids is still unusable, so it counts as empty too.
        assertTrue(new RaidCatalog(List.of(RaidBuild.of("HERO", "Hero")), List.of()).isEmpty());
    }

    @Test
    void buildsAndRaidsComeBackInThePositionTheBackendGaveThem() {
        RaidCatalog catalog = new RaidCatalog(
                List.of(
                        new RaidBuild("HADAL", "Hadal", 8),
                        new RaidBuild("ASCENDANCY", "Ascendancy", 1),
                        new RaidBuild("HERO", "Hero", 7)),
                List.of(
                        new RaidType("WTP", "WTP", "The Wartorn Palace", 5, Set.of("ASCENDANCY")),
                        new RaidType("TNA", "TNA", "The Nameless Anomaly", 1, Set.of("ASCENDANCY"))));

        assertEquals(
                List.of("ASCENDANCY", "HERO", "HADAL"),
                catalog.builds().stream().map(RaidBuild::key).toList());
        assertEquals(List.of("TNA", "WTP"), catalog.raids().stream().map(RaidType::key).toList());
    }

    @Test
    void aBuildKnowsEveryRaidItServes() {
        RaidCatalog catalog = sequoiaCatalog();

        assertEquals(
                List.of("TNA", "TCC", "NOL", "WTP"),
                catalog.raidsFor("ASCENDANCY").stream().map(RaidType::key).toList());
        assertEquals(List.of("NOTG"), catalog.raidsFor("HADAL").stream().map(RaidType::key).toList());
        assertEquals("TNA / TCC / NOL / WTP", catalog.raidCoverageLabel("ASCENDANCY"));
        assertEquals("NOTG", catalog.raidCoverageLabel("hero"), "keys match regardless of case");
    }

    @Test
    void oneMatchingBuildIsEnoughToCoverARaid() {
        RaidCatalog catalog = sequoiaCatalog();

        assertTrue(catalog.raid("NOTG").isCoveredBy(Set.of("HADAL")));
        assertFalse(catalog.raid("NOTG").isCoveredBy(Set.of("ASCENDANCY", "RESONANCE")));
        assertFalse(catalog.raid("TNA").isCoveredBy(Set.of()));
        assertFalse(catalog.raid("TNA").isCoveredBy(null));
    }

    @Test
    void matchingBuildKeysReturnsOnlyTheOnesMetaForThatRaid() {
        RaidCatalog catalog = sequoiaCatalog();
        Set<String> owned = Set.of("ASCENDANCY", "HADAL", "RESONANCE");

        assertEquals(Set.of("ASCENDANCY", "RESONANCE"), catalog.raid("TNA").matchingBuildKeys(owned));
        assertEquals(Set.of("HADAL"), catalog.raid("NOTG").matchingBuildKeys(owned));
        assertEquals(Set.of(), catalog.raid("TNA").matchingBuildKeys(Set.of()));
    }

    @Test
    void coveredRaidsFollowsFromTheBuildsOwned() {
        RaidCatalog catalog = sequoiaCatalog();

        assertEquals(
                List.of("TNA", "WTP"),
                catalog.coveredRaids(Set.of("RESONANCE")).stream().map(RaidType::key).toList());
        assertEquals(
                catalog.raids().size(),
                catalog.coveredRaids(Set.of("ASCENDANCY", "HADAL", "HALCYON")).size(),
                "three builds are enough to cover every raid");
    }

    @Test
    void aRaidResolvesFromTheNameWynncraftUsesForClearCounts() {
        RaidCatalog catalog = sequoiaCatalog();

        assertEquals("TNA", catalog.raidByApiName("The Nameless Anomaly").key());
        assertEquals("NOL", catalog.raidByApiName("orphion's nexus of light").key());
        assertNull(catalog.raidByApiName("Some Future Raid"));
        assertNull(catalog.raidByApiName(null));
    }

    @Test
    void aKeyTheCatalogNoLongerListsIsNotAKnownBuild() {
        RaidCatalog catalog = sequoiaCatalog();

        assertFalse(catalog.hasBuild("SOME_REMOVED_BUILD"));
        assertTrue(catalog.hasBuild("hero"));
    }

    @Test
    void labelsFallBackToTheKeyWhenTheCatalogHasNotLoaded() {
        assertEquals("Hero", sequoiaCatalog().labelFor("HERO"));
        assertEquals("HERO", RaidCatalog.empty().labelFor("hero"));
    }

    @Test
    void keysAreOrderedAndJoinedTheWayTheCatalogOrdersThem() {
        RaidCatalog catalog = sequoiaCatalog();
        Set<String> owned = Set.of("HADAL", "ASCENDANCY", "CSPRING");

        assertEquals(List.of("ASCENDANCY", "CSPRING", "HADAL"), catalog.orderKeys(owned));
        assertEquals("Ascendancy, Cspring, Hadal", catalog.joinLabels(owned));
        assertEquals("", catalog.joinLabels(Set.of()));
    }

    @Test
    void aBuildWithNoLabelIsShownReadablyRatherThanShouted() {
        assertEquals("Cspring", new RaidBuild("CSPRING", null, 0).displayName());
        assertEquals("Ascendancy", RaidBuild.of("  ascendancy  ", "  ").displayName());
        assertEquals("ASCENDANCY", RaidBuild.of("ascendancy", null).key());
    }

    @Test
    void anEntryWithoutAKeyIsNotKept() {
        RaidCatalog catalog = new RaidCatalog(
                java.util.Arrays.asList(RaidBuild.of("", "Nameless"), RaidBuild.of("HERO", "Hero"), null),
                List.of(new RaidType("TNA", "TNA", "The Nameless Anomaly", 1, Set.of("HERO"))));

        assertEquals(List.of("HERO"), catalog.builds().stream().map(RaidBuild::key).toList());
    }
}
