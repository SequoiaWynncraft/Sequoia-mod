package com.seqwawa.seq.ui;

import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;

/**
 * A rectangle in UI pixels, shared by the code that draws a control and the code
 * that tests the pointer against it, so the two can't drift apart.
 *
 * <p>Every Sequoia screen hit-tests with this one rule: a point on the edge is
 * inside, and an empty rectangle has nothing inside it.
 */
public record UiBounds(float x, float y, float width, float height) {

    public float right() {
        return x + width;
    }

    public float bottom() {
        return y + height;
    }

    public float centerX() {
        return x + width / 2f;
    }

    public float centerY() {
        return y + height / 2f;
    }

    public boolean contains(float pointX, float pointY) {
        return width > 0 && height > 0 && pointX >= x && pointX <= x + width
                && pointY >= y && pointY <= y + height;
    }

    public boolean contains(UiPointer pointer) {
        return contains(pointer.x(), pointer.y());
    }

    /**
     * The rectangle moved in by {@code dx} at the left and right and by {@code dy}
     * at the top and bottom. A negative inset grows it, as a hit area wider than
     * what is drawn.
     */
    public UiBounds inset(float dx, float dy) {
        return new UiBounds(x + dx, y + dy, width - 2 * dx, height - 2 * dy);
    }

    void fill(UiCanvas canvas, Color color) { canvas.fillRect(x, y, width, height, color); }
    void stroke(UiCanvas canvas, float thickness, Color color) { canvas.strokeRect(x, y, width, height, thickness, color); }
}
