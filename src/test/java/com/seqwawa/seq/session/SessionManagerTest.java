package com.seqwawa.seq.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.TickQueue;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.MinecraftAuthService;
import com.seqwawa.seq.network.auth.NotSignedInException;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Pins what each way of ending a session does, step by step and in order. Each list
 * is the one its path ran before {@link SessionManager} existed: the account switch in
 * SeqClient's tick, /seq logout and Clear session in ConnectionManager (and the command's
 * war planner reset), and a rejected token in the auth service. A rejected token now
 * ends the whole session, as agreed with the owner, keeping only the Leaf stream's place.
 */
class SessionManagerTest {

    private static final UUID OPERATOR = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
    private static final UUID SHARED_ACCOUNT = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");

    /** Signed in as the operator; the backend names the account a token speaks for. */
    private static final StoredAuthSession OPERATOR_SIGNED_IN = new StoredAuthSession(
            "operator-token", Instant.now().plusSeconds(86_400), OPERATOR.toString(), "Operator");

    /** The auth service, signed in; a token is handed out without asking the backend. */
    private final MinecraftAuthService auth = new MinecraftAuthService(OPERATOR_SIGNED_IN) {
        @Override
        public CompletableFuture<String> ensureValidToken(boolean forceRefresh) {
            return CompletableFuture.completedFuture(getCurrentSession().token());
        }
    };
    private final RecordingSteps steps = new RecordingSteps(auth);
    /** The game thread: what an end from another thread hands over waits here until a test runs it. */
    private final TickQueue ticks = new TickQueue(() -> 0L);
    private final RecordingServices services = new RecordingServices(steps.calls, ticks);
    private final SessionManager sessions = new SessionManager(steps, auth, services, ticks);

    @Test
    void anAccountSwitchResetsWhatThePreviousAccountHadInOrder() {
        services.recordEndOf(sessions.current());
        sessions.end(new EndReason.AccountSwitch(OPERATOR, SHARED_ACCOUNT, false));

        assertEquals(List.of(
                "resetGuildStorage",
                "resetConnectionForAccountChange",
                "clearSignIn",
                "end session",
                "forgetPartyClassBroadcast",
                "resetPartyFinder",
                "resetWynnPartySync",
                "resetRaidPartySnapshot",
                "resetGuildPresence",
                "resetGuildMembership",
                "resetNicknames",
                "resetWarTracking",
                "resetWarPlanning"), steps.calls);
    }

    @Test
    void guildStorageHandsOverItsRewardsBeforeTheConnectionCloses() {
        sessions.end(new EndReason.AccountSwitch(OPERATOR, SHARED_ACCOUNT, false));

        assertTrue(steps.calls.indexOf("resetGuildStorage") < steps.calls.indexOf("resetConnectionForAccountChange"));
    }

    @Test
    void switchingToTheAccountTheSignInSpeaksForKeepsTheConnectionAndSignIn() {
        services.recordEndOf(sessions.current());
        sessions.end(new EndReason.AccountSwitch(SHARED_ACCOUNT, OPERATOR, true));

        assertFalse(steps.calls.contains("resetConnectionForAccountChange"));
        assertFalse(steps.calls.contains("clearSignIn"));
        assertTrue(steps.calls.contains("end session"), "the account's own state still goes");
    }

    @Test
    void signingOutEndsTheSignInBeforeClosingTheConnection() {
        services.recordEndOf(sessions.current());
        sessions.end(EndReason.SIGN_OUT);

        assertEquals(List.of(
                "signOut",
                "end session",
                "disconnect",
                "resetPartyFinder",
                "resetWarPlanner"), steps.calls);
    }

    @Test
    void clearingTheSessionSignsOutAndForgetsTheDiscordNameAndQueuedWarEvents() {
        services.recordEndOf(sessions.current());
        sessions.end(EndReason.CLEAR_SESSION);

        assertEquals(List.of(
                "dropPendingWarEvents",
                "signOut",
                "end session",
                "disconnect",
                "resetPartyFinder",
                "forgetDiscordName",
                "tellPlayer: Backend session cleared. Run /seq connect to sign in again."), steps.calls);
    }

    @Test
    void aRejectedTokenStopsTheSignInAtOnce() {
        sessions.end(new EndReason.TokenRejected(AuthErrorCode.TOKEN_INVALID, "Backend rejected the stored token."));

        assertEquals(List.of("invalidateSignIn: TOKEN_INVALID Backend rejected the stored token."), steps.calls);
    }

    @Test
    void aRejectedTokenEndsTheWholeSessionOnTheGameThreadButKeepsTheLeafCursor() {
        services.recordEndOf(sessions.current());
        sessions.end(new EndReason.TokenRejected(AuthErrorCode.TOKEN_EXPIRED, "Backend token expired."));
        steps.calls.clear();

        ticks.runDue();

        assertEquals(List.of(
                "end session",
                "resetPartyFinder",
                "resetWarPlanning"), steps.calls);
    }

