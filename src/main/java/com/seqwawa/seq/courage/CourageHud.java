package com.seqwawa.seq.courage;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;

/**
 * Shared placement and text drawing for the two Courage readouts.
 *
 * <p>Both are drawn the way the game draws its own HUD: the Minecraft font and a
 * drop shadow, with nothing behind the text.
 */
public final class CourageHud {
    static final float MARGIN = 7f;
    static final int DEFAULT_TEXT_SIZE = 10;
    /** Vanilla stacks HUD lines one font pixel apart. */
    static final float LINE_SPACING = 1f / 9f;

    /** Vanilla offsets its shadow by a single pixel of a nine-pixel font. */
    private static final float SHADOW_OFFSET = 1f / 9f;

    private CourageHud() {}

    static float lineHeight(float textSize) {
        return textSize * (1f + LINE_SPACING);
    }

    static String font() {
        return SeqClient.getFontManager() == null ? "mc" : SeqClient.getFontManager().getSelectedFont();
    }

    static float textSize() {
        Setting.IntSetting setting = SeqClient.getCourageHudTextSizeSetting();
        return setting == null || setting.getValue() == null ? DEFAULT_TEXT_SIZE : setting.getValue();
    }

    static float measure(String text, float size) {
        return UiRenderer.measureText(text, font(), size).width();
    }

    /** Draws one run of HUD text with the game's own drop shadow behind it. */
    static void text(UiCanvas canvas, String value, float x, float y, float size, Color color) {
        float offset = size * SHADOW_OFFSET;
        canvas.drawText(value, x + offset, y + offset, style(size, shadowOf(color)));
        canvas.drawText(value, x, y, style(size, color));
    }

    private static UiCanvas.TextStyle style(float size, Color color) {
        return new UiCanvas.TextStyle(
                font(), size, color, UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
    }

    /** Vanilla shades a glyph's shadow to a quarter of its own colour rather than to black. */
    private static Color shadowOf(Color color) {
        return new Color(
                (color.getRed() & 0xFC) >> 2,
                (color.getGreen() & 0xFC) >> 2,
                (color.getBlue() & 0xFC) >> 2,
                color.getAlpha());
    }

    /** Maps a normalised position onto the screen, keeping the panel clear of the edges. */
    static Bounds place(
            float screenWidth, float screenHeight, float width, float height, float normalizedX, float normalizedY) {
        float travelX = Math.max(0f, screenWidth - MARGIN * 2f - width);
        float travelY = Math.max(0f, screenHeight - MARGIN * 2f - height);
        return new Bounds(
                MARGIN + Math.clamp(normalizedX, 0f, 1f) * travelX,
                MARGIN + Math.clamp(normalizedY, 0f, 1f) * travelY,
                width,
                height);
    }

    static Position positionForTopLeft(
            float screenWidth, float screenHeight, float width, float height, float left, float top) {
        float travelX = Math.max(0f, screenWidth - MARGIN * 2f - width);
        float travelY = Math.max(0f, screenHeight - MARGIN * 2f - height);
        return new Position(
                travelX == 0f ? 0f : Math.clamp((left - MARGIN) / travelX, 0f, 1f),
                travelY == 0f ? 0f : Math.clamp((top - MARGIN) / travelY, 0f, 1f));
    }

    static float position(Setting.FloatSetting setting, float fallback) {
        return setting == null || setting.getValue() == null
                ? fallback
                : Math.clamp(setting.getValue(), 0f, 1f);
    }

    static boolean enabled(Setting.BooleanSetting setting) {
        return setting == null || Boolean.TRUE.equals(setting.getValue());
    }

    public record Bounds(float x, float y, float width, float height) {
        public boolean contains(float pointX, float pointY, float padding) {
            return pointX >= x - padding
                    && pointX <= x + width + padding
                    && pointY >= y - padding
                    && pointY <= y + height + padding;
        }
    }

    public record Position(float x, float y) {}
}
