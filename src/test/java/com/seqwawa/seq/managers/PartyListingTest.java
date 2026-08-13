package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.model.PartyJoinPolicy;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyReservedSlotSource;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.PartyStatus;
import com.seqwawa.seq.model.ReservedSlot;
import com.seqwawa.seq.model.WynnClassType;

class PartyListingTest {

    @Test
    void observedReservationIsRecognizedAsPromotionCandidate() {
        Listing listing = new Listing(
                1L,
                List.of(),
                null,
                UUID.randomUUID().toString(),
                PartyRegion.EU,
                PartyStatus.OPEN,
                null,
                null,
                List.of(),
                List.of(new ReservedSlot(null, "NotReyz", null, Instant.now())),
                Instant.now());

        assertTrue(PartyFinderManager.hasObservedReservation(listing, "notreyz"));
    }

    @Test
    void deserializesBackendOtherRole() {
        assertEquals(PartyRole.OTHER, new Gson().fromJson("\"OTHER\"", PartyRole.class));
    }

    @Test
    void deserializesBackendReservedSlotSchema() {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(
                        Instant.class,
                        (JsonDeserializer<Instant>)
                                (json, type, context) -> Instant.parse(json.getAsString()))
                .create();

        Listing listing = gson.fromJson(
                """
                {
                  "id": 42,
                  "reservedSlots": [{
                    "playerUUID": "00000000-0000-0000-0000-000000000042",
                    "observedUsername": "ScannedPlayer",
                    "role": "OTHER",
                    "createdAt": "2026-08-05T10:00:00Z"
                  }]
                }
                """,
                Listing.class);

        ReservedSlot reservedSlot = listing.reservedSlots().getFirst();
        assertEquals("00000000-0000-0000-0000-000000000042", reservedSlot.playerUUID());
        assertEquals("ScannedPlayer", reservedSlot.observedUsername());
        assertEquals(PartyRole.OTHER, reservedSlot.role());
        assertEquals(Instant.parse("2026-08-05T10:00:00Z"), reservedSlot.createdAt());
    }

    @Test
    void scannedReservedSlotDisplaysObservedUsername() {
        PartyMember member = PartyMember.reserved(
                new ReservedSlot(null, "ScannedPlayer", PartyRole.OTHER, Instant.EPOCH));

        assertEquals("ScannedPlayer", member.displayName());
        assertTrue(member.isObserved);
        assertFalse(member.isReserved);
    }

    @Test
    void wynnSyncSlotDisplaysAsObservedRosterMember() {
        PartyMember member = PartyMember.reserved(new ReservedSlot(
                null,
                "ScannedPlayer",
                null,
                Instant.EPOCH,
                PartyReservedSlotSource.WYNN_SYNC));

        assertEquals("ScannedPlayer", member.displayName());
        assertTrue(member.isObserved);
        assertFalse(member.isReserved);
    }

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
    }

    @Test
    void displayLabelUsesWorldBeforeRegion() {
        PartyListing listing = new PartyListing(new Listing(
                1L,
                List.of(new Activity(1L, "TNA", 4)),
                null,
                "leader",
                PartyRegion.EU,
                "EU21",
                PartyStatus.OPEN,
                null,
                null,
                List.of(new Member("leader", PartyRole.DPS, WynnClassType.MAGE, Instant.EPOCH)),
                List.of(),
                Instant.EPOCH));

        assertEquals("EU21 · The Nameless Anomaly", listing.displayLabel());
        assertEquals("EU21", listing.tags.get(1));
        assertEquals(PartyJoinPolicy.OPEN, listing.joinPolicy);
    }

    @Test
    void inviteOnlyListingDoesNotExposePublicJoinAction() {
        PartyListing listing = new PartyListing(new Listing(
                2L,
                List.of(new Activity(1L, "TNA", 4)),
                null,
                "leader",
                PartyRegion.EU,
                "EU21",
                PartyStatus.OPEN,
                null,
                null,
                List.of(new Member("leader", PartyRole.DPS, WynnClassType.MAGE, Instant.EPOCH)),
                List.of(),
                Instant.EPOCH,
                PartyJoinPolicy.INVITE_ONLY));

        assertEquals(PartyJoinPolicy.INVITE_ONLY, listing.joinPolicy);
        assertFalse(listing.isJoinable());
    }

    @Test
    void missingJoinPolicyFromLegacyBackendDefaultsToOpen() {
        Listing legacyListing = new Listing(
                3L,
                List.of(new Activity(1L, "TNA", 4)),
                null,
                "leader",
                PartyRegion.EU,
                "EU21",
                PartyStatus.OPEN,
                null,
                null,
                List.of(new Member("leader", PartyRole.DPS, WynnClassType.MAGE, Instant.EPOCH)),
                List.of(),
                Instant.EPOCH,
                null);

        PartyListing listing = new PartyListing(legacyListing);

        assertEquals(PartyJoinPolicy.OPEN, legacyListing.resolvedJoinPolicy());
        assertEquals(PartyJoinPolicy.OPEN, listing.joinPolicy);
    }

    @Test
    void displaysBackendOtherRoleWithoutFallingBackToDps() {
        PartyListing listing = new PartyListing(new Listing(
                4L,
                List.of(new Activity(1L, "TNA", 4)),
                null,
                "leader",
                PartyRegion.EU,
                "EU21",
                PartyStatus.OPEN,
                null,
                null,
                List.of(new Member("leader", PartyRole.OTHER, WynnClassType.MAGE, Instant.EPOCH)),
                List.of(),
                Instant.EPOCH));

        assertEquals("Other", listing.members.getFirst().role);
    }
}
