package com.seqwawa.seq.network;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seqwawa.seq.model.BombShareType;
import com.seqwawa.seq.model.ChatItemPreview;
import com.seqwawa.seq.model.GuildWarQueueSubmission;
import com.seqwawa.seq.model.GuildWarSubmission;
import com.seqwawa.seq.model.WynnClassType;
import java.time.Instant;
import java.util.List;

final class OutboundPayloadFactory {
    private static final Gson GSON = new Gson();

    private OutboundPayloadFactory() {}

    static JsonObject guildWarSubmission(GuildWarSubmission submission) {
        JsonObject payload = new JsonObject();
        payload.addProperty("territory", submission.territory());
        payload.addProperty("submitted_by", submission.submittedBy());
        payload.addProperty("submitted_at", submission.submittedAt());
        payload.addProperty("start_time", submission.startTime());

        JsonArray warrers = new JsonArray();
        for (String warrer : submission.warrers()) {
            warrers.add(warrer);
        }
        payload.add("warrers", warrers);

        JsonObject damage = new JsonObject();
        damage.addProperty("low", submission.stats().damageLow());
        damage.addProperty("high", submission.stats().damageHigh());

        JsonObject stats = new JsonObject();
        stats.add("damage", damage);
        stats.addProperty("attack", submission.stats().attackSpeed());
        stats.addProperty("health", submission.stats().health());
        stats.addProperty("defence", submission.stats().defence());

        JsonObject results = new JsonObject();
        results.add("stats", stats);
        payload.add("results", results);

        payload.addProperty("sr", submission.seasonRating());
        if (submission.completedAt() != null && !submission.completedAt().isBlank()) {
            payload.addProperty("completed_at", submission.completedAt());
        }
        return payload;
    }

    static JsonObject bombShareRequest(String canonicalKey, List<BombShareType> requestedTypes) {
        JsonObject payload = new JsonObject();
        payload.addProperty("canonical_key", canonicalKey);
        JsonArray types = new JsonArray();
        if (requestedTypes != null) {
            for (BombShareType requestedType : requestedTypes) {
                if (requestedType != null) {
                    types.add(requestedType.name());
                }
            }
        }
        payload.add("requested_types", types);
        return payload;
    }

    static JsonObject bombShareSubmit(String requestId, List<String> worlds) {
        JsonObject payload = new JsonObject();
        payload.addProperty("request_id", requestId);
        JsonArray worldArray = new JsonArray();
        if (worlds != null) {
            for (String world : worlds) {
                if (world != null && !world.isBlank()) {
                    worldArray.add(world.trim());
                }
            }
        }
        payload.add("worlds", worldArray);
        return payload;
    }

    static JsonObject treasuryOut(TreasuryOutRequest request) {
        return GSON.toJsonTree(request).getAsJsonObject();
    }

    static JsonObject treasuryAuth(TreasuryAuthResponse response) {
        return GSON.toJsonTree(response).getAsJsonObject();
    }

    static JsonObject guildChat(
            String username,
            String nickname,
            String message,
            String avatarUrl,
            List<ChatItemPreview> itemPreviews) {
        JsonObject payload = new JsonObject();
        if (username != null) {
            payload.addProperty("username", username);
        }
        if (nickname != null) {
            payload.addProperty("nickname", nickname);
        }
        payload.addProperty("message", message);
        if (avatarUrl != null) {
            payload.addProperty("avatar_url", avatarUrl);
        }
        JsonArray previews = itemPreviewArray(itemPreviews);
        if (!previews.isEmpty()) {
            payload.add("item_previews", previews);
        }
        return payload;
    }