    @Test
    void aRejectedTokenLeavesTheConnectionToReconnectByItself() {
        sessions.end(new EndReason.TokenRejected(AuthErrorCode.WEBSOCKET_AUTH_REJECTED, "Rejected."));
        ticks.runDue();

        assertFalse(steps.calls.contains("disconnect"));
        assertFalse(steps.calls.contains("signOut"), "a rejected token is not the player signing out");
    }

    @Test
    void theFirstAccountSeenStartsNothing() {
        assertFalse(sessions.checkAccount(OPERATOR, "Operator"));
        assertFalse(sessions.checkAccount(OPERATOR, "Operator"));

        assertEquals(List.of(), steps.calls);
    }

    @Test
    void noAccountYetIsNotASwitch() {
        sessions.checkAccount(OPERATOR, "Operator");

        assertFalse(sessions.checkAccount(null, null));

        assertEquals(List.of(), steps.calls);
    }

    @Test
    void aDifferentAccountEndsTheSessionOnce() {
        services.recordEndOf(sessions.current());
        sessions.checkAccount(OPERATOR, "Operator");

        assertTrue(sessions.checkAccount(SHARED_ACCOUNT, "Shared"));
        assertFalse(sessions.checkAccount(SHARED_ACCOUNT, "Shared"), "the next tick is the same account");

        assertEquals(1, steps.calls.stream().filter("end session"::equals).count());
        assertTrue(steps.calls.contains("clearSignIn"));
    }

    @Test
    void switchingBackToTheOperatorTheSignInSpeaksForKeepsIt() {
        sessions.checkAccount(SHARED_ACCOUNT, "Shared");

        assertTrue(sessions.checkAccount(OPERATOR, "Operator"));

        assertFalse(steps.calls.contains("clearSignIn"));
    }

