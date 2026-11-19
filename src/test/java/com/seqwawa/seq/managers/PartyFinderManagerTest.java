package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.TickQueue;
import com.seqwawa.seq.managers.PartyFinderManager.CommandResult;
import com.seqwawa.seq.managers.PartyGameCommands.InviteAllResult;
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.CreateInviteResponse;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.model.PartyJoinPolicy;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.PartyStatus;
import com.seqwawa.seq.model.ReservedSlot;
import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import com.seqwawa.seq.session.EndReason;
import com.seqwawa.seq.session.SessionManager;
import com.seqwawa.seq.session.TestSessions;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PartyFinderManagerTest {

    private static final String LEADER = "00000000-0000-0000-0000-00000000000a";
    private static final String KICK_TARGET = "00000000-0000-0000-0000-00000000000b";
    private static final String OTHER_MEMBER = "00000000-0000-0000-0000-00000000000c";
    private static final String THIRD_MEMBER = "00000000-0000-0000-0000-00000000000d";
    private static final String SESSION_ENDED = "Your account changed or you signed out before this finished.";

    private long clockMs;
    /** The game thread, which runs only when a test lets it. */
    private final TickQueue gameThread = new TickQueue(() -> clockMs);
    /** The player's server connection, which a test can swap for another, as reconnecting does. */
    private volatile Object connection = new Object();
    private final List<String> shown = new CopyOnWriteArrayList<>();
    private final List<String> sent = new CopyOnWriteArrayList<>();
    private Function<String, CompletableFuture<String>> names = uuid -> CompletableFuture.completedFuture(null);
    private Function<String, CompletableFuture<String>> uuids = username -> CompletableFuture.completedFuture(null);
    /** Plays the game as the leader: shows what it is told, and sends what it is asked to. */
    private final PartyFinderPlayer playing = new PartyFinderPlayer() {
        @Override
        String uuid() {
            return LEADER;
        }

        @Override
        String name() {
            return "Leader";
        }

        @Override
        Object connection() {
            return connection;
        }

        @Override
        CompletableFuture<String> nameOf(String uuid) {
            return names.apply(uuid);
        }

        @Override
        String knownName(String uuid) {
            return names.apply(uuid).getNow("Loading...");
        }

        @Override
        CompletableFuture<String> uuidOf(String username) {
            return uuids.apply(username);
        }

        @Override
        void sendCommand(String command) {
            sent.add(command);
        }

        @Override
        void notify(String message) {
            shown.add(message);
        }

        @Override
        void show(Component... lines) {
            shown.add(Arrays.stream(lines).map(Component::getString).collect(Collectors.joining("\n")));
        }
    };
    private final SessionManager sessions = TestSessions.signedIn(
            new StoredAuthSession("leader-token", Instant.now().plusSeconds(86_400), LEADER, "Leader"), gameThread);
    private FakePartyFinderBackend backend;

    @AfterEach
    void stopBackend() {
        if (backend != null) {
            backend.close();
        }
    }

    /** The current session's party finder on {@link #backend}, ended with its session, as the game makes it. */
    private PartyFinderManager sessionPartyFinder() throws Exception {
        backend = new FakePartyFinderBackend();
        var session = sessions.current();
        PartyFinderManager partyFinder = new PartyFinderManager(backend.client(session), gameThread, playing);
        session.whenEnded("partyFinder", partyFinder::reset);
        return partyFinder;
    }

    /** A party finder that never reaches a backend. */
    private PartyFinderManager offline() {
        return new PartyFinderManager(null, gameThread, playing);
    }

    @Test
    void resetForgetsThePreviousAccountsPartyInvitesAndCards() {
        PartyFinderManager manager = offline();
        // Names that are not UUIDs keep the cards from looking players up through Minecraft.
        Listing joined = listing(7L, "previous-leader", member("previous-leader"), member("previous-account"));
        manager.applyJoined(joined);
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
        UUID target = PartyFinderActions.memberActionTarget(List.of(newer, mine), 10L, KICK_TARGET);

        assertEquals(UUID.fromString(KICK_TARGET), target);
    }

    @Test
    void aMemberActionDoesNothingOnceItsPlayerOrPartyIsGone() {
        Listing mine = listing(10L, LEADER, member(LEADER), member(OTHER_MEMBER));
        Listing elsewhere = listing(11L, "00000000-0000-0000-0000-0000000000ff", member(KICK_TARGET));

        assertNull(PartyFinderActions.memberActionTarget(List.of(mine, elsewhere), 10L, KICK_TARGET),
                "the player left, and is not kicked from the party they moved to");
        assertNull(PartyFinderActions.memberActionTarget(List.of(elsewhere), 10L, OTHER_MEMBER),
                "the party was disbanded");
        assertNull(PartyFinderActions.memberActionTarget(List.of(mine), 10L, "<RESERVED>"));
    }

    @Test
    void aMemberActionMatchesUuidsWrittenWithOrWithoutDashes() {
        Listing mine = listing(10L, LEADER, member(LEADER), member(KICK_TARGET.replace("-", "")));

        assertEquals(
                UUID.fromString(KICK_TARGET), PartyFinderActions.memberActionTarget(List.of(mine), 10L, KICK_TARGET));
    }

    @Test
    void joinGoesToTheListingTheButtonNamedOnlyWhileItIsListedAndThePlayerIsFree() {
        Listing first = listing(20L, LEADER, member(LEADER));
        Listing second = listing(21L, OTHER_MEMBER, member(OTHER_MEMBER));

        assertSame(second, PartyFinderActions.listingToJoin(List.of(second, first), 21L, null));
        assertNull(PartyFinderActions.listingToJoin(List.of(first), 21L, null), "the listing has gone");
        assertNull(PartyFinderActions.listingToJoin(List.of(first, second), 21L, first), "already in a party");
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

        assertNull(PartyFinderActions.listingToJoin(List.of(closed), 22L, null));
    }

    @Test
    void aReplyThatArrivesAfterAResetIsDroppedWithoutAWord() {
        PartyFinderManager manager = offline();
        CompletableFuture<Listing> request = new CompletableFuture<>();
        CompletableFuture<Listing> guarded = manager.send(manager.ticket(), api -> request, listing -> {
            throw new AssertionError("the previous account's reply was applied");
        });

        manager.reset();
        request.complete(listing(7L, "previous-leader", member("previous-leader"), member("previous-account")));
        gameThread.runDue();

        assertTrue(guarded.isCompletedExceptionally());
        Throwable failure = guarded.handle((value, error) -> error).join();
        assertTrue(PartyFinderManager.isSupersededByReset(failure));
        assertNull(PartyFinderErrors.message(failure, "Unable to join party"));
        assertNull(manager.consumeLatestPartyError(), "a dropped reply leaves no banner for the next account");
    }

    @Test
    void aRequestWhoseSessionEndedBeforeItWentOutNeverGoesOut() {
        PartyFinderManager manager = offline();
        PartyFinderManager.Ticket ticket = manager.ticket();
        manager.reset();

        CompletableFuture<Listing> guarded = manager.send(ticket, api -> {
            throw new AssertionError("the request went out");
        }, Function.identity());

        assertTrue(PartyFinderManager.isSupersededByReset(guarded.handle((value, error) -> error).join()));
    }

    @Test
    void aReplyWithNoResetInBetweenArrivesAsUsual() {
        PartyFinderManager manager = offline();
        CompletableFuture<String> request = new CompletableFuture<>();
        CompletableFuture<String> guarded = manager.send(manager.ticket(), api -> request, Function.identity());

        request.complete("listing");
        assertFalse(guarded.isDone(), "a reply is handed to the game thread");
        gameThread.runDue();

        assertEquals("listing", guarded.join());
    }

    @Test
    void aReplyChangesThePartyOnlyOnTheGameThreadAndOnlyWhileItsSessionLasts() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        partyFinder.applyJoined(listing(7L, OTHER_MEMBER, member(OTHER_MEMBER), member(LEADER)));
        String route = "PATCH /party-finder/members/me/role";
        CompletableFuture<String> reply = backend.hold(route);

        CompletableFuture<CommandResult<Listing>> changed = partyFinder.actions().changeRoleFromCommand(PartyRole.TANK);
        backend.awaitRequest(route);
        reply.complete(listingJson(7L, OTHER_MEMBER, OTHER_MEMBER, LEADER));
        // The reply reaches the client's own thread, which leaves it for the game thread.
        Thread.sleep(200);
        assertFalse(changed.isDone(), "nothing is changed off the game thread, where the socket's updates land too");
        // The session ends on the game thread before the reply is handed over.
        sessions.end(EndReason.SIGN_OUT);
        gameThread.runDue();

        assertEquals(SESSION_ENDED, changed.get(5, TimeUnit.SECONDS).message());
        assertNull(partyFinder.getCurrentListing());
        assertTrue(partyFinder.getListings().isEmpty(), "the reply brings back nothing for the next account");
    }

    @Test
    void anAnswerHeldPastAReconnectOrTheSessionsEndTellsNobody() {
        PartyFinderManager manager = offline();
        List<String> told = new CopyOnWriteArrayList<>();

        CompletableFuture<String> reconnected = new CompletableFuture<>();
        manager.reply(reconnected, (message, error) -> told.add(message));
        connection = new Object();
        reconnected.complete("Party listings (3):");
        CompletableFuture<String> ended = new CompletableFuture<>();
        manager.reply(ended, (message, error) -> told.add(message));
        manager.reset();
        ended.complete("Party listings (3):");
        gameThread.runDue();
        assertEquals(List.of(), told, "a /seq p list held past a reconnect or a sign-out prints nothing");

        CompletableFuture<String> kept = new CompletableFuture<>();
        manager.reply(kept, (message, error) -> told.add(message));
        kept.complete("Party listings (3):");
        assertEquals(List.of(), told, "told on the game thread");
        gameThread.runDue();
        assertEquals(List.of("Party listings (3):"), told);
    }

    @Test
    void anInviteWhoseSessionEndedWhileItsInviterWasLookedUpIsNeverShown() {
        PartyFinderManager manager = offline();
        CompletableFuture<String> name = new CompletableFuture<>();
        names = uuid -> name;
        manager.handlePartyFinderInvite(7L, OTHER_MEMBER, null, null, connection);
        // The session ends, as an account switch ends it, before the name comes back.
        manager.reset();
        name.complete("Inviter");
        gameThread.runDue();
        assertEquals(List.of(), shown);

        names = uuid -> CompletableFuture.completedFuture("Inviter");
        manager.handlePartyFinderInvite(8L, OTHER_MEMBER, null, null, connection);
        gameThread.runDue();
        assertEquals(List.of("Party Finder invite from Inviter."), shown);
    }

    @Test
    void anInviteLookedUpJustBeforeItsSessionEndedIsNeverShown() {
        PartyFinderManager manager = offline();
        CompletableFuture<String> name = new CompletableFuture<>();
        names = uuid -> name;
        manager.handlePartyFinderInvite(7L, OTHER_MEMBER, null, null, connection);

        // The name comes back, then the session ends before the game thread shows the invite.
        name.complete("Inviter");
        manager.reset();
        gameThread.runDue();

        assertEquals(List.of(), shown);
    }

    @Test
    void aPartyCommandGoesOnlyToTheSessionAndServerConnectionItWasFor() {
        PartyFinderManager manager = offline();
        CompletableFuture<String> name = new CompletableFuture<>();
        names = uuid -> name;
        UUID kicked = UUID.fromString(KICK_TARGET);

        // The session ends, as an account switch ends it, while their name is looked up.
        manager.game().partyCommandFor(manager.ticket(), kicked, PartyGameCommands.KICK);
        manager.reset();
        name.complete("Kicked");
        gameThread.runDue();
        assertEquals(List.of(), sent, "the next account never sends the previous one's command");

        names = uuid -> CompletableFuture.completedFuture("Kicked");
        manager.game().partyCommandFor(manager.ticket(), kicked, PartyGameCommands.KICK);
        connection = new Object();
        gameThread.runDue();
        assertEquals(List.of(), sent, "nor does the same session's player on another server connection");

        manager.game().partyCommandFor(manager.ticket(), kicked, PartyGameCommands.KICK);
        gameThread.runDue();
        assertEquals(List.of("party kick Kicked"), sent);
    }

    // ── D3: kick and promote act on the connection they were asked on ──

    @Test
    void aKickFromTheScreenKicksAndMessagesTheMemberInTheGame() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        names = uuid -> CompletableFuture.completedFuture("Kicked");
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER), member(KICK_TARGET)));
        backend.answer("DELETE /party-finder/listings/7/members/" + KICK_TARGET, listingJson(7L, LEADER, LEADER));

        partyFinder.actions().kickMemberById(7L, KICK_TARGET);
        runGameThreadUntil(() -> sent.size() == 2);

        assertEquals(List.of("party kick Kicked", "msg Kicked You were kicked from the party."), sent);
        assertEquals(1, partyFinder.getCurrentListing().members().size());
    }

    @Test
    void aKickWhoseReplyArrivesAfterThePlayerReconnectedSendsNothingOnTheNewConnection() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        names = uuid -> CompletableFuture.completedFuture("Kicked");
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER), member(KICK_TARGET)));
        String route = "DELETE /party-finder/listings/7/members/" + KICK_TARGET;
        CompletableFuture<String> reply = backend.hold(route);

        partyFinder.actions().kickMemberById(7L, KICK_TARGET);
        backend.awaitRequest(route);
        // The player reconnects to Wynncraft while the backend is still answering.
        connection = new Object();
        reply.complete(listingJson(7L, LEADER, LEADER));
        runGameThreadUntil(() -> partyFinder.getCurrentListing().members().size() == 1);
        settleGameThread();

        assertEquals(List.of(), sent, "neither /party kick nor the message goes out on the new connection");
        assertEquals(1, partyFinder.getCurrentListing().members().size(), "the listing still takes the backend's answer");
    }

    @Test
    void aPromoteCommandWhoseReplyArrivesAfterAReconnectSendsNoPartyCommand() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        names = uuid -> CompletableFuture.completedFuture("Promoted");
        uuids = username -> CompletableFuture.completedFuture(KICK_TARGET);
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER), member(KICK_TARGET)));
        CompletableFuture<String> reply = backend.hold("POST /party-finder/listings/7/transfer");

        CompletableFuture<CommandResult<Listing>> promoted = partyFinder.actions().promoteMemberFromCommand("Promoted");
        // The looked-up name is acted on on the game thread.
        runGameThreadUntil(() -> backend.saw("POST /party-finder/listings/7/transfer"));
        connection = new Object();
        reply.complete(listingJson(7L, KICK_TARGET, LEADER, KICK_TARGET));

        assertTrue(awaitOnGameThread(promoted).success());
        settleGameThread();
        assertEquals(List.of(), sent);
    }

    // ── D2: a lookup that outlives its session sends nothing for the next account ──

    @Test
    void anInviteCommandWhoseSessionEndsDuringTheLookupNeverGoesOut() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        partyFinder.applyJoined(inviteOnly(listing(7L, LEADER, member(LEADER))));
        CompletableFuture<String> lookup = new CompletableFuture<>();
        uuids = username -> lookup;

        CompletableFuture<CommandResult<Void>> invited = partyFinder.actions().createInviteFromCommand("Target");
        // The player signs out, or another account signs in, while the target is looked up.
        sessions.end(EndReason.SIGN_OUT);
        lookup.complete(KICK_TARGET);

        String message = awaitOnGameThread(invited).message();
        assertFalse(backend.saw("POST /party-finder/listings/7/invite"), "no invite goes out, with any token");
        assertEquals(SESSION_ENDED, message);
    }

    @Test
    void aKickCommandWhoseSessionEndsDuringTheLookupNeverGoesOut() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER), member(KICK_TARGET)));
        CompletableFuture<String> lookup = new CompletableFuture<>();
        uuids = username -> lookup;

        CompletableFuture<CommandResult<Listing>> kicked = partyFinder.actions().kickMemberFromCommand("Kicked");
        sessions.end(EndReason.SIGN_OUT);
        lookup.complete(KICK_TARGET);

        String message = awaitOnGameThread(kicked).message();
        assertFalse(backend.saw("DELETE /party-finder/listings/7/members/" + KICK_TARGET), "no kick goes out");
        assertEquals(SESSION_ENDED, message);
    }

    @Test
    void thePartyFinderSendsItsOwnSessionsTokenAndNothingOnceThatSessionEnds() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        backend.answer("GET /party-finder/listings", "[" + listingJson(7L, OTHER_MEMBER, OTHER_MEMBER) + "]");

        assertTrue(awaitOnGameThread(partyFinder.actions().refreshListingsForCommand()).success());
        sessions.end(EndReason.SIGN_OUT);
        var afterEnd = awaitOnGameThread(partyFinder.actions().refreshListingsForCommand());

        assertEquals(List.of(new FakePartyFinderBackend.Request("GET /party-finder/listings", "Bearer leader-token")),
                backend.requests);
        assertFalse(afterEnd.success());
    }

    // ── D6: Invite All and member notices stay with the session and connection they began on ──

    @Test
    void inviteAllStopsAtAReplacementConnectionAndFinishesWithoutAWordOnIt() {
        PartyFinderManager partyFinder = offline();
        names = uuid -> CompletableFuture.completedFuture(Map.of(KICK_TARGET, "First", OTHER_MEMBER, "Second").get(uuid));
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER), member(KICK_TARGET), member(OTHER_MEMBER)));

        CompletableFuture<InviteAllResult> invited = partyFinder.game().inviteAllCurrentMembers();
        gameThread.runDue();
        gameThread.runDue();
        assertEquals(List.of("party invite First"), sent);
        // The player reconnects before the second invite is due, half a second later.
        connection = new Object();
        clockMs += 500;
        gameThread.runDue();

        assertEquals(List.of("party invite First"), sent, "the second invite never goes out on the new connection");
        assertTrue(invited.isDone(), "the skipped invite still finishes the batch");
        assertEquals(new InviteAllResult(true, true, 1, 1, "sent 1 party invite. Skipped 1."), invited.join());
        gameThread.runDue();
        assertEquals(List.of(), shown, "the summary is not shown on the new connection");
    }

    @Test
    void inviteAllSkipsAMemberWhoLeftWhileTheInvitesWereGoingOut() {
        PartyFinderManager partyFinder = offline();
        names = uuid -> CompletableFuture.completedFuture(Map.of(KICK_TARGET, "First", OTHER_MEMBER, "Second").get(uuid));
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER), member(KICK_TARGET), member(OTHER_MEMBER)));

        CompletableFuture<InviteAllResult> invited = partyFinder.game().inviteAllCurrentMembers();
        gameThread.runDue();
        gameThread.runDue();
        assertEquals(List.of("party invite First"), sent);
        // Second leaves before their invite is due.
        partyFinder.applyUpdated(listing(7L, LEADER, member(LEADER), member(KICK_TARGET)));
        clockMs += 500;
        gameThread.runDue();

        assertEquals(List.of("party invite First"), sent, "no invite goes to a player who left the party");
        assertEquals(new InviteAllResult(true, true, 1, 1, "sent 1 party invite. Skipped 1."), invited.join());
    }

    @Test
    void inviteAllSendsOneInviteATickAndWaitsFromItForTheNextEvenAfterAStall() {
        PartyFinderManager partyFinder = offline();
        names = uuid -> CompletableFuture.completedFuture(
                Map.of(KICK_TARGET, "First", OTHER_MEMBER, "Second", THIRD_MEMBER, "Third").get(uuid));
        partyFinder.applyJoined(listing(
                7L, LEADER, member(LEADER), member(KICK_TARGET), member(OTHER_MEMBER), member(THIRD_MEMBER)));

        partyFinder.game().inviteAllCurrentMembers();
        List<List<String>> perTick = new java.util.ArrayList<>();
        perTick.add(tick());
        clockMs += 2_000; // the game stalls past every invite's half second
        perTick.add(tick());
        perTick.add(tick());
        clockMs += 500;
        perTick.add(tick());

        assertEquals(List.of(List.of("party invite First"), List.of("party invite Second"), List.of(),
                List.of("party invite Third")), perTick);
    }

    @Test
    void inviteAllCutShortByItsSessionsEndFinishesWithoutAWordToTheNextAccount() {
        PartyFinderManager partyFinder = offline();
        names = uuid -> CompletableFuture.completedFuture(KICK_TARGET.equals(uuid) ? "First" : "Second");
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER), member(KICK_TARGET), member(OTHER_MEMBER)));

        CompletableFuture<InviteAllResult> invited = partyFinder.game().inviteAllCurrentMembers();
        gameThread.runDue();
        gameThread.runDue();
        partyFinder.reset();
        clockMs += 500;
        gameThread.runDue();
        gameThread.runDue();

        assertEquals(List.of("party invite First"), sent);
        assertTrue(invited.isDone());
        assertEquals(List.of(), shown);
    }

    @Test
    void aMemberWhoJoinedIsNeverAnnouncedToALaterSession() {
        PartyFinderManager partyFinder = offline();
        CompletableFuture<String> name = new CompletableFuture<>();
        names = uuid -> name;
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER)));

        partyFinder.handlePartyFinderUpdate("UPDATED", listing(7L, LEADER, member(LEADER), member(KICK_TARGET)));
        partyFinder.reset();
        name.complete("Joiner");
        gameThread.runDue();

        assertEquals(List.of(), shown, "the next account never hears about the previous one's party");
    }

    @Test
    void aMemberWhoJoinedIsAnnouncedToTheLeaderByName() {
        PartyFinderManager partyFinder = offline();
        names = uuid -> CompletableFuture.completedFuture(KICK_TARGET.equals(uuid) ? "Joiner" : "Leaver");
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER), member(OTHER_MEMBER)));

        partyFinder.handlePartyFinderUpdate("UPDATED", listing(7L, LEADER, member(LEADER), member(KICK_TARGET)));
        gameThread.runDue();

        assertEquals(List.of("Joiner joined your party.", "Leaver left your party."), shown);
    }

    // ── A looked-up name is acted on on the game thread, and a reply applied where its session is checked ──

    @Test
    void aScreenInviteChecksItsLookedUpPlayerOnTheGameThreadAndOnlyForItsSession() {
        PartyFinderManager partyFinder = offline();
        partyFinder.applyJoined(inviteOnly(listing(7L, LEADER, member(LEADER))));
        CompletableFuture<String> lookup = new CompletableFuture<>();
        uuids = username -> lookup;

        partyFinder.actions().createInvite("Nobody");
        // The name service answers on a thread of its own: here, the test's.
        lookup.complete(null);
        assertNull(partyFinder.consumeLatestPartyError(), "nothing is written off the game thread");
        gameThread.runDue();
        gameThread.runDue();
        assertEquals("Unable to find that player UUID.", partyFinder.consumeLatestPartyError());

        CompletableFuture<String> late = new CompletableFuture<>();
        uuids = username -> late;
        partyFinder.actions().createInvite("Nobody");
        partyFinder.reset();
        late.complete(null);
        gameThread.runDue();
        gameThread.runDue();
        assertNull(partyFinder.consumeLatestPartyError(), "the next account sees no banner for the previous one's invite");
    }

    @Test
    void aKickCommandWhoseLookupFinishesOnAnotherThreadGoesOutFromTheGameThread() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER), member(KICK_TARGET)));
        CompletableFuture<String> lookup = new CompletableFuture<>();
        uuids = username -> lookup;
        String route = "DELETE /party-finder/listings/7/members/" + KICK_TARGET;
        backend.answer(route, listingJson(7L, LEADER, LEADER));

        CompletableFuture<CommandResult<Listing>> kicked = partyFinder.actions().kickMemberFromCommand("Kicked");
        // The name service answers on a thread of its own: here, the test's.
        lookup.complete(KICK_TARGET);
        Thread.sleep(200);

        assertFalse(backend.saw(route), "the lookup's answer waits for the game thread, as its reply will");
        assertTrue(awaitOnGameThread(kicked).success());
        assertEquals(1, partyFinder.getCurrentListing().members().size());
    }

    // ── What the socket heard is told on the server connection it heard it on ──

    @Test
    void whatTheSocketHeardBeforeAReconnectChangesTheListingsButIsNotToldOnTheNewConnection() {
        PartyFinderManager partyFinder = offline();
        names = uuid -> CompletableFuture.completedFuture("Joiner");
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER)));
        Object heardOn = connection;
        // The player reconnects before the game thread hands on what the socket heard.
        connection = new Object();

        partyFinder.handlePartyFinderUpdate("UPDATED", listing(7L, LEADER, member(LEADER), member(KICK_TARGET)), heardOn);
        partyFinder.handlePartyFinderInvite(8L, OTHER_MEMBER, null, null, heardOn);
        partyFinder.handlePartyFinderStaleWarning("idle", 7L, null, 5, heardOn);
        gameThread.runDue();

        assertEquals(List.of(), shown);
        assertEquals(2, partyFinder.getCurrentListing().members().size(), "the listing still takes the update");
        partyFinder.handlePartyFinderStaleWarning("idle", 7L, null, 5, connection);
        assertEquals(1, shown.size(), "a warning heard on the player's connection is shown");
    }

    // ── A screen's banner, and a linked listing's sync, stay with the connection they began on ──

    @Test
    void aScreenRequestThatFailsAfterAReconnectLeavesNoBannerOnTheNewConnection() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        partyFinder.applyJoined(listing(7L, LEADER, member(LEADER)));
        String route = "POST /party-finder/listings/7/close";
        CompletableFuture<String> refusal = backend.hold(route);

        partyFinder.actions().closeParty(7L);
        backend.awaitRequest(route);
        // The same account reconnects to Wynncraft while the backend answers.
        connection = new Object();
        refusal.complete(null);
        runGameThreadFor(300);

        assertNull(partyFinder.consumeLatestPartyError(), "the failure is not shown on the new connection");
    }

    @Test
    void aLinkedListingCreatedAcrossAReconnectSendsNoPartyListOnTheNewConnection() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        String route = "POST /party-finder/listings";
        CompletableFuture<String> created = linkedCreateBackend(route);
        GuildPresenceManager presence = presenceWithoutRoster();
        List<String> told = new CopyOnWriteArrayList<>();
        List<PartyFinderManager> synced = new CopyOnWriteArrayList<>();

        presence.openPartyFinderListing(partyFinder, List.of("NOTG"), PartyRegion.NA, told::add, synced::add);
        runGameThreadUntil(() -> backend.saw(route));
        connection = new Object();
        created.complete(listingJson(9L, LEADER, LEADER));
        runGameThreadUntil(() -> partyFinder.getCurrentListing() != null);
        runGameThreadFor(200);

        assertEquals(List.of(), synced, "the Wynn party sync asks nothing on the new connection");
        assertEquals(List.of(), told);
    }

    // ── Nothing waits on a request for good ──

    @Test
    void aRequestStillWaitingWhenItsSessionEndsIsSettledThen() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        partyFinder.applyJoined(listing(7L, OTHER_MEMBER, member(OTHER_MEMBER), member(LEADER)));
        String route = "PATCH /party-finder/members/me/role";
        backend.hold(route);

        CompletableFuture<CommandResult<Listing>> changed = partyFinder.actions().changeRoleFromCommand(PartyRole.TANK);
        backend.awaitRequest(route);
        sessions.end(EndReason.SIGN_OUT);

        assertTrue(changed.isDone(), "settled as the session ends, not whenever the backend answers");
        assertEquals(SESSION_ENDED, changed.join().message());
    }

    @Test
    void aLinkedListingStillWaitingWhenItsSessionEndsLetsTheNextSessionOpenOne() throws Exception {
        PartyFinderManager partyFinder = sessionPartyFinder();
        String route = "POST /party-finder/listings";
        linkedCreateBackend(route);
        GuildPresenceManager presence = presenceWithoutRoster();

        presence.openPartyFinderListing(partyFinder, List.of("NOTG"), PartyRegion.NA, line -> {}, synced -> {});
        runGameThreadUntil(() -> backend.saw(route));
        sessions.end(EndReason.SIGN_OUT);
        PartyFinderManager next = new PartyFinderManager(backend.client(sessions.current()), gameThread, playing);
        presence.openPartyFinderListing(next, List.of("NOTG"), PartyRegion.NA, line -> {}, synced -> {});

        runGameThreadUntil(() -> backend.requests.stream().filter(request -> request.route().equals(route)).count() == 2);
    }

    /** A backend with no listings and one raid, holding the create {@code route}'s reply until the test answers. */
    private CompletableFuture<String> linkedCreateBackend(String route) {
        backend.answer("GET /party-finder/listings", "[]");
        backend.answer("GET /party-finder/activities", "[{\"id\":1,\"name\":\"NOTG\",\"maxPartySize\":4}]");
        return backend.hold(route);
    }

    private GuildPresenceManager presenceWithoutRoster() {
        return new GuildPresenceManager(prefix -> new CompletableFuture<>(), () -> true, answer -> {}, () -> clockMs,
                gameThread);
    }

    /** Runs the game thread for {@code ms}, for what a reply the backend's client hands back may start. */
    private void runGameThreadFor(long ms) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(ms);
        while (System.nanoTime() < deadline) {
            gameThread.runDue();
            Thread.sleep(5);
        }
        gameThread.runDue();
    }

    /** Runs the game thread until {@code done}, for a reply the backend's client hands back from its own thread. */
    private void runGameThreadUntil(java.util.function.BooleanSupplier done) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!done.getAsBoolean()) {
            assertTrue(System.nanoTime() < deadline, "timed out");
            gameThread.runDue();
            Thread.sleep(5);
        }
    }

    /** Runs the game thread until {@code result} completes, and returns it. */
    private <T> T awaitOnGameThread(CompletableFuture<T> result) throws InterruptedException {
        runGameThreadUntil(result::isDone);
        return result.join();
    }

    /** Runs the game thread once, and returns the commands it sent. */
    private List<String> tick() {
        int before = sent.size();
        gameThread.runDue();
        return List.copyOf(sent.subList(before, sent.size()));
    }

    /** Lets what the last reply started reach the game thread, and runs it. */
    private void settleGameThread() throws InterruptedException {
        Thread.sleep(200);
        gameThread.runDue();
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

    private static Listing inviteOnly(Listing listing) {
        return new Listing(listing.id(), listing.activities(), null, listing.leaderUUID(), listing.region(), null,
                listing.status(), null, null, listing.members(), listing.reservedSlots(), listing.createdAt(),
                PartyJoinPolicy.INVITE_ONLY);
    }

    /** A listing as the backend sends it. */
    private static String listingJson(long id, String leaderUuid, String... memberUuids) {
        String members = Arrays.stream(memberUuids)
                .map(uuid -> "{\"playerUUID\":\"" + uuid + "\",\"role\":\"DPS\",\"classType\":\"WARRIOR\","
                        + "\"joinedAt\":\"2026-01-01T00:00:00Z\"}")
                .collect(Collectors.joining(","));
        return "{\"id\":" + id + ",\"activities\":[{\"id\":1,\"name\":\"NOTG\",\"maxPartySize\":4}],"
                + "\"leaderUUID\":\"" + leaderUuid + "\",\"region\":\"NA\",\"status\":\"OPEN\",\"members\":[" + members
                + "],\"reservedSlots\":[],\"createdAt\":\"2026-01-01T00:00:00Z\",\"joinPolicy\":\"OPEN\"}";
    }
}
