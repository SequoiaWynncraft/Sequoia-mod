package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WarPlannerDialogUi.*;
import static com.seqwawa.seq.ui.WarPingPicker.pingCandidates;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static com.seqwawa.seq.ui.WarMapGeometry.*;

/** Queue labels, highlights, tooltips and click identity. */
final class WarQueueMapOverlay {
    private static final float PADDING = 12;
    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;

    private static final long WAR_QUEUE_DOUBLE_CLICK_MILLIS = 350;
    private static final float WAR_QUEUE_DOUBLE_CLICK_MOVE_TOLERANCE = 4;
    private static final float WAR_QUEUE_MAP_MAX_FONT_SIZE = 8;
    private static final float WAR_QUEUE_MAP_MIN_FONT_SIZE = 5;
    private static final long WAR_QUEUE_PULSE_PERIOD_MILLIS = 1_600;
    private static final int WAR_QUEUE_PULSE_MIN_ALPHA = 36;
    private static final int WAR_QUEUE_PULSE_MAX_ALPHA = 96;
    static final int RESOURCE_FILL_ALPHA = 96;
    private static final float WAR_QUEUE_TOOLTIP_TEXT_SIZE = 9;
    private static final float WAR_QUEUE_TOOLTIP_LINE_HEIGHT = 13;
    static void drawWarQueuePulses(
            UiCanvas canvas,
            List<WarQueueMapMarker> markers,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale,
            long elapsedMillis) {
        for (WarQueueMapMarker marker : markers) {
            drawPreviewFill(
                    canvas,
                    marker.territory(),
                    fitted,
                    offsetX,
                    offsetY,
                    scale,
                    warQueuePulseColor(marker.queue(), elapsedMillis));
        }
    }

    static List<WarQueueMapMarker> warQueueMapMarkers(
            List<TerritoryQueue> queues,
            Set<String> shownZoneTerritories,
            Map<String, GuildTerritory> territoriesByName) {
        if (queues == null
                || queues.isEmpty()
                || shownZoneTerritories == null
                || shownZoneTerritories.isEmpty()
                || territoriesByName == null
                || territoriesByName.isEmpty()) {
            return List.of();
        }
        Set<String> shownTerritoryKeys = shownZoneTerritories.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        Map<String, GuildTerritory> territoriesByKey = new java.util.HashMap<>();
        territoriesByName.values().stream()
                .filter(java.util.Objects::nonNull)
                .forEach(territory -> territoriesByKey.putIfAbsent(
                        territory.name().toLowerCase(Locale.ROOT), territory));
        Set<String> markedTerritories = new java.util.HashSet<>();
        ArrayList<WarQueueMapMarker> markers = new ArrayList<>();
        for (TerritoryQueue queue : queues) {
            if (queue == null || queue.territory() == null || queue.territory().isBlank()) continue;
            String territoryKey = queue.territory().trim().toLowerCase(Locale.ROOT);
            GuildTerritory territory = territoriesByKey.get(territoryKey);
            if (!shownTerritoryKeys.contains(territoryKey)
                    || territory == null
                    || !markedTerritories.add(territoryKey)) {
                continue;
            }
            markers.add(new WarQueueMapMarker(queue, territory));
        }
        return List.copyOf(markers);
    }

    static TerritoryQueue warQueueForTerritory(List<WarQueueMapMarker> markers, String territoryName) {
        if (markers == null || territoryName == null || territoryName.isBlank()) return null;
        return markers.stream()
                .filter(marker -> marker.territory().name().equalsIgnoreCase(territoryName.trim()))
                .map(WarQueueMapMarker::queue)
                .findFirst()
                .orElse(null);
    }

    static String warQueuePulseDefense(TerritoryQueue queue) {
        if (queue == null) return null;
        WarTerritoryQueueHudRenderer.DefenseRatings defenses =
                WarTerritoryQueueHudRenderer.defenseRatings(
                        queue.queuedDefenseRating(), queue.reportedDefenseRating());
        return defenses.queued() == null ? defenses.reported() : defenses.queued();
    }

    static int warQueuePulseAlpha(long elapsedMillis) {
        long phaseMillis = Math.floorMod(elapsedMillis, WAR_QUEUE_PULSE_PERIOD_MILLIS);
        double phase = phaseMillis / (double) WAR_QUEUE_PULSE_PERIOD_MILLIS;
        double pulse = (1d - Math.cos(phase * Math.PI * 2d)) / 2d;
        return WAR_QUEUE_PULSE_MIN_ALPHA
                + (int) Math.round((WAR_QUEUE_PULSE_MAX_ALPHA - WAR_QUEUE_PULSE_MIN_ALPHA) * pulse);
    }

