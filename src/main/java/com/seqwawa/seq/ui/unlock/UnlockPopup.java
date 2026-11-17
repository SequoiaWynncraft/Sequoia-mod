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
 * @param icon what the reward came from: a raid's art or the war tower for toasts and quests, and the
 *     pixelated emblem in the middle of a milestone's or level up's medal
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
                        "war".equals(source) ? "War for " + detail : detail,
                        number(data, "leaf"), number(data, "xp"), null,
                        "war".equals(source) ? WAR_ICON : raidIconByName(detail));
            }
            case "quest_completed" -> {
                JsonObject quest = object(data, "quest");
                if (quest == null) yield null;
                JsonObject reward = object(quest, "reward");
                String slot = text(quest, "slot");
                JsonObject objective = object(quest, "objective");
                yield new UnlockPopup(Kind.CARD, slotName(slot) + " quest completed", text(quest, "title"), null,
                        reward == null ? 0 : number(reward, "leaf"), reward == null ? 0 : number(reward, "xp"),
                        questTier(slot),
                        objective == null ? SEQUOIA_ICON : objectiveIcon(text(objective, "type"), text(objective, "raid")));
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
                    "Leaf level up", 0, 0, "level", GUILD_EMBLEM);
            default -> null;
        };
    }

    static final String WAR_ICON = "unlock_war";
    static final String SEQUOIA_ICON = "icon";
    static final String SEASON_RATING_ICON = "star";
    static final String GUILD_EMBLEM = "unlock_emblem_icon";

    /**
     * The emblem in a milestone's medal: that raid's art pixelated, the guild's
     * own for all raids and anything else, and the tower for wars.
     */
    static String emblem(String ladder) {
        if (ladder == null) return GUILD_EMBLEM;
        if (ladder.startsWith("war:")) return WAR_ICON;
        if (ladder.startsWith("raid:") && !ladder.equals("raid:all raids")) {
            String raid = objectiveIcon("raids", ladder.substring("raid:".length()).toLowerCase(Locale.ROOT));
            return SEQUOIA_ICON.equals(raid) ? GUILD_EMBLEM : "unlock_emblem_" + raid;
        }
        return GUILD_EMBLEM;
    }

    /** The raid's art from its full name, as Leaf reports it, or the Sequoia icon for a raid the mod does not know. */
    static String raidIconByName(String name) {
        if (name != null) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (SeqRaid raid : SeqRaid.values()) {
                if (lower.contains(raid.displayName().toLowerCase(Locale.ROOT))) return raid.assetKey();
            }
        }
        return SEQUOIA_ICON;
    }

    /** The icon for a quest's objective: the raid it names, any raid, wars or season rating. */
    static String objectiveIcon(String type, String raid) {
        if ("wars".equals(type)) return WAR_ICON;
        if ("season_rating".equals(type)) return SEASON_RATING_ICON;
        if (raid == null) return SEQUOIA_ICON;
        // Leaf calls The Wartorn Palace "wtp"; the mod calls it TWP.
        String code = "wtp".equalsIgnoreCase(raid) ? "TWP" : raid.toUpperCase(Locale.ROOT);
        for (SeqRaid known : SeqRaid.values()) {
            if (known.code().equals(code)) return known.assetKey();
        }
        return SEQUOIA_ICON;
    }

    /** "TNA raids", "guild raids" or "guild wars", from Sierra's ladder key. */
    static String ladderPhrase(String ladder) {
        if (ladder == null) return "";
        if (ladder.equals("raid:all raids")) return "guild raids";
        if (ladder.startsWith("raid:")) return ladder.substring("raid:".length()).toUpperCase(Locale.ROOT) + " raids";
        if (ladder.startsWith("war:")) return "guild wars";
        return ladder;
    }

    static String slotName(String slot) {
        if (slot == null || slot.isEmpty()) return "Quest";
        return capitalise(slot);
    }

    /** The colours each quest slot uses on the quest board. */
    static String questTier(String slot) {
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

    private static String text(JsonObject object, String field) {
        JsonElement value = object.get(field);
        return value == null || value.isJsonNull() || !value.isJsonPrimitive() ? null : value.getAsString();
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
