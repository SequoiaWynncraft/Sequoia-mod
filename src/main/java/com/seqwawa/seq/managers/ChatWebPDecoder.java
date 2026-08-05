package com.seqwawa.seq.managers;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.glavo.webp.WebPFrame;
import org.glavo.webp.WebPImageLoadOptions;
import org.glavo.webp.WebPImageReader;

/** Decodes bounded static or animated WebP chat media into NanoVG-friendly PNG frames. */
final class ChatWebPDecoder {
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;
    private static final int MAX_SOURCE_FRAMES = 2_000;

    private ChatWebPDecoder() {
    }

    static DecodedWebP decode(byte[] bytes) throws IOException {
        WebPImageLoadOptions options = WebPImageLoadOptions.builder()
                .requestedWidth(ChatGifDecoder.MAX_PREVIEW_WIDTH)
                .requestedHeight(ChatGifDecoder.MAX_PREVIEW_HEIGHT)
                .preserveRatio(true)
                .smooth(true)
                .build();
        try (WebPImageReader reader = WebPImageReader.open(new ByteArrayInputStream(bytes), options)) {
            int frameCount = reader.getFrameCount();
            if (frameCount <= 0 || frameCount > MAX_SOURCE_FRAMES) {
                throw new IOException("WebP frame count is outside the preview limit");
            }
            if (reader.getSourceWidth() <= 0
                    || reader.getSourceHeight() <= 0
                    || (long) reader.getSourceWidth() * reader.getSourceHeight() > MAX_SOURCE_PIXELS) {
                throw new IOException("WebP dimensions are outside the preview limit");
            }

            int stride = samplingStride(frameCount);
            List<byte[]> frames = new ArrayList<>(Math.min(frameCount, ChatGifDecoder.MAX_FRAMES));
            List<Integer> delays = new ArrayList<>(Math.min(frameCount, ChatGifDecoder.MAX_FRAMES));
            WebPFrame frame;
            int frameIndex = 0;
            while ((frame = reader.readNextFrame()) != null) {
                int delay = Math.max(20, frame.getDurationMillis());
                if (frameIndex % stride != 0) {
                    int previous = delays.size() - 1;
                    delays.set(previous, delays.get(previous) + delay);
                    frameIndex++;
                    continue;
                }
                BufferedImage image = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
                image.setRGB(
                        0,
                        0,
                        frame.getWidth(),
                        frame.getHeight(),
                        frame.getArgbArray(),
                        0,
                        frame.getScanlineStride());
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, "png", output)) {
                    throw new IOException("Unable to encode WebP preview frame");
                }
                frames.add(output.toByteArray());
                delays.add(delay);
                frameIndex++;
            }
            if (frames.isEmpty()) {
                throw new IOException("WebP contains no decodable frames");
            }
            return new DecodedWebP(List.copyOf(frames), List.copyOf(delays));
        }
    }

    static int samplingStride(int frameCount) {
        return Math.max(1, (frameCount + ChatGifDecoder.MAX_FRAMES - 1) / ChatGifDecoder.MAX_FRAMES);
    }

    record DecodedWebP(List<byte[]> pngFrames, List<Integer> delaysMs) {
        DecodedWebP {
            pngFrames = List.copyOf(pngFrames);
            delaysMs = List.copyOf(delaysMs);
            if (pngFrames.isEmpty() || pngFrames.size() != delaysMs.size()) {
                throw new IllegalArgumentException("WebP frames and delays must be non-empty and aligned");
            }
        }
    }
}
