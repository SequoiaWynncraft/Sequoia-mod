package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.WynnClassType;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.AuthException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins how a connection goes from nothing to ready, through the socket port, as it behaves
 * today: a member, a non-member and the Treasury account, connecting on their own or because
 * the player asked, and how connecting, disconnecting and signing out end.
 */
class ConnectionLifecycleTest {

    @TempDir
    Path config;

    private ConnectionHarness harness;

    @BeforeEach
    void start() {
        harness = new ConnectionHarness(config);
    }

    @AfterEach
    void stop() {
        harness.close();
    }

    // ── A member ──

    @Test
    void anAutomaticConnectSignsInWithTheStoredTokenAndSaysNothing() {
        harness.connectAutomatically();

        assertEquals(List.of(false), harness.tokens.requests, "an automatic connect uses the token it has");
        FakeBackendSocket socket = harness.socket();
        assertTrue(socket.connected());
        assertEquals("Bearer session-token", socket.headers().get("Authorization"));
        assertTrue(socket.headers().containsKey(ClientVersion.MOD_VERSION_HEADER));
        assertEquals(List.of(), harness.told);
    }

    @Test
    void theTokenIsHandedToTheGameThreadBeforeTheSocketConnects() {
        harness.connection().connect();

        assertFalse(harness.socket().connected(), "nothing connects inside the call that asked");
        harness.settle();
        assertTrue(harness.socket().connected());
    }

    @Test
    void anOpenSocketIsNotConnectedUntilTheBackendAcceptsTheToken() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();

