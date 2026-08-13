package com.seqwawa.seq.wynnbuilder.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_DIVIDER;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_SECONDARY;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import com.seqwawa.seq.wynnbuilder.data.WynnItem;
import java.awt.Color;

/**
 * Drawing helpers shared by the WynnBuilder screens.
 *
 * <p>The screens in this mod each hand-roll their primitives; with three related screens here it is
 * worth sharing them so the builder, crafter and pickers stay visually identical.
 */
public final class WynnBuilderUi {

    public static final float PANEL_RADIUS = 7;
    public static final float OUTER_MARGIN = 14;
    public static final float HEADER_HEIGHT = 42;

    private WynnBuilderUi() {}

    public static String font() {
        return SeqClient.getFontManager().getSelectedFont();
    }

    public static void drawText(
            UiCanvas canvas,
            String text,
            float x,
            float y,
            float size,
            Color textColor,
            UiCanvas.HorizontalAlign horizontalAlign,
            UiCanvas.VerticalAlign verticalAlign) {
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(font(), size, textColor, horizontalAlign, verticalAlign));
    }

    public static void drawLeft(UiCanvas canvas, String text, float x, float y, float size, Color textColor) {
        drawText(canvas, text, x, y, size, textColor, UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE);
    }

    public static void drawCentered(UiCanvas canvas, String text, float x, float y, float size, Color textColor) {
        drawText(canvas, text, x, y, size, textColor, UiCanvas.HorizontalAlign.CENTER, UiCanvas.VerticalAlign.MIDDLE);
    }

    public static void drawRight(UiCanvas canvas, String text, float x, float y, float size, Color textColor) {
        drawText(canvas, text, x, y, size, textColor, UiCanvas.HorizontalAlign.RIGHT, UiCanvas.VerticalAlign.MIDDLE);
    }

    /** A standard button. Returns whether the pointer is over it, so callers can skip a second test. */
    public static boolean drawButton(
            UiCanvas canvas, float x, float y, float width, float height, String label, float mouseX, float mouseY) {
        return drawButton(canvas, x, y, width, height, label, mouseX, mouseY, true);
    }

    public static boolean drawButton(
            UiCanvas canvas,
            float x,
            float y,
            float width,
            float height,
            String label,
            float mouseX,
            float mouseY,
            boolean enabled) {
        boolean hovered = enabled && contains(mouseX, mouseY, x, y, width, height);
        canvas.fillRoundedRect(x, y, width, height, 5,
                hovered ? color(CONTROL_INPUT_HOVER, 245) : color(CONTROL_INPUT, enabled ? 235 : 150));
        canvas.strokeRect(x, y, width, height, 1, color(ACCENT_DIVIDER));
        drawCentered(canvas, label, x + width / 2f, y + height / 2f, 10,
                hovered ? color(ACCENT_PRIMARY_HOVER) : color(TEXT_SECONDARY, enabled ? 255 : 120));
        return hovered;
    }

    public static boolean contains(float pointX, float pointY, float x, float y, float width, float height) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static float measure(String text, float size) {
        return UiRenderer.measureText(text, font(), size).width();
    }

    /** Shortens text with an ellipsis so it fits the given width. */
    public static String ellipsize(String text, float maxWidth, float fontSize) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (measure(text, fontSize) <= maxWidth) {
            return text;
        }
        String suffix = "…";
        int low = 0;
        int high = text.length();
        while (low < high) {
            int middle = (low + high + 1) / 2;
            if (measure(text.substring(0, middle) + suffix, fontSize) <= maxWidth) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low <= 0 ? suffix : text.substring(0, low) + suffix;
    }

    /**
     * The colour Wynncraft uses for an item rarity.
     *
     * <p>These are game constants rather than theme choices: a Mythic must read as Mythic under every
     * Sequoia theme, so they are fixed rather than drawn from {@code UiColor}.
     */
    public static Color rarityColor(WynnItem.Tier tier) {
        if (tier == null) {
            return new Color(255, 255, 255);
        }
        return switch (tier) {
            case NORMAL -> new Color(255, 255, 255);
            case SET -> new Color(0, 255, 0);
            case UNIQUE -> new Color(255, 255, 85);
            case RARE -> new Color(255, 85, 255);
            case LEGENDARY -> new Color(85, 255, 255);
            case FABLED -> new Color(255, 85, 85);
            case MYTHIC -> new Color(170, 0, 170);
            case CRAFTED -> new Color(0, 170, 170);
        };
    }

    /** Colour for a stat value: beneficial values read green, drawbacks red. */
    public static Color statColor(int value, boolean inverted) {
        boolean beneficial = (value > 0) != inverted;
        if (value == 0) {
            return color(TEXT_SECONDARY);
        }
        return beneficial ? new Color(85, 255, 85) : new Color(255, 85, 85);
    }

    /** Formats a stat value with its sign and, where relevant, a percent sign. */
    public static String formatStat(int value, boolean percentage) {
        String sign = value > 0 ? "+" : "";
        return sign + value + (percentage ? "%" : "");
    }
}
