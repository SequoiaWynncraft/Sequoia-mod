package com.seqwawa.seq.network;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads why the game websocket closed, so that only a close that really refuses the
 * session's token signs the player in again, and a close that no retry can fix stops
 * reconnecting.
 * <p>
 * The backend closes with 1008 (policy violation) for several unrelated reasons, named
 * in the close reason. A handshake it refuses never opens: the socket reports that
 * as a close whose reason carries the HTTP status.
 */
final class BackendCloseClassifier {

    enum Kind {
        /** This client closed the socket. The backend echoes the close back, so it is not the backend's verdict. */
        CLIENT_INITIATED,
        /** The backend refused or revoked the token (a 401 handshake, an unlinked account): sign in afresh. */
        AUTH_REJECTED,
        /** A 426 handshake, or a 1008 close naming it: only an update helps. */
        VERSION_UNSUPPORTED,
        PARTY_FINDER_BANNED,
        /** A 403 handshake: the backend refuses this account (a ban) or this transport, so retrying cannot help. */
        HANDSHAKE_FORBIDDEN,
        TREASURY_AUTH_FAILED,
        /** Anything else, such as a restart or a dropped network: reconnect as usual. */
        OTHER
    }

    static final int POLICY_VIOLATION = 1008;

    /** The close code, and reason, a socket reports for a handshake answered with anything but 101. */
    static final int HANDSHAKE_FAILED = 1002;
    private static final Pattern REFUSED_HANDSHAKE = Pattern.compile("^invalid status code received: (\\d{3})\\b");

    private BackendCloseClassifier() {}

    static Kind classify(int code, String reason, boolean closedByClient) {
        if (closedByClient) {
            return Kind.CLIENT_INITIATED;
        }
        String normalized = reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
        Matcher refused = REFUSED_HANDSHAKE.matcher(normalized);
        // Only the handshake failure itself: a later close whose reason quotes the same words is not one.
        if (code == HANDSHAKE_FAILED && refused.find()) {
            return switch (refused.group(1)) {
                case "401" -> Kind.AUTH_REJECTED;
                case "403" -> Kind.HANDSHAKE_FORBIDDEN;
                case "426" -> Kind.VERSION_UNSUPPORTED;
                default -> Kind.OTHER;
            };
        }
        if (code != POLICY_VIOLATION) {
            return Kind.OTHER;
        }
        return switch (normalized) {
            case "mod_version_unsupported" -> Kind.VERSION_UNSUPPORTED;
            case "party_finder_banned" -> Kind.PARTY_FINDER_BANNED;
            case "treasury_auth_failed", "treasury_auth_timeout" -> Kind.TREASURY_AUTH_FAILED;
            // account_unlinked, account_transferred, guild_leave and account_revoked all revoke the
            // token; a revocation reason added later lands here too.
            default -> Kind.AUTH_REJECTED;
        };
    }
}
