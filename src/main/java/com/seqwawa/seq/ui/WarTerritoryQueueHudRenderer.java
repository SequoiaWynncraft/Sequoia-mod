package com.seqwawa.seq.ui;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.managers.ThemeManager;
import com.seqwawa.seq.managers.WarTerritoryQueueManager;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import com.seqwawa.seq.ui.theme.UiColor;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;

/** Compact top-right queue feed shown while the local player is available for wars. */
public final class WarTerritoryQueueHudRenderer {
    static final int DEFAULT_MAX_ROWS = 6;
    static final int MAX_CONFIGURABLE_ROWS = 20;
    static final float DEFAULT_TEXT_SIZE = 9f;
    private static final float ROW_GAP = 3f;
    private static final float HUD_MARGIN = 7f;
    private static final String PREVIEW_FIRST_LINE =
            "Soup Person 12m ago - Alekin (Very Low/Very High) 02:40  3/5";
    private static final String PREVIEW_SECOND_LINE =
            "Queue Owner 19s ago - Detlas (Low) 01:41  1/5";

    private WarTerritoryQueueHudRenderer() {}

    public static void render(UiCanvas canvas) {
        WarTerritoryQueueManager manager = SeqClient.getWarTerritoryQueueManager();
        if (canvas == null || manager == null || !manager.isActive()) {
            return;
        }
        String font = SeqClient.getFontManager() == null
                ? "mc"
                : SeqClient.getFontManager().getSelectedFont();
        float textSize = textSize(SeqClient.getWarQueueHudTextSizeSetting());
        int visibleRows = Math.min(
                maxRows(SeqClient.getWarQueueHudMaxRowsSetting()),
                rowsFittingHeight(canvas.metrics().height(), textSize));
        List<TerritoryQueue> queues = displayedQueues(
                manager.activeQueues(),
                manager.localPlayerUuid(),
                onlyOwnedOrJoined(SeqClient.getWarQueueHudOnlyOwnedOrJoinedSetting()),
                visibleRows);
        if (queues.isEmpty()) {
            return;
        }

        Instant now = manager.serverNow();
        drawLines(
                canvas,
                queues.stream().map(queue -> styledSegments(queue, now)).toList(),
                font,
                textSize,
                position(SeqClient.getWarQueueHudXSetting(), 1f),
                position(SeqClient.getWarQueueHudYSetting(), 0f));
    }

    static Bounds renderPreview(UiCanvas canvas) {
        String font = SeqClient.getFontManager() == null
                ? "mc"
                : SeqClient.getFontManager().getSelectedFont();
        return drawLines(
                canvas,
                List.of(
                        List.of(new Segment(
                                PREVIEW_FIRST_LINE,
                                ThemeManager.color(UiColor.TEXT_PRIMARY))),
                        List.of(new Segment(
                                PREVIEW_SECOND_LINE,
                                ThemeManager.color(UiColor.TEXT_SECONDARY)))),
                font,
                textSize(SeqClient.getWarQueueHudTextSizeSetting()),
                position(SeqClient.getWarQueueHudXSetting(), 1f),
                position(SeqClient.getWarQueueHudYSetting(), 0f));
    }

    static Bounds previewBounds(float screenWidth, float screenHeight) {
        String font = SeqClient.getFontManager() == null
                ? "mc"
                : SeqClient.getFontManager().getSelectedFont();
        float textSize = textSize(SeqClient.getWarQueueHudTextSizeSetting());
        float width = Math.max(
                UiRenderer.measureText(PREVIEW_FIRST_LINE, font, textSize).width(),
                UiRenderer.measureText(PREVIEW_SECOND_LINE, font, textSize).width());
        return positionBounds(
                screenWidth,
                screenHeight,
                width,
                textSize + rowHeight(textSize),
                position(SeqClient.getWarQueueHudXSetting(), 1f),
                position(SeqClient.getWarQueueHudYSetting(), 0f));
    }

    private static Bounds drawLines(
            UiCanvas canvas,
            List<List<Segment>> lines,
            String font,
            float textSize,
            float normalizedX,
            float normalizedY) {
        List<Float> widths = lines.stream().map(line -> lineWidth(line, font, textSize)).toList();
        float width = widths.stream().max(Float::compare).orElse(0f);
        float height = lines.isEmpty() ? 0f : textSize + (lines.size() - 1) * rowHeight(textSize);
        Bounds bounds = positionBounds(
                canvas.metrics().width(), canvas.metrics().height(), width, height, normalizedX, normalizedY);
        for (int row = 0; row < lines.size(); row++) {
            List<Segment> segments = lines.get(row);
            float x = bounds.x() + width - widths.get(row);
            float y = bounds.y() + row * rowHeight(textSize);
            for (Segment segment : segments) {
                canvas.drawText(
                        segment.text(),
                        x,
                        y,
                        new UiCanvas.TextStyle(
                                font,
                                textSize,
                                segment.color(),
                                UiCanvas.HorizontalAlign.LEFT,
                                UiCanvas.VerticalAlign.TOP));
                x += UiRenderer.measureText(segment.text(), font, textSize).width();
            }
        }
        return bounds;
    }

