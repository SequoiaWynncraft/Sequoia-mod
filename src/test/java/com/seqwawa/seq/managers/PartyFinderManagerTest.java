package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.TickQueue;
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.CreateInviteResponse;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.PartyStatus;
import com.seqwawa.seq.model.ReservedSlot;
import com.seqwawa.seq.model.WynnClassType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PartyFinderManagerTest {

    private static final String LEADER = "00000000-0000-0000-0000-00000000000a";
    private static final String KICK_TARGET = "00000000-0000-0000-0000-00000000000b";
    private static final String OTHER_MEMBER = "00000000-0000-0000-0000-00000000000c";

    /** The game thread, which runs only when a test lets it. */
    private final TickQueue gameThread = new TickQueue(() -> 0L);
    /** The player's server connection, which a test can swap for another. */
    private Object connection = new Object();
    private final List<String> shown = new ArrayList<>();
    private final List<String> sent = new ArrayList<>();
    /** Plays the game for the player: shows what it is told, and sends what it is asked to. */
    private final PartyFinderManager playing = new PartyFinderManager(gameThread) {
        @Override
        public void notify(String message) {
            shown.add(message);
        }

        @Override
        Object gameConnection() {
            return connection;
        }

        @Override
        void sendGameCommand(String command) {
            sent.add(command);
        }
    };

    @AfterEach
    void restoreNameLookups() {
        PartyFinderManager.playerNames = com.seqwawa.seq.utils.PlayerNameCache::resolveAsync;
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

    @Test
    void aReplyThatArrivesAfterAResetIsDroppedWithoutAWord() {
        PartyFinderManager manager = new PartyFinderManager();
        java.util.concurrent.CompletableFuture<Listing> request = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<Listing> guarded = manager.sinceReset(() -> request);

        manager.reset();
        request.complete(listing(7L, "previous-leader", member("previous-leader"), member("previous-account")));

        assertTrue(guarded.isCompletedExceptionally());
        Throwable failure = guarded.handle((value, error) -> error).join();
        assertTrue(PartyFinderManager.isSupersededByReset(failure));
        manager.pushUiError(null);
        assertNull(manager.consumeLatestPartyError(), "a dropped reply leaves no banner for the next account");
    }

    @Test
    void anInviteWhoseSessionEndedWhileItsInviterWasLookedUpIsNeverShown() {
        CompletableFuture<String> name = new CompletableFuture<>();
        PartyFinderManager.playerNames = uuid -> name;
        playing.handlePartyFinderInvite(7L, LEADER, null, null);
        // The session ends, as an account switch does, before the name comes back.
        playing.reset();
        name.complete("Inviter");
        gameThread.runDue();
        assertEquals(List.of(), shown);

        CompletableFuture<String> next = new CompletableFuture<>();
        PartyFinderManager.playerNames = uuid -> next;
        playing.handlePartyFinderInvite(8L, LEADER, null, null);
        next.complete("Inviter");
        gameThread.runDue();
        assertEquals(List.of("Party Finder invite from Inviter."), shown);
    }

    @Test
    void anInviteLookedUpJustBeforeItsSessionEndedIsNeverShown() {
        CompletableFuture<String> name = new CompletableFuture<>();
        PartyFinderManager.playerNames = uuid -> name;
        playing.handlePartyFinderInvite(7L, LEADER, null, null);

        // The name comes back, then the session ends before the game thread shows the invite.
        name.complete("Inviter");
        playing.reset();
        gameThread.runDue();

        assertEquals(List.of(), shown);
    }

    @Test
    void aPartyCommandGoesOnlyToTheSessionAndServerConnectionItWasFor() {
        CompletableFuture<String> name = new CompletableFuture<>();
        PartyFinderManager.playerNames = uuid -> name;
        UUID kicked = UUID.fromString(KICK_TARGET);

        // The backend kicked them; the session ends, as an account switch ends it, while their name is looked up.
        playing.sendGamePartyCommandForUuid(kicked, "party kick ");
        playing.reset();
        name.complete("Kicked");
        gameThread.runDue();
        assertEquals(List.of(), sent, "the next account never sends the previous one's command");

        PartyFinderManager.playerNames = uuid -> CompletableFuture.completedFuture("Kicked");
        playing.sendGamePartyCommandForUuid(kicked, "party kick ");
        connection = new Object();
        gameThread.runDue();
        assertEquals(List.of(), sent, "nor does the same session's player on another server connection");

        playing.sendGamePartyCommandForUuid(kicked, "party kick ");
        gameThread.runDue();
        assertEquals(List.of("party kick Kicked"), sent);
    }

    @Test
    void aReplyWithNoResetInBetweenArrivesAsUsual() {
        PartyFinderManager manager = new PartyFinderManager();
        java.util.concurrent.CompletableFuture<String> request = new java.util.concurrent.CompletableFuture<>();
        java.util.concurrent.CompletableFuture<String> guarded = manager.sinceReset(() -> request);

        request.complete("listing");

        assertEquals("listing", guarded.join());
    }
}
