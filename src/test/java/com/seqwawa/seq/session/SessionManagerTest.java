package com.seqwawa.seq.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.TickQueue;
import com.seqwawa.seq.network.TestApiClients;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.AuthState;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.NotSignedInException;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import com.seqwawa.seq.ui.unlock.UnlockPopup;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;

/**
 * Pins what each way of ending a session does, step by step and in order. Each list is the one
 * its path ran before {@link SessionManager} existed, except that a rejected token ends the whole
 * session, as agreed with the owner, keeping only the Leaf stream's place.
 */
class SessionManagerTest {

    private static final UUID OPERATOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID SHARED_ACCOUNT = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
    private static final StoredAuthSession OPERATOR_SIGNED_IN = new StoredAuthSession(
            "operator-token", Instant.now().plusSeconds(86_400), OPERATOR.toString(), "Operator");

    /** Every step by name, in the order it ran, the auth service's included. */
    private final List<String> calls = new ArrayList<>();
    /** Signed in as the operator; hands out its token without asking the backend, and notes each way a sign-in ends. */
    private final MinecraftAuthService auth = new MinecraftAuthService(OPERATOR_SIGNED_IN) {
        @Override
        public CompletableFuture<String> ensureValidToken(boolean forceRefresh) {
            return CompletableFuture.completedFuture(getCurrentSession().token());
        }

        @Override
        public void clearSession() {
            calls.add("clearSession");
            super.clearSession();
        }

        @Override
        public void signOut() {
            calls.add("signOut");
            super.signOut();
        }

        @Override
        public void carrySignInOver() {
            calls.add("carrySignInOver");
            super.carrySignInOver();
        }

        @Override
        public boolean invalidateSession(String token, AuthErrorCode code, String message) {
            calls.add("invalidateSession: " + code + " " + message);
            return super.invalidateSession(token, code, message);
        }
    };
    /** Writes each step down instead of touching the game; a session's own objects are really stopped. */
    private final BiConsumer<String, Runnable> steps = (name, step) -> {
        calls.add(name);
        if (name.endsWith(" stopped")) {
            step.run();
        }
    };
    private final TickQueue ticks = new TickQueue(() -> 0L);
    private final SessionManager sessions = new SessionManager(auth, ticks, steps, null);

    @Test
    void anAccountSwitchResetsWhatThePreviousAccountHadInOrder() {
        recordEndOf(sessions.current());
        sessions.end(new EndReason.AccountSwitch(false));

        assertEquals(List.of(
                // First: its reset hands reward bursts to the socket, which closes next.
                "resetGuildStorage",
                "resetConnectionForAccountChange",
                "clearSession",
                "end session",
                "forgetPartyClassBroadcast",
                "resetWynnPartySync",
                "resetRaidPartySnapshot",
                "resetGuildPresence",
                "resetGuildMembership",
                "resetNicknames",
                "resetWarTracking"), calls);
    }

    @Test
    void switchingToTheAccountTheSignInSpeaksForHandsTheTokenOnButNotWhatTheBackendSaidToTheOtherAccount() {
        recordEndOf(sessions.current());
        sessions.end(new EndReason.AccountSwitch(true));

        // A pause or refusals the shared account's socket earned would otherwise hold the operator's connects back.
        assertEquals(List.of("resetGuildStorage", "resetConnectionForAccountChange", "carrySignInOver", "end session"),
                calls.subList(0, 4), "the account's own state still goes");
        assertFalse(calls.contains("clearSession"));
    }

    @Test
    void signingOutEndsTheSignInBeforeTheSession() {
        recordEndOf(sessions.current());
        sessions.end(EndReason.SIGN_OUT);

        assertEquals(List.of("signOut", "end session"), calls);
    }

    @Test
    void endingASessionClosesItsConnectionAndDropsItsPartyFinderThroughTheSession() {
        Session first = sessions.current();
        List<String> closed = new ArrayList<>();
        first.whenEnded("connection", () -> closed.add("socket closed"));
        var partyFinder = first.partyFinder();
        assertSame(partyFinder, first.partyFinder());

        sessions.end(EndReason.SIGN_OUT);

        assertEquals(List.of("signOut", "connection stopped", "partyFinder stopped"), calls);
        assertEquals(List.of("socket closed"), closed);
        assertNotSame(partyFinder, sessions.current().partyFinder(), "the next session has a party finder of its own");
        first.whenEnded("late", () -> closed.add("late socket closed"));
        assertEquals(List.of("socket closed", "late socket closed"), closed, "one that ended stops what joins it at once");
    }

