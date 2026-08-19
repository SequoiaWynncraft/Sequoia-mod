package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_DIVIDER;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.BACKGROUND_POPUP;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_BORDER;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_DISABLED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_MUTED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_PRIMARY;

import com.seqwawa.seq.managers.PrincessRaidStatsManager.Snapshot;
import com.seqwawa.seq.managers.PrincessRaidStatsManager.State;
import com.seqwawa.seq.model.PrincessRaidStats.LeaderboardEntry;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.util.List;

/** Compact Princess-mode leaderboard card used by the settings easter egg. */
final class PrincessLeaderboardPanel {
    static final float HEIGHT = 116;
    static final float MIN_HEIGHT = 60;
    private static final int MAX_ROWS = 5;
    private static final float FIRST_ROW_Y = 39;
    private static final float ROW_SPACING = 14;

    private PrincessLeaderboardPanel() {}

    static void render(
            UiCanvas canvas,
            String font,
            float x,
            float y,
            float width,
            float height,
            Snapshot snapshot) {
        if (width <= 0 || height < MIN_HEIGHT) {
            return;
        }

        canvas.fillRect(x, y, width, height, color(BACKGROUND_POPUP));
        canvas.strokeRect(x, y, width, height, 1, color(CONTROL_BORDER));
        canvas.save();
        canvas.scissor(x, y, width, height);
        try {
            draw(
                    canvas,
                    font,
                    10,
                    color(ACCENT_PRIMARY),
                    UiCanvas.HorizontalAlign.LEFT,
                    x + 7,
                    y + 14,
                    width < 150 ? "Princess LB" : "Princess graids");
            draw(
                    canvas,
                    font,
                    width < 150 ? 9 : 10,
                    snapshot.countKnown() ? color(TEXT_PRIMARY) : color(TEXT_MUTED),
                    UiCanvas.HorizontalAlign.RIGHT,
                    x + width - 7,
                    y + 14,
                    ownSummary(snapshot, width < 175));
            canvas.fillRect(x + 6, y + 26, width - 12, 1, color(ACCENT_DIVIDER));

            List<LeaderboardEntry> entries = visibleEntries(snapshot, height);
            if (entries.isEmpty()) {
                draw(
                        canvas,
                        font,
                        width < 150 ? 8 : 10,
                        color(TEXT_MUTED),
                        UiCanvas.HorizontalAlign.CENTER,
                        x + width / 2f,
                        emptyMessageY(y, height),
                        emptyMessage(snapshot));
            } else {
                float rowY = y + FIRST_ROW_Y;
                for (LeaderboardEntry entry : entries) {
                    draw(
                            canvas,
                            font,
                            width < 150 ? 9 : 10,
                            color(TEXT_PRIMARY),
                            UiCanvas.HorizontalAlign.LEFT,
                            x + 7,
                            rowY,
                            entryLabel(entry, width));
                    draw(
                            canvas,
                            font,
                            width < 150 ? 9 : 10,
                            color(ACCENT_PRIMARY),
                            UiCanvas.HorizontalAlign.RIGHT,
                            x + width - 7,
                            rowY,
                            Long.toString(entry.raidCount()));
                    rowY += ROW_SPACING;
                }
            }

            String status = statusLine(snapshot);
            if (status != null) {
                draw(
                        canvas,
                        font,
                        width < 150 ? 7 : 8,
                        color(TEXT_DISABLED),
                        UiCanvas.HorizontalAlign.CENTER,
                        x + width / 2f,
                        y + height - 7,
                        status);
            }
        } finally {
            canvas.restore();
        }
    }

    static List<LeaderboardEntry> visibleEntries(Snapshot snapshot) {
        return visibleEntries(snapshot, HEIGHT);
    }

    static List<LeaderboardEntry> visibleEntries(Snapshot snapshot, float height) {
        return snapshot.leaderboard().stream().limit(visibleRowCount(height)).toList();
    }

    static int visibleRowCount(float height) {
        if (height < MIN_HEIGHT) {
            return 0;
        }
        return Math.clamp((int) Math.floor((height - 46) / ROW_SPACING), 1, MAX_ROWS);
    }

    static String entryLabel(LeaderboardEntry entry, float width) {
        String username = entry.minecraftUsername();
        int maximumLength = Math.clamp((int) ((width - 75) / 6), 3, 16);
        if (username.length() > maximumLength) {
            username = username.substring(0, maximumLength) + "…";
        }
        return entry.rank() + ".  " + username;
    }

    static String ownSummary(Snapshot snapshot) {
        return ownSummary(snapshot, false);
    }

    private static String ownSummary(Snapshot snapshot, boolean compact) {
        if (!snapshot.countKnown()) {
            return compact ? "…" : "You: …";
        }
        String rank = snapshot.ownRank() == null ? "" : "  #" + snapshot.ownRank();
        return (compact ? "" : "You: ") + snapshot.ownRaidCount() + rank;
    }

    static String emptyMessage(Snapshot snapshot) {
        return switch (snapshot.state()) {
            case IDLE, LOADING -> "Loading royal records…";
            case READY -> "No Princess graids yet";
            case UNAVAILABLE -> snapshot.countKnown() ? "Cached royal record" : "Leaderboard unavailable";
        };
    }

    static String statusLine(Snapshot snapshot) {
        if (snapshot.state() == State.LOADING && snapshot.countKnown()) {
            return "Refreshing…";
        }
        return snapshot.state() == State.UNAVAILABLE ? "Princess stats unavailable" : null;
    }

    private static float emptyMessageY(float y, float height) {
        return y + 27 + (height - 41) / 2f;
    }

    private static void draw(
            UiCanvas canvas,
            String font,
            float size,
            java.awt.Color textColor,
            UiCanvas.HorizontalAlign align,
            float x,
            float y,
            String text) {
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(
                font, size, textColor, align, UiCanvas.VerticalAlign.MIDDLE));
    }
}
