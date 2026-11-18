package com.seqwawa.seq.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.client.TickQueue;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import java.util.ArrayList;
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

    private final RecordingSteps steps = new RecordingSteps();
    /** The game thread: what an end from another thread hands over waits here until a test runs it. */
    private final TickQueue ticks = new TickQueue(() -> 0L);
    private final SessionManager sessions = new SessionManager(steps, ticks);

    @Test
    void anAccountSwitchResetsWhatThePreviousAccountHadInOrder() {
        sessions.end(new EndReason.AccountSwitch(OPERATOR, SHARED_ACCOUNT, false));

        assertEquals(List.of(
                "resetGuildStorage",
                "resetConnectionForAccountChange",
                "clearSignIn",
                "forgetPartyClassBroadcast",
                "resetPartyFinder",
                "resetWynnPartySync",
                "resetRaidPartySnapshot",
                "resetGuildPresence",
                "resetGuildMembership",
                "resetNicknames",
                "clearRaidProfilesSignIn",
                "resetGuildRaidProgress",
                "resetLeafQuests",
                "resetLeafEvents",
                "clearPopups",
                "resetWarTracking",
                "resetWarPlanning",
                "resetPrincessStats"), steps.calls);
    }

    @Test
    void guildStorageHandsOverItsRewardsBeforeTheConnectionCloses() {
        sessions.end(new EndReason.AccountSwitch(OPERATOR, SHARED_ACCOUNT, false));

        assertTrue(steps.calls.indexOf("resetGuildStorage") < steps.calls.indexOf("resetConnectionForAccountChange"));
    }

    @Test
    void switchingToTheAccountTheSignInSpeaksForKeepsTheConnectionAndSignIn() {
        sessions.end(new EndReason.AccountSwitch(SHARED_ACCOUNT, OPERATOR, true));

        assertFalse(steps.calls.contains("resetConnectionForAccountChange"));
        assertFalse(steps.calls.contains("clearSignIn"));
        assertTrue(steps.calls.contains("resetLeafQuests"), "the account's own state still goes");
    }

    @Test
    void signingOutEndsTheSignInBeforeClosingTheConnection() {
        sessions.end(EndReason.SIGN_OUT);

        assertEquals(List.of(
                "signOut",
                "disconnect",
                "resetPartyFinder",
                "clearRaidProfilesSignIn",
                "resetWarPlanner"), steps.calls);
    }

    @Test
    void clearingTheSessionSignsOutAndForgetsTheDiscordNameAndQueuedWarEvents() {
        sessions.end(EndReason.CLEAR_SESSION);

        assertEquals(List.of(
                "dropPendingWarEvents",
                "signOut",
                "disconnect",
                "resetPartyFinder",
                "clearRaidProfilesSignIn",
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
        sessions.end(new EndReason.TokenRejected(AuthErrorCode.TOKEN_EXPIRED, "Backend token expired."));
        steps.calls.clear();

        ticks.runDue();

        assertEquals(List.of(
                "resetPartyFinder",
                "clearRaidProfilesSignIn",
                "resetGuildRaidProgress",
                "resetLeafQuests",
                "endLeafEventsKeepingCursor",
                "clearPopups",
                "resetWarPlanning",
                "resetPrincessStats"), steps.calls);
    }

    @Test
    void aRejectedTokenLeavesTheConnectionToReconnectByItself() {
        sessions.end(new EndReason.TokenRejected(AuthErrorCode.WEBSOCKET_AUTH_REJECTED, "Rejected."));
        ticks.runDue();

        assertFalse(steps.calls.contains("disconnect"));
        assertFalse(steps.calls.contains("signOut"), "a rejected token is not the player signing out");
        assertFalse(steps.calls.contains("resetLeafEvents"), "the same account's stream resumes");
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
        sessions.checkAccount(OPERATOR, "Operator");

        assertTrue(sessions.checkAccount(SHARED_ACCOUNT, "Shared"));
        assertFalse(sessions.checkAccount(SHARED_ACCOUNT, "Shared"), "the next tick is the same account");

        assertEquals(1, steps.calls.stream().filter("resetLeafQuests"::equals).count());
        assertTrue(steps.calls.contains("clearSignIn"));
    }

    @Test
    void switchingBackToTheOperatorTheSignInSpeaksForKeepsIt() {
        steps.signedInAccount = OPERATOR.toString();
        sessions.checkAccount(SHARED_ACCOUNT, "Shared");

        assertTrue(sessions.checkAccount(OPERATOR, "Operator"));

        assertFalse(steps.calls.contains("clearSignIn"));
    }

    @Test
    void switchingToTheSharedAccountDropsTheOperatorsSignIn() {
        assertFalse(SessionManager.signInSpeaksFor(SHARED_ACCOUNT, OPERATOR.toString()));
        assertFalse(SessionManager.signInSpeaksFor(SHARED_ACCOUNT, null));
        assertFalse(SessionManager.signInSpeaksFor(SHARED_ACCOUNT, "not-a-uuid"));
        assertTrue(SessionManager.signInSpeaksFor(OPERATOR, OPERATOR.toString()));
    }

    /** Records each step by name, in the order it ran. */
    static final class RecordingSteps implements SessionManager.Steps {
        final List<String> calls = new ArrayList<>();
        String signedInAccount;

        @Override public String signedInAccount() { return signedInAccount; }
        @Override public void resetGuildStorage() { calls.add("resetGuildStorage"); }
        @Override public void resetConnectionForAccountChange() { calls.add("resetConnectionForAccountChange"); }
        @Override public void clearSignIn() { calls.add("clearSignIn"); }
        @Override public void signOut() { calls.add("signOut"); }
        @Override public void invalidateSignIn(AuthErrorCode code, String message) {
            calls.add("invalidateSignIn: " + code + " " + message);
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
        @Override public void clearRaidProfilesSignIn() { calls.add("clearRaidProfilesSignIn"); }
        @Override public void resetGuildRaidProgress() { calls.add("resetGuildRaidProgress"); }
        @Override public void resetLeafQuests() { calls.add("resetLeafQuests"); }
        @Override public void resetLeafEvents() { calls.add("resetLeafEvents"); }
        @Override public void endLeafEventsKeepingCursor() { calls.add("endLeafEventsKeepingCursor"); }
        @Override public void clearPopups() { calls.add("clearPopups"); }
        @Override public void resetWarTracking() { calls.add("resetWarTracking"); }
        @Override public void resetWarPlanning() { calls.add("resetWarPlanning"); }
        @Override public void resetWarPlanner() { calls.add("resetWarPlanner"); }
        @Override public void resetPrincessStats() { calls.add("resetPrincessStats"); }
    }
}