    @Test
    void clearingTheSessionSignsOutAndForgetsTheDiscordNameAndQueuedWarEvents() {
        recordEndOf(sessions.current());
        sessions.end(EndReason.CLEAR_SESSION);

        assertEquals(List.of(
                "dropPendingWarEvents",
                "signOut",
                "end session",
                "forgetDiscordName",
                "tellPlayer: Backend session cleared. Run /seq connect to sign in again."), calls);
    }

    @Test
    void aRejectedTokenEndsTheSessionAndLeavesTheConnectionToReconnectByItself() {
        recordEndOf(sessions.current());
        sessions.end(new EndReason.TokenRejected("operator-token", AuthErrorCode.TOKEN_INVALID, "Rejected."));

        assertEquals(List.of("invalidateSession: TOKEN_INVALID Rejected.", "end session"), calls,
                "neither a sign-out nor a disconnect: the refused socket already closes to sign in afresh");
    }

    @Test
    void anOldSocketsRejectionAfterAnAccountSwitchLeavesTheNewAccountsSignInAlone() {
        sessions.end(new EndReason.AccountSwitch(false));
        Session shared = sessions.current();
        long sharedSignIn = auth.sessionGeneration();
        calls.clear();

        // The operator's socket reports its refusal after the switch has replaced its sign-in.
        sessions.end(new EndReason.TokenRejected("operator-token", AuthErrorCode.TOKEN_INVALID, "Rejected."));

        assertEquals(sharedSignIn, auth.sessionGeneration(), "the shared account's sign-in is not ended");
        assertSame(shared, sessions.current());
        assertFalse(shared.isEnded());
        assertEquals(List.of("invalidateSession: TOKEN_INVALID Rejected."), calls, "nothing of the shared session stops");
    }

    @Test
    void aStepThatThrowsLeavesTheRestOfTheEndAndTheNextSessionToRun() {
        // The previous account's socket closed just before its reward flush, and one of its parts fails to stop.
        SessionManager failing = new SessionManager(auth, ticks, (name, step) -> {
            steps.accept(name, step);
            if (name.equals("resetGuildStorage") || name.equals("leafQuests stopped")) {
                throw new IllegalStateException(name + " failed");
            }
        }, null);
        Session previous = failing.current();
        previous.leafQuests();
        previous.raidProfiles();

        failing.end(new EndReason.AccountSwitch(false));

        assertTrue(calls.containsAll(List.of("resetConnectionForAccountChange", "clearSession", "raidProfiles stopped",
                "resetWarTracking")), calls.toString());
        assertTrue(previous.isEnded());
        assertNotSame(previous, failing.current());
        assertNull(failing.current().signedIn(), "the next account's session is the one current");
    }

    @Test
    void theFirstAccountSeenStartsNothing() {
        assertFalse(sessions.checkAccount(OPERATOR, "Operator"));
        assertFalse(sessions.checkAccount(OPERATOR, "Operator"));
        assertFalse(sessions.checkAccount(null, null), "nor does no account yet");

        assertEquals(List.of(), calls);
    }

    @Test
    void aDifferentAccountEndsTheSessionOnce() {
        recordEndOf(sessions.current());
        sessions.checkAccount(OPERATOR, "Operator");

        assertTrue(sessions.checkAccount(SHARED_ACCOUNT, "Shared"));
        assertFalse(sessions.checkAccount(SHARED_ACCOUNT, "Shared"), "the next tick is the same account");

        assertEquals(1, calls.stream().filter("end session"::equals).count());
        assertTrue(calls.contains("clearSession"));
    }

    @Test
    void switchingBackToTheOperatorTheSignInSpeaksForKeepsIt() {
        sessions.checkAccount(SHARED_ACCOUNT, "Shared");

        assertTrue(sessions.checkAccount(OPERATOR, "Operator"));

        assertFalse(calls.contains("clearSession"));
    }

    @Test
    void switchingToTheSharedAccountDropsTheOperatorsSignIn() {
        assertFalse(SessionManager.signInSpeaksFor(SHARED_ACCOUNT, OPERATOR.toString()));
        assertFalse(SessionManager.signInSpeaksFor(SHARED_ACCOUNT, null));
        assertFalse(SessionManager.signInSpeaksFor(SHARED_ACCOUNT, "not-a-uuid"));
        assertTrue(SessionManager.signInSpeaksFor(OPERATOR, OPERATOR.toString()));
    }

