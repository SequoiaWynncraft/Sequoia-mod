package com.seqwawa.seq.managers;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Validates and bounds static chat preview images before GPU upload. */
final class ChatImageDecoder {
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;

    private ChatImageDecoder() {
    }

    static byte[] decodeToPreviewPng(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_SOURCE_PIXELS) {
                    throw new IOException("Image dimensions are outside the preview limit");
                }
                BufferedImage source = reader.read(0);
                BufferedImage preview = scale(source);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                if (!ImageIO.write(preview, "png", output)) {
                    throw new IOException("Unable to encode image preview");
                }
                return output.toByteArray();
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage scale(BufferedImage source) {
        double scale = Math.min(1d, Math.min(
                (double) ChatGifDecoder.MAX_PREVIEW_WIDTH / source.getWidth(),
                (double) ChatGifDecoder.MAX_PREVIEW_HEIGHT / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage preview = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = preview.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return preview;
    }
}