    static Color warQueuePulseColor(TerritoryQueue queue, long elapsedMillis) {
        Color defenseColor = WarTerritoryQueueHudRenderer.vanillaDefenseColor(warQueuePulseDefense(queue));
        if (defenseColor == null) {
            // Unknown defenses use the configured fallback unchanged; only vanilla palette markers pulse.
            return color(TEXT_SECONDARY);
        }
        return new Color(
                defenseColor.getRed(),
                defenseColor.getGreen(),
                defenseColor.getBlue(),
                warQueuePulseAlpha(elapsedMillis));
    }

    static void drawWarQueueLabels(
            UiCanvas canvas,
            List<WarQueueMapMarker> markers,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale) {
        for (WarQueueMapMarker marker : markers) {
            drawWarQueueLabel(
                    canvas,
                    marker.territory(),
                    warQueueMapUsername(marker.queue()),
                    fitted,
                    offsetX,
                    offsetY,
                    scale);
        }
    }

    static void drawWarQueueLabel(
            UiCanvas canvas,
            GuildTerritory territory,
            String displayName,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale) {
        if (displayName == null || displayName.isBlank()) return;
        WarQueueLabelBounds available = warQueueLabelBounds(
                territory, fitted, offsetX, offsetY, scale, Float.MAX_VALUE, Float.MAX_VALUE);
        if (available == null) return;

        float fontSize = Math.min(WAR_QUEUE_MAP_MAX_FONT_SIZE, available.height() - 4);
        if (fontSize < WAR_QUEUE_MAP_MIN_FONT_SIZE) return;
        String font = SeqClient.getFontManager().getSelectedFont();
        float maxTextWidth = available.width() - 6;
        while (fontSize > WAR_QUEUE_MAP_MIN_FONT_SIZE
                && UiRenderer.measureText(displayName, font, fontSize).width() > maxTextWidth) {
            fontSize = Math.max(WAR_QUEUE_MAP_MIN_FONT_SIZE, fontSize - .5f);
        }
        float fittedFontSize = fontSize;
        String label = fitWarQueueText(
                displayName,
                maxTextWidth,
                value -> UiRenderer.measureText(value, font, fittedFontSize).width());
        if (label.isBlank()) return;

        float textWidth = UiRenderer.measureText(label, font, fontSize).width();
        WarQueueLabelBounds labelBounds = warQueueLabelBounds(
                territory, fitted, offsetX, offsetY, scale, textWidth + 6, fontSize + 4);
        if (labelBounds == null) return;
        canvas.fillRect(
                labelBounds.x(),
                labelBounds.y(),
                labelBounds.width(),
                labelBounds.height(),
                color(BACKGROUND_POPUP));
        text(
                canvas,
                label,
                labelBounds.x() + labelBounds.width() / 2,
                labelBounds.y() + labelBounds.height() / 2,
                fontSize,
                color(TEXT_PRIMARY),
                true);
    }

    static String warQueueMapUsername(TerritoryQueue queue) {
        if (queue == null || queue.minecraftUsername() == null || queue.minecraftUsername().isBlank()) {
            return "Unknown";
        }
        return queue.minecraftUsername();
    }

    static String fitWarQueueText(
            String value, float maxWidth, ToDoubleFunction<String> widthMeasurer) {
        if (value == null || value.isBlank() || maxWidth <= 0 || widthMeasurer == null) return "";
        String normalized = value.trim();
        if (widthMeasurer.applyAsDouble(normalized) <= maxWidth) return normalized;
        String ellipsis = "…";
        if (widthMeasurer.applyAsDouble(ellipsis) > maxWidth) return "";
        int low = 0;
        int high = normalized.length();
        while (low < high) {
            int midpoint = (low + high + 1) / 2;
            String candidate = normalized.substring(0, midpoint).stripTrailing() + ellipsis;
            if (widthMeasurer.applyAsDouble(candidate) <= maxWidth) {
                low = midpoint;
            } else {
                high = midpoint - 1;
            }
        }
        return normalized.substring(0, low).stripTrailing() + ellipsis;
    }