    private static float lineWidth(List<Segment> segments, String font, float textSize) {
        return segments.stream()
                .map(segment -> UiRenderer.measureText(segment.text(), font, textSize).width())
                .reduce(0f, Float::sum);
    }

    private static float position(Setting.FloatSetting setting, float fallback) {
        return setting == null || setting.getValue() == null ? fallback : Math.clamp(setting.getValue(), 0f, 1f);
    }

    static float textSize(Setting.IntSetting setting) {
        return setting == null || setting.getValue() == null ? DEFAULT_TEXT_SIZE : setting.getValue();
    }

    static float rowHeight(float textSize) {
        return textSize + ROW_GAP;
    }

    static boolean onlyOwnedOrJoined(Setting.BooleanSetting setting) {
        return setting != null && Boolean.TRUE.equals(setting.getValue());
    }

    static int maxRows(Setting.IntSetting setting) {
        int configured = setting == null || setting.getValue() == null ? DEFAULT_MAX_ROWS : setting.getValue();
        return Math.max(1, Math.min(MAX_CONFIGURABLE_ROWS, configured));
    }

    static int rowsFittingHeight(float canvasHeight, float textSize) {
        if (!Float.isFinite(canvasHeight) || !Float.isFinite(textSize) || textSize <= 0f) {
            return 0;
        }
        float remainingAfterFirstRow = canvasHeight - HUD_MARGIN - textSize;
        if (remainingAfterFirstRow < 0f) {
            return 0;
        }
        return 1 + Math.max(0, (int) Math.floor(remainingAfterFirstRow / rowHeight(textSize)));
    }

    static List<TerritoryQueue> displayedQueues(
            List<TerritoryQueue> queues, String localPlayerUuid, boolean onlyOwnedOrJoined, int maxRows) {
        if (queues == null || queues.isEmpty() || maxRows <= 0) {
            return List.of();
        }
        ArrayList<TerritoryQueue> displayed = new ArrayList<>(Math.min(queues.size(), maxRows));
        for (TerritoryQueue queue : queues) {
            if (queue == null || (onlyOwnedOrJoined && !includesPlayer(queue, localPlayerUuid))) {
                continue;
            }
            displayed.add(queue);
            if (displayed.size() >= maxRows) {
                break;
            }
        }
        return List.copyOf(displayed);
    }

