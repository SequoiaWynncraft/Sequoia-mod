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
import org.sequoia.seq.client.SeqClient;

public final class StartupVideoOverlay {
    private static final String STARTUP_PREFIX = "textures/startup";
    private static final String STARTUP_VIDEO_KEY = "rat";
    private static final Pattern FRAME_PATH_PATTERN = Pattern.compile(
            Pattern.quote(STARTUP_PREFIX) + "/([a-z0-9_./-]+)/frame_(\\d+)\\.png");
    private static final long FRAME_NANOS = 1_000_000_000L / 24L;
    private static final int EDGE_PADDING = 12;
    private static final double MAX_WIDTH_FRACTION = 0.42;
    private static final double MAX_HEIGHT_FRACTION = 0.42;

    private static boolean scannedFrames;
    private static Map<String, StartupFrames> framesByKey = Map.of();
    private static long startedAtNanos;

    private StartupVideoOverlay() {}

    public static void render(GuiGraphics graphics) {
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
        int drawWidth = (int) Math.ceil(screenWidth * MAX_WIDTH_FRACTION);
        int drawHeight = (int) Math.ceil(drawWidth / sourceAspect);
        int maxHeight = (int) Math.ceil(screenHeight * MAX_HEIGHT_FRACTION);
        if (drawHeight > maxHeight) {
            drawHeight = maxHeight;
            drawWidth = (int) Math.ceil(drawHeight * sourceAspect);
        }
        int drawX = (screenWidth - drawWidth) / 2;
        int drawY = screenHeight - drawHeight - EDGE_PADDING;

        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                frame,
                drawX,
                drawY,
                0,
                0,
                drawWidth,
                drawHeight,
                startupFrames.width(),
                startupFrames.height(),
                startupFrames.width(),
                startupFrames.height());
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
}
