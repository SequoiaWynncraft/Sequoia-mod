package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PartyListingTest {

    @Test
    void mapsWartornPalaceAcrossDisplayBackendAndAssetNames() {
        assertEquals("The Wartorn Palace", PartyListing.backendNameToDisplayName("TWP"));
        assertEquals("twp", PartyListing.displayNameToAssetKey("The Wartorn Palace"));
        assertEquals("twp", PartyListing.displayNameToAssetKey("tHe WaRtOrN pAlAcE"));
        assertEquals("TWP", PartyListing.displayNameToBackendName("The Wartorn Palace"));
    }

    @Test
    void exposesWartornPalaceInActivityLists() {
        assertEquals(
                List.of(
                        "NOTG",
                        "TNA",
                        "TCC",
                        "NOL",
                        "TWP",
                        "ANNI"),
                PartyListing.activityCommandAliases());
        assertEquals(
                List.of(
                        "Nest of the Grootslangs",
                        "The Nameless Anomaly",
                        "The Canyon Colossus",
                        "Nexus of Light",
                        "The Wartorn Palace",
                        "Prelude to Annihilation"),
                PartyListing.activityDisplayNames());
    }
}
