package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.model.LeafQuests.Goal;
import com.seqwawa.seq.model.LeafQuests.Objective;
import com.seqwawa.seq.model.LeafQuests.Quest;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.ui.unlock.PixelArt;
import com.seqwawa.seq.ui.unlock.UnlockPopup;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiCanvas.HorizontalAlign;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * One quest slot drawn in the popups' pixel art, like the board's header and its
 * recent list: a frame in the slot's colour, the slot's name with the banked
 * dailies as pips beside it and its clock under it, the objective's picture in a
 * framed tile, the quest, the Leaf it pays, and its progress bar with the count
 * written on it.
 *
 * <p>A quest that raids or wars can finish splits into two columns, one per goal:
 * each goal's picture over its own bar, with "or" between the pictures, so both
 * counts show at once. Once it is done it is one bar again.
 *
 * <p>Measured in art pixels two UI pixels wide; 80 tall.
 */
final class QuestCard {

    /** UI pixels per art pixel. */
    static final float P = 2;
    /** Art pixels. */
    static final int HEIGHT = 80;
    static final int TILE = 22;
    static final int PAD = 5;
    static final int PIP = 3;
    /** Art pixels between the two goals' bars. */
    static final int GOAL_GAP = 3;

    private QuestCard() {}

    /** The 16-pixel picture for an objective: the raid's art, the portal for any raid, the tower, or the star. */
    static String tileIcon(Objective objective) {
        if (objective == null) return UnlockPopup.RAID_ICON;
        return UnlockPopup.objectiveIcon(objective.type(), objective.raid());
    }

    static String goalIcon(Goal goal) {
        return UnlockPopup.objectiveIcon(goal.type(), goal.raid());
    }

    /**
     * Where a two-goal card's columns go: two equal widths, {@link #GOAL_GAP} apart,
     * filling the card inside its padding, on whole art pixels. The second takes
     * any odd pixel left over.
     */
    record Columns(float leftX, float leftWidth, float rightX, float rightWidth) {}

    static Columns columns(float x, float width, float p) {
        float inside = width - 2 * PAD * p;
        float left = Math.max(0, (float) Math.floor((inside - GOAL_GAP * p) / 2 / p) * p);
        float rightX = x + PAD * p + left + GOAL_GAP * p;
        return new Columns(x + PAD * p, left, rightX, x + width - PAD * p - rightX);
    }

    /**
     * The Leaf a quest pays as it fits in {@code room}: in full, then shortened
     * ("+12.5k"). Null when even that does not fit.
     *
     * @param leafWidth how wide an amount is drawn with its Leaf
     */
    static String rewardText(long leaf, float room, Function<String, Float> leafWidth) {
        String full = "+" + QuestBoardScreen.formatCount(leaf);
        if (leafWidth.apply(full) <= room) return full;
        String shortened = "+" + QuestBoardScreen.shortCount(leaf);
        return leafWidth.apply(shortened) <= room ? shortened : null;
    }

    /** A quest's reward line: the Leaf, and the XP beside it, or null when it does not fit. */
    record RewardLine(String leaf, String xp) {}

    /**
     * What a quest pays, as it fits in {@code room}: the Leaf and the XP
     * ("+300 [leaf]  +150 XP"), then the Leaf alone. Null when not even the Leaf fits.
     *
     * @param gap the space between the Leaf and the XP
     * @param leafWidth how wide an amount is drawn with its Leaf
     * @param xpWidth how wide the XP is drawn
     */
    static RewardLine rewardLine(long leaf, long xp, float room, float gap, Function<String, Float> leafWidth,
            Function<String, Float> xpWidth) {
        String leafText = rewardText(leaf, room, leafWidth);
        if (leafText == null) return null;
        String xpText = xp > 0 ? "+" + QuestBoardScreen.formatCount(xp) + " XP" : null;
        boolean both = xpText != null && leafWidth.apply(leafText) + gap + xpWidth.apply(xpText) <= room;
        return new RewardLine(leafText, both ? xpText : null);
    }