    @Test
    void everyWayOutStartsANewSessionWithNoToken() {
        // The rejection first, while the sign-in still holds the token it refuses.
        for (EndReason reason : List.of(
                new EndReason.TokenRejected("operator-token", AuthErrorCode.TOKEN_INVALID, "Rejected."),
                new EndReason.AccountSwitch(false),
                EndReason.SIGN_OUT,
                EndReason.CLEAR_SESSION)) {
            Session before = sessions.current();

            sessions.end(reason);

            assertTrue(before.isEnded(), reason + " ends the session");
            assertNotSame(before, sessions.current(), reason + " starts another");
            assertFalse(sessions.current().isEnded());
            assertNull(sessions.current().signedIn(), reason + " leaves the next session signed out");
            assertFalse(sessions.current().hasToken());
            assertEquals(auth.sessionGeneration(), sessions.current().id(), "the auth service's count is the id");
        }
    }

    @Test
    void anEndedSessionsTokenRefusesWithoutSigningIn() {
        Session signedIn = sessions.current();
        assertEquals("operator-token", signedIn.token(false).join());

        sessions.end(EndReason.SIGN_OUT);

        assertNotSignedIn(signedIn.token(false));
        assertNotSignedIn(signedIn.token(true));
        assertNull(signedIn.signedIn());
        assertFalse(signedIn.hasToken());
    }

    @Test
    void aSignInKeptAcrossASwitchServesOnlyTheNewSession() {
        Session shared = sessions.current();

        sessions.end(new EndReason.AccountSwitch(true));

        assertNotEquals(shared.id(), sessions.current().id(), "the token carries on, in a sign-in of its own");
        assertEquals(auth.sessionGeneration(), sessions.current().id());
        assertEquals("operator-token", sessions.current().token(false).join());
        assertNotSignedIn(shared.token(false));
    }

    @Test
    void aRenewalTheEndedSessionAskedForIsNotStoredForTheOneThatKeptItsToken() {
        long shared = sessions.current().id();

        sessions.end(new EndReason.AccountSwitch(true));

        assertNotSignedIn(auth.ensureSessionToken(true, shared));
        assertEquals(AuthState.AUTHENTICATED, auth.getState(), "the kept token still counts as signed in");
    }

    @Test
    void eachSessionHasItsOwnObjectsAndEndingOneStopsWhatItMadeAndNothingElse() {
        Session first = sessions.current();
        var quests = first.leafQuests();
        var raidProfiles = first.raidProfiles();
        assertSame(quests, first.leafQuests(), "made once, the first time it is asked for");

        sessions.end(EndReason.SIGN_OUT);

        assertEquals(List.of("signOut", "leafQuests stopped", "raidProfiles stopped"), calls);
        assertNotSignedIn(raidProfiles.ensureToken(false));
        Session next = sessions.current();
        assertNotSame(quests, next.leafQuests());
        assertNotSame(raidProfiles, next.raidProfiles());
        var princess = next.princessStats();
        var progress = next.raidProgress();
        var planner = next.warPlanner();
        var queue = next.warQueue();
        var partyFinder = next.partyFinder();
        calls.clear();

        sessions.end(EndReason.CLEAR_SESSION);

        assertEquals(List.of("leafQuests stopped", "raidProfiles stopped", "princessStats stopped", "raidProgress stopped",
                        "warPlanner stopped", "warQueue stopped", "partyFinder stopped"),
                calls.stream().filter(call -> call.endsWith(" stopped")).toList());
        assertNotSame(princess, sessions.current().princessStats());
        assertNotSame(progress, sessions.current().raidProgress());
        assertNotSame(planner, sessions.current().warPlanner());
        assertNotSame(queue, sessions.current().warQueue());
        assertNotSame(partyFinder, sessions.current().partyFinder());
    }

    @Test
    void anEndedSessionsOwnRequestsRefuseRatherThanTakeTheNextAccountsToken() {
        Session previous = sessions.current();
        var previousClient = com.seqwawa.seq.network.ApiClient.getInstance().forSession(previous);

        sessions.end(new EndReason.AccountSwitch(true));
        assertEquals("operator-token", sessions.current().token(false).join(), "the next session holds a token");

        // Guild raid progress, its 404/405 fallback GET included, asks its own session for the token, before any request.
        assertNotSignedIn(previousClient.getGuildRaidProgress(true));
    }

