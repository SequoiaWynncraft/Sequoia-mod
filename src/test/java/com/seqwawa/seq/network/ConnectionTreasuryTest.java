package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.authlib.exceptions.AuthenticationException;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the Treasury account's connection: no bearer token, a Minecraft session proof for
 * the backend's challenge instead, and Treasury OUT requests once the proof is accepted.
 */
class ConnectionTreasuryTest {

    private static final String REQUEST_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final TreasuryOutRequest REQUEST =
            new TreasuryOutRequest(REQUEST_ID, "2stx", "cinfrascitizen", "season payout");
    private static final String PROOF_FAILED = "Treasury identity verification failed. Restart Minecraft and try again.";

    @TempDir
    Path config;

    private ConnectionHarness harness;

    @BeforeEach
    void start() {
        harness = new ConnectionHarness(config);
        harness.game.username = ConnectionHarness.TREASURY;
    }

    @AfterEach
    void stop() {
        harness.close();
    }

    @Test
    void theSessionProofAnswersTheBackendsNonceAndNeverSendsTheAccessToken() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();

        harness.challengeTreasury();

        assertEquals(1, harness.game.joinedServerIds.size());
        assertTrue(harness.game.joinedServerIds.getFirst().matches("[0-9a-f]{40}"),
                "the server id is derived here, never the backend's to choose");
        var response = socket.lastSent(TreasuryAuthResponse.TYPE);
        assertEquals(ConnectionHarness.TREASURY_NONCE, response.get("nonce").getAsString());
        assertTrue(response.has("client_nonce"));
        assertEquals(3, response.size());
    }

    @Test
    void theMinecraftSessionJoinRunsOffTheGameThread() {
        harness.holdProofs = true;
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();

        harness.challengeTreasury();
        assertEquals(List.of(), harness.game.joinedServerIds);
        assertNull(socket.lastSent(TreasuryAuthResponse.TYPE));

        harness.runHeldProofs();
        assertEquals(1, harness.game.joinedServerIds.size());
        assertTrue(socket.sentTypes().contains(TreasuryAuthResponse.TYPE));
    }

    @Test
    void aProofThatFinishesAfterTheSocketClosedIsNotSent() {
        harness.holdProofs = true;
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        harness.challengeTreasury();

        harness.closeFromBackend(1006, "");
        harness.runHeldProofs();

        assertEquals(List.of(), socket.sentTypes());
    }

    @Test
    void anInvalidChallengeFailsTheProofAndStopsReconnecting() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();

        harness.receive("{\"type\":\"treasury_auth_challenge\",\"nonce\":\"short\",\"proof_version\":2}");

        assertEquals(List.of(PROOF_FAILED), harness.told);
        assertEquals(List.of(), harness.game.joinedServerIds);
        assertEquals(BackendCloseClassifier.POLICY_VIOLATION, socket.closeCode());
        assertEquals("Treasury authentication failed", socket.closeReason());
        harness.echoClientClose();
        assertFalse(ConnectionManager.hasReconnectTask());
        assertFalse(ConnectionManager.isTreasuryOutConnected());
    }

    @Test
    void aChallengeForAnUnsupportedProofVersionFailsTheProof() {
        harness.connectAutomatically();
        harness.open();

        harness.receive("{\"type\":\"treasury_auth_challenge\",\"nonce\":\"" + ConnectionHarness.TREASURY_NONCE
                + "\",\"proof_version\":1}");

        assertEquals(List.of(PROOF_FAILED), harness.told);
        assertEquals(List.of(), harness.game.joinedServerIds);
    }

    @Test
    void mojangRefusingTheJoinFailsTheProof() {
        harness.game.joinFailure = new AuthenticationException("Invalid session");
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();

        harness.challengeTreasury();

        assertEquals(List.of(PROOF_FAILED), harness.told);
        assertEquals(BackendCloseClassifier.POLICY_VIOLATION, socket.closeCode());
        assertNull(socket.lastSent(TreasuryAuthResponse.TYPE));
    }

    @Test
    void aSecondChallengeIsIgnored() {
        harness.connectAutomatically();
        harness.open();
        harness.challengeTreasury();

        harness.challengeTreasury();

        assertEquals(1, harness.game.joinedServerIds.size());
        assertEquals(List.of(), harness.told);
    }

    @Test
    void aConfirmationForAnotherProofIsIgnored() {
        harness.connectAutomatically();
        harness.open();
        harness.challengeTreasury();

        harness.receive("{\"type\":\"treasury_authenticated\",\"nonce\":\"" + ConnectionHarness.TREASURY_NONCE
                + "\",\"client_nonce\":\"someone-elses\"}");

        assertFalse(ConnectionManager.isTreasuryOutConnected());
        harness.acceptTreasuryProof();
        assertTrue(ConnectionManager.isTreasuryOutConnected());
    }

    @Test
    void aTreasurySocketIgnoresATokenSignIn() {
        harness.connectAutomatically();
        harness.open();

        harness.signIn();

        assertFalse(ConnectionManager.isConnected());
        assertFalse(harness.connection().isAuthenticated());
    }

    @Test
    void aTokenSocketIgnoresATreasuryChallenge() {
        harness.game.username = ConnectionHarness.MEMBER;
        harness.connectAsMember();

        harness.challengeTreasury();

        assertEquals(List.of(), harness.game.joinedServerIds);
        assertTrue(ConnectionManager.isConnected());
    }

    @Test
    void theBackendRejectingTheProofIsShownAndStopsReconnecting() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        harness.challengeTreasury();

        harness.receive("{\"type\":\"error\",\"code\":\"treasury_auth_failed\",\"message\":\"Session not joined\"}");

        assertEquals(List.of("Treasury identity verification failed: Session not joined"), harness.told);
        assertEquals(BackendCloseClassifier.POLICY_VIOLATION, socket.closeCode());
        assertTrue(harness.connection().isAuthFailed());
        harness.echoClientClose();
        assertFalse(ConnectionManager.hasReconnectTask());
    }

    @Test
    void aTreasurySocketThatDropsReconnectsLikeAnyOther() {
        harness.connectAsTreasury();

        harness.closeFromBackend(1006, "");

        assertFalse(ConnectionManager.isTreasuryOutConnected());
        harness.advance(1_000);
        assertEquals(2, harness.sockets.size());
        assertTrue(harness.socket().connected());
    }

    // ── Treasury OUT ──

    @Test
    void treasuryOutWaitsForTheAcceptedProof() {
        harness.connectAutomatically();
        FakeBackendSocket socket = harness.open();
        assertFalse(harness.connection().sendTreasuryOut(REQUEST));

        harness.challengeTreasury();
        assertFalse(harness.connection().sendTreasuryOut(REQUEST));

        harness.acceptTreasuryProof();
        assertTrue(harness.connection().sendTreasuryOut(REQUEST));
        assertEquals(REQUEST_ID, socket.lastSent("treasury_out").get("request_id").getAsString());
    }

    @Test
    void treasuryOutIsOnlyForTheTreasuryAccount() {
        harness.connectAsTreasury();
        harness.game.username = "SomeoneElse";

        assertFalse(harness.connection().sendTreasuryOut(REQUEST));
    }

    @Test
    void treasuryOutRefusesAnIncompleteRequest() {
        harness.connectAsTreasury();

        assertFalse(harness.connection().sendTreasuryOut(null));
        assertFalse(harness.connection().sendTreasuryOut(new TreasuryOutRequest(REQUEST_ID, " ", "cinfrascitizen", "why")));
        assertFalse(harness.connection().sendTreasuryOut(new TreasuryOutRequest(REQUEST_ID, "1stx", "cinfrascitizen", "")));
    }

    @Test
    void treasuryOutGoesOnlyFromTheMainServer() {
        harness.connectAsTreasury();
        harness.scope = WynncraftServerPolicy.Scope.UNKNOWN;

        assertFalse(harness.connection().sendTreasuryOut(REQUEST));
    }

    @Test
    void treasuryOutSharesTheSendThrottle() {
        FakeBackendSocket socket = harness.connectAsTreasury();

        assertTrue(harness.connection().sendTreasuryOut(REQUEST));
        assertFalse(harness.connection().sendTreasuryOut(REQUEST));
        harness.advance(50);
        assertTrue(harness.connection().sendTreasuryOut(REQUEST));
        assertEquals(2, socket.sentTypes().stream().filter("treasury_out"::equals).count());
    }

    @Test
    void aRecordedPayoutReachesItsHandler() {
        List<TreasuryOutRecordedMessage> recorded = new ArrayList<>();
        ConnectionManager.onTreasuryOutRecorded(recorded::add);
        harness.connectAsTreasury();

        harness.receive("{\"type\":\"treasury_out_recorded\",\"request_id\":\"" + REQUEST_ID
                + "\",\"sheet_name\":\"Season 32\",\"row\":7}");

        assertEquals(1, recorded.size());
        assertEquals(REQUEST_ID, recorded.getFirst().requestId());
    }

    @Test
    void anErrorTheTreasuryHandlerClaimsIsNotShownAgain() {
        List<TreasuryOutErrorMessage> errors = new ArrayList<>();
        ConnectionManager.onTreasuryOutError(error -> errors.add(error));
        harness.connectAsTreasury();

        harness.receive("{\"type\":\"error\",\"request_id\":\"" + REQUEST_ID
                + "\",\"code\":\"invalid_request\",\"message\":\"amount must use a supported denomination\"}");

        assertEquals(1, errors.size());
        assertEquals(List.of(), harness.told);
        assertTrue(ConnectionManager.isTreasuryOutConnected());
    }

    @Test
    void anErrorTheTreasuryHandlerDoesNotClaimIsHandledAsAnyOther() {
        ConnectionManager.onTreasuryOutError(error -> false);
        harness.connectAsTreasury();

        harness.receive("{\"type\":\"error\",\"request_id\":\"" + REQUEST_ID
                + "\",\"code\":\"invalid_request\",\"message\":\"Something broke\"}");

        assertEquals(List.of("Error: Something broke"), harness.told);
    }

    @Test
    void aClaimedErrorThatRejectsTheTokenDropsItAndClosesToSignInAgain() {
        ConnectionManager.onTreasuryOutError(error -> true);
        FakeBackendSocket socket = harness.connectAsTreasury();

        harness.receive("{\"type\":\"error\",\"request_id\":\"" + REQUEST_ID
                + "\",\"code\":\"token_invalid\",\"message\":\"Token invalid\"}");

        assertEquals(List.of(AuthErrorCode.TOKEN_INVALID), harness.tokens.invalidations);
        assertEquals(4001, socket.closeCode());
        assertEquals(List.of(), harness.told, "the Treasury handler reports it");
    }

    @Test
    void aClaimedVersionErrorStopsReconnecting() {
        ConnectionManager.onTreasuryOutError(error -> true);
        harness.connectAsTreasury();

        harness.receive("{\"type\":\"error\",\"request_id\":\"" + REQUEST_ID
                + "\",\"code\":\"mod_version_unsupported\",\"message\":\"Update\"}");
        harness.closeFromBackend(1001, "");

        assertFalse(ConnectionManager.hasReconnectTask());
    }
}
