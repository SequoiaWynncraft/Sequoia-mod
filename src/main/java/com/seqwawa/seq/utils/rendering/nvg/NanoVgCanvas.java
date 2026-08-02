package com.seqwawa.seq.utils.rendering.nvg;

import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_BASELINE;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_BOTTOM;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_CENTER;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_LEFT;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_MIDDLE;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_RIGHT;
import static org.lwjgl.nanovg.NanoVG.NVG_ALIGN_TOP;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgClosePath;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillPaint;
import static org.lwjgl.nanovg.NanoVG.nvgFontFace;
import static org.lwjgl.nanovg.NanoVG.nvgFontSize;
import static org.lwjgl.nanovg.NanoVG.nvgImagePattern;
import static org.lwjgl.nanovg.NanoVG.nvgRect;
import static org.lwjgl.nanovg.NanoVG.nvgText;
import static org.lwjgl.nanovg.NanoVG.nvgTextAlign;
import static org.lwjgl.nanovg.NanoVG.nvgSave;
import static org.lwjgl.nanovg.NanoVG.nvgRestore;
import static org.lwjgl.nanovg.NanoVG.nvgTranslate;
import static org.lwjgl.nanovg.NanoVG.nvgRotate;
import static org.lwjgl.nanovg.NanoVG.nvgScale;
import static org.lwjgl.nanovg.NanoVG.nvgScissor;
import static org.lwjgl.nanovg.NanoVG.nvgResetScissor;

import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderMetrics;
import java.awt.Color;
import java.util.List;
import org.lwjgl.nanovg.NVGColor;
import org.lwjgl.nanovg.NVGPaint;

final class NanoVgCanvas implements UiCanvas {
    private final long context;
    private final UiRenderMetrics metrics;

    NanoVgCanvas(long context, UiRenderMetrics metrics) {
        this.context = context;
        this.metrics = metrics;
    }

    long context() {
        return context;
    }

    @Override
    public UiRenderMetrics metrics() {
        return metrics;
    }

    @Override
    public void fillRect(float x, float y, float width, float height, Color color) {
        NVGWrapper.drawRect(context, x, y, width, height, color);
    }

