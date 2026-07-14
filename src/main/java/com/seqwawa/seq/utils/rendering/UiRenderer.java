package com.seqwawa.seq.utils.rendering;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Coordinates scoped UI work independently of the active drawing backend. */
public final class UiRenderer {
    private static final UiRenderQueue<Consumer<UiCanvas>> QUEUE = new UiRenderQueue<>();
    private static final ThreadLocal<UiCanvas> CURRENT_CANVAS = new ThreadLocal<>();

    private static volatile UiRenderBackend backend;

    private UiRenderer() {
    }

    public static synchronized boolean initialize(UiRenderBackend newBackend) {
        Objects.requireNonNull(newBackend, "newBackend");
        if (backend != null) {
            return backend.isAvailable();
        }
        backend = newBackend;
        boolean initialized = false;
        try {
            initialized = backend.initialize();
            return initialized;
        } finally {
            if (!initialized) {
                try {
                    backend.close();
                } finally {
                    backend = null;
                }
            }
        }
    }

    public static void renderScreen(Object owner, Consumer<UiCanvas> command) {
        if (isAvailable()) {
            QUEUE.submitScreen(owner, command);
        }
    }

    public static void renderHud(Consumer<UiCanvas> command) {
        if (isAvailable()) {
            QUEUE.submitHud(command);
        }
    }

    public static void renderResource(Consumer<UiCanvas> command) {
        if (isAvailable()) {
            QUEUE.submitResource(command);
        }
    }

    public static void flush(Object currentOwner, UiRenderMetrics metrics) {
        UiRenderBackend currentBackend = backend;
        if (currentBackend == null || !currentBackend.isAvailable()) {
            QUEUE.clear();
            return;
        }
        List<Consumer<UiCanvas>> commands = QUEUE.drain(currentOwner);
        if (!commands.isEmpty()) {
            List<Consumer<UiCanvas>> scopedCommands = new ArrayList<>(commands.size());
            for (Consumer<UiCanvas> command : commands) {
                scopedCommands.add(canvas -> runWithCanvas(canvas, command));
            }
            currentBackend.renderFrame(metrics, List.copyOf(scopedCommands));
        }
    }

    public static UiCanvas currentCanvas() {
        UiCanvas canvas = CURRENT_CANVAS.get();
        if (canvas == null) {
            throw new IllegalStateException("UI drawing is only valid inside a render callback");
        }
        return canvas;
    }

    public static boolean registerFont(String name, ByteBuffer data) {
        UiRenderBackend currentBackend = backend;
        return currentBackend != null && currentBackend.isAvailable() && currentBackend.registerFont(name, data);
    }

    public static boolean registerFont(String name, String filePath) {
        UiRenderBackend currentBackend = backend;
        return currentBackend != null && currentBackend.isAvailable() && currentBackend.registerFont(name, filePath);
    }

    public static UiTextMetrics measureText(String text, String font, float size) {
        UiRenderBackend currentBackend = backend;
        if (currentBackend == null || !currentBackend.isAvailable()) {
            return new UiTextMetrics(0, 0, 0, 0);
        }
        return currentBackend.measureText(text, font, size);
    }

    public static UiImage createImage(ByteBuffer data, boolean nearestNeighbor) {
        UiRenderBackend currentBackend = backend;
        if (currentBackend == null || !currentBackend.isAvailable()) {
            return null;
        }
        return currentBackend.createImage(data, nearestNeighbor);
    }

    public static void deleteImage(UiImage image) {
        UiRenderBackend currentBackend = backend;
        if (currentBackend != null && currentBackend.isAvailable() && image != null) {
            currentBackend.deleteImage(image);
        }
    }

    public static synchronized void shutdown() {
        QUEUE.clear();
        if (backend != null) {
            try {
                backend.close();
            } finally {
                backend = null;
                CURRENT_CANVAS.remove();
            }
        }
    }

    public static boolean isAvailable() {
        UiRenderBackend currentBackend = backend;
        return currentBackend != null && currentBackend.isAvailable();
    }

    public static UiRenderBackend backend() {
        return backend;
    }

    private static void runWithCanvas(UiCanvas canvas, Consumer<UiCanvas> command) {
        UiCanvas previousCanvas = CURRENT_CANVAS.get();
        CURRENT_CANVAS.set(canvas);
        try {
            command.accept(canvas);
        } finally {
            if (previousCanvas == null) {
                CURRENT_CANVAS.remove();
            } else {
                CURRENT_CANVAS.set(previousCanvas);
            }
        }
    }
}
