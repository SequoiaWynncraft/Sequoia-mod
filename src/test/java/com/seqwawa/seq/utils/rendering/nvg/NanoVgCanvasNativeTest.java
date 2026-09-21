package com.seqwawa.seq.utils.rendering.nvg;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MAJOR;
import static org.lwjgl.glfw.GLFW.GLFW_CONTEXT_VERSION_MINOR;
import static org.lwjgl.glfw.GLFW.GLFW_FALSE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_CORE_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_OPENGL_PROFILE;
import static org.lwjgl.glfw.GLFW.GLFW_STENCIL_BITS;
import static org.lwjgl.glfw.GLFW.GLFW_VISIBLE;
import static org.lwjgl.glfw.GLFW.glfwCreateWindow;
import static org.lwjgl.glfw.GLFW.glfwDestroyWindow;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwMakeContextCurrent;
import static org.lwjgl.glfw.GLFW.glfwSetErrorCallback;
import static org.lwjgl.glfw.GLFW.glfwTerminate;
import static org.lwjgl.glfw.GLFW.glfwWindowHint;
import static org.lwjgl.nanovg.NanoVG.nvgBeginFrame;
import static org.lwjgl.nanovg.NanoVG.nvgEndFrame;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_ANTIALIAS;
import static org.lwjgl.nanovg.NanoVGGL3.NVG_STENCIL_STROKES;
import static org.lwjgl.nanovg.NanoVGGL3.nvgCreate;
import static org.lwjgl.nanovg.NanoVGGL3.nvgDelete;
import static org.lwjgl.system.MemoryUtil.NULL;

import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderMetrics;
import java.awt.Color;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWErrorCallback;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

class NanoVgCanvasNativeTest {
    private static final int WIDTH = 80;
    private static final int HEIGHT = 80;

    @Test
    void overlappingBatchedFillsRetainPerShapeAlpha() {
        withContext(context -> {
            GL11.glViewport(0, 0, WIDTH, HEIGHT);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
            nvgBeginFrame(context, WIDTH, HEIGHT, 1f);

            NanoVgCanvas canvas =
                    new NanoVgCanvas(context, new UiRenderMetrics(WIDTH, HEIGHT, 1.0, 0.5f));
            canvas.fillCircles(
                    List.of(new UiCanvas.Circle(18, 20, 10), new UiCanvas.Circle(26, 20, 10)),
                    new Color(255, 0, 0, 128));
            List<UiCanvas.Point> square = List.of(
                    new UiCanvas.Point(0, 0),
                    new UiCanvas.Point(18, 0),
                    new UiCanvas.Point(18, 18),
                    new UiCanvas.Point(0, 18));
            canvas.fillAndStrokePolygons(
                    List.of(
                            new UiCanvas.Polygon(square, 38, 48, true),
                            new UiCanvas.Polygon(square, 46, 48, true)),
                    new Color(0, 0, 255, 128),
                    null,
                    0);

            nvgEndFrame(context);
            GL11.glFinish();

            int circleSingleRed = readPixel(12, 20)[0];
            int circleOverlapRed = readPixel(22, 20)[0];
            int polygonSingleBlue = readPixel(40, 56)[2];
            int polygonOverlapBlue = readPixel(50, 56)[2];
            assertTrue(
                    circleOverlapRed >= circleSingleRed + 35,
                    () -> "Circle overlap should composite twice: single="
                            + circleSingleRed
                            + ", overlap="
                            + circleOverlapRed);
            assertTrue(
                    polygonOverlapBlue >= polygonSingleBlue + 35,
                    () -> "Polygon overlap should composite twice: single="
                            + polygonSingleBlue
                            + ", overlap="
                            + polygonOverlapBlue);
        });
    }

