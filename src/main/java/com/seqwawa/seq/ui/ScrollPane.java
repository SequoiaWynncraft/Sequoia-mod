package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_THUMB;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_TRACK;

import com.seqwawa.seq.utils.rendering.UiCanvas;

/**
 * How far a list or panel is scrolled, and its scrollbar.
 *
 * <p>The thumb is placed by one rule, {@link #bar}, used both to draw it and to
 * turn a drag into a scroll, so the thumb moves exactly with the pointer that
 * holds it. Screens say where the track goes; the pane keeps the offset within
 * what can be scrolled.
 */
final class ScrollPane {
    /** The shortest a thumb gets, so a long list's thumb can still be grabbed. */
    static final float MIN_THUMB = 20;

    private float offset;
    private float max;
    private boolean dragging;
    private float dragStartY;
    private float dragStartOffset;

    /** A scrollbar: its track, and the thumb's top and height within it. */
    record Bar(UiBounds track, float thumbY, float thumbHeight) {
        UiBounds thumb() {
            return new UiBounds(track.x(), thumbY, track.width(), thumbHeight);
        }
    }

    /** How far the content is scrolled, in UI pixels from its top. */
    float offset() {
        return offset;
    }

    /** How far the content can scroll: how much of it is past the viewport. */
    float max() {
        return max;
    }

    boolean canScroll() {
        return max > 0;
    }

    boolean dragging() {
        return dragging;
    }

    /**
     * Sets how far the content can scroll, once a frame has measured it, and
     * keeps the offset within it.
     */
    void setMax(float max) {
        this.max = Math.max(0, max);
        offset = clamp(offset);
    }

    void scrollBy(float amount) {
        offset = clamp(offset + amount);
    }

    void scrollTo(float offset) {
        this.offset = clamp(offset);
    }

    /** Back to the top, as when what the pane shows is replaced. */
    void reset() {
        offset = 0;
    }

    /**
     * Where the thumb is drawn on {@code track}: as long as the share of the content
     * the viewport shows, but at least {@link #MIN_THUMB} and at most the track, and
     * as far down as the content is scrolled. Null while nothing can scroll.
     */
    static Bar bar(UiBounds track, float offset, float max) {
        if (max <= 0 || track.height() <= 0) return null;
        float height = track.height();
        float thumb = Math.min(height, Math.max(MIN_THUMB, height * height / (height + max)));
        return new Bar(track, track.y() + (height - thumb) * offset / max, thumb);
    }

    Bar bar(UiBounds track) {
        return bar(track, offset, max);
    }

    /** Draws the track and the thumb, in the theme's scrollbar colours, while there is anything to scroll. */
    void draw(UiCanvas canvas, UiBounds track) {
        Bar bar = bar(track);
        if (bar == null) return;
        track.fill(canvas, color(CONTROL_TRACK));
        bar.thumb().fill(canvas, color(CONTROL_THUMB));
    }

    /**
     * Starts dragging the thumb when {@code pointerY}'s press lands in {@code grab},
     * the bar's hit area, while there is anything to scroll. Returns whether it did.
     */
    boolean startDrag(UiPointer pointer, UiBounds grab) {
        if (!canScroll() || !grab.contains(pointer)) return false;
        dragging = true;
        dragStartY = pointer.y();
        dragStartOffset = offset;
        return true;
    }

    /**
     * Scrolls as far as the pointer has moved the thumb along {@code track}. Returns
     * whether a drag is under way, so the screen does nothing else with the motion.
     */
    boolean drag(UiPointer pointer, UiBounds track) {
        if (!dragging) return false;
        Bar bar = bar(track);
        float room = bar == null ? 0 : track.height() - bar.thumbHeight();
        // A thumb that fills its track has nowhere to go.
        if (room > 0) offset = clamp(dragStartOffset + (pointer.y() - dragStartY) / room * max);
        return true;
    }

    /** Lets go of the thumb. Returns whether it was held. */
    boolean release() {
        boolean was = dragging;
        dragging = false;
        return was;
    }

    private float clamp(float value) {
        return Math.max(0, Math.min(max, value));
    }
}
