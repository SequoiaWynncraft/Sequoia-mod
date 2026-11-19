package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.network.auth.AuthErrorCode;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Pins what each way a signed-in socket can close does today, one case per
 * {@link BackendCloseClassifier.Kind}: whether it reconnects, what the player is told,
 * whether automatic connects pause, and what happens to the stored session.
 */
class ConnectionCloseTest {

    private static final String REFUSED_401 = "Invalid status code received: 401 Status line: HTTP/1.1 401 Unauthorized";
    private static final String REFUSED_403 = "Invalid status code received: 403 Status line: HTTP/1.1 403 Forbidden";
    private static final String REFUSED_502 = "Invalid status code received: 502 Status line: HTTP/1.1 502 Bad Gateway";

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

    /** Moves the clock in steps until the reconnect has a socket connecting: the reconnect delay, or the sign-in backoff. */
    private void waitForNextSocket() {
        int before = harness.sockets.size();
        for (int waited = 0; harness.sockets.size() == before || !harness.socket().connected(); waited += 100) {
            if (waited > 600_000) {
                throw new AssertionError("No reconnect within ten minutes");
            }
            harness.advance(100);
        }
    }

    /** How one close leaves the connection. */
    private record Outcome(boolean reconnects, boolean autoConnectPaused, List<String> told) {}

    private Outcome outcome() {
        return new Outcome(
                ConnectionManager.hasReconnectTask(),
                !ConnectionManager.canAutoConnectNow() && !ConnectionManager.hasReconnectTask(),
                List.copyOf(harness.told));
    }

    private static Stream<Arguments> backendCloses() {
        return Stream.of(
                // OTHER: a restart or dropped network, reconnect quietly.
                Arguments.of("going away (1001)", 1001, "", true, new Outcome(true, false, List.of())),
                Arguments.of("abnormal (1006)", 1006, "", true, new Outcome(true, false, List.of())),
                Arguments.of("normal close from the backend (1000)", 1000, "", true, new Outcome(true, false, List.of())),
                Arguments.of("server error (1011)", 1011, "internal error", true, new Outcome(true, false, List.of())),
                Arguments.of("refused handshake, 502", BackendCloseClassifier.HANDSHAKE_FAILED, REFUSED_502, false,
                        new Outcome(true, false, List.of())),
                // AUTH_REJECTED: the token is gone; sign in afresh on the reconnect.
                Arguments.of("account unlinked (1008)", 1008, "account_unlinked", true, new Outcome(true, false, List.of())),
                Arguments.of("guild left (1008)", 1008, "guild_leave", true, new Outcome(true, false, List.of())),
                Arguments.of("an unknown policy reason (1008)", 1008, "something_new", true,
                        new Outcome(true, false, List.of())),
                Arguments.of("refused handshake, 401", BackendCloseClassifier.HANDSHAKE_FAILED, REFUSED_401, false,
                        new Outcome(true, false, List.of())),
                // Refusals no retry fixes: pause until the player connects.
                Arguments.of("version unsupported (1008)", 1008, "mod_version_unsupported", true, new Outcome(false, true,
                        List.of("This version of Sequoia is no longer supported. Update the mod, then run /seq connect."))),
                Arguments.of("party finder ban (1008)", 1008, "party_finder_banned", true, new Outcome(false, true,
                        List.of("Sequoia closed the connection: this account is banned from the party finder. "
                                + "Run /seq connect to try again."))),
                Arguments.of("refused handshake, 403", BackendCloseClassifier.HANDSHAKE_FAILED, REFUSED_403, false,
                        new Outcome(false, true,
                                List.of("Sequoia refused to connect this account. Run /seq connect to try again."))),
                // TREASURY_AUTH_FAILED: the error before it already told the player.
                Arguments.of("treasury auth failed (1008)", 1008, "treasury_auth_failed", true,
                        new Outcome(false, false, List.of())),
                Arguments.of("treasury auth timeout (1008)", 1008, "treasury_auth_timeout", true,
                        new Outcome(false, false, List.of())));
    }

