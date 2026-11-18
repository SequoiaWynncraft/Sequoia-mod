package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.seqwawa.seq.model.LeafEvents;
import com.seqwawa.seq.ui.theme.UiColor;
import com.seqwawa.seq.ui.unlock.PixelArt;
import com.seqwawa.seq.ui.unlock.UnlockOverlay;
import com.seqwawa.seq.ui.unlock.UnlockPopup;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the quest board's "Recent" list says about each of the latest Leaf events:
 * where the Leaf came from, how much, and how long ago. Pure, so it is tested
 * without a game.
 */
final class RecentLeaf {

    /** How many events the list shows. */
    static final int SHOWN = 6;

    private RecentLeaf() {}

    /** What a row is about, which picks its colours. */
    enum Kind { RAID, WAR, DEFENCE, QUEST, MILESTONE, LEVEL }

    /**
     * One row of the list.
     *
     * @param icon the sprite in the row's tile, or null for a level, whose tile shows the level
     * @param tier a milestone's tier or a quest's colours ({@code gold}, {@code diamond}, ...), or null
     * @param title the main line, such as "The Nameless Anomaly" or "Complete 3 NOL raids"
     * @param label the small line under it, such as "Raid", "Defence" or "Daily quest"
     * @param leaf the Leaf it paid, 0 for a level up
     * @param level the level reached, for a level up
     */
    record Row(Kind kind, String icon, String tier, String title, String label, long leaf, long level, Instant at) {}

    /** The newest {@code limit} events the list knows how to show, newest first. */
    static List<Row> rows(List<LeafEvents.Event> events, int limit) {
        List<Row> rows = new ArrayList<>();
        if (events == null) return rows;
        for (LeafEvents.Event event : events) {
            if (rows.size() >= limit) break;
            Row row = row(event);
            if (row != null) rows.add(row);
        }
        return List.copyOf(rows);
    }

    static Row row(LeafEvents.Event event) {
        if (event == null || event.type() == null) return null;
        JsonObject data = event.data() == null ? new JsonObject() : event.data();
        Instant at = event.createdAt();
        return switch (event.type()) {
            case "leaf_earned" -> {
                String detail = orElse(text(data, "detail"), "Unknown");
                if ("war".equals(text(data, "source"))) {
                    boolean defence = UnlockPopup.isTrue(data, "in_claim");
                    yield new Row(defence ? Kind.DEFENCE : Kind.WAR, UnlockPopup.WAR_ICON, null, detail,
                            defence ? "Defence" : "War", number(data, "leaf"), 0, at);
                }
                yield new Row(Kind.RAID, UnlockPopup.raidIconByName(detail), null, detail, "Raid",
                        number(data, "leaf"), 0, at);
            }
            case "quest_completed" -> {
                JsonObject quest = object(data, "quest");
                if (quest == null) yield null;
                JsonObject reward = object(quest, "reward");
                JsonObject objective = object(quest, "objective");
                String slot = text(quest, "slot");
                yield new Row(Kind.QUEST,
                        objective == null ? UnlockPopup.RAID_ICON
                                : UnlockPopup.objectiveIcon(text(objective, "type"), text(objective, "raid")),
                        UnlockPopup.questTier(slot), orElse(text(quest, "title"), "Quest"),
                        UnlockPopup.slotName(slot) + " quest", reward == null ? 0 : number(reward, "leaf"), 0, at);
            }
            case "achievement_paid" -> {
                String tier = text(data, "tier") == null ? null : text(data, "tier").toLowerCase(Locale.ROOT);
                String label = orElse(text(data, "label"), "Milestone");
                long threshold = number(data, "threshold");
                String ladder = UnlockPopup.ladderPhrase(text(data, "ladder"));
                String title = threshold > 0 ? QuestBoardScreen.formatCount(threshold) + " " + ladder : ladder;
                yield new Row(Kind.MILESTONE, ladderIcon(text(data, "ladder")), tier,
                        title.isBlank() ? label : title, label + " milestone", number(data, "leaf"), 0, at);
            }
            case "level_up" -> {
                long level = number(data, "level");
                yield new Row(Kind.LEVEL, null, "level", "Level " + level, "Level up", 0, level, at);
            }
            default -> null;
        };
    }

    // ── Drawing ──

    /** UI pixels per art pixel. */
    static final float P = 2;
    /** Art pixels: the title band, each row, and the space after the last row. */
    static final int TITLE = 13;
    static final int ROW = 16;
    static final int FOOT = 3;
    static final String EMPTY_LINE = "Nothing yet. Raids, wars and quests you finish show up here.";
    static final String FAILED_LINE = "Couldn't load your recent Leaf. It will try again shortly.";

    /** The list's height in UI pixels; with no rows it keeps one row's room for a line saying so. */
    static float height(int rows) {
        return (TITLE + Math.max(1, rows) * ROW + FOOT) * P;
    }