    static JsonObject guildAllianceUpdate(String action, String guildName) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", action);
        payload.addProperty("guild_name", guildName);
        return payload;
    }

    static JsonObject guildAllianceSnapshot(List<String> guildNames) {
        JsonArray names = new JsonArray();
        for (String guildName : guildNames) {
            names.add(guildName);
        }
        JsonObject payload = new JsonObject();
        payload.add("guild_names", names);
        return payload;
    }

    static JsonObject raidAnnouncement(
            List<String> usernames,
            String raidType,
            int aspectCount,
            int emeraldCount,
            double experienceCount,
            int srCount) {
        JsonArray names = new JsonArray();
        usernames.forEach(names::add);
        JsonObject payload = new JsonObject();
        payload.add("usernames", names);
        payload.addProperty("raid_type", raidType);
        payload.addProperty("aspect_count", aspectCount);
        payload.addProperty("emerald_count", emeraldCount);
        payload.addProperty("experience_count", experienceCount);
        payload.addProperty("sr_count", srCount);
        return payload;
    }

    static JsonObject guildBankEvent(
            String action,
            String player,
            Integer quantity,
            String itemName,
            String charges,
            String accessTier,
            String rawMessage) {
        JsonObject payload = new JsonObject();
        payload.addProperty("action", action);
        payload.addProperty("player", player.trim());
        if (quantity != null) {
            payload.addProperty("quantity", quantity);
        }
        payload.addProperty("item_name", itemName.trim());
        if (charges != null && !charges.isBlank()) {
            payload.addProperty("charges", charges.trim());
        }
        payload.addProperty("access_tier", accessTier.trim());
        payload.addProperty("raw_message", rawMessage.trim());
        return payload;
    }

    static JsonObject guildStorageSnapshot(
            long emeraldCurrent, long emeraldMax, long aspectCurrent, long aspectMax) {
        JsonObject payload = new JsonObject();
        payload.addProperty("emerald_current", emeraldCurrent);
        payload.addProperty("emerald_max", emeraldMax);
        payload.addProperty("aspect_current", aspectCurrent);
        payload.addProperty("aspect_max", aspectMax);
        return payload;
    }

    static JsonObject guildStorageReward(
            String senderUsername,
            String recipientUsername,
            String resourceType,
            long amount,
            int count,
            Instant windowStartedAt) {
        JsonObject payload = new JsonObject();
        payload.addProperty("sender_username", senderUsername);
        payload.addProperty("recipient_username", recipientUsername);
        payload.addProperty("resource_type", resourceType);
        payload.addProperty("amount", amount);
        payload.addProperty("count", count);
        payload.addProperty("window_started_at", windowStartedAt.toString());
        return payload;
    }

    static JsonObject guildWarQueue(GuildWarQueueSubmission submission) {
        JsonObject payload = new JsonObject();
        payload.addProperty("territory", submission.territory());
        payload.addProperty("submitted_by", submission.submittedBy());
        payload.addProperty("submitted_at", submission.submittedAt());
        payload.addProperty("defense_rating", submission.defenseRating());
        payload.addProperty("queue_minutes", submission.queueMinutes());
        return payload;
    }

    static JsonObject partyClassUpdate(WynnClassType classType) {
        JsonObject payload = new JsonObject();
        payload.addProperty("class_type", classType.name());
        return payload;
    }

    static JsonObject partySyncSnapshot(boolean active, String leaderUsername, List<String> memberUsernames) {
        JsonObject payload = new JsonObject();
        payload.addProperty("active", active);
        if (leaderUsername != null && !leaderUsername.isBlank()) {
            payload.addProperty("leader_username", leaderUsername);
        }
        JsonArray usernames = new JsonArray();
        if (memberUsernames != null) {
            for (String memberUsername : memberUsernames) {
                if (memberUsername != null && !memberUsername.isBlank()) {
                    usernames.add(memberUsername);
                }
            }
        }
        payload.add("member_usernames", usernames);
        return payload;
    }

    static JsonObject partySyncMemberRemoved(String username, String reason) {
        JsonObject payload = new JsonObject();
        payload.addProperty("username", username);
        payload.addProperty("reason", reason);
        return payload;
    }

    private static JsonArray itemPreviewArray(List<ChatItemPreview> itemPreviews) {
        JsonArray previews = new JsonArray();
        if (itemPreviews == null || itemPreviews.isEmpty()) {
            return previews;
        }
        for (ChatItemPreview preview : itemPreviews) {
            if (preview == null || preview.name() == null || preview.name().isBlank()) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("name", preview.name());
            if (preview.subtitle() != null && !preview.subtitle().isBlank()) {
                json.addProperty("subtitle", preview.subtitle());
            }
            if (preview.color() != null) {
                json.addProperty("color", preview.color());
            }
            JsonArray attributes = stringArray(preview.attributes());
            if (!attributes.isEmpty()) {
                json.add("attributes", attributes);
            }
            JsonArray statLines = stringArray(preview.statLines());
            if (!statLines.isEmpty()) {
                json.add("stat_lines", statLines);
            }
            JsonArray statRolls = statRollArray(preview.statRolls());
            if (!statRolls.isEmpty()) {
                json.add("stat_rolls", statRolls);
            }
            if (preview.shinyStat() != null) {
                json.add("shiny_stat", shinyStatJson(preview.shinyStat()));
            }
            previews.add(json);
        }
        return previews;
    }

    private static JsonObject shinyStatJson(ChatItemPreview.ShinyStat shinyStat) {
        JsonObject json = new JsonObject();
        if (shinyStat.key() != null && !shinyStat.key().isBlank()) {
            json.addProperty("key", shinyStat.key());
        }
        if (shinyStat.displayName() != null && !shinyStat.displayName().isBlank()) {
            json.addProperty("display_name", shinyStat.displayName());
        }
        json.addProperty("value", shinyStat.value());
        json.addProperty("rerolls", shinyStat.rerolls());
        return json;
    }

    private static JsonArray statRollArray(List<ChatItemPreview.StatRoll> statRolls) {
        JsonArray array = new JsonArray();
        if (statRolls == null || statRolls.isEmpty()) {
            return array;
        }
        for (ChatItemPreview.StatRoll statRoll : statRolls) {
            if (statRoll == null || statRoll.percentage() == null) {
                continue;
            }
            JsonObject json = new JsonObject();
            if (statRoll.apiName() != null && !statRoll.apiName().isBlank()) {
                json.addProperty("api_name", statRoll.apiName());
            }
            if (statRoll.key() != null && !statRoll.key().isBlank()) {
                json.addProperty("key", statRoll.key());
            }
            if (statRoll.displayName() != null && !statRoll.displayName().isBlank()) {
                json.addProperty("display_name", statRoll.displayName());
            }
            json.addProperty("value", statRoll.value());
            json.addProperty("percentage", statRoll.percentage());
            array.add(json);
        }
        return array;
    }

    private static JsonArray stringArray(List<String> values) {
        JsonArray array = new JsonArray();
        if (values == null) {
            return array;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                array.add(value);
            }
        }
        return array;
    }
}