    @Test
    void adjacentImageTilesHaveUniformCoverageAtFractionalZooms() {
        withContext(context -> {
            ByteBuffer pixels = BufferUtils.createByteBuffer(16);
            while (pixels.hasRemaining()) pixels.put((byte) 255);
            pixels.flip();
            int handle = org.lwjgl.nanovg.NanoVG.nvgCreateImageRGBA(
                    context, 2, 2, org.lwjgl.nanovg.NanoVG.NVG_IMAGE_NEAREST, pixels);
            assertTrue(handle != 0);
            NanoVgImage image = new NanoVgImage(handle, 2, 2);
            try {
                for (float density : new float[] {1, 1.5f, 3}) {
                    NanoVgCanvas canvas = new NanoVgCanvas(
                            context, new UiRenderMetrics(WIDTH, HEIGHT, 1, density / 2));
                    for (float size : new float[] {11.3f, 18.25f, 23.75f}) {
                        for (float offset : new float[] {0.1f, 0.25f, 0.5f, 0.75f, 0.9f}) {
                            for (float alpha : new float[] {0.35f, 1}) {
                                GL11.glViewport(0, 0, WIDTH, HEIGHT);
                                GL11.glClearColor(0, 0, 0, 0);
                                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_STENCIL_BUFFER_BIT);
                                nvgBeginFrame(context, WIDTH / density, HEIGHT / density, density);
                                float origin = 10 + offset;
                                for (int row = 0; row < 2; row++) {
                                    for (int column = 0; column < 2; column++) {
                                        float x = origin + column * size;
                                        float y = origin + row * size;
                                        canvas.drawImageTile(image, x / density, y / density,
                                                size / density, size / density, alpha);
                                    }
                                }
                                nvgEndFrame(context);
                                GL11.glFinish();
                                ByteBuffer frame = BufferUtils.createByteBuffer(WIDTH * HEIGHT * 4);
                                GL11.glReadPixels(0, 0, WIDTH, HEIGHT, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, frame);
                                int expected = Math.round(alpha * 255);
                                // Include both seams and their crossing, excluding the outer map edge.
                                for (int y = (int) Math.ceil(origin + 1); y < origin + 2 * size - 1; y++) {
                                    for (int x = (int) Math.ceil(origin + 1); x < origin + 2 * size - 1; x++) {
                                        int actual = Byte.toUnsignedInt(frame.get(((HEIGHT - 1 - y) * WIDTH + x) * 4));
                                        assertTrue(Math.abs(actual - expected) <= 2,
                                                "Tile coverage at " + x + "," + y + " should be " + expected
                                                        + " but was " + actual + "; density=" + density
                                                        + ", size=" + size + ", offset=" + offset);
                                    }
                                }
                            }
                        }
                    }
                }
            } finally {
                org.lwjgl.nanovg.NanoVG.nvgDeleteImage(context, handle);
            }
        });
    }

    private static void withContext(LongConsumer test) {
        GLFWErrorCallback errorCallback = GLFWErrorCallback.createPrint(System.err);
        errorCallback.set();
        boolean glfwInitialized = glfwInit();
        long window = NULL;
        long context = NULL;
        try {
            assumeTrue(glfwInitialized, "A native OpenGL context is unavailable");
            glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
            glfwWindowHint(GLFW_STENCIL_BITS, 8);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2);
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
            window = glfwCreateWindow(WIDTH, HEIGHT, "NanoVG overlap regression", NULL, NULL);
            assumeTrue(window != NULL, "A native OpenGL window is unavailable");

            glfwMakeContextCurrent(window);
            GL.createCapabilities();
            context = nvgCreate(NVG_ANTIALIAS | NVG_STENCIL_STROKES);
            assumeTrue(context != NULL, "A native NanoVG context is unavailable");

            test.accept(context);
        } finally {
            if (context != NULL) {
                nvgDelete(context);
            }
            if (window != NULL) {
                glfwMakeContextCurrent(NULL);
                GL.setCapabilities(null);
                glfwDestroyWindow(window);
            }
            if (glfwInitialized) {
                glfwTerminate();
            }
            glfwSetErrorCallback(null);
            errorCallback.free();
        }
    }

    private static int[] readPixel(int x, int y) {
        ByteBuffer pixel = BufferUtils.createByteBuffer(4);
        GL11.glReadPixels(x, HEIGHT - 1 - y, 1, 1, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixel);
        return new int[] {
            Byte.toUnsignedInt(pixel.get(0)),
            Byte.toUnsignedInt(pixel.get(1)),
            Byte.toUnsignedInt(pixel.get(2)),
            Byte.toUnsignedInt(pixel.get(3))
        };
    }
}
