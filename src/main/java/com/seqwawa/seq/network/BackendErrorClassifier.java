package com.seqwawa.seq.network;

import java.util.Locale;

/**
 * Sorts an {@code error} message from the game websocket into what the client should
 * do about it, from the fields the backend sends ({@code code}, {@code status},
 * {@code capability}) and only then its text.
 * <p>
 * The checks run in a fixed order, the first match winning. Only a backend code or
 * status may end the session (token invalid or expired, not in guild); every other
 * error is ordinary and leaves the connection alone, whatever words its message uses.
 */
final class BackendErrorClassifier {

    enum Kind {
        /** The Treasury-only session failed its Minecraft session proof. */
        TREASURY_AUTH_FAILED,
        /** Guild chat from a sender outside Sequoia; dropped without a word. */
        GUILD_CHAT_SENDER_NOT_IN_GUILD,
        /** The account is not a Sequoia member: member-only features stop for this session. */
        NOT_A_SEQUOIA_MEMBER,
        VERSION_UNSUPPORTED,
        /** The session's auth request itself was malformed. */
        SESSION_AUTH_INVALID,
        TOKEN_INVALID,
        TOKEN_EXPIRED,
        PARTY_FINDER,
        /** One capability is closed to this account; the rest of the session is fine. */
        CAPABILITY_DENIED,
        NOT_IN_GUILD,
        VALIDATION,
        /** Any other error: shown, and the connection stays up. */
        OTHER
    }

    /** One backend error, as {@code OutgoingMessage.Error} serializes it. Absent fields are null, or -1 for status. */
    record BackendError(
            String requestId,
            String code,
            int status,
            String capability,
            String message,
            String context,
            String requestType) {

        String normalizedMessage() {
            return message == null ? "" : message.toLowerCase(Locale.ROOT);
        }
    }

    private BackendErrorClassifier() {}

    static Kind classify(BackendError error, boolean treasuryOnlyConnection) {
        String code = error.code();
        String capability = error.capability();
        String message = error.normalizedMessage();

        if (treasuryOnlyConnection && error.requestId() == null && isTreasuryAuthenticationError(code)) {
            return Kind.TREASURY_AUTH_FAILED;
        }
        if (isSilentGuildChatMembershipReject(code, capability, message)) {
            return Kind.GUILD_CHAT_SENDER_NOT_IN_GUILD;
        }
        if (isSessionMembershipReject(code, capability, message)) {
            return Kind.NOT_A_SEQUOIA_MEMBER;
        }
        if ("mod_version_unsupported".equalsIgnoreCase(code) || error.status() == 426) {
            return Kind.VERSION_UNSUPPORTED;
        }
        if (isSessionAuthenticationError(error.status(), capability, message)) {
            return Kind.SESSION_AUTH_INVALID;
        }
        if ("token_expired".equalsIgnoreCase(code)) {
            return Kind.TOKEN_EXPIRED;
        }
        if ("token_invalid".equalsIgnoreCase(code) || error.status() == 401) {
            return Kind.TOKEN_INVALID;
        }
        // The backend's capability names the feature: a guild invite error is not a party finder one.
        if (isCapabilityAuthorizationReject(error.status(), capability)) {
            return Kind.CAPABILITY_DENIED;
        }
        if (isPartyFinderError(error, message)) {
            return Kind.PARTY_FINDER;
        }
        // Only the backend's own verdict, never a message that merely mentions a guild.
        if (isBlank(capability) && ("not_in_guild".equalsIgnoreCase(code) || error.status() == 403)) {
            return Kind.NOT_IN_GUILD;
        }
        if (message.contains("validation")) {
            return Kind.VALIDATION;
        }
        return Kind.OTHER;
    }

    static boolean isTreasuryAuthenticationError(String code) {
        return "treasury_auth_failed".equalsIgnoreCase(code)
                || "treasury_auth_timeout".equalsIgnoreCase(code);
    }

    static boolean isSilentGuildChatMembershipReject(String backendCode, String capability, String normalizedMessage) {
        return "not_in_guild".equalsIgnoreCase(backendCode)
                && "guild_chat".equalsIgnoreCase(capability)
                && normalizedMessage != null
                && normalizedMessage.contains("guild");
    }

    static boolean isSessionMembershipReject(String backendCode, String capability, String normalizedMessage) {
        return "not_in_guild".equalsIgnoreCase(backendCode)
                && isBlank(capability)
                && normalizedMessage != null
                && normalizedMessage.contains("sequoia")
                && normalizedMessage.contains("member");
    }

    /**
     * The legacy "invalid auth request" answer. A bare 400 without it is an ordinary validation
     * error: this backend sends 400 only for rejected live-war payloads, and those name a capability.
     */
    static boolean isSessionAuthenticationError(int status, String capability, String normalizedMessage) {
        if (!isBlank(capability)) {
            return false;
        }
        return normalizedMessage != null && normalizedMessage.contains("invalid auth request");
    }

    static boolean isCapabilityAuthorizationReject(int status, String capability) {
        return status == 403 && !isBlank(capability);
    }

    private static boolean isPartyFinderError(BackendError error, String normalizedMessage) {
        if (normalizedMessage.contains("party finder")
                || normalizedMessage.contains("party_finder")
                || normalizedMessage.contains("listing")
                || normalizedMessage.contains("invite")) {
            return true;
        }
        if (error.context() != null) {
            String context = error.context().toLowerCase(Locale.ROOT);
            if (context.contains("party_finder") || context.contains("party finder")) {
                return true;
            }
        }
        if (error.requestType() != null) {
            String requestType = error.requestType().toLowerCase(Locale.ROOT);
            return requestType.startsWith("party_")
                    || requestType.contains("listing")
                    || requestType.contains("invite");
        }
        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
