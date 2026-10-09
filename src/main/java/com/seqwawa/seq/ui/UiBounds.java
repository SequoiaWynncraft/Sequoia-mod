package com.seqwawa.seq.ui;

import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;

/** Screen-space rectangle shared by a component's renderer and input handlers. */
record UiBounds(float x, float y, float width, float height) {
    boolean contains(float mouseX, float mouseY) {
        return width > 0 && height > 0 && mouseX >= x && mouseX <= x + width
                && mouseY >= y && mouseY <= y + height;
    }

    void fill(UiCanvas canvas, Color color) { canvas.fillRect(x, y, width, height, color); }
    void stroke(UiCanvas canvas, float thickness, Color color) { canvas.strokeRect(x, y, width, height, thickness, color); }
}