    private static boolean includesPlayer(TerritoryQueue queue, String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return false;
        }
        return playerUuid.equalsIgnoreCase(queue.queuedBy()) || queue.hasParticipant(playerUuid);
    }

    static String formatLine(TerritoryQueue queue, Instant now) {
        StringBuilder line = new StringBuilder(compact(queue.displayName(), 30));
        String age = formatAge(queue.queuedAt(), now);
        if (!age.isEmpty()) {
            line.append(' ').append(age);
        }
        line.append(" - ").append(compact(queue.territory(), 36));
        String defenses = formatDefenses(queue.queuedDefenseRating(), queue.reportedDefenseRating());
        if (!defenses.isEmpty()) {
            line.append(' ').append(defenses);
        }
        line.append(' ').append(formatCountdown(queue.expiresAt(), now));
        line.append("  ").append(participantLabel(queue.participantCount()));
        return line.toString();
    }

    static String formatAge(Instant queuedAt, Instant now) {
        if (queuedAt == null || now == null) {
            return "";
        }
        long elapsedSeconds = Math.max(0L, Duration.between(queuedAt, now).getSeconds());
        long hours = elapsedSeconds / 3600;
        long minutes = elapsedSeconds / 60 % 60;
        long seconds = elapsedSeconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m ago";
        }
        if (minutes > 0) {
            return minutes + "m " + seconds + "s ago";
        }
        return seconds + "s ago";
    }

    static String formatCountdown(Instant expiresAt, Instant now) {
        if (expiresAt == null || now == null) {
            return "--:--";
        }
        long remainingSeconds = Math.max(0L, Duration.between(now, expiresAt).getSeconds());
        return String.format(Locale.ROOT, "%02d:%02d", remainingSeconds / 60, remainingSeconds % 60);
    }

    static String formatDefenses(String queuedDefense, String reportedDefense) {
        DefenseRatings defenses = defenseRatings(queuedDefense, reportedDefense);
        String queued = defenses.queued();
        String reported = defenses.reported();
        if (queued == null && reported == null) {
            return "(Unknown)";
        }
        if (queued == null) {
            return "(" + reported + ")";
        }
        if (reported == null) {
            return "(" + queued + ")";
        }
        return "(" + queued + "/" + reported + ")";
    }

    static String participantLabel(int participantCount) {
        return Math.max(0, Math.min(5, participantCount)) + "/5";
    }

    private static List<Segment> styledSegments(TerritoryQueue queue, Instant now) {
        ArrayList<Segment> segments = new ArrayList<>();
        segments.add(new Segment(compact(queue.displayName(), 30), ThemeManager.color(UiColor.TEXT_PRIMARY)));

        String age = formatAge(queue.queuedAt(), now);
        if (!age.isEmpty()) {
            segments.add(new Segment(" " + age, ThemeManager.color(UiColor.TEXT_MUTED)));
        }
        segments.add(new Segment(" - ", ThemeManager.color(UiColor.TEXT_MUTED)));
        segments.add(new Segment(compact(queue.territory(), 36), ThemeManager.color(UiColor.TEXT_PRIMARY)));

        DefenseRatings defenses = defenseRatings(queue.queuedDefenseRating(), queue.reportedDefenseRating());
        String queuedDefense = defenses.queued();
        String reportedDefense = defenses.reported();
        if (queuedDefense == null && reportedDefense == null) {
            segments.add(new Segment(" (Unknown)", ThemeManager.color(UiColor.TEXT_MUTED)));
        } else {
            segments.add(new Segment(" (", ThemeManager.color(UiColor.TEXT_MUTED)));
            if (queuedDefense != null) {
                segments.add(new Segment(queuedDefense, defenseColor(queuedDefense)));
            }
            if (queuedDefense != null && reportedDefense != null) {
                segments.add(new Segment("/", ThemeManager.color(UiColor.TEXT_MUTED)));
            }
            if (reportedDefense != null) {
                segments.add(new Segment(reportedDefense, defenseColor(reportedDefense)));
            }
            segments.add(new Segment(")", ThemeManager.color(UiColor.TEXT_MUTED)));
        }

        segments.add(new Segment(
                " " + formatCountdown(queue.expiresAt(), now),
                ThemeManager.color(UiColor.MAP_TOTEM_RANGE)));
        segments.add(new Segment(
                "  " + participantLabel(queue.participantCount()),
                ThemeManager.color(queue.full() ? UiColor.CONTROL_WARNING : UiColor.TEXT_SECONDARY)));
        return List.copyOf(segments);
    }

    /** Mirrors Wynntils' GuildResourceValues#getDefenceColor vanilla formatting palette. */
    static Color defenseColor(String defense) {
        String normalized = normalize(defense);
        ChatFormatting formatting = normalized == null
                ? null
                : switch (normalized.toLowerCase(Locale.ROOT)) {
                    case "very low" -> ChatFormatting.DARK_GREEN;
                    case "low" -> ChatFormatting.GREEN;
                    case "medium" -> ChatFormatting.YELLOW;
                    case "high" -> ChatFormatting.RED;
                    case "very high" -> ChatFormatting.DARK_RED;
                    default -> null;
                };
        return formatting == null
                ? ThemeManager.color(UiColor.TEXT_SECONDARY)
                : new Color(formatting.getColor());
    }

    private static String compact(String value, int maxLength) {
        String normalized = normalize(value);
        if (normalized == null) {
            return "";
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    static DefenseRatings defenseRatings(String queuedDefense, String reportedDefense) {
        String queued = normalize(queuedDefense);
        String reported = normalize(reportedDefense);
        if (queued != null && reported != null && queued.equalsIgnoreCase(reported)) {
            reported = null;
        }
        return new DefenseRatings(queued, reported);
    }

    static Bounds positionBounds(
            float screenWidth,
            float screenHeight,
            float contentWidth,
            float contentHeight,
            float normalizedX,
            float normalizedY) {
        float travelX = Math.max(0f, screenWidth - HUD_MARGIN * 2f - contentWidth);
        float travelY = Math.max(0f, screenHeight - HUD_MARGIN * 2f - contentHeight);
        return new Bounds(
                HUD_MARGIN + Math.clamp(normalizedX, 0f, 1f) * travelX,
                HUD_MARGIN + Math.clamp(normalizedY, 0f, 1f) * travelY,
                contentWidth,
                contentHeight);
    }

    static Position positionForTopLeft(
            float screenWidth,
            float screenHeight,
            float contentWidth,
            float contentHeight,
            float left,
            float top) {
        float travelX = Math.max(0f, screenWidth - HUD_MARGIN * 2f - contentWidth);
        float travelY = Math.max(0f, screenHeight - HUD_MARGIN * 2f - contentHeight);
        return new Position(
                travelX == 0f ? 0f : Math.clamp((left - HUD_MARGIN) / travelX, 0f, 1f),
                travelY == 0f ? 0f : Math.clamp((top - HUD_MARGIN) / travelY, 0f, 1f));
    }

    private record Segment(String text, Color color) {}

    record Bounds(float x, float y, float width, float height) {
        boolean contains(float pointX, float pointY, float padding) {
            return pointX >= x - padding
                    && pointX <= x + width + padding
                    && pointY >= y - padding
                    && pointY <= y + height + padding;
        }
    }

    record Position(float x, float y) {}

    record DefenseRatings(String queued, String reported) {}
}