    static List<String> wrapWarQueueText(
            String value, float maxWidth, ToDoubleFunction<String> widthMeasurer) {
        if (value == null || value.isBlank() || maxWidth <= 0 || widthMeasurer == null) return List.of();
        ArrayList<String> lines = new ArrayList<>();
        String remaining = value.trim();
        while (!remaining.isEmpty()) {
            if (widthMeasurer.applyAsDouble(remaining) <= maxWidth) {
                lines.add(remaining);
                break;
            }
            int low = 0;
            int high = remaining.length();
            while (low < high) {
                int midpoint = (low + high + 1) / 2;
                if (widthMeasurer.applyAsDouble(remaining.substring(0, midpoint)) <= maxWidth) {
                    low = midpoint;
                } else {
                    high = midpoint - 1;
                }
            }
            if (low == 0) {
                lines.add(remaining.substring(0, 1));
                remaining = remaining.substring(1).stripLeading();
                continue;
            }
            int whitespace = -1;
            for (int index = low - 1; index >= 0; index--) {
                if (Character.isWhitespace(remaining.charAt(index))) {
                    whitespace = index;
                    break;
                }
            }
            int split = whitespace > 0 ? whitespace : low;
            lines.add(remaining.substring(0, split).stripTrailing());
            remaining = remaining.substring(split).stripLeading();
        }
        return List.copyOf(lines);
    }

    static WarQueueLabelBounds warQueueLabelBounds(
            GuildTerritory territory,
            MapBounds fitted,
            float offsetX,
            float offsetY,
            float scale,
            float desiredWidth,
            float desiredHeight) {
        if (territory == null || fitted == null || scale <= 0 || desiredWidth <= 0 || desiredHeight <= 0) {
            return null;
        }
        MapBounds bounds = territory.bounds();
        float territoryX = previewX(bounds.minX(), fitted, offsetX, scale);
        float territoryY = previewY(bounds.minZ(), fitted, offsetY, scale);
        float territoryWidth = (float) ((bounds.maxX() - bounds.minX()) * scale);
        float territoryHeight = (float) ((bounds.maxZ() - bounds.minZ()) * scale);
        float availableWidth = territoryWidth - 4;
        float availableHeight = territoryHeight - 4;
        if (availableWidth <= 0 || availableHeight <= 0) return null;
        float width = Math.min(desiredWidth, availableWidth);
        float height = Math.min(desiredHeight, availableHeight);
        return new WarQueueLabelBounds(
                territoryX + (territoryWidth - width) / 2,
                territoryY + (territoryHeight - height) / 2,
                width,
                height);
    }

    static List<String> warQueueTooltipLines(TerritoryQueue queue, Instant now) {
        if (queue == null) return List.of();
        String defenses = WarTerritoryQueueHudRenderer.formatDefenses(
                queue.queuedDefenseRating(), queue.reportedDefenseRating());
        if (defenses.startsWith("(") && defenses.endsWith(")")) {
            defenses = defenses.substring(1, defenses.length() - 1);
        }
        String age = WarTerritoryQueueHudRenderer.formatAge(queue.queuedAt(), now);
        String participantNames = queue.participants().stream()
                .map(participant -> participant.minecraftUsername())
                .filter(name -> name != null && !name.isBlank())
                .collect(java.util.stream.Collectors.joining(", "));
        ArrayList<String> lines = new ArrayList<>();
        lines.add(queue.displayName());
        lines.add(queue.territory() + (defenses.isBlank() ? " · Defense unknown" : " · Defense " + defenses));
        lines.add((age.isBlank() ? "Queued time unknown" : "Queued " + age)
                + " · "
                + WarTerritoryQueueHudRenderer.formatCountdown(queue.expiresAt(), now)
                + " remaining");
        lines.add("Party " + WarTerritoryQueueHudRenderer.participantLabel(queue.participantCount())
                + (participantNames.isBlank() ? "" : " · " + participantNames));
        return List.copyOf(lines);
    }

    static String warQueueActionHint(TerritoryQueue queue, String playerUuid) {
        if (queue == null) return "";
        if (samePlayer(playerUuid, queue.queuedBy())) {
            return "You own this queue · owner remains joined";
        }
        if (queue.hasParticipant(playerUuid)) {
            return "Double-click to leave";
        }
        return queue.full() ? "Queue full" : "Double-click to join";
    }

