package org.sequoia.seq.ui;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.config.ConfigManager;

public final class StartupVideoOverlay {
    private static final String STARTUP_PREFIX = "textures/startup";
    private static final String STARTUP_VIDEO_KEY = "rat";
    private static final Pattern FRAME_PATH_PATTERN = Pattern.compile(
            Pattern.quote(STARTUP_PREFIX) + "/([a-z0-9_./-]+)/frame_(\\d+)\\.png");
    private static final long FRAME_NANOS = 1_000_000_000L / 24L;
    private static final int EDGE_PADDING = 12;
    private static final double MAX_WIDTH_FRACTION = 0.42;
    private static final double MAX_HEIGHT_FRACTION = 0.42;
    private static final int MIN_DRAW_WIDTH = 80;
    private static final int HANDLE_SIZE = 12;
    private static final int HANDLE_HIT_SIZE = 16;
    private static final int OVERLAY_BORDER_COLOR = 0x80FFFFFF;
    private static final int OVERLAY_HANDLE_COLOR = 0xA0FFFFFF;

    private static boolean scannedFrames;
    private static Map<String, StartupFrames> framesByKey = Map.of();
    private static long startedAtNanos;
    private static Bounds currentBounds;
    private static DragMode dragMode = DragMode.NONE;
    private static double dragOffsetX;
    private static double dragOffsetY;
    private static boolean previousMouseDown;

    private StartupVideoOverlay() {}

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        if (SeqClient.getStartupVideoSetting() == null || !SeqClient.getStartupVideoSetting().getValue()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }

        if (!scannedFrames) {
            framesByKey = loadFrames(minecraft);
            scannedFrames = true;
        }
        StartupFrames startupFrames = framesByKey.get(STARTUP_VIDEO_KEY);
        if (startupFrames == null || startupFrames.frames().isEmpty()) {
            return;
        }

        if (startedAtNanos == 0L) {
            startedAtNanos = System.nanoTime();
        }

        long elapsedFrames = (System.nanoTime() - startedAtNanos) / FRAME_NANOS;
        Identifier frame = startupFrames.frames().get((int) (elapsedFrames % startupFrames.frames().size()));