    @Test
    void aRaidProgressFallbackDueAfterItsSessionEndedRefusesRatherThanGoOutWithoutAToken() throws Exception {
        HttpServer backend = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch posted = new CountDownLatch(1);
        CountDownLatch ended = new CountDownLatch(1);
        List<String> reads = new CopyOnWriteArrayList<>();
        backend.createContext("/achievements/progress", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                posted.countDown();
                try {
                    ended.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // A backend without the recording route.
                exchange.sendResponseHeaders(404, -1);
            } else {
                reads.add(String.valueOf(exchange.getRequestHeaders().getFirst("Authorization")));
                byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        backend.start();
        try {
            var client = TestApiClients.at("http://127.0.0.1:" + backend.getAddress().getPort()).forSession(sessions.current());

            // The POST goes out with the session's token; its 404 comes back once the session has ended.
            CompletableFuture<?> progress = client.getGuildRaidProgress(true);
            assertTrue(posted.await(5, TimeUnit.SECONDS), "the POST is under way");
            sessions.end(EndReason.SIGN_OUT);
            ended.countDown();

            assertNotSignedIn(progress);
            assertEquals(List.of(), reads, "the fallback read never goes out");
        } finally {
            backend.stop(0);
        }
    }

    @Test
    void endingASessionStopsItsLeafStreamAndDropsItsWaitingPopups() {
        Session first = sessions.current();
        first.leafEvents();
        first.popups().enqueue(popup("+330"));

        sessions.end(EndReason.SIGN_OUT);

        assertTrue(calls.containsAll(List.of("leafEvents stopped", "popups stopped")));
        assertTrue(first.popups().isIdle());
        assertTrue(sessions.current().popups().isIdle());
    }

    @Test
    void aPopupThePreviousAccountsStreamHandsOverAfterASwitchNeverShows() {
        Session previous = sessions.current();

        sessions.end(new EndReason.AccountSwitch(false));
        // The previous stream had read an event just before it stopped.
        previous.show(popup("+330"));
        ticks.runDue();

        assertTrue(sessions.current().popups().isIdle(), "the new account's queue never sees it");
        assertTrue(previous.popups().isIdle(), "nor does the ended session's own");
        assertNotSame(previous.popups(), sessions.current().popups());
    }

    @Test
    void aPopupFromTheCurrentSessionsStreamShowsOnTheGameThread() {
        sessions.current().show(popup("+330"));
        assertTrue(sessions.current().popups().isIdle(), "not before the game thread runs it");

        ticks.runDue();

        assertFalse(sessions.current().popups().isIdle());
    }

    @Test
    void theSamePlayersNextSessionResumesTheLeafStreamWhereTheLastOneWas() {
        SessionManager resuming = new SessionManager(auth, ticks, steps, "event-41");
        resuming.current().leafEvents();

        for (EndReason samePlayer : List.of(
                new EndReason.TokenRejected("operator-token", AuthErrorCode.TOKEN_INVALID, "Rejected."),
                EndReason.SIGN_OUT,
                EndReason.CLEAR_SESSION)) {
            resuming.end(samePlayer);

            assertEquals("event-41", resuming.current().leafEvents().cursor(), samePlayer + " keeps the place");
        }
    }

    @Test
    void aCursorCarriesAcrossSessionsThatNeverOpenedAStream() {
        SessionManager resuming = new SessionManager(auth, ticks, steps, "event-41");
        resuming.current().leafEvents();
        resuming.end(new EndReason.TokenRejected("operator-token", AuthErrorCode.TOKEN_INVALID, "Rejected."));

        // This session ends before its stream is ever asked for.
        resuming.end(EndReason.SIGN_OUT);

        assertEquals("event-41", resuming.current().leafEvents().cursor());
    }

    @Test
    void anotherAccountsStreamStartsFromNowEvenWhenTheOperatorKeepsTheirSignIn() {
        for (boolean keepSignIn : List.of(false, true)) {
            SessionManager resuming = new SessionManager(auth, ticks, steps, "event-41");
            resuming.current().leafEvents();

            resuming.end(new EndReason.AccountSwitch(keepSignIn));

            assertNull(resuming.current().leafEvents().cursor());
        }
    }

    private void recordEndOf(Session session) {
        session.onEnd(() -> calls.add("end session"));
    }

    private static UnlockPopup popup(String title) {
        return new UnlockPopup(UnlockPopup.Kind.TOAST, null, title, "The Nameless Anomaly", 330, 330, null, null);
    }

    private static void assertNotSignedIn(CompletableFuture<?> token) {
        CompletionException failure = assertThrows(CompletionException.class, token::join);
        assertInstanceOf(NotSignedInException.class, failure.getCause());
    }
}
