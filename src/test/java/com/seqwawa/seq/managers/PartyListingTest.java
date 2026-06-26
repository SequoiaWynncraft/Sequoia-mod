package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.model.PartyMode;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.PartyStatus;
import com.seqwawa.seq.model.WynnClassType;

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

    @Test
    void displayLabelUsesWorldBeforeRegion() {
        PartyListing listing = new PartyListing(new Listing(
                1L,
                List.of(new Activity(1L, "TNA", 4)),
                null,
                "leader",
                PartyMode.CHILL,
                false,
                PartyRegion.EU,
                "EU21",
                PartyStatus.OPEN,
                null,
                null,
                List.of(new Member("leader", PartyRole.DPS, WynnClassType.MAGE, Instant.EPOCH)),
                List.of(),
                Instant.EPOCH));

        assertEquals("EU21 · Chill · The Nameless Anomaly", listing.displayLabel());
        assertEquals("EU21", listing.tags.get(1));
    }
}
