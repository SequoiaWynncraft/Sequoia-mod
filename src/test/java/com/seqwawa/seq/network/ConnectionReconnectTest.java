package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins when automatic reconnects run, how they back off, and what stops them. */
class ConnectionReconnectTest {

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

    /** Moves the clock in steps until a new socket starts connecting; returns how long that took. */
    private long waitForNextSocket() {
        int before = harness.sockets.size();
        long waited = 0;
        while (harness.sockets.size() == before || !harness.socket().connected()) {
            if (waited > 600_000) {
                throw new AssertionError("No reconnect within ten minutes");
            }
            harness.advance(100);
            waited += 100;
        }
        return waited;
    }

    @Test
    void aReconnectWaitsASecondAfterTheSocketDrops() {
        harness.connectAsMember();
        harness.closeFromBackend(1006, "");

        harness.advance(999);
        assertEquals(1, harness.sockets.size(), "not yet");
        assertTrue(ConnectionManager.hasReconnectTask());

        harness.advance(1);
        assertEquals(2, harness.sockets.size());
        assertTrue(harness.socket().connected());
        assertFalse(ConnectionManager.hasReconnectTask(), "a reconnect that has started no longer waits");
    }

    @Test
    void reconnectsThatNeverOpenBackOffThenHandOverToThePlayer() {
        harness.connectAsMember();
        harness.closeFromBackend(1006, "");

        List<Long> waits = new ArrayList<>();
        for (int attempt = 0; attempt < ReconnectBackoff.MAX_ATTEMPTS; attempt++) {
            waits.add(waitForNextSocket());
            harness.socket().failToConnect("Connection refused");
            harness.settle();
        }

        assertEquals(List.of(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), waits);
        assertFalse(ConnectionManager.hasReconnectTask());
        assertEquals(List.of("Could not reconnect automatically. Run /seq connect manually."), harness.told);
        harness.advance(600_000);
        assertEquals(1 + ReconnectBackoff.MAX_ATTEMPTS, harness.sockets.size(), "nothing more after giving up");
    }

    @Test
    void afterGivingUpTheNextAutomaticConnectMayStillTry() {
        harness.connectAsMember();
        harness.closeFromBackend(1006, "");
        for (int attempt = 0; attempt < ReconnectBackoff.MAX_ATTEMPTS; attempt++) {
            waitForNextSocket();
            harness.socket().failToConnect("Connection refused");
            harness.settle();
        }

        assertTrue(ConnectionManager.canAutoConnectNow(), "the game's periodic recovery may connect again");
    }

    @Test
    void openingASocketStartsTheBackoffAgain() {
        harness.connectAsMember();
        harness.closeFromBackend(1006, "");
        waitForNextSocket();
        harness.socket().failToConnect("Connection refused");
        harness.settle();
        assertEquals(2_000L, waitForNextSocket());

        harness.open();
        harness.closeFromBackend(1006, "");

        assertEquals(1_000L, waitForNextSocket());
    }

    @Test
    void aReconnectDuringAServerTransferWaitsAgainWithoutUsingAnAttempt() {
        harness.connectAsMember();
        harness.closeFromBackend(1006, "");
        harness.scope = WynncraftServerPolicy.Scope.UNKNOWN;

        harness.advance(1_000);
        assertEquals(1, harness.sockets.size());
        assertTrue(ConnectionManager.hasReconnectTask());
        harness.advance(1_000);
        assertEquals(1, harness.sockets.size(), "it keeps waiting while the host is unknown");

        harness.scope = WynncraftServerPolicy.Scope.MAIN;
        assertEquals(1_000L, waitForNextSocket(), "the same one-second wait: no attempt was used");
    }

    @Test
    void aReconnectThatComesDueOnABlockedServerIsDropped() {
        harness.connectAsMember();
        harness.closeFromBackend(1006, "");
        harness.scope = WynncraftServerPolicy.Scope.BLOCKED;

        harness.advance(1_000);

        assertFalse(ConnectionManager.hasReconnectTask());
        harness.scope = WynncraftServerPolicy.Scope.MAIN;
        harness.advance(60_000);
        assertEquals(1, harness.sockets.size());
    }

    @Test
    void aCloseOnABlockedServerSchedulesNoReconnect() {
        harness.connectAsMember();
        harness.scope = WynncraftServerPolicy.Scope.BLOCKED;

        harness.closeFromBackend(1006, "");

        assertFalse(ConnectionManager.hasReconnectTask());
    }

    @Test
    void aManualConnectReplacesAWaitingReconnectSoOnlyOneSocketOpens() {
        harness.connectAsMember();
        harness.closeFromBackend(1006, "");

        harness.connectManually();
        harness.advance(60_000);

        assertEquals(2, harness.sockets.size());
        assertFalse(ConnectionManager.hasReconnectTask());
    }

    @Test
    void aReconnectThatFindsAConnectionAlreadyUnderWayLeavesItAlone() {
        harness.connectAsMember();
        harness.closeFromBackend(1006, "");
        harness.tokens.token = null;
        // An automatic connect cannot start while a reconnect waits, but a connect from code
        // that does not check, such as the welcome-message hook's /seq connect, can.
        harness.connection().connect();
        harness.settle();
        assertEquals(1, harness.tokens.pending.size());

        harness.advance(1_000);

        assertEquals(2, harness.sockets.size(), "the waiting reconnect made no third socket");
        assertEquals(1, harness.tokens.pending.size());
    }

    @Test
    void whileAReconnectWaitsAutomaticConnectsHoldOff() {
        harness.connectAsMember();
        harness.closeFromBackend(1006, "");

        assertFalse(ConnectionManager.canAutoConnectNow());
    }

    @Test
    void aSignInThatMayWorkLaterReconnectsOnTheSameBackoff() {
        harness.tokens.token = null;
        harness.connectAutomatically();
        harness.failToken(new com.seqwawa.seq.network.auth.AuthException(
                com.seqwawa.seq.network.auth.AuthErrorCode.BACKEND_UNAVAILABLE, "Backend unavailable", true));

        harness.advance(999);
        assertEquals(1, harness.tokens.requests.size());
        harness.advance(1);
        assertEquals(2, harness.tokens.requests.size());
        assertEquals(2, harness.sockets.size(), "the reconnect starts from a new socket");
    }

    @Test
    void theClientsOwnCloseToSignInAgainReconnects() {
        FakeBackendSocket socket = harness.connectAsMember();
        harness.receive("{\"type\":\"error\",\"code\":\"token_expired\",\"message\":\"Token expired\"}");
        assertEquals(4001, socket.closeCode());

        harness.echoClientClose();

        assertEquals(1_000L, waitForNextSocket());
    }

    @Test
    void stoppingAllTrafficStopsTheReconnectToo() {
        harness.connectAsMember();
        harness.closeFromBackend(1006, "");

        harness.connection().disconnect();
        harness.advance(60_000);

        assertEquals(2, harness.sockets.size(), "only the instance disconnect made, which never connects");
        assertFalse(harness.socket().connected());
    }
}
