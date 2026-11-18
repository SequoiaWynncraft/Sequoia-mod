package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.CreateInviteResponse;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.PartyStatus;
import com.seqwawa.seq.model.ReservedSlot;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.ConnectionManager;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PartyFinderManagerTest {

    private static final String LEADER = "00000000-0000-0000-0000-00000000000a";
    private static final String KICK_TARGET = "00000000-0000-0000-0000-00000000000b";
    private static final String OTHER_MEMBER = "00000000-0000-0000-0000-00000000000c";

    @AfterEach
    void unregisterWebsocketHandlers() {
        // Constructing the manager registers its websocket handlers on ConnectionManager.
        ConnectionManager.onPartyFinderUpdate(null);
        ConnectionManager.onPartyFinderInvite(null);
        ConnectionManager.onPartyFinderStaleWarning(null);
    }

    @Test
    void resetForgetsThePreviousAccountsPartyInvitesAndCards() {
        PartyFinderManager manager = new PartyFinderManager();
        // Names that are not UUIDs keep the cards from looking players up through Minecraft.
        Listing joined = listing(7L, "previous-leader", member("previous-leader"), member("previous-account"));
        manager.applyJoinedListingState(joined);
        manager.applyCreatedInvite(
                new CreateInviteResponse("invite-token", joined), 7L, UUID.fromString(KICK_TARGET), "Invitee");
        manager.pushUiError("Unable to join party");
        assertTrue(manager.isInParty());
        assertEquals(1, manager.getParties().size());
        assertTrue(manager.hasIssuedInvite(7L, "Invitee"));

        manager.reset();

        assertFalse(manager.isInParty(), "no class updates go out for a party the next account never joined");
        assertNull(manager.getCurrentListing());
        assertTrue(manager.getListings().isEmpty());
        assertTrue(manager.getParties().isEmpty());
        assertFalse(manager.hasIssuedInvite(7L, "Invitee"));
        assertNull(manager.consumeLatestPartyError());
    }

    @Test
    void aMemberActionFindsItsPlayerByIdAfterTheListIsReordered() {
        Listing mine = listing(10L, LEADER, member(LEADER), member(OTHER_MEMBER), member(KICK_TARGET));
        Listing newer = listing(11L, "00000000-0000-0000-0000-0000000000ff", member(OTHER_MEMBER));

        // A CREATED update moved a newer party to the top after the button was drawn.
        UUID target = PartyFinderManager.memberActionTarget(List.of(newer, mine), 10L, KICK_TARGET);

        assertEquals(UUID.fromString(KICK_TARGET), target);
    }

    @Test
    void aMemberActionDoesNothingOnceItsPlayerOrPartyIsGone() {
        Listing mine = listing(10L, LEADER, member(LEADER), member(OTHER_MEMBER));
        Listing elsewhere = listing(11L, "00000000-0000-0000-0000-0000000000ff", member(KICK_TARGET));

        assertNull(PartyFinderManager.memberActionTarget(List.of(mine, elsewhere), 10L, KICK_TARGET),
                "the player left, and is not kicked from the party they moved to");
        assertNull(PartyFinderManager.memberActionTarget(List.of(elsewhere), 10L, OTHER_MEMBER),
                "the party was disbanded");
        assertNull(PartyFinderManager.memberActionTarget(List.of(mine), 10L, "<RESERVED>"));
    }

    @Test
    void aMemberActionMatchesUuidsWrittenWithOrWithoutDashes() {
        Listing mine = listing(10L, LEADER, member(LEADER), member(KICK_TARGET.replace("-", "")));

        assertEquals(
                UUID.fromString(KICK_TARGET), PartyFinderManager.memberActionTarget(List.of(mine), 10L, KICK_TARGET));
    }

    @Test
    void joinGoesToTheListingTheButtonNamedOnlyWhileItIsListedAndThePlayerIsFree() {
        Listing first = listing(20L, LEADER, member(LEADER));
        Listing second = listing(21L, OTHER_MEMBER, member(OTHER_MEMBER));

        assertSame(second, PartyFinderManager.listingToJoin(List.of(second, first), 21L, null));
        assertNull(PartyFinderManager.listingToJoin(List.of(first), 21L, null), "the listing has gone");
        assertNull(PartyFinderManager.listingToJoin(List.of(first, second), 21L, first), "already in a party");
    }

    @Test
    void joinDoesNothingOnceTheListingClosedSinceItWasDrawn() {
        Listing closed = new Listing(
                22L,
                List.of(new Activity(1L, "Nest of the Grootslangs", 4)),
                null,
                LEADER,
                PartyRegion.NA,
                PartyStatus.CLOSED,
                null,
                null,
                List.of(member(LEADER)),
                List.<ReservedSlot>of(),
                Instant.EPOCH);

        assertNull(PartyFinderManager.listingToJoin(List.of(closed), 22L, null));
    }

    private static Member member(String playerUuid) {
        return new Member(playerUuid, PartyRole.DPS, WynnClassType.WARRIOR, Instant.EPOCH);
    }

    private static Listing listing(long id, String leaderUuid, Member... members) {
        return new Listing(
                id,
                List.of(new Activity(1L, "Nest of the Grootslangs", 4)),
                null,
                leaderUuid,
                PartyRegion.NA,
                PartyStatus.OPEN,
                null,
                null,
                List.of(members),
                List.<ReservedSlot>of(),
                Instant.EPOCH);
    }
}
