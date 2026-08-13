package com.seqwawa.seq.wynnbuilder.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_DIVIDER;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY_DARK;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_THUMB;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_TRACK;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_MUTED;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_SECONDARY;

import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.util.List;

/**
 * Draws a scrollable list of {@link StatLine}s, shared by the builder and crafter panels so both
 * read identically.
 *
 * <p>Rows have individual heights, so scrolling and hit testing walk the list rather than
 * multiplying by a constant.
 */
public final class StatLineRenderer {

    private static final float LEFT_PADDING = 12;
    private static final float DETAIL_INDENT = 20;
    private static final float RIGHT_PADDING = 12;
    private static final float VALUE_GAP = 12;

    private StatLineRenderer() {}

    /** Receives the hit box of a clickable row, in screen coordinates. */
    public interface ClickRegistrar {
        void register(float x, float y, float width, float height, Runnable onClick);

        /**
         * Registers a slider track, whose value depends on where along it the pointer lands.
         *
         * <p>Separate from a plain click because the track has to convert a position into a value.
         */
        default void registerTrack(float x, float y, float width, float height, StatLine.Slider slider) {
        }
    }

    private static final float CHIP_GAP = 4;
    private static final float CHIP_PADDING = 9;

    /**
     * Measures every row against the available width.
     *
     * <p>Chip rows wrap, so their height is not known until the width is. Measuring once and reusing
     * the result keeps drawing and hit testing in step.
     */
    public static float[] layout(List<StatLine> lines, float width) {
        float[] heights = new float[lines.size()];
        for (int i = 0; i < lines.size(); i++) {
            StatLine line = lines.get(i);
            heights[i] = line.kind() == StatLine.Kind.CHIPS
                    ? chipRows(line, width) * StatLine.CHIP_ROW_HEIGHT + 6
                    : line.height();
        }
        return heights;
    }

    /** How many wrapped rows a set of chips needs at this width. */
    private static int chipRows(StatLine line, float width) {
        float available = width - LEFT_PADDING - RIGHT_PADDING;
        float cursor = 0;
        int rows = 1;
        for (StatLine.Chip chip : line.chips()) {
            float chipWidth = WynnBuilderUi.measure(chip.label(), 10) + CHIP_PADDING * 2;
            if (cursor > 0 && cursor + chipWidth > available) {
                rows++;
                cursor = 0;
            }
            cursor += chipWidth + CHIP_GAP;
        }
        return rows;
    }

    /** Total height the lines occupy, used to work out the scroll range. */
    public static float contentHeight(List<StatLine> lines, float width) {
        float total = 0;
        for (float height : layout(lines, width)) {
            total += height;
        }
        return total;
    }

    /**
     * Draws the list clipped to the given area.
     *
     * @param scroll how far the content is scrolled, already clamped by the caller
     * @param registrar notified for each clickable row that is actually on screen, so a row scrolled
     *     out of view cannot be clicked
     */
    public static void draw(
            UiCanvas canvas,
            List<StatLine> lines,
            float x,
            float y,
            float width,
            float viewHeight,
            float scroll,
            float mouseX,
            float mouseY,
            ClickRegistrar registrar) {

        float[] heights = layout(lines, width);
        canvas.scissor(x, y, width, viewHeight);
        try {
            float cursorY = y - scroll;
            for (int i = 0; i < lines.size(); i++) {
                float height = heights[i];
                boolean visible = cursorY + height >= y && cursorY <= y + viewHeight;
                if (visible) {
                    drawLine(canvas, lines.get(i), x, cursorY, width, height, mouseX, mouseY, registrar);
                }
                cursorY += height;
            }
        } finally {
            canvas.resetScissor();
        }
    }