    @Override
    public void fillRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        NVGWrapper.drawRoundedRect(context, x, y, width, height, radius, color);
    }

    @Override
    public void strokeRect(float x, float y, float width, float height, float thickness, Color color) {
        NVGWrapper.drawRectOutline(context, x, y, width, height, thickness, color);
    }

    @Override
    public void strokeLine(float x1, float y1, float x2, float y2, float thickness, Color color) {
        nvgBeginPath(context);
        org.lwjgl.nanovg.NanoVG.nvgMoveTo(context, x1, y1);
        org.lwjgl.nanovg.NanoVG.nvgLineTo(context, x2, y2);
        org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(context, thickness);
        NVGColor strokeColor = NVGWrapper.nvgColor(color);
        org.lwjgl.nanovg.NanoVG.nvgStrokeColor(context, strokeColor);
        org.lwjgl.nanovg.NanoVG.nvgStroke(context);
        nvgClosePath(context);
        strokeColor.free();
    }

    @Override
    public void fillCircle(float centerX, float centerY, float radius, Color color) {
        nvgBeginPath(context);
        org.lwjgl.nanovg.NanoVG.nvgCircle(context, centerX, centerY, radius);
        NVGColor fillColor = NVGWrapper.nvgColor(color);
        nvgFillColor(context, fillColor);
        nvgFill(context);
        nvgClosePath(context);
        fillColor.free();
    }

    @Override
    public void fillCircles(List<Circle> circles, Color color) {
        if (circles.isEmpty()) {
            return;
        }
        nvgBeginPath(context);
        for (Circle circle : circles) {
            org.lwjgl.nanovg.NanoVG.nvgCircle(context, circle.centerX(), circle.centerY(), circle.radius());
            nvgClosePath(context);
        }
        NVGColor fillColor = NVGWrapper.nvgColor(color);
        nvgFillColor(context, fillColor);
        nvgFill(context);
        fillColor.free();
    }

    @Override
    public void strokeCircle(float centerX, float centerY, float radius, float thickness, Color color) {
        nvgBeginPath(context);
        org.lwjgl.nanovg.NanoVG.nvgCircle(context, centerX, centerY, radius);
        NVGColor strokeColor = NVGWrapper.nvgColor(color);
        org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(context, thickness);
        org.lwjgl.nanovg.NanoVG.nvgStrokeColor(context, strokeColor);
        org.lwjgl.nanovg.NanoVG.nvgStroke(context);
        nvgClosePath(context);
        strokeColor.free();
    }

    @Override
    public void fillAndStrokePolygon(
            java.util.List<Point> points, Color fill, Color stroke, float strokeWidth, boolean closed) {
        if (points.isEmpty()) {
            return;
        }
        nvgBeginPath(context);
        Point first = points.getFirst();
        org.lwjgl.nanovg.NanoVG.nvgMoveTo(context, first.x(), first.y());
        for (int index = 1; index < points.size(); index++) {
            Point point = points.get(index);
            org.lwjgl.nanovg.NanoVG.nvgLineTo(context, point.x(), point.y());
        }
        if (closed) {
            nvgClosePath(context);
        }
        if (fill != null) {
            NVGColor fillColor = NVGWrapper.nvgColor(fill);
            nvgFillColor(context, fillColor);
            nvgFill(context);
            fillColor.free();
        }
        if (stroke != null && strokeWidth > 0f) {
            NVGColor strokeColor = NVGWrapper.nvgColor(stroke);
            org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(context, strokeWidth);
            org.lwjgl.nanovg.NanoVG.nvgStrokeColor(context, strokeColor);
            org.lwjgl.nanovg.NanoVG.nvgStroke(context);
            strokeColor.free();
        }
    }

    @Override
    public void fillAndStrokePolygons(
            List<Polygon> polygons, Color fill, Color stroke, float strokeWidth) {
        if (polygons.isEmpty()) {
            return;
        }
        nvgBeginPath(context);
        for (Polygon polygon : polygons) {
            if (polygon.points().isEmpty()) {
                continue;
            }
            Point first = polygon.points().getFirst();
            org.lwjgl.nanovg.NanoVG.nvgMoveTo(
                    context, first.x() + polygon.offsetX(), first.y() + polygon.offsetY());
            for (int index = 1; index < polygon.points().size(); index++) {
                Point point = polygon.points().get(index);
                org.lwjgl.nanovg.NanoVG.nvgLineTo(
                        context, point.x() + polygon.offsetX(), point.y() + polygon.offsetY());
            }
            if (polygon.closed()) {
                nvgClosePath(context);
            }
        }
        if (fill != null) {
            NVGColor fillColor = NVGWrapper.nvgColor(fill);
            nvgFillColor(context, fillColor);
            nvgFill(context);
            fillColor.free();
        }
        if (stroke != null && strokeWidth > 0f) {
            NVGColor strokeColor = NVGWrapper.nvgColor(stroke);
            org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(context, strokeWidth);
            org.lwjgl.nanovg.NanoVG.nvgStrokeColor(context, strokeColor);
            org.lwjgl.nanovg.NanoVG.nvgStroke(context);
            strokeColor.free();
        }
    }

    @Override
    public void fillHorizontalGradient(
            float x, float y, float width, float height, Color startColor, Color endColor) {
        NVGWrapper.drawHorizontalGradient(context, x, y, width, height, startColor, endColor);
    }

    @Override
    public void fillVerticalGradient(
            float x, float y, float width, float height, Color startColor, Color endColor) {
        NVGColor start = NVGWrapper.nvgColor(startColor);
        NVGColor end = NVGWrapper.nvgColor(endColor);
        try (NVGPaint paint = NVGPaint.calloc()) {
            org.lwjgl.nanovg.NanoVG.nvgLinearGradient(
                    context, x, y, x, y + height, start, end, paint);
            nvgBeginPath(context);
            nvgRect(context, x, y, width, height);
            nvgFillPaint(context, paint);
            nvgFill(context);
            nvgClosePath(context);
        } finally {
            start.free();
            end.free();
        }
    }

    @Override
    public void drawImage(UiImage image, float x, float y, float width, float height, float alpha) {
        NanoVgImage nanoVgImage = requireImage(image);
        try (NVGPaint paint = NVGPaint.calloc()) {
            nvgImagePattern(context, x, y, width, height, 0, nanoVgImage.handle(), alpha, paint);
            nvgBeginPath(context);
            nvgRect(context, x, y, width, height);
            nvgFillPaint(context, paint);
            nvgFill(context);
            nvgClosePath(context);
        }
    }

    @Override
    public void fillCurrentPathWithImage(UiImage image, float x, float y, float width, float height, float alpha) {
        NanoVgImage nanoVgImage = requireImage(image);
        try (NVGPaint paint = NVGPaint.calloc()) {
            nvgImagePattern(context, x, y, width, height, 0, nanoVgImage.handle(), alpha, paint);
            nvgFillPaint(context, paint);
            nvgFill(context);
        }
    }

    @Override
    public void beginPath() {
        nvgBeginPath(context);
    }

    @Override
    public void closePath() {
        nvgClosePath(context);
    }

    @Override
    public void moveTo(float x, float y) {
        org.lwjgl.nanovg.NanoVG.nvgMoveTo(context, x, y);
    }

    @Override
    public void lineTo(float x, float y) {
        org.lwjgl.nanovg.NanoVG.nvgLineTo(context, x, y);
    }

    @Override
    public void circle(float centerX, float centerY, float radius) {
        org.lwjgl.nanovg.NanoVG.nvgCircle(context, centerX, centerY, radius);
    }

    @Override
    public void arc(
            float centerX,
            float centerY,
            float radius,
            float startAngle,
            float endAngle,
            ArcDirection direction) {
        int nanoVgDirection = direction == ArcDirection.CLOCKWISE
                ? org.lwjgl.nanovg.NanoVG.NVG_CW
                : org.lwjgl.nanovg.NanoVG.NVG_CCW;
        org.lwjgl.nanovg.NanoVG.nvgArc(
                context, centerX, centerY, radius, startAngle, endAngle, nanoVgDirection);
    }

    @Override
    public void fillPath(Color color) {
        NVGColor fillColor = NVGWrapper.nvgColor(color);
        nvgFillColor(context, fillColor);
        nvgFill(context);
        fillColor.free();
    }

    @Override
    public void strokePath(float thickness, Color color) {
        NVGColor strokeColor = NVGWrapper.nvgColor(color);
        org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(context, thickness);
        org.lwjgl.nanovg.NanoVG.nvgStrokeColor(context, strokeColor);
        org.lwjgl.nanovg.NanoVG.nvgStroke(context);
        strokeColor.free();
    }

    @Override
    public void drawText(String text, float x, float y, TextStyle style) {
        nvgFontFace(context, style.font());
        nvgFontSize(context, style.size());
        nvgTextAlign(context, horizontalAlign(style.horizontalAlign()) | verticalAlign(style.verticalAlign()));
        NVGColor color = NVGWrapper.nvgColor(style.color());
        nvgFillColor(context, color);
        nvgText(context, x, y, text);
        color.free();
    }

    @Override
    public void save() {
        nvgSave(context);
    }

    @Override
    public void restore() {
        nvgRestore(context);
    }

    @Override
    public void translate(float x, float y) {
        nvgTranslate(context, x, y);
    }

    @Override
    public void rotateDegrees(float angleDegrees) {
        nvgRotate(context, (float) Math.toRadians(angleDegrees));
    }

    @Override
    public void scale(float x, float y) {
        nvgScale(context, x, y);
    }

    @Override
    public void scissor(float x, float y, float width, float height) {
        nvgScissor(context, x, y, width, height);
    }

    @Override
    public void resetScissor() {
        nvgResetScissor(context);
    }

    private static int horizontalAlign(HorizontalAlign align) {
        return switch (align) {
            case LEFT -> NVG_ALIGN_LEFT;
            case CENTER -> NVG_ALIGN_CENTER;
            case RIGHT -> NVG_ALIGN_RIGHT;
        };
    }

    private static int verticalAlign(VerticalAlign align) {
        return switch (align) {
            case TOP -> NVG_ALIGN_TOP;
            case MIDDLE -> NVG_ALIGN_MIDDLE;
            case BOTTOM -> NVG_ALIGN_BOTTOM;
            case BASELINE -> NVG_ALIGN_BASELINE;
        };
    }

    private static NanoVgImage requireImage(UiImage image) {
        if (image instanceof NanoVgImage nanoVgImage) {
            return nanoVgImage;
        }
        throw new IllegalArgumentException("Image belongs to a different rendering backend");
    }
}