    /**
     * The list in a pixel frame: a tile for where each amount came from, what it
     * was and how long ago, and the Leaf it paid on the right.
     */
    static void render(UiCanvas canvas, List<Row> rows, boolean failed, Instant now, float x, float y, float width) {
        float p = P;
        Color green = color(CONTROL_SUCCESS);
        PixelArt.pixelPanel(canvas, x, y, width, height(rows.size()), p, PixelArt.mix(green, color(BACKGROUND_POPUP), 0.35f),
                1f, PixelArt.SEE_THROUGH);
        // Lined up with the tiles' left edges.
        PixelArt.outlinedText(canvas, "RECENT", x + 5 * p, y + 8 * p, 10, green, 1f, HorizontalAlign.LEFT);
        float listTop = y + TITLE * p;
        if (rows.isEmpty()) {
            PixelArt.fitted(canvas, failed ? FAILED_LINE : EMPTY_LINE, x + 5 * p, listTop + ROW / 2f * p, width - 10 * p, 10,
                    failed ? color(CONTROL_WARNING) : color(TEXT_MUTED), 1f);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float top = listTop + i * ROW * p;
            canvas.fillRect(x + 5 * p, top - p, width - 10 * p, p, PixelArt.withAlpha(Color.BLACK, 0.28f));
            Color accent = color(accent(row));
            float tile = 12 * p;
            float tileX = x + 5 * p;
            float tileY = top + 2 * p;
            PixelArt.iconTile(canvas, row.icon(), tileX, tileY, tile, p, accent, 1f);
            if (row.kind() == Kind.LEVEL) {
                // The tile shows the level reached, like the level medal does.
                canvas.fillRect(tileX + p, tileY + p, tile - 2 * p, tile - 2 * p, color(CONTROL_INPUT));
                String level = Long.toString(row.level());
                float size = PixelArt.measure(level, 20) <= tile - 4 * p ? 20 : 10;
                PixelArt.levelText(canvas, level, tileX + tile / 2, tileY + tile / 2 + 1, size, 1f, HorizontalAlign.CENTER);
            }

            float right = x + width - 6 * p;
            float amountLeft = right;
            if (row.leaf() > 0) {
                String amount = "+" + QuestBoardScreen.formatCount(row.leaf());
                float amountWidth = PixelArt.leafAmountWidth(amount, 20, p);
                amountLeft = PixelArt.snap(right - amountWidth, p);
                PixelArt.leafAmount(canvas, amount, amountLeft, top + 8 * p, 20, 1f, p);
            }
            float textX = tileX + tile + 4 * p;
            float textWidth = Math.max(0, amountLeft - 6 * p - textX);
            PixelArt.fitted(canvas, row.title(), textX, top + 6 * p, textWidth, 10, color(TEXT_PRIMARY), 1f);
            Color labelColour = row.kind() == Kind.DEFENCE ? color(ACHIEVEMENT_GOLD) : color(TEXT_SECONDARY);
            String label = PixelArt.fit(row.label(), textWidth, 10);
            PixelArt.text(canvas, label, textX, top + 12 * p, 10, labelColour, HorizontalAlign.LEFT);
            String when = "  ·  " + ago(row.at(), now);
            float labelWidth = PixelArt.measure(label, 10);
            if (labelWidth + PixelArt.measure(when, 10) <= textWidth) {
                PixelArt.text(canvas, when, textX + labelWidth, top + 12 * p, 10, color(TEXT_MUTED), HorizontalAlign.LEFT);
            }
        }
    }

    static UiColor accent(Row row) {
        return switch (row.kind()) {
            case DEFENCE -> ACHIEVEMENT_GOLD;
            case QUEST, MILESTONE -> UnlockOverlay.tierToken(row.tier());
            default -> CONTROL_SUCCESS;
        };
    }

    /**
     * The 16-pixel picture for a milestone's ladder, which fits a row's tile: the
     * raid's art, the tower for wars, and the portal for all raids and anything else.
     */
    static String ladderIcon(String ladder) {
        if (ladder == null) return UnlockPopup.RAID_ICON;
        if (ladder.startsWith("war:")) return UnlockPopup.WAR_ICON;
        if (ladder.startsWith("raid:")) {
            String asset = UnlockPopup.raidAsset(ladder.substring("raid:".length()));
            if (asset != null) return "unlock_icon_" + asset;
        }
        return UnlockPopup.RAID_ICON;
    }

    /** "just now", "12m ago", "3h ago", "2d ago". */
    static String ago(Instant at, Instant now) {
        if (at == null || now == null) return "";
        long minutes = Math.max(0, Duration.between(at, now).toMinutes());
        if (minutes < 1) return "just now";
        if (minutes < 60) return minutes + "m ago";
        if (minutes < 48 * 60) return minutes / 60 + "h ago";
        return minutes / (24 * 60) + "d ago";
    }

    private static String orElse(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
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
