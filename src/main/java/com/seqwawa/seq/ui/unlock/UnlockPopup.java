package com.seqwawa.seq.ui.unlock;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.seqwawa.seq.model.SeqRaid;
import java.util.Locale;
import java.util.Set;

/**
 * One thing worth celebrating, made from a Leaf event.
 *
 * @param eyebrow the small line above the title, such as "Daily quest completed"
 * @param title the main line, such as "Complete 3 NOL raids" or "Diamond"
 * @param detail the line under it, such as "1,000 TNA raids", or null
 * @param tier which medal and colours to use: {@code bronze} to {@code mythril}, {@code level}, or null
 * @param icon what the reward came from: a raid's art, the raid portal or the war tower for toasts and
 *     quests, and the emblem in the middle of a milestone's medal. Null for a level up, whose medal shows
 *     the level itself
 */
public record UnlockPopup(
        Kind kind, String eyebrow, String title, String detail, long leaf, long xp, String tier, String icon) {

    public enum Kind {
        /** Leaf from one raid or war: small, in the corner, out of the way. */
        TOAST,
        /** A finished quest or a smaller milestone: a card at the top of the screen. */
        CARD,
        /** A big milestone or a level up: the whole screen. */
        CEREMONY
    }

    /** Milestones from Platinum up get the whole screen; the earlier ones a card. */
    static final Set<String> CEREMONY_TIERS = Set.of("platinum", "diamond", "obsidian", "mythril");

    /** The popup for one Leaf event, or null for an event the mod does not show. */
    public static UnlockPopup fromEvent(String type, JsonObject data) {
        if (type == null || data == null) return null;
        return switch (type) {
            case "leaf_earned" -> {
                String source = text(data, "source");
                String detail = text(data, "detail");
                yield new UnlockPopup(Kind.TOAST, null, "+" + formatCount(number(data, "leaf")),
                        "war".equals(source) ? (isTrue(data, "in_claim") ? "Defended " : "War for ") + detail : detail,
                        number(data, "leaf"), number(data, "xp"), null,
                        "war".equals(source) ? WAR_ICON : raidIconByName(detail));
            }
            case "quest_completed" -> {
                JsonObject quest = object(data, "quest");
                if (quest == null) yield null;
                JsonObject reward = object(quest, "reward");
                String slot = text(quest, "slot");
                JsonObject objective = object(quest, "objective");
                yield new UnlockPopup(Kind.CARD, slotName(slot) + " quest completed", finishedQuestTitle(quest), null,
                        reward == null ? 0 : number(reward, "leaf"), reward == null ? 0 : number(reward, "xp"),
                        questTier(slot),
                        objective == null ? RAID_ICON : objectiveIcon(text(objective, "type"), text(objective, "raid")));
            }
            case "achievement_paid" -> {
                String tier = text(data, "tier") == null ? null : text(data, "tier").toLowerCase(Locale.ROOT);
                String label = text(data, "label") != null ? text(data, "label") : capitalise(text(data, "milestone"));
                long threshold = number(data, "threshold");
                String ladder = ladderPhrase(text(data, "ladder"));
                yield new UnlockPopup(tier != null && CEREMONY_TIERS.contains(tier) ? Kind.CEREMONY : Kind.CARD,
                        "Milestone reached", label,
                        threshold > 0 ? formatCount(threshold) + " " + ladder : ladder,
                        number(data, "leaf"), number(data, "xp"), tier, emblem(text(data, "ladder")));
            }
            case "level_up" -> new UnlockPopup(Kind.CEREMONY, "Sequoia level", "Level " + number(data, "level"),
                    "Leaf level up", number(data, "leaf"), 0, "level", null);
            default -> null;
        };
    }

    public static final String WAR_ICON = "unlock_war";
    public static final String SEASON_RATING_ICON = "star";
    /** A raid portal: for any raid, all raids, and raids the mod has no art for yet. */
    public static final String RAID_ICON = "unlock_raid";

    /** The raid's pixelated art for a tile, from its full name as Leaf reports it. */
    public static String raidIconByName(String name) {
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (SeqRaid raid : SeqRaid.values()) {
                if (lower.contains(raid.displayName().toLowerCase(Locale.ROOT))) return "unlock_icon_" + raid.assetKey();
            }
        }
        return RAID_ICON;
    }

    /** The tile icon for a quest's objective: the raid it names, any raid, wars or season rating. */
    public static String objectiveIcon(String type, String raid) {
        if ("wars".equals(type)) return WAR_ICON;
        if ("season_rating".equals(type)) return SEASON_RATING_ICON;
        String asset = raidAsset(raid);
        return asset == null ? RAID_ICON : "unlock_icon_" + asset;
    }

    /**
     * The emblem in a milestone's medal: that raid's art pixelated, the raid
     * portal for all raids and anything else, and the tower for wars.
     */
    public static String emblem(String ladder) {
        if (ladder == null) return RAID_ICON;
        if (ladder.startsWith("war:")) return WAR_ICON;
        if (ladder.startsWith("raid:") && !ladder.equals("raid:all raids")) {
            String asset = raidAsset(ladder.substring("raid:".length()));
            return asset == null ? RAID_ICON : "unlock_emblem_" + asset;
        }
        return RAID_ICON;
    }

    /** The mod's asset key for a raid's short name, such as {@code tna}, or null for one it does not know. */
    public static String raidAsset(String raid) {
        if (raid == null) return null;
        // Leaf calls The Wartorn Palace "wtp"; the mod calls it TWP.
        String code = "wtp".equalsIgnoreCase(raid) ? "TWP" : raid.toUpperCase(Locale.ROOT);
        for (SeqRaid known : SeqRaid.values()) {
            if (known.code().equals(code)) return known.assetKey();
        }
        return null;
    }

    /** The number a level-up medal shows: the digits in its title, such as 8 from "Level 8", or 0. */
    public long level() {
        String digits = title == null ? "" : title.replaceAll("[^0-9]", "");
        return digits.isEmpty() || digits.length() > 6 ? 0 : Long.parseLong(digits);
    }

    /** "TNA raids", "guild raids" or "guild wars", from Sierra's ladder key. */
    public static String ladderPhrase(String ladder) {
        if (ladder == null) return "";
        if (ladder.equals("raid:all raids")) return "guild raids";
        if (ladder.startsWith("raid:")) return ladder.substring("raid:".length()).toUpperCase(Locale.ROOT) + " raids";
        if (ladder.startsWith("war:")) return "guild wars";
        return ladder;
    }

    public static String slotName(String slot) {
        if (slot == null || slot.isEmpty()) return "Quest";
        return capitalise(slot);
    }

    /** The colours each quest slot uses on the quest board. */
    public static String questTier(String slot) {
        if ("daily".equals(slot)) return "gold";
        if ("weekly".equals(slot)) return "diamond";
        return "bronze";
    }

    static String formatCount(long count) {
        return String.format(Locale.US, "%,d", count);
    }

    private static String capitalise(String value) {
        if (value == null || value.isEmpty()) return "";
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    /**
     * What a finished quest says it was: the goal that finished it, such as "Take part
     * in 9 wars" for a quest that raids or wars could finish, or the quest's title
     * when Leaf sends no goals.
     */
    public static String finishedQuestTitle(JsonObject quest) {
        JsonElement goals = quest.get("goals");
        if (goals != null && goals.isJsonArray()) {
            for (JsonElement element : goals.getAsJsonArray()) {
                if (element == null || !element.isJsonObject()) continue;
                JsonObject goal = element.getAsJsonObject();
                long target = number(goal, "target");
                String title = text(goal, "title");
                if (target > 0 && number(goal, "progress") >= target && title != null) return title;
            }
        }
        return text(quest, "title");
    }

    private static String text(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
    }

    /** Whether a field is the JSON value {@code true}; false when it is anything else or missing. */
    public static boolean isTrue(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() && value.getAsBoolean();
    }

    private static long number(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return 0;
        return value.getAsLong();
    }

    private static JsonObject object(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