    @Test
    void everyWayOutStartsANewSessionWithNoToken() {
        for (EndReason reason : List.of(
                new EndReason.AccountSwitch(OPERATOR, SHARED_ACCOUNT, false),
                EndReason.SIGN_OUT,
                EndReason.CLEAR_SESSION,
                new EndReason.TokenRejected(AuthErrorCode.TOKEN_INVALID, "Rejected."))) {
            Session before = sessions.current();

            sessions.end(reason);
            ticks.runDue();

            assertTrue(before.isEnded(), reason + " ends the session");
            assertNotSame(before, sessions.current(), reason + " starts another");
            assertFalse(sessions.current().isEnded());
            assertNull(sessions.current().signedIn(), reason + " leaves the next session signed out");
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
    }

    @Test
    void aSignInKeptAcrossASwitchServesOnlyTheNewSession() {
        Session shared = sessions.current();

        sessions.end(new EndReason.AccountSwitch(SHARED_ACCOUNT, OPERATOR, true));

        assertEquals(shared.id(), sessions.current().id(), "the sign-in carries on");
        assertEquals("operator-token", sessions.current().token(false).join());
        assertNotSignedIn(shared.token(false));
    }

    @Test
    void aRejectedTokenRefusesAtOnceAndTheNextSessionStartsOnTheGameThread() {
        Session refused = sessions.current();

        sessions.end(new EndReason.TokenRejected(AuthErrorCode.TOKEN_INVALID, "Rejected."));

        assertSame(refused, sessions.current(), "the game thread has not caught up yet");
        assertNotSignedIn(refused.token(false));

        ticks.runDue();

        assertTrue(refused.isEnded());
        assertNotSame(refused, sessions.current());
    }

    @Test
    void aRejectionThatLandsAfterAnotherEndLeavesTheNewSessionAlone() {
        sessions.end(new EndReason.TokenRejected(AuthErrorCode.TOKEN_INVALID, "Rejected."));
        sessions.end(EndReason.SIGN_OUT);
        Session afterSignOut = sessions.current();

        ticks.runDue();

        assertSame(afterSignOut, sessions.current());
        assertFalse(afterSignOut.isEnded());
    }

    @Test
    void switchingToTheSharedAccountDropsTheOperatorsSignIn() {
        assertFalse(SessionManager.signInSpeaksFor(SHARED_ACCOUNT, OPERATOR.toString()));
        assertFalse(SessionManager.signInSpeaksFor(SHARED_ACCOUNT, null));
        assertFalse(SessionManager.signInSpeaksFor(SHARED_ACCOUNT, "not-a-uuid"));
        assertTrue(SessionManager.signInSpeaksFor(OPERATOR, OPERATOR.toString()));
    }

    @Test
    void eachSessionHasItsOwnLeafQuestsRaidProgressPrincessStatsAndRaidProfileSignIn() {
        Session first = sessions.current();
        var quests = first.leafQuests();
        var progress = first.raidProgress();
        var princess = first.princessStats();
        var raidProfiles = first.raidProfiles();
        assertSame(quests, first.leafQuests(), "made once, the first time it is asked for");

        sessions.end(EndReason.SIGN_OUT);
        Session next = sessions.current();

        assertNotSame(quests, next.leafQuests());
        assertNotSame(progress, next.raidProgress());
        assertNotSame(princess, next.princessStats());
        assertNotSame(raidProfiles, next.raidProfiles());
    }

    @Test
    void endingASessionStopsWhatItsObjectsHadRunningAndNothingElse() {
        Session first = sessions.current();
        first.leafQuests();
        first.raidProfiles();

        sessions.end(EndReason.SIGN_OUT);

        assertEquals(List.of("leafQuests stopped", "raidProfiles stopped"), services.stopped,
                "only what the session made, once each");
        sessions.current().princessStats();
        sessions.end(EndReason.CLEAR_SESSION);
        assertEquals(List.of("leafQuests stopped", "raidProfiles stopped", "princessStats stopped"), services.stopped);
    }

    @Test
    void aRejectedTokenStopsTheSessionsObjectsOnTheGameThread() {
        sessions.current().raidProgress();

        sessions.end(new EndReason.TokenRejected(AuthErrorCode.TOKEN_INVALID, "Rejected."));
        assertEquals(List.of(), services.stopped, "not on the socket's thread");

        ticks.runDue();
        assertEquals(List.of("raidProgress stopped"), services.stopped);
    }

    @Test
    void aPopupThePreviousAccountsStreamHandsOverAfterASwitchNeverShows() {
        Session previous = sessions.current();
        var handOver = LiveSessionServices.intoQueueOf(previous, ticks);

        sessions.end(new EndReason.AccountSwitch(OPERATOR, SHARED_ACCOUNT, false));
        // The previous stream had read an event just before it stopped.
        handOver.accept(popup("+330"));
        ticks.runDue();

        assertTrue(sessions.current().popups().isIdle(), "the new account's queue never sees it");
        assertNotSame(previous.popups(), sessions.current().popups());
    }

    @Test
    void aPopupFromTheCurrentSessionsStreamShows() {
        LiveSessionServices.intoQueueOf(sessions.current(), ticks).accept(popup("+330"));
        assertTrue(sessions.current().popups().isIdle(), "not before the game thread runs it");

        ticks.runDue();

        assertFalse(sessions.current().popups().isIdle());
    }

    @Test
    void endingASessionStopsItsLeafStreamAndDropsItsWaitingPopups() {
        Session first = sessions.current();
        first.leafEvents();
        first.popups().enqueue(popup("+330"));

        sessions.end(EndReason.SIGN_OUT);

        assertEquals(List.of("leafEvents stopped", "popups stopped"), services.stopped);
        assertTrue(sessions.current().popups().isIdle());
    }

    @Test
    void theSamePlayersNextSessionResumesTheLeafStreamWhereTheLastOneWas() {
        for (EndReason samePlayer : List.of(
                EndReason.SIGN_OUT,
                EndReason.CLEAR_SESSION,
                new EndReason.TokenRejected(AuthErrorCode.TOKEN_INVALID, "Rejected."))) {
            seenLeafEvent("event-41");

            sessions.end(samePlayer);
            ticks.runDue();

            assertEquals("event-41", sessions.current().leafEvents().cursor(), samePlayer + " keeps the place");
        }
    }

    @Test
    void anotherAccountsStreamStartsFromNow() {
        seenLeafEvent("event-41");

        sessions.end(new EndReason.AccountSwitch(OPERATOR, SHARED_ACCOUNT, false));

        assertNull(sessions.current().leafEvents().cursor());
    }

    @Test
    void anOperatorSwitchingBackKeepsTheSignInButNotTheSharedAccountsStreamPlace() {
        seenLeafEvent("event-41");

        sessions.end(new EndReason.AccountSwitch(SHARED_ACCOUNT, OPERATOR, true));

        assertNull(sessions.current().leafEvents().cursor());
    }

    @Test
    void aCursorCarriesAcrossSessionsThatNeverOpenedAStream() {
        seenLeafEvent("event-41");
        sessions.end(new EndReason.TokenRejected(AuthErrorCode.TOKEN_INVALID, "Rejected."));
        ticks.runDue();

        // This session ends before its stream is ever asked for.
        sessions.end(EndReason.SIGN_OUT);

        assertEquals("event-41", sessions.current().leafEvents().cursor());
    }

    /** Has the current session's stream seen {@code eventId}, as if it had read it from Leaf. */
    private void seenLeafEvent(String eventId) {
        services.nextStreamHasSeen = eventId;
        sessions.current().leafEvents();
    }

    private static com.seqwawa.seq.ui.unlock.UnlockPopup popup(String title) {
        return new com.seqwawa.seq.ui.unlock.UnlockPopup(
                com.seqwawa.seq.ui.unlock.UnlockPopup.Kind.TOAST, null, title, "The Nameless Anomaly", 330, 330, null, null);
    }

    private static void assertNotSignedIn(CompletableFuture<String> token) {
        CompletionException failure = assertThrows(CompletionException.class, token::join);
        assertInstanceOf(NotSignedInException.class, failure.getCause());
    }

    /**
     * The game's own objects for each session, recording when a session ends and which of
     * them it stopped. Nothing here reads the network until asked to.
     */
    static final class RecordingServices implements Session.Services {
        private final Session.Services live;
        private final List<String> calls;
        final List<String> stopped = new ArrayList<>();
        /** The next stream made starts as if it had already read this event, once. */
        String nextStreamHasSeen;
        private final java.util.Set<Session> watched = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

        RecordingServices(List<String> calls, java.util.concurrent.Executor gameThread) {
            this.calls = calls;
            this.live = new LiveSessionServices(gameThread);
        }

        /** Notes in the step list the moment {@code session} ends. */
        void recordEndOf(Session session) {
            if (watched.add(session)) {
                session.onEnd(() -> calls.add("end session"));
            }
        }

        private void watch(Session session, String name) {
            session.onEnd(() -> stopped.add(name + " stopped"));
        }

        @Override public com.seqwawa.seq.managers.LeafQuestService leafQuests(Session session) {
            watch(session, "leafQuests");
            return live.leafQuests(session);
        }
        @Override public com.seqwawa.seq.managers.GuildRaidProgressService raidProgress(Session session) {
            watch(session, "raidProgress");
            return live.raidProgress(session);
        }
        @Override public com.seqwawa.seq.managers.PrincessRaidStatsManager princessStats(Session session) {
            watch(session, "princessStats");
            return live.princessStats(session);
        }
        @Override public com.seqwawa.seq.network.auth.RaidProfilesSession raidProfiles(Session session) {
            watch(session, "raidProfiles");
            return live.raidProfiles(session);
        }
        @Override public com.seqwawa.seq.network.LeafEventStream leafEvents(Session session, String resumeAfter) {
            watch(session, "leafEvents");
            String seen = nextStreamHasSeen;
            nextStreamHasSeen = null;
            return live.leafEvents(session, seen != null ? seen : resumeAfter);
        }
        @Override public com.seqwawa.seq.ui.unlock.UnlockOverlay popups(Session session) {
            watch(session, "popups");
            return live.popups(session);
        }
    }

    /** Records each step by name, in the order it ran; the sign-in steps also end the auth service's sign-in. */
    static final class RecordingSteps implements SessionManager.Steps {
        final List<String> calls = new ArrayList<>();
        private final MinecraftAuthService auth;

        RecordingSteps(MinecraftAuthService auth) {
            this.auth = auth;
        }

        @Override public void resetGuildStorage() { calls.add("resetGuildStorage"); }
        @Override public void resetConnectionForAccountChange() { calls.add("resetConnectionForAccountChange"); }
        @Override public void clearSignIn() {
            calls.add("clearSignIn");
            auth.clearSession();
        }
        @Override public void signOut() {
            calls.add("signOut");
            auth.signOut();
        }
        @Override public void invalidateSignIn(AuthErrorCode code, String message) {
            calls.add("invalidateSignIn: " + code + " " + message);
            auth.invalidateSession(code, message);
        }
        @Override public void disconnect() { calls.add("disconnect"); }
        @Override public void dropPendingWarEvents() { calls.add("dropPendingWarEvents"); }
        @Override public void forgetDiscordName() { calls.add("forgetDiscordName"); }
        @Override public void tellPlayer(String message) { calls.add("tellPlayer: " + message); }
        @Override public void forgetPartyClassBroadcast() { calls.add("forgetPartyClassBroadcast"); }
        @Override public void resetPartyFinder() { calls.add("resetPartyFinder"); }
        @Override public void resetWynnPartySync() { calls.add("resetWynnPartySync"); }
        @Override public void resetRaidPartySnapshot() { calls.add("resetRaidPartySnapshot"); }
        @Override public void resetGuildPresence() { calls.add("resetGuildPresence"); }
        @Override public void resetGuildMembership() { calls.add("resetGuildMembership"); }
        @Override public void resetNicknames() { calls.add("resetNicknames"); }
        @Override public void resetWarTracking() { calls.add("resetWarTracking"); }
        @Override public void resetWarPlanning() { calls.add("resetWarPlanning"); }
        @Override public void resetWarPlanner() { calls.add("resetWarPlanner"); }
    }
}
