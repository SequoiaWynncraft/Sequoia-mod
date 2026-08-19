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
    static final float WIDTH = 206;
    static final float HEIGHT = 116;
    private static final int MAX_ROWS = 5;

    private PrincessLeaderboardPanel() {}

    static void render(UiCanvas canvas, String font, float x, float y, float width, Snapshot snapshot) {
        canvas.fillRect(x, y, width, HEIGHT, color(BACKGROUND_POPUP));
        canvas.strokeRect(x, y, width, HEIGHT, 1, color(CONTROL_BORDER));
        draw(
                canvas,
                font,
                12,
                color(ACCENT_PRIMARY),
                UiCanvas.HorizontalAlign.LEFT,
                x + 8,
                y + 14,
                "Princess graids");
        draw(
                canvas,
                font,
                10,
                snapshot.countKnown() ? color(TEXT_PRIMARY) : color(TEXT_MUTED),
                UiCanvas.HorizontalAlign.RIGHT,
                x + width - 8,
                y + 14,
                ownSummary(snapshot, width < 175));
        canvas.fillRect(x + 7, y + 26, width - 14, 1, color(ACCENT_DIVIDER));

        List<LeaderboardEntry> entries = visibleEntries(snapshot);
        if (entries.isEmpty()) {
            draw(
                    canvas,
                    font,
                    10,
                    color(TEXT_MUTED),
                    UiCanvas.HorizontalAlign.CENTER,
                    x + width / 2f,
                    y + 58,
                    emptyMessage(snapshot));
        } else {
            float rowY = y + 39;
            for (LeaderboardEntry entry : entries) {
                draw(
                        canvas,
                        font,
                        10,
                        color(TEXT_PRIMARY),
                        UiCanvas.HorizontalAlign.LEFT,
                        x + 8,
                        rowY,
                        entryLabel(entry, width));
                draw(
                        canvas,
                        font,
                        10,
                        color(ACCENT_PRIMARY),
                        UiCanvas.HorizontalAlign.RIGHT,
                        x + width - 8,
                        rowY,
                        Long.toString(entry.raidCount()));
                rowY += 14;
            }
        }

        String status = statusLine(snapshot);
        if (status != null) {
            draw(
                    canvas,
                    font,
                    8,
                    color(TEXT_DISABLED),
                    UiCanvas.HorizontalAlign.CENTER,
                    x + width / 2f,
                    y + HEIGHT - 7,
                    status);
        }
    }

    static List<LeaderboardEntry> visibleEntries(Snapshot snapshot) {
        return snapshot.leaderboard().stream().limit(MAX_ROWS).toList();
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