        int screenWidth = graphics.guiWidth();
        int screenHeight = graphics.guiHeight();
        double sourceAspect = (double) startupFrames.width() / startupFrames.height();
        currentBounds = boundsForScreen(screenWidth, screenHeight, sourceAspect);
        handleMouseState(minecraft, mouseX, mouseY, screenWidth, screenHeight);

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                frame,
                currentBounds.x(),
                currentBounds.y(),
                0,
                0,
                currentBounds.width(),
                currentBounds.height(),
                startupFrames.width(),
                startupFrames.height(),
                startupFrames.width(),
                startupFrames.height());

        if (dragMode != DragMode.NONE || currentBounds.contains(mouseX, mouseY)) {
            drawInteractionFrame(graphics, currentBounds);
        }
    }

    private static void handleMouseState(
            Minecraft minecraft, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        boolean mouseDown =
                GLFW.glfwGetMouseButton(minecraft.getWindow().handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                        == GLFW.GLFW_PRESS;
        if (mouseDown && !previousMouseDown) {
            startDrag(mouseX, mouseY);
        } else if (mouseDown && dragMode != DragMode.NONE) {
            updateDrag(mouseX, mouseY, screenWidth, screenHeight);
        } else if (!mouseDown && previousMouseDown && dragMode != DragMode.NONE) {
            DragMode finishedMode = dragMode;
            dragMode = DragMode.NONE;
            saveCurrentBounds();
            SeqClient.LOGGER.info(
                    "[StartupVideo] Saved {} bounds: x={}, y={}, width={}, height={}",
                    finishedMode.name().toLowerCase(Locale.ROOT),
                    currentBounds.x(),
                    currentBounds.y(),
                    currentBounds.width(),
                    currentBounds.height());
        }
        previousMouseDown = mouseDown;
    }

    private static void startDrag(int mouseX, int mouseY) {
        if (currentBounds == null) {
            return;
        }
        if (currentBounds.resizeHandleContains(mouseX, mouseY)) {
            dragMode = DragMode.RESIZE;
            SeqClient.LOGGER.info("[StartupVideo] Started resize at {}, {}", mouseX, mouseY);
            return;
        }
        if (currentBounds.contains(mouseX, mouseY)) {
            dragMode = DragMode.MOVE;
            dragOffsetX = mouseX - currentBounds.x();
            dragOffsetY = mouseY - currentBounds.y();
            SeqClient.LOGGER.info("[StartupVideo] Started move at {}, {}", mouseX, mouseY);
        }
    }

    private static void updateDrag(int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (dragMode == DragMode.MOVE) {
            int x = clamp((int) Math.round(mouseX - dragOffsetX), 0, Math.max(0, screenWidth - currentBounds.width()));
            int y = clamp((int) Math.round(mouseY - dragOffsetY), 0, Math.max(0, screenHeight - currentBounds.height()));
            currentBounds = new Bounds(x, y, currentBounds.width(), currentBounds.height());
            return;
        }

        double aspect = (double) currentBounds.width() / currentBounds.height();
        int maxWidth = Math.max(MIN_DRAW_WIDTH, screenWidth - currentBounds.x());
        int maxHeight = Math.max((int) Math.round(MIN_DRAW_WIDTH / aspect), screenHeight - currentBounds.y());
        int width = clamp((int) Math.round(mouseX - currentBounds.x()), MIN_DRAW_WIDTH, maxWidth);
        int height = (int) Math.round(width / aspect);
        if (height > maxHeight) {
            height = maxHeight;
            width = (int) Math.round(height * aspect);
        }
        currentBounds = new Bounds(currentBounds.x(), currentBounds.y(), width, height);
    }

    private static Bounds boundsForScreen(int screenWidth, int screenHeight, double sourceAspect) {
        ConfigManager.StartupVideoBounds savedBounds =
                SeqClient.getConfigManager() == null ? null : SeqClient.getConfigManager().getStartupVideoBounds();
        if (currentBounds != null && dragMode != DragMode.NONE) {
            return clampBounds(currentBounds, screenWidth, screenHeight);
        }
        if (savedBounds != null) {
            int width = Math.max(MIN_DRAW_WIDTH, (int) Math.round(savedBounds.width() * screenWidth));
            int height = Math.max(1, (int) Math.round(savedBounds.height() * screenHeight));
            int x = (int) Math.round(savedBounds.x() * screenWidth);
            int y = (int) Math.round(savedBounds.y() * screenHeight);
            return clampBounds(new Bounds(x, y, width, height), screenWidth, screenHeight);
        }
        return defaultBounds(screenWidth, screenHeight, sourceAspect);
    }

    private static Bounds defaultBounds(int screenWidth, int screenHeight, double sourceAspect) {
        int drawWidth = (int) Math.ceil(screenWidth * MAX_WIDTH_FRACTION);
        int drawHeight = (int) Math.ceil(drawWidth / sourceAspect);
        int maxHeight = (int) Math.ceil(screenHeight * MAX_HEIGHT_FRACTION);
        if (drawHeight > maxHeight) {
            drawHeight = maxHeight;
            drawWidth = (int) Math.ceil(drawHeight * sourceAspect);
        }
        int drawX = (screenWidth - drawWidth) / 2;
        int drawY = screenHeight - drawHeight - EDGE_PADDING;
        return clampBounds(new Bounds(drawX, drawY, drawWidth, drawHeight), screenWidth, screenHeight);
    }

    private static Bounds clampBounds(Bounds bounds, int screenWidth, int screenHeight) {
        int width = clamp(bounds.width(), MIN_DRAW_WIDTH, Math.max(MIN_DRAW_WIDTH, screenWidth));
        int height = clamp(bounds.height(), 1, Math.max(1, screenHeight));
        int x = clamp(bounds.x(), 0, Math.max(0, screenWidth - width));
        int y = clamp(bounds.y(), 0, Math.max(0, screenHeight - height));
        return new Bounds(x, y, width, height);
    }

    private static void saveCurrentBounds() {
        if (SeqClient.getConfigManager() == null || currentBounds == null) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        int screenHeight = minecraft.getWindow().getGuiScaledHeight();
        if (screenWidth <= 0 || screenHeight <= 0) {
            return;
        }
        SeqClient.getConfigManager()
                .setStartupVideoBounds(
                        (double) currentBounds.x() / screenWidth,
                        (double) currentBounds.y() / screenHeight,
                        (double) currentBounds.width() / screenWidth,
                        (double) currentBounds.height() / screenHeight);
    }

    private static void drawInteractionFrame(GuiGraphics graphics, Bounds bounds) {
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + 1, OVERLAY_BORDER_COLOR);
        graphics.fill(bounds.x(), bounds.y(), bounds.x() + 1, bounds.y() + bounds.height(), OVERLAY_BORDER_COLOR);
        graphics.fill(
                bounds.x(),
                bounds.y() + bounds.height() - 1,
                bounds.x() + bounds.width(),
                bounds.y() + bounds.height(),
                OVERLAY_BORDER_COLOR);
        graphics.fill(
                bounds.x() + bounds.width() - 1,
                bounds.y(),
                bounds.x() + bounds.width(),
                bounds.y() + bounds.height(),
                OVERLAY_BORDER_COLOR);

        int handleX = bounds.x() + bounds.width() - HANDLE_SIZE;
        int handleY = bounds.y() + bounds.height() - HANDLE_SIZE;
        graphics.fill(handleX, handleY + HANDLE_SIZE - 2, handleX + HANDLE_SIZE, handleY + HANDLE_SIZE, OVERLAY_HANDLE_COLOR);
        graphics.fill(handleX + HANDLE_SIZE - 2, handleY, handleX + HANDLE_SIZE, handleY + HANDLE_SIZE, OVERLAY_HANDLE_COLOR);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Map<String, StartupFrames> loadFrames(Minecraft minecraft) {
        try {
            Map<String, List<Identifier>> loadedFrames = new HashMap<>();
            minecraft.getResourceManager()
                    .listResources(STARTUP_PREFIX, id -> isFrame(id.getPath()))
                    .keySet()
                    .forEach(id -> loadedFrames
                            .computeIfAbsent(frameKey(id.getPath()), ignored -> new java.util.ArrayList<>())
                            .add(id));
            loadedFrames.values().forEach(frames -> frames.sort(Comparator.comparingInt(id -> frameIndex(id.getPath()))));

            Map<String, StartupFrames> startupFrames = new HashMap<>();
            loadedFrames.forEach((uuid, frames) -> readFrameDimensions(minecraft, frames.getFirst())
                    .ifPresent(dimensions -> startupFrames.put(
                            uuid, new StartupFrames(List.copyOf(frames), dimensions.width(), dimensions.height()))));
            return startupFrames;
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[StartupVideo] Failed to load startup video frames.", exception);
            return Map.of();
        }
    }

    private static boolean isFrame(String path) {
        return FRAME_PATH_PATTERN.matcher(path.toLowerCase(Locale.ROOT)).matches();
    }

    private static String frameKey(String path) {
        var matcher = FRAME_PATH_PATTERN.matcher(path.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a startup video frame: " + path);
        }
        return matcher.group(1);
    }

    private static int frameIndex(String path) {
        var matcher = FRAME_PATH_PATTERN.matcher(path.toLowerCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Not a startup video frame: " + path);
        }
        return Integer.parseInt(matcher.group(2));
    }

    private static java.util.Optional<FrameDimensions> readFrameDimensions(Minecraft minecraft, Identifier frame) {
        try (var inputStream = minecraft.getResourceManager().open(frame)) {
            BufferedImage image = ImageIO.read(inputStream);
            if (image == null) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new FrameDimensions(image.getWidth(), image.getHeight()));
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[StartupVideo] Failed to read frame dimensions for {}.", frame, exception);
            return java.util.Optional.empty();
        }
    }

    private record StartupFrames(List<Identifier> frames, int width, int height) {}

    private record FrameDimensions(int width, int height) {}

    private record Bounds(int x, int y, int width, int height) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }

        private boolean resizeHandleContains(double mouseX, double mouseY) {
            return mouseX >= x + width - HANDLE_HIT_SIZE
                    && mouseX <= x + width
                    && mouseY >= y + height - HANDLE_HIT_SIZE
                    && mouseY <= y + height;
        }
    }

    private enum DragMode {
        NONE,
        MOVE,
        RESIZE
    }
}
