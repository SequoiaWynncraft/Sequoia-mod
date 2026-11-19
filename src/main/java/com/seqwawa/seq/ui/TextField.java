package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;

/**
 * A one-line text field drawn the way Party Finder draws its search: the input
 * colour, lighter and outlined while it has focus, a grey placeholder while it is
 * empty and unfocused, and a caret after the text while it has focus.
 *
 * <p>It keeps the text and the focus. Screens decide what the keys do, since their
 * fields differ there, and call {@link #type} and {@link #backspace} for the editing,
 * which changes the text only while the field has focus.
 */
final class TextField {
    /** Where the text starts, from the field's left edge. */
    static final float TEXT_INSET = 6;
    /** How many frames the caret shows, and then hides, as Party Finder's does. */
    static final int CARET_BLINK_FRAMES = 1000;

    private String text = "";
    private boolean focused;
    private int frames;

    String text() {
        return text;
    }

    boolean focused() {
        return focused;
    }

    /** Gives the field focus, with its caret showing. */
    void focus() {
        focused = true;
        frames = 0;
    }

    void blur() {
        focused = false;
    }

    /** Adds {@code typed} after the text, while the field has focus. Returns whether the text changed. */
    boolean type(String typed) {
        if (!focused || typed == null || typed.isEmpty()) return false;
        text += typed;
        return true;
    }

    /** Takes the last character off, while the field has focus. Returns whether there was one. */
    boolean backspace() {
        if (!focused || text.isEmpty()) return false;
        text = text.substring(0, text.length() - 1);
        return true;
    }

    /** Draws the field in {@code bounds}, the text at {@code fontSize}, the placeholder while there is nothing to show. */
    void draw(UiCanvas canvas, UiBounds bounds, String font, float fontSize, String placeholder) {
        bounds.fill(canvas, focused ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT));
        if (focused) bounds.stroke(canvas, 1, color(CONTROL_BORDER));
        canvas.save();
        canvas.scissor(bounds.x(), bounds.y(), bounds.width(), bounds.height());
        boolean showPlaceholder = text.isEmpty() && !focused;
        canvas.drawText(showPlaceholder ? placeholder : text, bounds.x() + TEXT_INSET, bounds.centerY(),
                new UiCanvas.TextStyle(font, fontSize, color(showPlaceholder ? TEXT_DISABLED : TEXT_PRIMARY),
                        UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.MIDDLE));
        canvas.restore();

        frames++;
        if (focused && caretShown(frames)) {
            float textWidth = text.isEmpty() ? 0 : UiRenderer.measureText(text, font, fontSize).width();
            canvas.fillRect(caretX(bounds, textWidth), bounds.y() + 3, 1, bounds.height() - 6, color(TEXT_PRIMARY));
        }
    }

    /** Whether the caret shows on the {@code frames}th frame since the field took focus. */
    static boolean caretShown(int frames) {
        return (frames / CARET_BLINK_FRAMES) % 2 == 0;
    }

    /** The caret's left edge: a pixel after the text, kept inside the field. */
    static float caretX(UiBounds bounds, float textWidth) {
        return Math.min(bounds.x() + TEXT_INSET + textWidth + 1, bounds.right() - 1);
    }
}
