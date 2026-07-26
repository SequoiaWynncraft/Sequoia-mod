package com.seqwawa.seq.utils.rendering;

import java.awt.Color;
import java.util.List;

/** Backend-neutral drawing surface used by Sequoia UI code. */
public interface UiCanvas {
    UiRenderMetrics metrics();

    void fillRect(float x, float y, float width, float height, Color color);

    void fillRoundedRect(float x, float y, float width, float height, float radius, Color color);

    void strokeRect(float x, float y, float width, float height, float thickness, Color color);

    void strokeLine(float x1, float y1, float x2, float y2, float thickness, Color color);

    void fillCircle(float centerX, float centerY, float radius, Color color);

    void strokeCircle(float centerX, float centerY, float radius, float thickness, Color color);

    void fillAndStrokePolygon(List<Point> points, Color fill, Color stroke, float strokeWidth, boolean closed);

    void fillHorizontalGradient(
            float x, float y, float width, float height, Color startColor, Color endColor);

    void fillVerticalGradient(
            float x, float y, float width, float height, Color startColor, Color endColor);

    void drawImage(UiImage image, float x, float y, float width, float height, float alpha);

    void fillCurrentPathWithImage(UiImage image, float x, float y, float width, float height, float alpha);

    void beginPath();

    void closePath();

    void moveTo(float x, float y);

    void lineTo(float x, float y);

    void circle(float centerX, float centerY, float radius);

    void arc(float centerX, float centerY, float radius, float startAngle, float endAngle, ArcDirection direction);

    void fillPath(Color color);

    void strokePath(float thickness, Color color);

    void drawText(String text, float x, float y, TextStyle style);

    void save();

    void restore();

    void translate(float x, float y);

    void rotateDegrees(float angleDegrees);

    void scale(float x, float y);

    void scissor(float x, float y, float width, float height);

    void resetScissor();

    enum HorizontalAlign {
        LEFT,
        CENTER,
        RIGHT
    }

    enum VerticalAlign {
        TOP,
        MIDDLE,
        BOTTOM,
        BASELINE
    }

    enum ArcDirection {
        CLOCKWISE,
        COUNTER_CLOCKWISE
    }

    record TextStyle(
            String font,
            float size,
            Color color,
            HorizontalAlign horizontalAlign,
            VerticalAlign verticalAlign) {
        public TextStyle {
            if (font == null || font.isBlank()) {
                throw new IllegalArgumentException("font must not be blank");
            }
            if (!Float.isFinite(size) || size <= 0f) {
                throw new IllegalArgumentException("size must be positive");
            }
            if (color == null || horizontalAlign == null || verticalAlign == null) {
                throw new IllegalArgumentException("text style fields must not be null");
            }
        }
    }

    record Point(float x, float y) {
    }
}
