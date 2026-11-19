package com.seqwawa.seq.managers;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.WynncraftServerPolicy;
import java.util.Locale;

/** What the player reads when a party finder request fails: the backend's reason in plain words. */
final class PartyFinderErrors {

    private PartyFinderErrors() {}

    /** The message to show for a failed request, or null for one dropped because its session ended. */
    static String message(Throwable throwable, String fallbackMessage) {
        if (PartyFinderManager.isSupersededByReset(throwable)) {
            return null;
        }
        ApiClient.ApiException apiException = findApiException(throwable);
        if (apiException == null) {
            return fallbackMessage;
        }

        String responseBody = apiException.getResponseBody();
        String backendError = errorField(responseBody);
        String statusMapped = statusMessage(apiException.getStatusCode(), responseBody, backendError);
        if (statusMapped != null) {
            return statusMapped;
        }
        String translated = translate(backendError);
        return translated != null ? translated : fallbackMessage;
    }

    private static String statusMessage(int statusCode, String responseBody, String backendError) {
        String body = responseBody == null ? "" : responseBody.toLowerCase(Locale.ROOT);
        if (body.contains("main_server_only")) {
            return WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE;
        }
        if (statusCode == 426 || body.contains("mod_version_unsupported")) {
            if (backendError != null) {
                return backendError;
            }
            String minimumSafeVersion = field(responseBody, "minimum_safe_version");
            if (minimumSafeVersion != null) {
                return "Update Sequoia to at least " + minimumSafeVersion + ".";
            }
            return "Update Sequoia to a newer version.";
        }
        if (statusCode == 400 || statusCode == 422) {
            String translated = translate(backendError);
            return translated != null ? translated : "Request rejected by backend validation. Check your inputs.";
        }
        if (statusCode == 401) {
            return "Authentication required. Run /seq connect to start a fresh backend session.";
        }
        if (statusCode == 403) {
            if (body.contains("guild") || body.contains("not in guild")) {
                return "Access denied: managing parties requires Sequoia/allied guild membership or access to the"
                        + " Party Finder Discord channel.";
            }
            return backendError != null ? backendError : "Access denied by backend authorization.";
        }
        return null;
    }

    private static String translate(String backendError) {
        if (backendError == null || backendError.isBlank()) {
            return null;
        }
        return switch (backendError.toLowerCase(Locale.ROOT)) {
            case "listing is not open for new members" -> "This party is currently closed by the leader.";
            case "listing is invite only", "listing requires an invite" ->
                "This party is invite only. Ask the leader for an invite.";
            case "party is already full" -> "This party is already full.";
            case "invite has expired" -> "This invite has expired.";
            case "invite is no longer active" -> "This invite is no longer active.";
            case "this invite was not intended for your linked account" ->
                "This invite was sent to a different account.";
            case "role is required" -> "Choose a role before joining this party.";
            case "listing has been disbanded", "listing has already been disbanded" ->
                "This party has already been disbanded.";
            case "listing is already closed" -> "This party is already closed.";
            case "only a closed listing can be reopened" -> "This party is not currently closed.";
            case "only the party leader can close the listing" -> "Only the party leader can close the party.";
            case "only the party leader can reopen the listing" -> "Only the party leader can reopen the party.";
            case "only the party leader can disband the listing" -> "Only the party leader can disband the party.";
            case "you are not a member of this party" -> "You are not in this party.";
            default -> backendError;
        };
    }

    /** The body's {@code error}, else its {@code message}, else its {@code detail}. */
    private static String errorField(String responseBody) {
        for (String key : new String[] {"error", "message", "detail"}) {
            String value = field(responseBody, key);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /** A non-blank string field of a JSON object body, else null. */
    private static String field(String responseBody, String key) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            JsonElement parsed = JsonParser.parseString(responseBody);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonElement element = parsed.getAsJsonObject().get(key);
            if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
                return null;
            }
            String value = element.getAsString();
            return value.isBlank() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ApiClient.ApiException findApiException(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof ApiClient.ApiException apiException) {
                return apiException;
            }
        }
        return null;
    }
}
