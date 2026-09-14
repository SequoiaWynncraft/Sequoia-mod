package com.seqwawa.seq.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.BuildConfig;
import com.seqwawa.seq.network.auth.AuthErrorCode;
import com.seqwawa.seq.network.auth.AuthException;
import com.seqwawa.seq.network.auth.RaidProfilesSession;
import java.util.Locale;

/**
 * Turns a raid-profile API failure into a line worth showing the player.
 * <p>
 * The first job is saying <em>which side</em> failed, because "it does not work"
 * is the least useful thing a message can say. A request that never left the
 * machine, an endpoint that is not deployed, and a backend that answered with an
 * error are three different problems with three different fixes, and they used to
 * produce the same sentence.
 * <p>
 * When the backend does send a {@code message} it is used as written: it is
 * composed for a player to read and caps how much of the submitted input it
 * echoes back.
 */
public final class RaidProfileApiError {

    private RaidProfileApiError() {}

    public static String describe(Throwable throwable, String fallback) {
        // A failed sign-in wraps the backend's answer, so it is checked first: read as a
        // plain 403 it would claim the player is not in the guild.
        AuthException signIn = findAuthException(throwable);
        if (signIn != null) {
            return describeSignIn(signIn);
        }

        ApiClient.ApiException api = findApiException(throwable);
        if (api == null) {
            // Nothing answered, so this never got past the network.
            String cause = rootCauseMessage(throwable);
            return cause == null ? fallback : "Could not reach the backend: " + cause;
        }

        String body = api.getResponseBody();
        String backendMessage = extractField(body, "message");

        // Checked before the status, because this one arrives as a 403 that has
        // nothing to do with guild membership.
        if (isMainServerOnly(body)) {
            return backendMessage == null ? "Sequoia only works on the main Wynncraft server." : backendMessage;
        }

        int status = api.getStatusCode();
        return switch (status) {
            case 400, 422 -> orElse(backendMessage, "The backend rejected that. Check your builds and region.");
                // Rare but real: the backend holds no Minecraft identity for this
                // account, so a profile saved now could never be listed.
            case 409 -> orElse(
                    backendMessage,
                    "identity_unknown".equals(extractField(body, "code"))
                            ? "The backend does not know your Minecraft account yet. Rejoin the game and try again."
                            : "The backend refused that request.");
            case 401 -> orElse(backendMessage, "Your session expired. Run /seq connect and try again.");
            case 403 -> orElse(backendMessage, "Your account is not recognised as a guild member.");
            case 404 -> orElse(
                    backendMessage,
                    "This backend has no raid profiles endpoint yet (404). It has not been deployed.");
            case 426 -> orElse(backendMessage, "Update Sequoia to a newer version.");
            default -> orElse(
                    backendMessage,
                    status >= 500
                            ? "The backend errored (" + status + ")."
                            : "The backend refused that request (" + status + ").");
        };
    }

    /**
     * A one-line summary for the log: the status and a slice of the body, so a
     * report of "it says it cannot reach the backend" can be resolved from the log
     * rather than from guesswork.
     */
    public static String describeForLog(Throwable throwable) {
        AuthException signIn = findAuthException(throwable);
        ApiClient.ApiException api = findApiException(throwable);
        if (signIn != null) {
            return "sign-in failed code=" + signIn.getStableCode()
                    + (api == null ? "" : " HTTP " + api.getStatusCode() + " body=" + truncate(api.getResponseBody()));
        }
        if (api == null) {
            Throwable cause = rootCause(throwable);
            return "no response ("
                    + (cause == null ? "unknown" : cause.getClass().getSimpleName())
                    + ": "
                    + rootCauseMessage(throwable)
                    + ")";
        }
        return "HTTP " + api.getStatusCode() + " body=" + truncate(api.getResponseBody());
    }

    /** The error code the backend sent, or null. Exposed so callers can branch on it. */
    public static String code(Throwable throwable) {
        ApiClient.ApiException api = findApiException(throwable);
        return api == null ? null : extractField(api.getResponseBody(), "code");
    }

    /** The HTTP status behind a failure, or {@code 0} when it never got a response. */
    public static int status(Throwable throwable) {
        ApiClient.ApiException api = findApiException(throwable);
        return api == null ? 0 : api.getStatusCode();
    }

    static boolean isMainServerOnly(String responseBody) {
        return responseBody != null && responseBody.toLowerCase(Locale.ROOT).contains("main_server_only");
    }

    static String extractField(String responseBody, String field) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(responseBody);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject object = parsed.getAsJsonObject();
            if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
                return null;
            }
            String value = object.get(field).getAsString();
            return value == null || value.isBlank() ? null : value;
        } catch (RuntimeException ignored) {
            // A body that is not JSON, such as a proxy's HTML error page, is not
            // worth putting in front of a player raw.
            return null;
        }
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    /**
     * What to show when signing in to the raid-profiles backend failed, which only
     * happens when that backend is a separate one from the rest of the mod.
     */
    static String describeSignIn(AuthException failure) {
        String host = RaidProfilesSession.hostOf(BuildConfig.RAID_PROFILES_API_URL);
        if (failure.getCode() == AuthErrorCode.ACCOUNT_NOT_LINKED) {
            // The backend's own wording points at the Discord bot, which links the main
            // database. The chat link opens the website flow of this backend instead.
            return "Your account is not linked on " + host + " yet. Use the link sent in chat, then press Refresh.";
        }
        String reason = failure.getMessage();
        return reason == null || reason.isBlank()
                ? "Could not sign in to " + host + "."
                : "Could not sign in to " + host + ": " + reason;
    }

    static AuthException findAuthException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AuthException auth) {
                return auth;
            }
            current = current.getCause();
        }
        return null;
    }

    static ApiClient.ApiException findApiException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ApiClient.ApiException api) {
                return api;
            }
            current = current.getCause();
        }
        return null;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static String rootCauseMessage(Throwable throwable) {
        Throwable cause = rootCause(throwable);
        if (cause == null) {
            return null;
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static String truncate(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String single = body.replaceAll("\\s+", " ").trim();
        return single.length() <= 200 ? single : single.substring(0, 200) + "...";
    }
}
