package com.seqwawa.seq.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seqwawa.seq.model.BombShareType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class IncomingMessageParser {
    private static final Gson GSON = new Gson();

    private IncomingMessageParser() {}

    static IncomingMessage parse(String message) {
        JsonObject payload = GSON.fromJson(message, JsonObject.class);
        if (payload == null || !payload.has("type")) {
            return null;
        }
        return new IncomingMessage(payload.get("type").getAsString(), payload);
    }

    static String primitiveString(JsonObject payload, String key) {
        if (payload == null || !payload.has(key) || !payload.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            String value = payload.get(key).getAsString();
            return value == null || value.isBlank() ? null : value;
        } catch (Exception ignored) {
            return null;
        }
    }

    static List<String> connectedUsers(JsonObject payload) {
        List<String> users = new ArrayList<>();
        payload.getAsJsonArray("users").forEach(element -> users.add(element.getAsString()));
        return users;
    }

    static String authenticatedDiscordUsername(JsonObject payload) {
        return payload.has("discord_username") && payload.get("discord_username").isJsonPrimitive()
                ? payload.get("discord_username").getAsString()
                : null;
    }

    static ConnectionManager.BombSharePromptMessage bombSharePrompt(JsonObject payload) {
        List<BombShareType> requestedTypes =
                bombShareTypes(payload.has("requested_types") ? payload.getAsJsonArray("requested_types") : null);
        return new ConnectionManager.BombSharePromptMessage(
                primitiveString(payload, "request_id"),
                primitiveString(payload, "requester_username"),
                primitiveString(payload, "canonical_key"),
                requestedTypes,
                payload.has("expires_at") && !payload.get("expires_at").isJsonNull()
                        ? Instant.parse(payload.get("expires_at").getAsString())
                        : null,
                payload.has("first_prompt")
                        && payload.get("first_prompt").isJsonPrimitive()
                        && payload.get("first_prompt").getAsBoolean());
    }

    static ConnectionManager.BombShareResultMessage bombShareResult(JsonObject payload) {
        return new ConnectionManager.BombShareResultMessage(
                primitiveString(payload, "request_id"),
                primitiveString(payload, "canonical_key"),
                bombShareTypes(payload.has("requested_types") ? payload.getAsJsonArray("requested_types") : null),
                primitiveStrings(payload.has("worlds") ? payload.getAsJsonArray("worlds") : null),
                payload.has("share_count") && payload.get("share_count").isJsonPrimitive()
                        ? payload.get("share_count").getAsInt()
                        : 0);
    }

    static TreasuryOutRecordedMessage treasuryOutRecorded(JsonObject payload) {
        return GSON.fromJson(payload, TreasuryOutRecordedMessage.class);
    }

    static GuildStorageSnapshot guildStorageSnapshot(JsonObject payload) {
        return new GuildStorageSnapshot(
                payload.get("emerald_current").getAsLong(),
                payload.get("emerald_max").getAsLong(),
                payload.get("aspect_current").getAsLong(),
                payload.get("aspect_max").getAsLong());
    }

    static ConnectionManager.DiscordChatMessage discordChat(JsonObject payload) {
        return new ConnectionManager.DiscordChatMessage(
                payload.get("username").getAsString(), payload.get("message").getAsString());
    }

    static String partyFinderAction(JsonObject payload) {
        return payload.has("action") && !payload.get("action").isJsonNull()
                ? payload.get("action").getAsString()
                : "unknown";
    }

    static ConnectionManager.PartyFinderUpdateMessage partyFinderUpdate(JsonObject payload, String action) {
        return new ConnectionManager.PartyFinderUpdateMessage(action, payload.getAsJsonObject("listing"));
    }

    static ConnectionManager.PartyFinderInviteMessage partyFinderInvite(JsonObject payload) {
        JsonObject listing = payload.has("listing") && payload.get("listing").isJsonObject()
                ? payload.getAsJsonObject("listing")
                : null;
        return new ConnectionManager.PartyFinderInviteMessage(
                payload.get("listing_id").getAsLong(),
                payload.get("inviter_uuid").getAsString(),
                payload.get("invite_token").getAsString(),
                listing);
    }

    static ConnectionManager.PartyFinderStaleWarningMessage partyFinderStaleWarning(JsonObject payload) {
        return new ConnectionManager.PartyFinderStaleWarningMessage(
                primitiveString(payload, "reason"),
                payload.get("listing_id").getAsLong(),
                payload.has("disband_at") && !payload.get("disband_at").isJsonNull()
                        ? Instant.parse(payload.get("disband_at").getAsString())
                        : null,
                payload.has("minutes_remaining") ? payload.get("minutes_remaining").getAsLong() : 0L);
    }

    static BackendError backendError(JsonObject payload) {
        return new BackendError(
                statusCode(payload),
                primitiveString(payload, "code"),
                errorMessage(payload),
                primitiveString(payload, "request_id"),
                primitiveString(payload, "minimum_safe_version"),
                primitiveString(payload, "capability"),
                primitiveString(payload, "context"),
                primitiveString(payload, "request_type"));
    }

    private static int statusCode(JsonObject payload) {
        if (payload == null) {
            return -1;
        }
        if (payload.has("status") && payload.get("status").isJsonPrimitive()) {
            try {
                return payload.get("status").getAsInt();
            } catch (Exception ignored) {
            }
        }
        if (payload.has("code") && payload.get("code").isJsonPrimitive()) {
            try {
                return payload.get("code").getAsInt();
            } catch (Exception ignored) {
            }
        }
        return -1;
    }

    private static String errorMessage(JsonObject payload) {
        if (payload == null) {
            return "Unknown backend error";
        }
        for (String key : List.of("message", "error", "detail")) {
            String message = primitiveString(payload, key);
            if (message != null) {
                return message;
            }
        }
        return "Unknown backend error";
    }

    private static List<BombShareType> bombShareTypes(JsonArray requestedTypesJson) {
        if (requestedTypesJson == null) {
            return List.of();
        }

        List<BombShareType> requestedTypes = new ArrayList<>();
        requestedTypesJson.forEach(element -> BombShareType.fromWireValue(element.getAsString()).ifPresent(type -> {
            if (!requestedTypes.contains(type)) {
                requestedTypes.add(type);
            }
        }));
        return List.copyOf(requestedTypes);
    }

    private static List<String> primitiveStrings(JsonArray jsonArray) {
        if (jsonArray == null) {
            return List.of();
        }

        List<String> values = new ArrayList<>();
        jsonArray.forEach(element -> {
            if (element != null && element.isJsonPrimitive()) {
                String value = element.getAsString();
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
        });
        return List.copyOf(values);
    }

    record IncomingMessage(String type, JsonObject payload) {}

    record GuildStorageSnapshot(long emeraldCurrent, long emeraldMax, long aspectCurrent, long aspectMax) {}

    record BackendError(
            int status,
            String code,
            String message,
            String requestId,
            String minimumSafeVersion,
            String capability,
            String context,
            String requestType) {}
}
