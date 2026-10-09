package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/** Stateless map drawing primitives shared by screen and feature panels. */
final class WorldMapUi {
    static final float SIDEBAR_WIDTH = 230;
    static final float PADDING = 12;
    private WorldMapUi() {}
    static boolean drawMapAsset(UiCanvas canvas, String assetName, float x, float y, float size) {
        AssetManager.Asset asset = AssetManager.getAssetsMap().get(assetName);
        if (asset == null || asset.getImage() == null) {
            return false;
        }
        float scale = size / Math.max(asset.getWidth(), asset.getHeight());
        float width = asset.getWidth() * scale;
        float height = asset.getHeight() * scale;
        canvas.drawImage(asset.getImage(), x - width / 2f, y - height / 2f, width, height, 1f);
        return true;
    }

    static void drawTotemMarker(
            UiCanvas canvas, float x, float y, float size, Color border, boolean highlighted) {
        float halfSize = size / 2f + 1;
        drawSquareMarker(canvas, x, y, halfSize, color(BACKGROUND_MODAL_OVERLAY));
        drawSquareMarkerOutline(canvas, x, y, halfSize, highlighted ? 1.5f : 1, border);
        if (!drawMapAsset(canvas, "shaman", x, y, size)) {
            drawSquareMarker(canvas, x, y, size / 4f, color(MAP_TOTEM));
        }
    }

    static void drawSquareMarker(UiCanvas canvas, float x, float y, float halfSize, Color color) {
        canvas.fillRect(x - halfSize, y - halfSize, halfSize * 2, halfSize * 2, color);
    }

    static void drawSquareMarkerOutline(
            UiCanvas canvas, float x, float y, float halfSize, float width, Color color) {
        canvas.strokeRect(x - halfSize, y - halfSize, halfSize * 2, halfSize * 2, width, color);
    }

    static void drawCircle(UiCanvas canvas, float x, float y, float radius, Color color) {
        canvas.fillCircle(x, y, radius, color);
    }

    static void drawCircleOutline(UiCanvas canvas, float x, float y, float radius, float width, Color color) {
        canvas.strokeCircle(x, y, radius, width, color);
    }

    static void drawText(
            UiCanvas canvas, float x, float y, float size, String text, Color color, TextAlignment align) {
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(),
                size,
                color,
                align.horizontalAlign(),
                UiCanvas.VerticalAlign.MIDDLE));
    }

    static void drawSidebarText(UiCanvas canvas, float x, float y, float size, String text, Color color) {
        drawFittedText(canvas, x, y, size, text, color, SIDEBAR_WIDTH - x - PADDING, TextAlignment.LEFT);
    }

    static void drawFittedText(
            UiCanvas canvas,
            float x,
            float y,
            float size,
            String text,
            Color color,
            float maxWidth,
            TextAlignment align) {
        String fitted = fitText(canvas, text, maxWidth, size);
        drawText(canvas, x, y, size, fitted, color, align);
    }

    static float drawWrappedText(
            UiCanvas canvas,
            float x,
            float y,
            float size,
            String text,
            Color color,
            float maxWidth,
            float lineHeight) {
        List<String> lines = wrapText(text, maxWidth, size);
        for (int index = 0; index < lines.size(); index++) {
            drawText(canvas, x, y + index * lineHeight, size, lines.get(index), color, TextAlignment.LEFT);
        }
        return y + lines.size() * lineHeight;
    }

    static int wrappedLineCount(String text, float maxWidth, float size) {
        return wrapText(text, maxWidth, size).size();
    }

    static List<String> wrapText(String text, float maxWidth, float size) {
        if (text == null || text.isBlank() || maxWidth <= 0) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.strip().split("\\R")) {
            String currentLine = "";
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
                if (currentLine.isEmpty() || textWidth(candidate, size) <= maxWidth) {
                    currentLine = candidate;
                } else {
                    lines.add(currentLine);
                    currentLine = word;
                }
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine);
            }
        }
        return List.copyOf(lines);
    }

    static String fitText(UiCanvas canvas, String text, float maxWidth, float size) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (textWidth(text, size) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        if (textWidth(ellipsis, size) > maxWidth) {
            return "";
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = text.substring(0, mid).stripTrailing() + ellipsis;
            if (textWidth(candidate, size) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low).stripTrailing() + ellipsis;
    }

    static float textWidth(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return UiRenderer.measureText(text, SeqClient.getFontManager().getSelectedFont(), size).width();
    }

    enum TextAlignment {
        LEFT(UiCanvas.HorizontalAlign.LEFT),
        CENTER(UiCanvas.HorizontalAlign.CENTER),
        RIGHT(UiCanvas.HorizontalAlign.RIGHT);

        private final UiCanvas.HorizontalAlign horizontalAlign;

        TextAlignment(UiCanvas.HorizontalAlign horizontalAlign) {
            this.horizontalAlign = horizontalAlign;
        }

        UiCanvas.HorizontalAlign horizontalAlign() {
            return horizontalAlign;
        }
    }
    static void drawInsightsSectionTitle(UiCanvas canvas, float x, float y, String label) {
        drawText(canvas, x, y, 12, label, color(MAP_SUBTEXT), TextAlignment.LEFT);
    }

    static void drawInsightRow(UiCanvas canvas, float x, float y, float width, String label, String value) {
        drawFittedText(canvas, x, y, 10, label, color(MAP_SUBTEXT), width * 0.42f, TextAlignment.LEFT);
        drawFittedText(canvas, x + width, y, 10, value, color(MAP_TEXT), width * 0.58f, TextAlignment.RIGHT);
    }
    static String displayEnumValue(String value) {
        String lower = value.toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return lower.isEmpty() ? "" : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