    private static void drawLine(
            UiCanvas canvas,
            StatLine line,
            float x,
            float y,
            float width,
            float height,
            float mouseX,
            float mouseY,
            ClickRegistrar registrar) {

        switch (line.kind()) {
            case SPACER -> {
                // Nothing to draw; the gap is the point.
            }
            case DIVIDER -> canvas.fillRect(
                    x + LEFT_PADDING, y + height / 2f, width - LEFT_PADDING - RIGHT_PADDING, 1,
                    color(ACCENT_DIVIDER, 130));
            case HEADING -> {
                // The title sits low in its row so the space above separates it from what precedes.
                float baseline = y + height - 9;
                WynnBuilderUi.drawLeft(canvas, line.label(), x + LEFT_PADDING, baseline, 11, color(ACCENT_PRIMARY));
                canvas.fillRect(x + LEFT_PADDING, y + height - 2,
                        width - LEFT_PADDING - RIGHT_PADDING, 1, color(ACCENT_DIVIDER, 110));
            }
            case SUBHEADING -> WynnBuilderUi.drawLeft(
                    canvas, line.label(), x + DETAIL_INDENT, y + height - 7, 9, color(TEXT_MUTED));
            case EXPANDER -> {
                boolean hovered = WynnBuilderUi.contains(mouseX, mouseY, x + 6, y, width - 12, height);
                if (hovered) {
                    canvas.fillRoundedRect(x + 6, y + 1, width - 12, height - 2, 4, color(CONTROL_INPUT_HOVER, 150));
                }
                WynnBuilderUi.drawLeft(canvas,
                        WynnBuilderUi.ellipsize(line.label(), width - LEFT_PADDING - RIGHT_PADDING - 6, 11),
                        x + LEFT_PADDING, y + height / 2f, 11,
                        line.color() == null ? color(ACCENT_PRIMARY) : line.color());
                registrar.register(x + 6, y, width - 12, height, line.onClick());
            }
            case EXPANDER_VALUE -> {
                boolean hovered = WynnBuilderUi.contains(mouseX, mouseY, x + 6, y, width - 12, height);
                if (hovered) {
                    canvas.fillRoundedRect(x + 6, y, width - 12, height, 3, color(CONTROL_INPUT_HOVER, 140));
                }
                float middle = y + height / 2f;
                float valueWidth = line.value().isEmpty() ? 0 : WynnBuilderUi.measure(line.value(), 10) + VALUE_GAP;
                WynnBuilderUi.drawLeft(canvas,
                        WynnBuilderUi.ellipsize(line.label(), width - DETAIL_INDENT - RIGHT_PADDING - valueWidth, 10),
                        x + DETAIL_INDENT - 6, middle, 10, color(TEXT_SECONDARY));
                if (!line.value().isEmpty()) {
                    WynnBuilderUi.drawRight(canvas, line.value(), x + width - RIGHT_PADDING, middle, 10,
                            line.color() == null ? color(TEXT_SECONDARY) : line.color());
                }
                registrar.register(x + 6, y, width - 12, height, line.onClick());
            }
            case BUTTON -> {
                float buttonWidth = Math.min(130, width - DETAIL_INDENT - RIGHT_PADDING);
                WynnBuilderUi.drawButton(canvas, x + DETAIL_INDENT, y + 2, buttonWidth, height - 5,
                        line.label(), mouseX, mouseY);
                registrar.register(x + DETAIL_INDENT, y + 2, buttonWidth, height - 5, line.onClick());
            }
            case CHIPS -> {
                float available = width - LEFT_PADDING - RIGHT_PADDING;
                float chipX = x + LEFT_PADDING;
                float chipY = y + 3;
                float chipHeight = StatLine.CHIP_ROW_HEIGHT - 4;
                for (StatLine.Chip chip : line.chips()) {
                    float chipWidth = WynnBuilderUi.measure(chip.label(), 10) + CHIP_PADDING * 2;
                    if (chipX > x + LEFT_PADDING && chipX + chipWidth > x + LEFT_PADDING + available) {
                        chipX = x + LEFT_PADDING;
                        chipY += StatLine.CHIP_ROW_HEIGHT;
                    }
                    boolean hovered = WynnBuilderUi.contains(mouseX, mouseY, chipX, chipY, chipWidth, chipHeight);
                    canvas.fillRoundedRect(chipX, chipY, chipWidth, chipHeight, 4,
                            chip.active() ? color(ACCENT_PRIMARY_DARK, 255)
                                    : hovered ? color(CONTROL_INPUT_HOVER, 235) : color(CONTROL_INPUT, 200));
                    if (chip.active()) {
                        canvas.strokeRect(chipX, chipY, chipWidth, chipHeight, 1, color(ACCENT_PRIMARY));
                    }
                    WynnBuilderUi.drawCentered(canvas, chip.label(), chipX + chipWidth / 2f,
                            chipY + chipHeight / 2f, 10,
                            chip.active() ? color(TEXT_PRIMARY) : color(TEXT_SECONDARY));
                    registrar.register(chipX, chipY, chipWidth, chipHeight, chip.onClick());
                    chipX += chipWidth + CHIP_GAP;
                }
            }
            case SLIDER -> {
                StatLine.Slider slider = line.slider();
                WynnBuilderUi.drawLeft(canvas, line.label(), x + DETAIL_INDENT, y + 10, 10,
                        color(TEXT_SECONDARY));
                WynnBuilderUi.drawRight(canvas, slider.value() + " / " + slider.maximum(),
                        x + width - RIGHT_PADDING - 46, y + 10, 10, color(TEXT_PRIMARY));

                float trackX = x + DETAIL_INDENT;
                float trackWidth = Math.max(20, width - DETAIL_INDENT - RIGHT_PADDING - 50);
                float trackY = y + 23;
                canvas.fillRect(trackX, trackY, trackWidth, 3, color(CONTROL_TRACK, 200));
                float fraction = slider.maximum() <= 0
                        ? 0
                        : Math.min(1f, (float) slider.value() / slider.maximum());
                canvas.fillRect(trackX, trackY, trackWidth * fraction, 3, color(ACCENT_PRIMARY));
                canvas.fillCircle(trackX + trackWidth * fraction, trackY + 1.5f, 5, color(CONTROL_THUMB));
                // The whole lower half of the row grabs the slider: a three pixel track is far too
                // fine a target, which is what made it feel unusable.
                registrar.registerTrack(trackX, y + 14, trackWidth, height - 14, slider);

                // Buttons alongside the track, for nudging a value by one without aiming.
                float buttonY = y + 15;
                WynnBuilderUi.drawButton(canvas, x + width - RIGHT_PADDING - 42, buttonY, 18, 15, "-",
                        mouseX, mouseY);
                registrar.register(x + width - RIGHT_PADDING - 42, buttonY, 18, 15,
                        () -> slider.onChange().accept(Math.max(0, slider.value() - 1)));
                WynnBuilderUi.drawButton(canvas, x + width - RIGHT_PADDING - 20, buttonY, 18, 15, "+",
                        mouseX, mouseY);
                registrar.register(x + width - RIGHT_PADDING - 20, buttonY, 18, 15,
                        () -> slider.onChange().accept(Math.min(slider.maximum(), slider.value() + 1)));
            }
            case TEXT -> {
                float middle = y + height / 2f;
                // The value is measured first so the label is only truncated by what it actually needs.
                float valueWidth = line.value().isEmpty() ? 0 : WynnBuilderUi.measure(line.value(), 10) + VALUE_GAP;
                float labelWidth = width - DETAIL_INDENT - RIGHT_PADDING - valueWidth;
                WynnBuilderUi.drawLeft(canvas, WynnBuilderUi.ellipsize(line.label(), labelWidth, 10),
                        x + DETAIL_INDENT, middle, 10, color(TEXT_SECONDARY));
                if (!line.value().isEmpty()) {
                    WynnBuilderUi.drawRight(canvas, line.value(), x + width - RIGHT_PADDING, middle, 10,
                            line.color() == null ? color(TEXT_SECONDARY) : line.color());
                }
            }
        }
    }

    /** Draws the scrollbar for a list, when it does not fit. */
    public static void drawScrollbar(
            UiCanvas canvas, float x, float y, float width, float viewHeight, float scroll, float contentHeight) {
        if (contentHeight <= viewHeight) {
            return;
        }
        float trackX = x + width - 5;
        canvas.fillRect(trackX, y, 2, viewHeight, color(CONTROL_TRACK, 150));
        float thumbHeight = Math.max(20, viewHeight * (viewHeight / contentHeight));
        float maxScroll = contentHeight - viewHeight;
        float thumbY = y + (viewHeight - thumbHeight) * (maxScroll <= 0 ? 0 : scroll / maxScroll);
        canvas.fillRect(trackX, thumbY, 2, thumbHeight, color(CONTROL_THUMB, 220));
    }
}
