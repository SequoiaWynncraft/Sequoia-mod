package com.seqwawa.seq.utils.rendering;

/** Bounds relative to a left-aligned baseline at (0, 0), returned by the active UI text backend. */
public record UiTextMetrics(float minX, float minY, float maxX, float maxY) {
    public float width() {
        return maxX - minX;
    }

    public float height() {
        return maxY - minY;
    }
}
