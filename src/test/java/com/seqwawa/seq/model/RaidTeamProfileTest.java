package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RaidTeamProfileTest {

    private static RaidCatalog catalog() {
        return RaidCatalogTest.sequoiaCatalog();
    }

    @Test
    void anEmptyProfileIsNotComplete() {
        RaidTeamProfile profile = RaidTeamProfile.empty();

        assertFalse(profile.isComplete());
        assertTrue(profile.buildKeys().isEmpty());
        assertFalse(profile.canBringAuras());
        assertNull(profile.region());
        assertFalse(profile.hasStatus());
    }

    @Test
    void savingIsWhatMarksAProfileComplete() {
        RaidTeamProfile saved = RaidTeamProfile.empty().savedAt(1_700_000_000_000L);

        assertTrue(saved.isComplete(), "an intentionally empty profile still counts as filled in");
        assertEquals(1_700_000_000_000L, saved.updatedAtEpochMs());
    }

    @Test
    void togglingABuildAddsThenRemovesIt() {
        RaidTeamProfile profile = RaidTeamProfile.empty().withBuildToggled("HADAL");
        assertTrue(profile.hasBuild("hadal"), "keys match regardless of case");

        profile = profile.withBuildToggled("hadal");
        assertFalse(profile.hasBuild("HADAL"));

        assertTrue(RaidTeamProfile.empty().withBuildToggled(null).buildKeys().isEmpty());
        assertTrue(RaidTeamProfile.empty().withBuildToggled("  ").buildKeys().isEmpty());
    }

    @Test
    void buildKeysAreStoredUppercase() {
        assertEquals(
                Set.of("HERO", "HADAL"),
                RaidTeamProfile.empty().withBuildKeys(Set.of(" hero ", "Hadal")).buildKeys());
    }

    @Test
    void coveredRaidsFollowsFromTheBuildsOwned() {
        RaidTeamProfile profile = RaidTeamProfile.empty().withBuildKeys(Set.of("RESONANCE"));

        assertEquals(
                List.of("TNA", "WTP"),
                profile.coveredRaids(catalog()).stream().map(RaidType::key).toList());
        assertTrue(profile.coversRaid(catalog().raid("TNA")));
        assertFalse(profile.coversRaid(catalog().raid("NOTG")));
        assertFalse(profile.coversRaid(null));
    }

    @Test
    void aProfileWithoutACatalogCoversNothingRatherThanFailing() {
        RaidTeamProfile profile = RaidTeamProfile.empty().withBuildKeys(Set.of("RESONANCE"));

        assertEquals(List.of(), profile.coveredRaids(null));
        assertEquals(List.of(), profile.coveredRaids(RaidCatalog.empty()));
    }

    @Test
    void statusIsTrimmedBlankedAndCapped() {
        assertNull(RaidTeamProfile.empty().withStatus("   ").status());
        assertEquals("down for tna", RaidTeamProfile.empty().withStatus("  down for tna  ").status());

        String tooLong = "x".repeat(RaidTeamProfile.MAX_STATUS_LENGTH + 40);
        assertEquals(
                RaidTeamProfile.MAX_STATUS_LENGTH,
                RaidTeamProfile.empty().withStatus(tooLong).status().length());
    }

    @Test
    void withersKeepTheOtherFieldsIntact() {
        RaidTeamProfile profile = RaidTeamProfile.empty()
                .withBuildKeys(Set.of("CSPRING"))
                .withCanBringAuras(true)
                .withRegion(PartyRegion.EU)
                .withStatus("free after 9")
                .savedAt(42L);

        RaidTeamProfile toggled = profile.withBuildToggled("HERO");

        assertTrue(toggled.canBringAuras());
        assertEquals(PartyRegion.EU, toggled.region());
        assertEquals("free after 9", toggled.status());
        assertEquals(42L, toggled.updatedAtEpochMs());
        assertEquals(Set.of("CSPRING", "HERO"), toggled.buildKeys());
    }
}