    /** Splits text into lines no wider than {@code width}, breaking at spaces. */
    static List<String> wrap(String value, float width, Function<String, Float> textWidth) {
        List<String> lines = new ArrayList<>();
        if (value == null) return lines;
        StringBuilder line = new StringBuilder();
        for (String word : value.split(" ")) {
            String tried = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && textWidth.apply(tried) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(tried);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        // A single word wider than the card is cut short rather than drawn past its edges.
        lines.replaceAll(each -> PixelArt.fit(each, width, textWidth));
        return lines;
    }

    /**
     * A quest's title on one line, or two when it is too long. A quest raids or wars
     * can finish breaks before its "or", so each way reads whole: "Complete 3 NOL
     * raids", "or 9 wars". A third line's words join the second, cut short to fit.
     */
    static List<String> titleLines(String title, float width, Function<String, Float> textWidth) {
        if (title == null) return List.of();
        if (textWidth.apply(title) <= width) return List.of(title);
        int or = title.lastIndexOf(" or ");
        if (or > 0) {
            String first = title.substring(0, or);
            String second = title.substring(or + 1);
            if (textWidth.apply(first) <= width && textWidth.apply(second) <= width) return List.of(first, second);
        }
        List<String> lines = wrap(title, width, textWidth);
        if (lines.size() <= 2) return lines;
        return List.of(lines.get(0), PixelArt.fit(String.join(" ", lines.subList(1, lines.size())), width, textWidth));
    }

    /** One part of the bar per raid or war a short quest asks for; none for long counts or season rating. */
    static int notches(long target) {
        return target >= 2 && target <= 12 ? (int) target : 0;
    }

    /** "Daily", "Weekly" or "Repeatable". */
    static String slotName(String slot) {
        return UnlockPopup.slotName(slot);
    }

    /** Draws one card with its top left at ({@code x}, {@code y}), {@code width} UI pixels wide. */
    static void render(UiCanvas canvas, QuestBoardScreen.Card card, float x, float y, float width) {
        float p = P;
        float w = width;
        float h = HEIGHT * p;
        Color accent = color(QuestBoardScreen.slotAccent(card.slot()));
        PixelArt.pixelPanel(canvas, x, y, w, h, p, PixelArt.mix(accent, color(BACKGROUND_POPUP), 0.35f), 1f,
                PixelArt.SEE_THROUGH);
        float cx = x + w / 2;
        float inner = w - 2 * PAD * p;

        // The slot's name, with a pip for each daily the bank can hold, lit for the ones it does.
        String name = slotName(card.slot());
        float nameWidth = PixelArt.outlinedInkWidth(name, 20);
        int pips = card.bankSize();
        float pipsWidth = pips > 0 ? pips * PIP * p + (pips - 1) * p : 0;
        float groupWidth = nameWidth + (pips > 0 ? 3 * p + pipsWidth : 0);
        float left = PixelArt.snap(cx - groupWidth / 2, p);
        PixelArt.outlinedText(canvas, name, left, y + 9 * p, 20, accent, 1f, HorizontalAlign.LEFT);
        for (int i = 0; i < pips; i++) {
            float px = left + nameWidth + 3 * p + i * (PIP + 1) * p;
            float py = y + 8 * p;
            canvas.fillRect(px - p, py - p, (PIP + 2) * p, (PIP + 2) * p, new Color(8, 6, 12, 150));
            canvas.fillRect(px, py, PIP * p, PIP * p, i < card.banked() ? accent : PixelArt.mix(accent, color(CONTROL_INPUT), 0.8f));
            if (i < card.banked()) canvas.fillRect(px, py, PIP * p, p, PixelArt.mix(accent, Color.WHITE, 0.5f));
        }
        // When the next daily comes or the weekly ends. A repeatable needs no clock: the next one comes when it is done.
        if (!LeafQuests.REPEATABLE.equals(card.slot())) {
            PixelArt.text(canvas, PixelArt.fit(card.timer(), inner, 10), cx, y + 17 * p, 10, color(TEXT_MUTED),
                    HorizontalAlign.CENTER);
        }

        Quest quest = card.quest();
        if (quest == null) {
            List<String> lines = wrap(card.emptyLine(), inner, text -> PixelArt.measure(text, 10));
            float firstY = y + (40 - (lines.size() - 1) * 3) * p;
            for (int i = 0; i < lines.size(); i++) {
                PixelArt.text(canvas, lines.get(i), cx, firstY + i * 6 * p, 10, color(TEXT_SECONDARY), HorizontalAlign.CENTER);
            }
            return;
        }

        List<Goal> goals = quest.goalsOrObjective();
        boolean either = goals.size() > 1 && !quest.completed();
        Columns columns = columns(x, w, p);
        float tileY = y + 22 * p;
        if (either) {
            // Each goal's picture over the middle of its own bar, and "or" between them.
            tile(canvas, goalIcon(goals.get(0)), columns.leftX() + columns.leftWidth() / 2, tileY, accent);
            tile(canvas, goalIcon(goals.get(1)), columns.rightX() + columns.rightWidth() / 2, tileY, accent);
            PixelArt.text(canvas, "or", cx, tileY + TILE * p / 2, 10, color(TEXT_MUTED), HorizontalAlign.CENTER);
        } else {
            // A finished quest shows the goal that finished it, which Leaf sends as its objective.
            tile(canvas, tileIcon(quest.objective()), cx, tileY, accent);
        }

        // The quest, on up to two lines, the pair centred on where one line would sit.
        List<String> title = titleLines(quest.title(), inner, text -> PixelArt.measure(text, 10));
        float titleY = y + (title.size() > 1 ? 49 : 51) * p;
        for (int i = 0; i < title.size(); i++) {
            PixelArt.text(canvas, title.get(i), cx, titleY + i * 6 * p, 10, color(TEXT_PRIMARY), HorizontalAlign.CENTER);
        }

        // The Leaf and XP it pays, as the popups write them, then the bar with how far along written on it.
        float gap = 3 * p;
        RewardLine reward = quest.reward() == null ? null : rewardLine(quest.reward().leaf(), quest.reward().xp(), inner,
                gap, text -> PixelArt.leafAmountWidth(text, 10, p), text -> PixelArt.outlinedInkWidth(text, 10));
        if (reward != null) {
            float rewardWidth = PixelArt.leafAmountWidth(reward.leaf(), 10, p)
                    + (reward.xp() == null ? 0 : gap + PixelArt.outlinedInkWidth(reward.xp(), 10));
            float rewardY = y + 61 * p;
            float after = PixelArt.leafAmount(canvas, reward.leaf(), PixelArt.snap(cx - rewardWidth / 2, p), rewardY, 10,
                    1f, p);
            if (reward.xp() != null) {
                PixelArt.outlinedText(canvas, reward.xp(), after + gap, rewardY, 10, color(TEXT_SECONDARY), 1f,
                        HorizontalAlign.LEFT);
            }
        }
        float barY = y + 67 * p;
        float barH = 9 * p;
        if (either) {
            bar(canvas, goals.get(0), columns.leftX(), barY, columns.leftWidth(), barH, accent);
            bar(canvas, goals.get(1), columns.rightX(), barY, columns.rightWidth(), barH, accent);
            return;
        }
        float barX = x + PAD * p;
        float barW = w - 2 * PAD * p;
        if (quest.completed()) {
            PixelArt.progressBar(canvas, barX, barY, barW, barH, p, 1f, color(CONTROL_SUCCESS), 1f, 0);
            barLabel(canvas, "Done", barX, barY, barW, barH);
            return;
        }
        bar(canvas, goals.getFirst(), barX, barY, barW, barH, accent);
    }

    private static void tile(UiCanvas canvas, String icon, float centreX, float y, Color accent) {
        PixelArt.iconTile(canvas, icon, PixelArt.snap(centreX - TILE * P / 2, P), y, TILE * P, P, accent, 1f);
    }

    /** One goal's bar, a part per raid or war, with how far along written on it. */
    private static void bar(UiCanvas canvas, Goal goal, float x, float y, float width, float height, Color accent) {
        PixelArt.progressBar(canvas, x, y, width, height, P, QuestBoardScreen.progressRatio(goal), accent, 1f,
                notches(goal.target()));
        barLabel(canvas, QuestBoardScreen.progressLabel(goal), x, y, width, height);
    }

    private static void barLabel(UiCanvas canvas, String label, float x, float y, float width, float height) {
        PixelArt.outlinedText(canvas, PixelArt.fit(label, width - 4 * P, 10), x + width / 2, y + height / 2, 10,
                color(TEXT_PRIMARY), 1f, HorizontalAlign.CENTER);
    }
}
