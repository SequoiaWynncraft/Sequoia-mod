package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.List;
import java.util.function.IntPredicate;

/** Shared flat dropdown visuals and row geometry. Screens own their selection and search behavior. */
public final class DropdownMenu {
    public static final float CONTROL_HEIGHT = 18;
    public static final float ROW_HEIGHT = 22;
    public static final float FONT_SIZE = 10;
    private DropdownMenu() {}

    public static void trigger(UiCanvas canvas, float x, float y, float width, float height,
            String label, boolean open, boolean enabled, float mouseX, float mouseY) {
        canvas.fillRect(x, y, width, height, color(!enabled ? CONTROL_INPUT_SECONDARY
                : contains(mouseX, mouseY, x, y, width, height) ? CONTROL_INPUT_HOVER : CONTROL_INPUT));
        label(canvas, x + 8, y + height / 2, Math.max(0, width - 28), label,
                color(enabled ? TEXT_PRIMARY : TEXT_DISABLED));
        chevron(canvas, x + width - 10, y + height / 2, open, enabled);
    }

    public static void chevron(UiCanvas canvas, float x, float y, boolean open, boolean enabled) {
        Color tint = color(enabled ? TEXT_SECONDARY : TEXT_DISABLED);
        canvas.strokeLine(x - 3, y + (open ? 2 : -2), x, y + (open ? -2 : 2), 1.5f, tint);
        canvas.strokeLine(x, y + (open ? -2 : 2), x + 3, y + (open ? 2 : -2), 1.5f, tint);
    }

    public static void row(UiCanvas canvas, float x, float y, float width, float height,
            String label, boolean selected, boolean hovered) {
        row(canvas, x, y, width, height, label, null, selected, hovered, true);
    }

    public static void row(UiCanvas canvas, float x, float y, float width, float height,
            String label, String detail, boolean selected, boolean hovered, boolean enabled) {
        // A popup must cover underlying labels, regardless of the panel it opens over.
        canvas.fillRect(x, y, width, height, new Color(color(BACKGROUND_POPUP).getRGB()));
        if (selected || enabled && hovered) {
            canvas.fillRect(x, y, width, height, color(selected ? ACCENT_PRIMARY_DARK : CONTROL_INPUT_HOVER));
        }
        float detailWidth = detail == null ? 0 : Math.min(120, width * .42f);
        label(canvas, x + 8, y + height / 2, Math.max(0, width - 16 - detailWidth), label,
                color(enabled ? TEXT_PRIMARY : TEXT_DISABLED));
        if (detail != null) label(canvas, x + width - detailWidth, y + height / 2,
                Math.max(0, detailWidth - 8), detail, color(TEXT_MUTED));
    }

    public static void list(UiCanvas canvas, float x, float y, float width, float rowHeight,
            List<String> labels, IntPredicate selected, int scroll, int visibleRows, float mouseX, float mouseY) {
        list(canvas, x, y, width, rowHeight, labels, selected, scroll, visibleRows, mouseX, mouseY, -1);
    }

    public static void list(UiCanvas canvas, float x, float y, float width, float rowHeight,
            List<String> labels, IntPredicate selected, int scroll, int visibleRows, float mouseX, float mouseY,
            int focusedIndex) {
        int count = Math.min(visibleRows, labels.size());
        scroll = clampScroll(scroll, labels.size(), count);
        if (count == 0) {
            canvas.fillRect(x, y, width, rowHeight, new Color(color(BACKGROUND_POPUP).getRGB()));
            label(canvas, x + 8, y + rowHeight / 2, Math.max(0, width - 16), "No matches", color(TEXT_MUTED));
            return;
        }
        boolean scrolling = labels.size() > count;
        for (int i = 0; i < count; i++) {
            row(canvas, x, y + i * rowHeight, width, rowHeight, labels.get(scroll + i), selected.test(scroll + i),
                    scroll + i == focusedIndex || contains(mouseX, mouseY, x, y + i * rowHeight, width, rowHeight));
        }
        if (scrolling) {
            float height = count * rowHeight;
            float thumbHeight = Math.max(8, height * count / labels.size());
            float thumbY = y + (height - thumbHeight) * scroll / (labels.size() - count);
            canvas.fillRect(x + width - 3, y, 3, height, color(CONTROL_TRACK));
            canvas.fillRect(x + width - 3, thumbY, 3, thumbHeight, color(CONTROL_THUMB));
        }
    }

    public static void label(UiCanvas canvas, float x, float centerY, float width, String value, Color tint) {
        label(canvas, x, centerY, width, value, tint, FONT_SIZE);
    }

    public static void label(UiCanvas canvas, float x, float centerY, float width, String value, Color tint, float fontSize) {
        String font = SeqClient.getFontManager().getSelectedFont();
        String text = value == null ? "" : value;
        if (UiRenderer.measureText(text, font, fontSize).width() > width) {
            while (!text.isEmpty() && UiRenderer.measureText(text + "...", font, fontSize).width() > width) {
                text = text.substring(0, text.offsetByCodePoints(text.length(), -1));
            }
            text = UiRenderer.measureText("...", font, fontSize).width() <= width ? text + "..." : "";
        }
        canvas.drawText(text, x, centerY, new UiCanvas.TextStyle(font, fontSize, tint,
                UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE));
    }

    public static int optionAt(float mouseX, float mouseY, float x, float y, float width,
            float rowHeight, int visibleRows, int scroll) {
        if (!contains(mouseX, mouseY, x, y, width, rowHeight * visibleRows)) return -1;
        return scroll + (int) ((mouseY - y) / rowHeight);
    }

    public static boolean contains(float mouseX, float mouseY, float x, float y, float width, float height) {
        return width > 0 && height > 0 && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    public static int clampScroll(int scroll, int count, int visibleRows) {
        return Math.max(0, Math.min(scroll, Math.max(0, count - visibleRows)));
    }

    /** Fit an overlay above or below its trigger, with whole rows and a bounded scrolling window. */
    public static Popup fit(float x, float y, float width, float height, int count, float top, float bottom) {
        float below = Math.max(0, bottom - y - height);
        float above = Math.max(0, y - top);
        boolean upward = below < Math.min(count, 8) * ROW_HEIGHT && above > below;
        int rows = Math.min(Math.min(count, 8), (int) ((upward ? above : below) / ROW_HEIGHT));
        return new Popup(x, upward ? y - rows * ROW_HEIGHT : y + height, width, Math.max(0, rows));
    }

    public record Popup(float x, float y, float width, int rows) {
        public int optionAt(float mouseX, float mouseY, int scroll) {
            return DropdownMenu.optionAt(mouseX, mouseY, x, y, width, ROW_HEIGHT, rows, scroll);
        }
    }
}
