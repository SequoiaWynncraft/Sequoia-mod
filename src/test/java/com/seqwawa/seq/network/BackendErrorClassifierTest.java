package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.network.BackendErrorClassifier.BackendError;
import com.seqwawa.seq.network.BackendErrorClassifier.Kind;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The errors GameWebSocketHandler sends, with the code, status and capability it sends them with. */
class BackendErrorClassifierTest {

    private static final int NO_STATUS = -1;

    @ParameterizedTest(name = "{0}")
    @MethodSource("ordinaryErrorsThatOnlyMentionAGuildOrExpiry")
    void ordinaryErrorsLeaveTheSessionAlone(String message, BackendError error) {
        assertEquals(Kind.OTHER, BackendErrorClassifier.classify(error, false));
    }

    private static Stream<Arguments> ordinaryErrorsThatOnlyMentionAGuildOrExpiry() {
        return Stream.of(
                ordinary("invalid_request", "Invalid guild bank event payload"),
                ordinary("rate_limited", "Too many guild war submissions"),
                ordinary("invalid_request", "Guild membership event actor is not in the cached guild roster"),
                ordinary("invalid_request", "Invalid guild storage reward payload"),
                ordinary("invalid_request", "Guild alliance snapshot is invalid"),
                ordinary("invalid_request", "Guild chat message is too long"),
                // From the Discord link flow: "expired" here is an auth code's, not the session token's.
                ordinary("invalid_request", "Invalid or expired auth code"),
                ordinary("invalid_request", "Auth code has expired"),
                ordinary("account_already_linked", "Minecraft account is already linked to Discord: someone"),
                ordinary("treasury_forbidden", "Treasury-authenticated sessions only support treasury_out."),
                ordinary("invalid_request", "Invalid message format"),
                Arguments.of(
                        "guild roster unavailable (503, war_status)",
                        new BackendError(
                                null,
                                "guild_roster_unavailable",
                                503,
                                "war_status",
                                "Current guild roster information is temporarily unavailable.",
                                null,
                                null)),
                Arguments.of(
                        "invalid war status payload (400, war_status)",
                        new BackendError(
                                null, "invalid_request", 400, "war_status", "Invalid war status payload", null, null)),
                Arguments.of(
                        "war status rate limit (429, war_status)",
                        new BackendError(
                                null,
                                "rate_limited",
                                429,
                                "war_status",
                                "Too many war status updates; try again later.",
                                null,
                                null)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("errorsTheBackendMeansAsVerdicts")
    void sessionLevelKindsComeFromTheBackendsCodeOrStatus(String name, BackendError error, Kind expected) {
        assertEquals(expected, BackendErrorClassifier.classify(error, false));
    }

    private static Stream<Arguments> errorsTheBackendMeansAsVerdicts() {
        return Stream.of(
                Arguments.of(
                        "unauthenticated guild chat",
                        error("token_invalid", "Authenticate before sending guild chat"),
                        Kind.TOKEN_INVALID),
                Arguments.of(
                        "unauthenticated class update",
                        error("token_invalid", "Authenticate before updating class"),
                        Kind.TOKEN_INVALID),
                Arguments.of(
                        "unauthenticated war status (401, war_status)",
                        new BackendError(
                                null,
                                "token_invalid",
                                401,
                                "war_status",
                                "Authenticate before sending war status updates",
                                null,
                                null),
                        Kind.TOKEN_INVALID),
                Arguments.of("expired token code", error("token_expired", "Expired token"), Kind.TOKEN_EXPIRED),
                Arguments.of(
                        "global version rejection (426, websocket)",
                        new BackendError(
                                null,
                                "mod_version_unsupported",
                                426,
                                "websocket",
                                "Update Sequoia to at least 1.12.0. The server rejected this request because "
                                        + "your Sequoia version is too old.",
                                null,
                                null),
                        Kind.VERSION_UNSUPPORTED),
                Arguments.of(
                        "guild chat from a sender outside Sequoia",
                        new BackendError(
                                null,
                                "not_in_guild",
                                NO_STATUS,
                                "guild_chat",
                                "Guild chat sender is not in Sequoia",
                                null,
                                null),
                        Kind.GUILD_CHAT_SENDER_NOT_IN_GUILD),
                Arguments.of(
                        "member-only feature for a non-member",
                        error("not_in_guild", "This feature is limited to Sequoia members"),
                        Kind.NOT_A_SEQUOIA_MEMBER),
                Arguments.of(
                        "Discord link for a non-member",
                        error("not_in_guild", "You must be a member of Sequoia guild to use this feature"),
                        Kind.NOT_A_SEQUOIA_MEMBER),
                Arguments.of(
                        "war planner for a non-member (403, war_status)",
                        new BackendError(
                                null,
                                "not_in_guild",
                                403,
                                "war_status",
                                "This feature is limited to current Sequoia members.",
                                null,
                                null),
                        Kind.CAPABILITY_DENIED),
                Arguments.of(
                        "unscoped not_in_guild without the member wording",
                        error("not_in_guild", "Not in guild"),
                        Kind.NOT_IN_GUILD),
                Arguments.of(
                        "unscoped 403",
                        new BackendError(null, "forbidden", 403, null, "Access denied", null, null),
                        Kind.NOT_IN_GUILD),
                Arguments.of(
                        "invalid auth request",
                        error("invalid_request", "Invalid auth request"),
                        Kind.SESSION_AUTH_INVALID),
                Arguments.of(
                        "plain 400 without a capability",
                        new BackendError(null, "invalid_request", 400, null, "Validation failed: missing territory",
                                null, null),
                        Kind.VALIDATION),
                Arguments.of(
                        "guild invite refused for a capability",
                        new BackendError(null, "forbidden", 403, "guild_membership_event", "Could not send guild invite",
                                null, null),
                        Kind.CAPABILITY_DENIED),
                Arguments.of(
                        "party finder listing",
                        error("invalid_request", "Invalid party finder listing ID"),
                        Kind.PARTY_FINDER),
                Arguments.of(
                        "party finder request type",
                        new BackendError(null, "invalid_request", NO_STATUS, null, "Rejected", null, "party_join"),
                        Kind.PARTY_FINDER),
                Arguments.of(
                        "validation wording",
                        error("invalid_request", "Request failed validation"),
                        Kind.VALIDATION));
    }

    @ParameterizedTest(name = "{0} on a Treasury-only connection: {2}")
    @MethodSource("treasuryErrors")
    void treasuryAuthFailureNeedsTheTreasuryConnectionAndNoRequestId(
            String code, String requestId, boolean treasuryOnly, Kind expected) {
        BackendError error = new BackendError(
                requestId, code, NO_STATUS, null, "Treasury authentication failed.", null, null);

        assertEquals(expected, BackendErrorClassifier.classify(error, treasuryOnly));
    }

    private static Stream<Arguments> treasuryErrors() {
        return Stream.of(
                Arguments.of("treasury_auth_failed", null, true, Kind.TREASURY_AUTH_FAILED),
                Arguments.of("treasury_auth_timeout", null, true, Kind.TREASURY_AUTH_FAILED),
                Arguments.of("treasury_auth_failed", "550e8400-e29b-41d4-a716-446655440000", true, Kind.OTHER),
                Arguments.of("treasury_auth_failed", null, false, Kind.OTHER));
    }

    private static Arguments ordinary(String code, String message) {
        return Arguments.of(message, error(code, message));
    }

    private static BackendError error(String code, String message) {
        return new BackendError(null, code, NO_STATUS, null, message, null, null);
    }
}
