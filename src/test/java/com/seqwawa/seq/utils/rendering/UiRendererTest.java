package com.seqwawa.seq.utils.rendering;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UiRendererTest {
    private static final UiRenderMetrics METRICS = new UiRenderMetrics(1920, 1080, 2.0, 1f);

    @AfterEach
    void shutDownRenderer() {
        UiRenderer.shutdown();
    }

    @Test
    void batchesValidCommandsIntoOneBackendFrame() {
        FakeBackend backend = new FakeBackend();
        Object currentScreen = new Object();
        List<String> rendered = new ArrayList<>();
        assertTrue(UiRenderer.initialize(backend));

        UiRenderer.renderScreen(currentScreen, canvas -> rendered.add("screen"));
        UiRenderer.renderScreen(new Object(), canvas -> rendered.add("stale"));
        UiRenderer.renderResource(canvas -> rendered.add("resource"));
        UiRenderer.flush(currentScreen, METRICS);

        assertEquals(List.of("screen", "resource"), rendered);
        assertEquals(1, backend.frameCount);
        assertEquals(List.of(2), backend.commandCounts);
        assertEquals(METRICS, backend.lastMetrics);
    }

    @Test
    void doesNotQueueCommandsWhenBackendInitializationFails() {
        FakeBackend backend = new FakeBackend();
        backend.available = false;

        assertFalse(UiRenderer.initialize(backend));
        UiRenderer.renderHud(canvas -> backend.executedCommands++);
        UiRenderer.flush(null, METRICS);

        assertEquals(0, backend.frameCount);
        assertEquals(0, backend.executedCommands);
    }

    @Test
    void initializationAndShutdownOwnBackendLifecycle() {
        FakeBackend backend = new FakeBackend();

        assertTrue(UiRenderer.initialize(backend));
        assertTrue(UiRenderer.initialize(new FakeBackend()));
        UiRenderer.shutdown();

        assertEquals(1, backend.initializeCount);
        assertEquals(1, backend.closeCount);
        assertFalse(UiRenderer.isAvailable());
    }

    @Test
    void failedInitializationCanBeReplacedByAnotherBackend() {
        FakeBackend failedBackend = new FakeBackend();
        failedBackend.available = false;
        FakeBackend replacement = new FakeBackend();

        assertFalse(UiRenderer.initialize(failedBackend));
        assertTrue(UiRenderer.initialize(replacement));

        assertEquals(1, failedBackend.closeCount);
        assertEquals(1, replacement.initializeCount);
    }

    @Test
    void activeCanvasIsClearedWhenACommandFails() {
        FakeBackend backend = new FakeBackend();
        UiRenderer.initialize(backend);
        UiRenderer.renderHud(canvas -> {
            assertEquals(canvas, UiRenderer.currentCanvas());
            throw new IllegalStateException("draw failed");
        });

        assertThrows(IllegalStateException.class, () -> UiRenderer.flush(null, METRICS));
        assertThrows(IllegalStateException.class, UiRenderer::currentCanvas);
    }

    @Test
    void canvasBatchFallbacksPreserveEveryShape() {
        FakeBackend backend = new FakeBackend();
        UiRenderer.initialize(backend);
        UiRenderer.renderHud(canvas -> {
            canvas.fillCircles(
                    List.of(new UiCanvas.Circle(1, 2, 3), new UiCanvas.Circle(4, 5, 6)), Color.WHITE);
            canvas.fillAndStrokePolygons(
                    List.of(
                            new UiCanvas.Polygon(List.of(new UiCanvas.Point(1, 2)), 10, 20, false),
                            new UiCanvas.Polygon(List.of(new UiCanvas.Point(3, 4)), 30, 40, false)),
                    null,
                    Color.WHITE,
                    1);
        });

        UiRenderer.flush(null, METRICS);

        assertEquals(2, backend.canvas.circleCount);
        assertEquals(2, backend.canvas.polygonCount);
        assertEquals(List.of(new UiCanvas.Point(11, 22), new UiCanvas.Point(33, 44)), backend.canvas.firstPolygonPoints);
    }

    private static final class FakeBackend implements UiRenderBackend {
        private final FakeCanvas canvas = new FakeCanvas();
        private final List<Integer> commandCounts = new ArrayList<>();
        private boolean available = true;
        private int initializeCount;
        private int closeCount;
        private int frameCount;
        private int executedCommands;
        private UiRenderMetrics lastMetrics;

        @Override
        public boolean initialize() {
            initializeCount++;
            return available;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public void renderFrame(UiRenderMetrics metrics, List<Consumer<UiCanvas>> commands) {
            frameCount++;
            lastMetrics = metrics;
            commandCounts.add(commands.size());
            commands.forEach(command -> {
                command.accept(canvas);
                executedCommands++;
            });
        }

        @Override
        public boolean registerFont(String name, ByteBuffer data) {
            return available;
        }

        @Override
        public boolean registerFont(String name, String filePath) {
            return available;
        }

        @Override
        public UiTextMetrics measureText(String text, String font, float size) {
            return new UiTextMetrics(0, 0, text.length() * size, size);
        }

        @Override
        public UiImage createImage(ByteBuffer data, boolean nearestNeighbor) {
            return new FakeImage();
        }

        @Override
        public void deleteImage(UiImage image) {
        }

        @Override
        public void close() {
            closeCount++;
            available = false;
        }
    }

    private static final class FakeCanvas implements UiCanvas {
        private int circleCount;
        private int polygonCount;
        private final List<Point> firstPolygonPoints = new ArrayList<>();

        @Override
        public UiRenderMetrics metrics() {
            return METRICS;
        }

        @Override
        public void fillRect(float x, float y, float width, float height, Color color) {
        }

        @Override
        public void fillRoundedRect(float x, float y, float width, float height, float radius, Color color) {
        }

        @Override
        public void strokeRect(float x, float y, float width, float height, float thickness, Color color) {
        }

        @Override
        public void strokeLine(float x1, float y1, float x2, float y2, float thickness, Color color) {
        }

        @Override
        public void fillCircle(float centerX, float centerY, float radius, Color color) {
            circleCount++;
        }

        @Override
        public void strokeCircle(float centerX, float centerY, float radius, float thickness, Color color) {
        }

        @Override
        public void fillAndStrokePolygon(
                List<Point> points, Color fill, Color stroke, float strokeWidth, boolean closed) {
            polygonCount++;
            firstPolygonPoints.add(points.getFirst());
        }

        @Override
        public void fillHorizontalGradient(
                float x, float y, float width, float height, Color startColor, Color endColor) {
        }

        @Override
        public void fillVerticalGradient(
                float x, float y, float width, float height, Color startColor, Color endColor) {
        }

        @Override
        public void drawImage(UiImage image, float x, float y, float width, float height, float alpha) {
        }

        @Override
        public void fillCurrentPathWithImage(
                UiImage image, float x, float y, float width, float height, float alpha) {
        }

        @Override
        public void beginPath() {
        }

        @Override
        public void closePath() {
        }

        @Override
        public void moveTo(float x, float y) {
        }

        @Override
        public void lineTo(float x, float y) {
        }

        @Override
        public void circle(float centerX, float centerY, float radius) {
        }

        @Override
        public void arc(
                float centerX,
                float centerY,
                float radius,
                float startAngle,
                float endAngle,
                ArcDirection direction) {
        }

        @Override
        public void fillPath(Color color) {
        }

        @Override
        public void strokePath(float thickness, Color color) {
        }

        @Override
        public void drawText(String text, float x, float y, TextStyle style) {
        }

        @Override
        public void save() {
        }

        @Override
        public void restore() {
        }

        @Override
        public void translate(float x, float y) {
        }

        @Override
        public void rotateDegrees(float angleDegrees) {
        }

        @Override
        public void scale(float x, float y) {
        }

        @Override
        public void scissor(float x, float y, float width, float height) {
        }

        @Override
        public void resetScissor() {
        }
    }

    private record FakeImage() implements UiImage {
        @Override
        public int width() {
            return 1;
        }

        @Override
        public int height() {
            return 1;
        }
    }
}
