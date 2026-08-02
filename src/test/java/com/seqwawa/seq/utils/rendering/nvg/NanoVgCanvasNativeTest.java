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
