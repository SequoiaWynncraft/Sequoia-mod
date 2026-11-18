package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.network.BackendCloseClassifier.Kind;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The closes GameWebSocketHandler and its handshake interceptor produce, as Java-WebSocket reports them. */
class BackendCloseClassifierTest {

    @ParameterizedTest(name = "{0} \"{1}\" -> {2}")
    @MethodSource("backendCloses")
    void readsWhyTheBackendClosed(int code, String reason, Kind expected) {
        assertEquals(expected, BackendCloseClassifier.classify(code, reason, false));
    }

    private static Stream<Arguments> backendCloses() {
        return Stream.of(
                // GameWebSocketHandler closes these with POLICY_VIOLATION and a reason.
                Arguments.of(1008, "mod_version_unsupported", Kind.VERSION_UNSUPPORTED),
                Arguments.of(1008, "party_finder_banned", Kind.PARTY_FINDER_BANNED),
                Arguments.of(1008, "treasury_auth_failed", Kind.TREASURY_AUTH_FAILED),
                Arguments.of(1008, "treasury_auth_timeout", Kind.TREASURY_AUTH_FAILED),
                Arguments.of(1008, "account_unlinked", Kind.AUTH_REJECTED),
                Arguments.of(1008, "account_transferred", Kind.AUTH_REJECTED),
                Arguments.of(1008, "guild_leave", Kind.AUTH_REJECTED),
                Arguments.of(1008, "account_revoked", Kind.AUTH_REJECTED),
                // The handshake interceptor answers with an HTTP status; Tomcat sends no reason phrase.
                Arguments.of(1002, "Invalid status code received: 401 Status line: HTTP/1.1 401 ", Kind.AUTH_REJECTED),
                Arguments.of(
                        1002,
                        "Invalid status code received: 401 Status line: HTTP/1.1 401 Unauthorized",
                        Kind.AUTH_REJECTED),
                Arguments.of(
                        1002,
                        "Invalid status code received: 403 Status line: HTTP/1.1 403 ",
                        Kind.HANDSHAKE_FORBIDDEN),
                Arguments.of(
                        1002,
                        "Invalid status code received: 403 Status line: HTTP/1.1 403 Forbidden",
                        Kind.HANDSHAKE_FORBIDDEN),
                Arguments.of(
                        1002,
                        "Invalid status code received: 429 Status line: HTTP/1.1 429 Too Many Requests",
                        Kind.OTHER),
                Arguments.of(
                        1002,
                        "Invalid status code received: 502 Status line: HTTP/1.1 502 Bad Gateway",
                        Kind.OTHER),
                // Restarts and dropped networks, including reasons that only mention auth.
                Arguments.of(1001, "", Kind.OTHER),
                Arguments.of(1006, "", Kind.OTHER),
                Arguments.of(-1, "Connection refused", Kind.OTHER),
                Arguments.of(1011, "auth service restarting", Kind.OTHER),
                Arguments.of(1000, "", Kind.OTHER));
    }

    @ParameterizedTest(name = "{0} \"{1}\"")
    @MethodSource("closesThisClientSends")
    void aCloseThisClientStartedIsNeverReadAsTheBackends(int code, String reason) {
        assertEquals(Kind.CLIENT_INITIATED, BackendCloseClassifier.classify(code, reason, true));
    }

    private static Stream<Arguments> closesThisClientSends() {
        return Stream.of(
                Arguments.of(1000, ""),
                Arguments.of(1008, "Treasury authentication failed"));
    }
}