    /**
     * A close from the backend ends a signed-in socket; a refused handshake ends one that never
     * opened, and Java-WebSocket reports it as a local close carrying the HTTP status.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("backendCloses")
    void eachCloseFromTheBackendReconnectsOrStopsAsItAlwaysHas(
            String name, int code, String reason, boolean afterSignIn, Outcome expected) {
        if (afterSignIn) {
            harness.connectAsMember().closeFromBackend(code, reason);
        } else {
            harness.connectAutomatically();
            harness.socket().closeLocally(code, reason);
        }
        harness.settle();

        assertFalse(ConnectionManager.isConnected());
        assertEquals(expected, outcome());
    }

    @Test
    void aRevokedTokenIsDroppedSoTheReconnectSignsInAfresh() {
        harness.connectAsMember();

        harness.closeFromBackend(BackendCloseClassifier.POLICY_VIOLATION, "account_revoked");

        assertEquals(List.of(AuthErrorCode.WEBSOCKET_AUTH_REJECTED), harness.tokens.invalidations);
        // The reconnect waits out the sign-in backoff the refusal started, which spans reconnects since step 6.
        harness.advance(1_999);
        assertEquals(1, harness.sockets.size());
        harness.advance(1);
        assertEquals(2, harness.sockets.size());
        assertEquals(List.of(false, false), harness.tokens.requests,
                "the reconnect asks for a token, which the invalidated session no longer has");
    }

    @Test
    void anOrdinaryCloseKeepsTheSession() {
        harness.connectAsMember();

        harness.closeFromBackend(1006, "");

        assertEquals(List.of(), harness.tokens.invalidations);
    }

    @Test
    void aRevokedTokenIsReportedOnlyWhileThePlayerWaitsOnAConnect() {
        harness.connectManually();
        // Refused before it opened: the flow the player started is still running.
        harness.socket().closeLocally(BackendCloseClassifier.HANDSHAKE_FAILED, REFUSED_401);
        harness.settle();

        assertEquals(List.of(
                "Connecting to production...",
                "Backend rejected websocket authentication. Re-authenticating."), harness.told);
    }

    @Test
    void threeRefusedSignInsInARowStopReconnectingUntilThePlayerConnects() {
        harness.connectAutomatically();
        for (int refusal = 1; refusal <= 3; refusal++) {
            harness.socket().closeLocally(BackendCloseClassifier.HANDSHAKE_FAILED, REFUSED_401);
            harness.settle();
            if (refusal < 3) {
                assertTrue(ConnectionManager.hasReconnectTask(), "refusal " + refusal);
                waitForNextSocket();
            }
        }

        assertFalse(ConnectionManager.hasReconnectTask());
        assertFalse(ConnectionManager.canAutoConnectNow());
        assertEquals(List.of("Sequoia keeps rejecting this session. Run /seq connect to try again."), harness.told);

        harness.connectManually();
        assertTrue(harness.socket().connected(), "the player's connect ends the pause");
    }

    @Test
    void anAcceptedSignInBetweenRefusalsStartsTheCountAgain() {
        harness.connectAsMember();
        harness.closeFromBackend(1008, "account_unlinked");
        waitForNextSocket();
        harness.socket().closeLocally(BackendCloseClassifier.HANDSHAKE_FAILED, REFUSED_401);
        harness.settle();
        waitForNextSocket();
        harness.open();
        harness.signIn();

        harness.closeFromBackend(1008, "account_unlinked");

        assertTrue(ConnectionManager.hasReconnectTask(), "this is the first refusal since a sign-in was accepted");
        assertEquals(List.of(), harness.told);
    }

    @Test
    void aVersionCloseAfterTheBackendExplainedSaysNothingMore() {
        harness.connectAsMember();
        harness.receive("{\"type\":\"error\",\"code\":\"mod_version_unsupported\",\"status\":426,"
                + "\"message\":\"Update Sequoia to 1.13.0\",\"minimum_safe_version\":\"1.13.0\"}");

        harness.closeFromBackend(1008, "mod_version_unsupported");

        assertEquals(List.of("Update Sequoia to 1.13.0"), harness.told);
        assertFalse(ConnectionManager.canAutoConnectNow());
    }

    @Test
    void aPausedConnectionWaitsForThePlayersConnect() {
        harness.connectAsMember();
        harness.closeFromBackend(1008, "party_finder_banned");

        harness.connectAutomatically();
        assertEquals(1, harness.sockets.size());

        harness.connectManually();
        assertTrue(harness.socket().connected());
    }

    @Test
    void anAccountChangeEndsAPauseTheBackendCaused() {
        harness.connectAsMember();
        harness.closeFromBackend(1008, "party_finder_banned");

        ConnectionManager.resetForAccountChange();

        assertTrue(ConnectionManager.canAutoConnectNow());
    }

    @Test
    void aLocalCleanCloseIsTakenAsIntendedAndDoesNotReconnect() {
        FakeBackendSocket socket = harness.connectAsMember();

        socket.closeLocally(1000, "");
        harness.settle();

        assertFalse(ConnectionManager.hasReconnectTask());
        assertTrue(ConnectionManager.canAutoConnectNow());
    }

    @Test
    void aLocalAbnormalCloseReconnects() {
        FakeBackendSocket socket = harness.connectAsMember();

        socket.closeLocally(1006, "");
        harness.settle();

        assertTrue(ConnectionManager.hasReconnectTask());
    }

    @Test
    void theEchoOfTheClientsOwnCloseIsNotTakenForTheBackendsVerdict() {
        FakeBackendSocket socket = harness.connectAsMember();
        harness.connection().disconnect();

        // The backend sends the close back, marked remote; it is still the client's own close.
        socket.echoClientClose();
        harness.settle();

        assertEquals(List.of(), harness.tokens.invalidations);
        assertFalse(ConnectionManager.hasReconnectTask(), "the client meant to close");
    }

    @Test
    void aCloseFromAReplacedSocketChangesNothing() {
        FakeBackendSocket first = harness.connectAsMember();
        harness.connection().disconnect();
        first.echoClientClose();
        harness.settle();
        harness.connectAutomatically();
        harness.open();
        harness.signIn();

        first.closeFromBackend(1008, "account_unlinked");
        harness.settle();

        assertTrue(ConnectionManager.isConnected());
        assertEquals(List.of(), harness.tokens.invalidations);
        assertFalse(ConnectionManager.hasReconnectTask());
    }

    @Test
    void aSocketThatFailsToConnectIsReportedOnlyWhenThePlayerAsked() {
        harness.connectAutomatically();
        harness.socket().failToConnect("Connection refused");
        assertTrue(harness.socket().isClosing(), "a socket that failed to connect can't be used again");
        harness.settle();
        assertEquals(List.of(), harness.told);
        assertTrue(ConnectionManager.hasReconnectTask());

        harness.connectManually();
        harness.socket().failToConnect("Connection refused");
        harness.settle();
        assertEquals("Connection error: Connection refused", harness.told.getLast());
    }
}
