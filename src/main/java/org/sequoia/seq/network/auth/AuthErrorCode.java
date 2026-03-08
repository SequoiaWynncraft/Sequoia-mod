package org.sequoia.seq.network.auth;

public enum AuthErrorCode {
    CHALLENGE_EXPIRED("challenge_expired"),
    CHALLENGE_USED("challenge_used"),
    MINECRAFT_SESSION_INVALID("minecraft_session_invalid"),
    TOKEN_INVALID("token_invalid"),
    TOKEN_EXPIRED("token_expired"),
    RATE_LIMITED("rate_limited"),
    TRANSPORT_INSECURE("transport_insecure"),
    BACKEND_UNAVAILABLE("backend_unavailable"),
    BACKEND_VERIFICATION_FAILED("backend_verification_failed"),
    WEBSOCKET_AUTH_REJECTED("websocket_auth_rejected"),
    PLAYER_NOT_LOGGED_IN("player_not_logged_in"),
    SESSION_JOIN_FAILED("session_join_failed"),
    NETWORK_FAILURE("network_failure"),
    MALFORMED_RESPONSE("malformed_response"),
    UNKNOWN("unknown");

    private final String code;

    AuthErrorCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static AuthErrorCode fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            return UNKNOWN;
        }
        for (AuthErrorCode value : values()) {
            if (value.code.equalsIgnoreCase(rawCode)) {
                return value;
            }
        }
        return UNKNOWN;
    }
}