    static void drawWarQueueTooltip(
            UiCanvas canvas, TerritoryQueue queue, Instant now, WarMapLayout layout, String playerUuid, float nvgMouseX, float nvgMouseY) {
        ArrayList<String> lines = new ArrayList<>(warQueueTooltipLines(queue, now));
        if (lines.isEmpty()) return;
        String actionHint = warQueueActionHint(queue, playerUuid);
        if (!actionHint.isBlank()) lines.add(actionHint);
        String font = SeqClient.getFontManager().getSelectedFont();
        float contentLeft = PADDING;
        float contentRight = layout.sidebarX() + layout.sidebarWidth();
        float maximumWidth = Math.max(1, contentRight - contentLeft);
        float measuredWidth = 0;
        for (String line : lines) {
            measuredWidth = Math.max(
                    measuredWidth,
                    UiRenderer.measureText(line, font, WAR_QUEUE_TOOLTIP_TEXT_SIZE).width());
        }
        float tooltipWidth = Math.min(maximumWidth, Math.max(170, measuredWidth + 16));
        ArrayList<WarQueueTooltipLine> wrappedLines = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            for (String line : wrapWarQueueText(
                    lines.get(index),
                    tooltipWidth - 16,
                    value -> UiRenderer.measureText(value, font, WAR_QUEUE_TOOLTIP_TEXT_SIZE).width())) {
                wrappedLines.add(new WarQueueTooltipLine(line, index));
            }
        }
        float tooltipHeight = 10 + wrappedLines.size() * WAR_QUEUE_TOOLTIP_LINE_HEIGHT;
        float tooltipX = tooltipCoordinate(nvgMouseX, tooltipWidth, contentLeft, contentRight);
        float tooltipY = tooltipCoordinate(
                nvgMouseY,
                tooltipHeight,
                layout.mapY() + 4,
                layout.mapY() + layout.mapHeight() - 4);
        canvas.fillRect(
                tooltipX,
                tooltipY,
                tooltipWidth,
                tooltipHeight,
                plannerBackground(color(BACKGROUND_POPUP)));
        canvas.strokeRect(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 1, color(CONTROL_BORDER));
        for (int index = 0; index < wrappedLines.size(); index++) {
            WarQueueTooltipLine line = wrappedLines.get(index);
            text(
                    canvas,
                    line.text(),
                    tooltipX + 8,
                    tooltipY + 8 + index * WAR_QUEUE_TOOLTIP_LINE_HEIGHT,
                    WAR_QUEUE_TOOLTIP_TEXT_SIZE,
                    color(line.detailIndex() == 0
                            ? TEXT_PRIMARY
                            : line.detailIndex() == 1 ? TEXT_SECONDARY : TEXT_MUTED),
                    false);
        }
    }

    static float tooltipCoordinate(float pointer, float size, float minimum, float maximum) {
        if (maximum <= minimum || size >= maximum - minimum) return minimum;
        float afterPointer = pointer + 12;
        if (afterPointer + size <= maximum) return Math.max(minimum, afterPointer);
        return Math.max(minimum, Math.min(pointer - size - 12, maximum - size));
    }

    static List<TerritoryQueue> warQueuesForMap(
            List<TerritoryQueue> queues, String localPlayerUuid, boolean onlyOwnedOrJoined) {
        return WarTerritoryQueueHudRenderer.displayedQueues(
                queues, localPlayerUuid, onlyOwnedOrJoined, Integer.MAX_VALUE);
    }

    static boolean isWarQueueDoubleClick(
            PendingWarQueueClick previous,
            long queueId,
            String territory,
            float mouseX,
            float mouseY,
            long clickedAtMillis) {
        if (previous == null || territory == null) return false;
        long elapsed = clickedAtMillis - previous.clickedAtMillis();
        return previous.queueId() == queueId
                && territory.equalsIgnoreCase(previous.territory())
                && elapsed >= 0
                && elapsed <= WAR_QUEUE_DOUBLE_CLICK_MILLIS
                && !warQueueClickMoved(previous, mouseX, mouseY);
    }

    static boolean warQueueClickMoved(PendingWarQueueClick click, float mouseX, float mouseY) {
        return click != null
                && Math.hypot(mouseX - click.mouseX(), mouseY - click.mouseY())
                        >= WAR_QUEUE_DOUBLE_CLICK_MOVE_TOLERANCE;
    }

    record WarQueueLabelBounds(float x, float y, float width, float height) {}

    record WarQueueMapMarker(TerritoryQueue queue, GuildTerritory territory) {}

    private record WarQueueTooltipLine(String text, int detailIndex) {}

    record PendingWarQueueClick(
            long queueId, String territory, float mouseX, float mouseY, long clickedAtMillis) {}
}