        assertTrue(harness.connection().isOpen());
        assertFalse(ConnectionManager.isConnected());
        assertNull(harness.connection().getConnectedSince());
        assertEquals(List.of(), socket.sentTypes());
    }

    @Test
    void signingInProbesMembershipAndRemembersTheLinkedDiscordName() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        harness.signIn();

        assertTrue(ConnectionManager.isConnected());
        assertTrue(harness.connection().isAuthenticated());
        assertEquals(Instant.parse("2026-11-18T12:00:00Z").plusMillis(harness.nowMs.get()),
                harness.connection().getConnectedSince());
        assertEquals(List.of("get_connected"), socket.sentTypes());
        assertEquals("sequoian", harness.config.getDiscordUsername(ConnectionHarness.PROFILE));
        assertTrue(harness.connection().isDiscordLinked());
    }

    @Test
    void signingInWithoutADiscordNameForgetsTheOneStored() {
        harness.config.setDiscordUsername(ConnectionHarness.PROFILE, "someone-else");
        harness.connectAutomatically();
        harness.open();

        harness.receive("{\"type\":\"authenticated\"}");

        assertNull(harness.config.getDiscordUsername(ConnectionHarness.PROFILE));
        assertFalse(harness.connection().isDiscordLinked());
    }

    @Test
    void signingInSendsThePartyClassWhenTheGameKnowsIt() {
        harness.game.classType = WynnClassType.MAGE;
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();

        harness.signIn();

        assertEquals(List.of("get_connected", "party_class_update"), socket.sentTypes());
        assertEquals("MAGE", socket.lastSent("party_class_update").get("class_type").getAsString());
    }

    @Test
    void aConfirmedMemberIsReadyForMemberTraffic() {
        harness.connectAsMember();

        assertTrue(ConnectionManager.isConnected());
        assertTrue(ConnectionManager.isLiveWarTelemetryReady());
        assertFalse(ConnectionManager.isTreasuryOutConnected());
        assertFalse(ConnectionManager.canAutoConnectNow());
        assertEquals(List.of(), harness.told);
    }

    @Test
    void theUptimeCountsFromSigningIn() {
        harness.connectAsMember();

        harness.advance(65_000);

        assertEquals("1m 5s", harness.connection().getUptimeString());
        harness.advance(3_600_000);
        assertEquals("1h 1m", harness.connection().getUptimeString());
    }

    @Test
    void aManualConnectRefreshesTheTokenAndReportsEachStepUntilTheSocketOpens() {
        harness.connectManually();

        assertEquals(List.of(true), harness.tokens.requests, "the player asked: sign in afresh");
        assertEquals(1, harness.tokens.allowSignIns);
        assertEquals(List.of("Connecting to production..."), harness.told);

        harness.open();
        assertEquals(List.of(
                "Connecting to production...",
                "Connected websocket to production. Waiting for backend authentication..."), harness.told);

        harness.signIn();
        assertEquals(2, harness.told.size(), "the flow the player started ended when the socket opened");
    }

    @Test
    void theOpenNoticeNamesTheAccountTheSignInSpeaksFor() {
        harness.tokens.signedInUsername = "Sequoian";
        harness.connectManually();

        harness.open();

        assertEquals("Connected websocket for Sequoian. Waiting for backend authentication...", harness.told.getLast());
    }

    @Test
    void connectingAgainWhileOpenSaysSoAndMakesNoSecondSocket() {
        harness.connectAsMember();

        harness.connectManually();

        assertEquals(List.of("Already connected/connecting."), harness.told);
        assertEquals(1, harness.sockets.size());
    }

    @Test
    void connectingAgainWhileSigningInSaysSoAndAsksForNoSecondToken() {
        harness.tokens.token = null;
        harness.connectAutomatically();

        harness.connectManually();

        assertEquals(List.of("Already connected/connecting."), harness.told);
        assertEquals(1, harness.tokens.requests.size());
        assertEquals(1, harness.tokens.pending.size());
    }

    // ── A non-member ──

    @Test
    void aNonMemberIsToldOnceAndKeepsOnlyTheTrafficEveryoneMaySend() {
        FakeBackendSocket socket = harness.connectAsMember();
        socket.clearSent();

        harness.rejectMembership();
        harness.rejectMembership();

        assertEquals(List.of("You are not a Sequoia member. Sequoia-only features are disabled for this session."),
                harness.told);
        assertTrue(ConnectionManager.isConnected(), "the session stays up for party finder traffic");
        assertFalse(ConnectionManager.isLiveWarTelemetryReady());
        harness.advance(50);
        assertFalse(harness.connection().sendGuildChat("Sequoian", null, "hello", null, List.of(), false));
        harness.advance(50);
        assertTrue(harness.connection().sendPartySyncMemberRemoved("Other", "left"));
        assertEquals(List.of("party_sync_member_removed"), socket.sentTypes());
    }

    @Test
    void aNonMemberAsksForConnectedUsersAndIsAnsweredWithNoOneAtOnce() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        harness.signIn();
        harness.rejectMembership();
        socket.clearSent();
        List<List<String>> answers = new ArrayList<>();

        harness.connection().requestConnectedUsers(answers::add);

        assertEquals(List.of(List.of()), answers);
        assertEquals(List.of(), socket.sentTypes());
    }

    @Test
    void aNonMembersWarEventsAreAcceptedButNeverSent() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        harness.signIn();
        harness.rejectMembership();
        socket.clearSent();

        assertTrue(harness.connection().sendGuildWarQueue(new com.seqwawa.seq.model.GuildWarQueueSubmission(
                "Olux", "a9e1132c-c13f-4fd4-abea-812ebf35d5c3", "2026-11-18T12:00:00Z", "Low", 5)));
        harness.advance(1_000);
        ConnectionManager.flushPendingOutbound();

        assertEquals(List.of(), socket.sentTypes());
    }

    // ── The Treasury account ──

    @Test
    void theTreasuryAccountConnectsWithoutABearerToken() {
        harness.game.username = ConnectionHarness.TREASURY;

        harness.connectAutomatically();

        assertEquals(List.of(), harness.tokens.requests);
        assertTrue(harness.socket().connected(), "it connects within the call: there is no token to wait for");
        assertNull(harness.socket().headers().get("Authorization"));
        assertTrue(harness.socket().headers().containsKey(ClientVersion.MOD_VERSION_HEADER));
    }

    @Test
    void theTreasuryAccountIsReadyOnceItsSessionProofIsAccepted() {
        harness.game.username = ConnectionHarness.TREASURY;
        harness.connectManually();
        FakeBackendSocket socket = harness.open();
        assertFalse(ConnectionManager.isTreasuryOutConnected());

        harness.challengeTreasury();
        assertEquals(1, harness.game.joinedServerIds.size());
        assertNotNull(socket.lastSent(TreasuryAuthResponse.TYPE));
        assertFalse(ConnectionManager.isTreasuryOutConnected(), "the backend has yet to check the proof");

        harness.acceptTreasuryProof();

        assertTrue(ConnectionManager.isTreasuryOutConnected());
        assertFalse(ConnectionManager.isConnected(), "a Treasury socket never signs in with a token");
        assertNotNull(harness.connection().getConnectedSince());
        assertEquals(List.of(
                "Connecting to production...",
                "Connected for Treasury OUT. Verifying the cinfrascitizen Minecraft session...",
                "Treasury OUT identity verified as cinfrascitizen."), harness.told);
    }

    // ── Where it may connect ──

    @Test
    void aManualConnectDuringAServerTransferWaitsAndSaysSo() {
        harness.scope = WynncraftServerPolicy.Scope.UNKNOWN;

        harness.connectManually();

        assertEquals(List.of("Waiting until Wynncraft server transfer finishes."), harness.told);
        assertEquals(List.of(), harness.tokens.requests);
        assertFalse(ConnectionManager.hasReconnectTask());
        assertTrue(ConnectionManager.canAutoConnectNow(), "the next automatic connect may try again");
    }

    @Test
    void aManualConnectOffTheMainServerIsRefusedAndStopsReconnecting() {
        harness.scope = WynncraftServerPolicy.Scope.BLOCKED;

        harness.connectManually();

        assertEquals(List.of(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE), harness.told);
        assertEquals(List.of(), harness.tokens.requests);
    }

    @Test
    void anAutomaticConnectOffTheMainServerDoesNothingQuietly() {
        harness.scope = WynncraftServerPolicy.Scope.BLOCKED;

        harness.connectAutomatically();

        assertEquals(List.of(), harness.told);
        assertEquals(List.of(), harness.tokens.requests);
    }

    @Test
    void leavingForABlockedServerClosesTheSocketAndSaysWhy() {
        FakeBackendSocket socket = harness.connectAsMember();
        harness.scope = WynncraftServerPolicy.Scope.BLOCKED;

        ConnectionManager.disconnectForBlockedServer();
        harness.settle();

        assertEquals(1, socket.closes());
        assertEquals(List.of(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE), harness.told);
        assertFalse(ConnectionManager.isConnected());
        harness.echoClientClose();
        assertFalse(ConnectionManager.hasReconnectTask());
    }

    @Test
    void leavingForABlockedServerWithNothingConnectedSaysNothing() {
        harness.connection();
        harness.scope = WynncraftServerPolicy.Scope.BLOCKED;

        ConnectionManager.disconnectForBlockedServer();

        assertEquals(List.of(), harness.told);
    }

    @Test
    void onTheMainServerTheBlockedServerHookLeavesTheConnectionAlone() {
        FakeBackendSocket socket = harness.connectAsMember();

        ConnectionManager.disconnectForBlockedServer();

        assertEquals(0, socket.closes());
        assertTrue(ConnectionManager.isConnected());
    }

    // ── Ending a connection ──

    @Test
    void aManualDisconnectClosesTheSocketAndHoldsOffAutomaticConnects() {
        FakeBackendSocket socket = harness.connectAsMember();

        harness.connection().disconnectManually();
        harness.settle();

        assertEquals(List.of("Disconnected"), harness.told);
        assertEquals(1000, socket.closeCode());
        assertFalse(ConnectionManager.isConnected());
        assertNull(harness.connection().getConnectedSince());
        harness.echoClientClose();
        assertFalse(ConnectionManager.hasReconnectTask());
        assertTrue(ConnectionManager.isAutoConnectSuppressedByManualDisconnect());
        assertFalse(ConnectionManager.canAutoConnectNow());

        harness.connectAutomatically();
        assertFalse(harness.socket().connected(), "an automatic connect waits for the player");
        harness.connectManually();
        assertEquals(2, harness.sockets.size());
        assertTrue(harness.socket().connected());
        assertFalse(ConnectionManager.isAutoConnectSuppressedByManualDisconnect());
    }

    @Test
    void aManualDisconnectWithNothingConnectedSaysSo() {
        harness.connection().disconnectManually();

        assertEquals(List.of("Not connected"), harness.told);
    }

    @Test
    void anAutomaticDisconnectClosesQuietlyAndDoesNotHoldOffTheNextConnect() {
        FakeBackendSocket socket = harness.connectAsMember();

        harness.connection().disconnect();
        harness.echoClientClose();

        assertEquals(1, socket.closes());
        assertEquals(List.of(), harness.told);
        assertFalse(ConnectionManager.hasReconnectTask());
        assertTrue(ConnectionManager.canAutoConnectNow());
    }

    @Test
    void aDisconnectBeforeTheTokenArrivesStopsThatSignInFromConnecting() {
        harness.tokens.token = null;
        harness.connectAutomatically();
        ConnectionManager first = harness.connection();

        first.disconnect();
        harness.answerToken("late-token");

        assertFalse(harness.sockets.getFirst().connected());
        assertNotSame(first, harness.connection());
    }

    @Test
    void signingOutClosesTheSocketAndNothingConnectsUntilThePlayerAsks() {
        FakeBackendSocket socket = harness.connectAsMember();

        harness.signOut();
        harness.echoClientClose();

        assertEquals(1, socket.closes());
        assertEquals(List.of(), harness.told);
        assertFalse(ConnectionManager.canAutoConnectNow());
        harness.connectAutomatically();
        assertFalse(harness.socket().connected());

        harness.connectManually();
        assertEquals(2, harness.sockets.size());
        assertTrue(harness.socket().connected());
        assertFalse(harness.tokens.signedOutByUser);
    }

    @Test
    void anAccountChangeDropsTheConnection() {
        FakeBackendSocket socket = harness.connectAsMember();
        ConnectionManager before = harness.connection();

        ConnectionManager.resetForAccountChange();

        assertEquals(1, socket.closes());
        assertFalse(ConnectionManager.isConnected());
        harness.echoClientClose();
        assertNotSame(before, harness.connection());
        assertTrue(ConnectionManager.canAutoConnectNow());
    }

    @Test
    void anErrorThatLeavesTheSocketOpenEndsTheSignInButKeepsTheSocket() {
        FakeBackendSocket socket = harness.connectAsMember();

        socket.error(new RuntimeException("handler failed"));
        harness.settle();

        assertFalse(ConnectionManager.isConnected());
        assertNull(harness.connection().getConnectedSince());
        assertTrue(harness.connection().isOpen());
        assertEquals(List.of(), harness.told);
        // Pinned as it is today: while that socket stays open, a connect can't replace it.
        harness.connectManually();
        assertEquals(List.of("Already connected/connecting."), harness.told);
    }

    @Test
    void anErrorForgetsTheWholeSessionAsACloseDoes() {
        FakeBackendSocket socket = harness.connectAsMember();
        harness.receive("{\"type\":\"error\",\"code\":\"token_invalid\",\"message\":\"Token invalid\"}");
        assertTrue(harness.connection().isAuthFailed());

        // Before the backend answers the client's close, the socket fails.
        socket.error(new java.io.IOException("Broken pipe"));
        harness.settle();

        assertFalse(harness.connection().isAuthFailed());
        assertFalse(harness.connection().isNotInGuild());
        assertFalse(ConnectionManager.isConnected());
    }

    // ── When signing in fails ──

    @Test
    void aSignInThatMayWorkLaterRetriesQuietly() {
        harness.tokens.token = null;
        harness.connectAutomatically();

        harness.failToken(new AuthException(AuthErrorCode.NETWORK_FAILURE, "Backend unreachable", true));

        assertEquals(List.of(), harness.told);
        assertTrue(harness.connection().isAuthFailed());
        assertTrue(ConnectionManager.hasReconnectTask());
        assertFalse(harness.socket().connected());
    }

    @Test
    void aSignInThatCannotWorkIsShownEvenWhenNobodyAsked() {
        harness.tokens.token = null;
        harness.connectAutomatically();

        harness.failToken(new AuthException(AuthErrorCode.MINECRAFT_SESSION_INVALID, "Restart Minecraft", false));

        assertEquals(List.of("Restart Minecraft"), harness.told);
        assertFalse(ConnectionManager.hasReconnectTask());
    }

    @Test
    void anUnexpectedSignInFailureCountsAsANetworkFailureAndRetries() {
        harness.tokens.token = null;
        harness.connectAutomatically();

        harness.failToken(new IllegalStateException("socket reset"));

        assertEquals(List.of(), harness.told);
        assertTrue(ConnectionManager.hasReconnectTask());
    }

    @Test
    void anUnlinkedAccountIsWarnedWithADismissButtonForThatAccount() {
        harness.tokens.token = null;
        harness.connectManually();

        harness.failToken(new AuthException(AuthErrorCode.ACCOUNT_NOT_LINKED, "Link your account first", false));

        assertEquals(List.of("Connecting to production..."), harness.told);
        assertEquals(1, harness.toldLines.size());
        var line = harness.toldLines.getFirst();
        assertTrue(line.getString().contains("Link your account first"), line.getString());
        var clicks = new java.util.HashSet<net.minecraft.network.chat.ClickEvent>();
        line.visit((style, text) -> {
            if (style.getClickEvent() != null) clicks.add(style.getClickEvent());
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        assertEquals(java.util.Set.of(new net.minecraft.network.chat.ClickEvent.RunCommand(
                "/seq _dismiss-unlinked " + ConnectionHarness.PROFILE)), clicks);
    }

    @Test
    void aDismissedUnlinkedWarningStaysHiddenOnAutomaticRetries() {
        harness.tokens.token = null;
        harness.connectAutomatically();
        harness.failToken(new AuthException(AuthErrorCode.ACCOUNT_NOT_LINKED, "Link your account first", true));
        assertEquals(1, harness.toldLines.size());
        com.seqwawa.seq.network.auth.UnlinkedAccountWarning.getInstance().dismiss(ConnectionHarness.PROFILE);

        harness.advance(1_000);
        harness.failToken(new AuthException(AuthErrorCode.ACCOUNT_NOT_LINKED, "Link your account first", true));

        assertEquals(1, harness.toldLines.size());
    }

    @Test
    void aSecondConnectOnTheSameSocketRightAfterARefusedSignInIsThrottled() {
        harness.tokens.token = null;
        harness.connectAutomatically();
        harness.failToken(new AuthException(AuthErrorCode.MINECRAFT_SESSION_INVALID, "Restart Minecraft", false));
        ConnectionManager failed = harness.connection();
        harness.told.clear();

        harness.connectManually();

        assertSame(failed, harness.connection(), "nothing replaced the socket, so its backoff still applies");
        assertEquals(List.of("Connecting to production...", "Auth throttled. Retrying in 2s."), harness.told);
        assertEquals(1, harness.tokens.requests.size());
        assertFalse(ConnectionManager.hasReconnectTask(), "despite its wording, nothing retries");

        harness.advance(2_000);
        harness.connectManually();
        assertEquals(2, harness.tokens.requests.size());
    }

    @Test
    void aTokenThatArrivesAfterLeavingTheMainServerWaitsForTheTransfer() {
        harness.tokens.token = null;
        harness.connectAutomatically();
        harness.scope = WynncraftServerPolicy.Scope.UNKNOWN;

        harness.answerToken("token");

        assertFalse(harness.socket().connected());
        assertTrue(ConnectionManager.hasReconnectTask());
        harness.scope = WynncraftServerPolicy.Scope.MAIN;
        harness.tokens.token = "token";
        harness.advance(1_000);
        assertEquals(2, harness.sockets.size());
        assertTrue(harness.socket().connected());
    }

    @Test
    void aTokenThatArrivesOnABlockedServerIsDropped() {
        harness.tokens.token = null;
        harness.connectManually();
        harness.scope = WynncraftServerPolicy.Scope.BLOCKED;

        harness.answerToken("token");

        assertFalse(harness.socket().connected());
        assertFalse(ConnectionManager.hasReconnectTask());
        assertEquals(WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE, harness.told.getLast());
    }

    @Test
    void aSocketThatFailsToStartIsDroppedAndRetried() {
        ConnectionManager first = harness.connection();
        harness.socket().connectFailure = new IllegalStateException("no route to host");

        harness.connectManually();

        assertEquals("Failed to connect: no route to host", harness.told.getLast());
        assertTrue(ConnectionManager.hasReconnectTask());
        assertNotSame(first, harness.connection(), "the failed instance is dropped");
        harness.advance(1_000);
        assertTrue(harness.socket().connected());
    }
}
