package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WorldMapUi.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapViewport;
import com.seqwawa.seq.utils.rendering.UiCanvas;

/** Territory bounds, hover hit testing and fitted territory labels. */
final class GatheringTerritoryLayer {
    static GuildTerritory renderTerritories(UiCanvas canvas, WorldMapFrame frame, GuildTerritoryIndex territoryIndex, GuildTerritory selectedTerritory, boolean showTerritories) {
        MapViewport viewport = frame.viewport();
        GuildTerritory hoveredTerritory = null;
        if (!showTerritories) {
            return null;
        }
        if (!frame.dragging() && viewport.isInsideScreen(frame.mouseX(), frame.mouseY())) {
            hoveredTerritory = territoryIndex.territoryAt(
                    viewport.screenToWorldX(frame.mouseX()),
                    viewport.screenToWorldZ(frame.mouseY()));
        }
        MapBounds visibleBounds = viewport.visibleBounds();
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (GuildTerritory territory : territoryIndex.territories()) {
            MapBounds bounds = territory.bounds();
            if (!intersects(visibleBounds, bounds)) {
                continue;
            }
            float x = viewport.worldToScreenX(bounds.minX());
            float y = viewport.worldToScreenZ(bounds.minZ());
            float width = viewport.worldToScreenX(bounds.maxX()) - x;
            float height = viewport.worldToScreenZ(bounds.maxZ()) - y;
            boolean selected = territory.equals(selectedTerritory);
            boolean hovered = territory.equals(hoveredTerritory);
            Color color = selected ? color(MAP_SELECTED_TERRITORY) : color(MAP_TERRITORY);
            if (selected || hovered) {
                canvas.fillRect(x, y, width, height, color);
            }
            canvas.strokeRect(x,
                    y,
                    width,
                    height,
                    selected || hovered ? 1.8f : 0.8f,
                    color);
        }
        canvas.resetScissor();
            return hoveredTerritory;
    }

    static void renderTerritoryNames(UiCanvas canvas, MapViewport viewport, GuildTerritoryIndex territoryIndex, GuildTerritory selectedTerritory, GuildTerritory hoveredTerritory, boolean showTerritories, boolean showTerritoryNames) {
        if (!showTerritories || !showTerritoryNames) {
            return;
        }
        MapBounds visibleBounds = viewport.visibleBounds();
        for (GuildTerritory territory : territoryIndex.territories()) {
            MapBounds bounds = territory.bounds();
            if (!intersects(visibleBounds, bounds)) {
                continue;
            }
            float x = viewport.worldToScreenX(bounds.minX());
            float y = viewport.worldToScreenZ(bounds.minZ());
            float width = viewport.worldToScreenX(bounds.maxX()) - x;
            float height = viewport.worldToScreenZ(bounds.maxZ()) - y;
            TerritoryLabelLayout label = fitTerritoryLabel(canvas, territory.name(), width - 8, height - 6);
            if (label == null) {
                continue;
            }

            float clipX = Math.max(x, viewport.screenX());
            float clipY = Math.max(y, viewport.screenY());
            float clipMaxX = Math.min(x + width, viewport.screenX() + viewport.screenWidth());
            float clipMaxY = Math.min(y + height, viewport.screenY() + viewport.screenHeight());
            if (clipMaxX <= clipX || clipMaxY <= clipY) {
                continue;
            }

            Color textColor = territory.equals(selectedTerritory)
                    ? color(MAP_SELECTED_TERRITORY)
                    : territory.equals(hoveredTerritory) ? color(MAP_TERRITORY_HOVER_TEXT) : color(MAP_TEXT);
            float totalHeight = label.lines().size() * label.lineHeight();
            float lineY = y + (height - totalHeight) / 2f + label.lineHeight() / 2f;
            canvas.save();
            canvas.scissor(clipX, clipY, clipMaxX - clipX, clipMaxY - clipY);
            for (String line : label.lines()) {
                drawText(
                        canvas,
                        x + width / 2f + 1,
                        lineY + 1,
                        label.fontSize(),
                        line,
                        color(BACKGROUND_MODAL_OVERLAY),
                        TextAlignment.CENTER);
                drawText(
                        canvas,
                        x + width / 2f,
                        lineY,
                        label.fontSize(),
                        line,
                        textColor,
                        TextAlignment.CENTER);
                lineY += label.lineHeight();
            }
            canvas.restore();
        }
    }

    static TerritoryLabelLayout fitTerritoryLabel(UiCanvas canvas, String name, float maxWidth, float maxHeight) {
        if (maxWidth < 4 || maxHeight < 6) {
            return null;
        }
        for (float fontSize = 11; fontSize >= 6; fontSize--) {
            List<String> lines = wrapTerritoryName(canvas, name, maxWidth, fontSize);
            float lineHeight = fontSize + 2;
            if (!lines.isEmpty() && lines.size() * lineHeight <= maxHeight) {
                return new TerritoryLabelLayout(lines, fontSize, lineHeight);
            }
        }
        return null;
    }

    static List<String> wrapTerritoryName(UiCanvas canvas, String name, float maxWidth, float fontSize) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (textWidth(candidate, fontSize) <= maxWidth) {
                currentLine.setLength(0);
                currentLine.append(candidate);
                continue;
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
            }
            if (textWidth(word, fontSize) <= maxWidth) {
                currentLine.append(word);
                continue;
            }
            List<String> pieces = splitTerritoryWord(canvas, word, maxWidth, fontSize);
            if (pieces.isEmpty()) {
                return List.of();
            }
            lines.addAll(pieces.subList(0, pieces.size() - 1));
            currentLine.append(pieces.getLast());
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return List.copyOf(lines);
    }

    static List<String> splitTerritoryWord(UiCanvas canvas, String word, float maxWidth, float fontSize) {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (int index = 0; index < word.length(); index++) {
            char character = word.charAt(index);
            String candidate = piece.toString() + character;
            if (textWidth(candidate, fontSize) <= maxWidth) {
                piece.append(character);
                continue;
            }
            if (piece.isEmpty()) {
                return List.of();
            }
            pieces.add(piece.toString());
            piece.setLength(0);
            if (textWidth(String.valueOf(character), fontSize) > maxWidth) {
                return List.of();
            }
            piece.append(character);
        }
        if (!piece.isEmpty()) {
            pieces.add(piece.toString());
        }
        return pieces;
    }

    static boolean intersects(MapBounds left, MapBounds right) {
        return left.maxX() >= right.minX()
                && left.minX() <= right.maxX()
                && left.maxZ() >= right.minZ()
                && left.minZ() <= right.maxZ();
    }

    private record TerritoryLabelLayout(List<String> lines, float fontSize, float lineHeight) {}
}
