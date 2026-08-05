package com.seqwawa.seq.managers;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/** Decodes and composites bounded animated GIFs into NanoVG-friendly PNG frames. */
final class ChatGifDecoder {
    static final int MAX_FRAMES = 120;
    static final int MAX_PREVIEW_WIDTH = 360;
    static final int MAX_PREVIEW_HEIGHT = 220;
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;

    private ChatGifDecoder() {
    }

    static DecodedGif decode(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
            if (!readers.hasNext()) {
                throw new IOException("No GIF reader is available");
            }

            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                int count = reader.getNumImages(true);
                if (count <= 0 || count > MAX_FRAMES) {
                    throw new IOException("GIF frame count is outside the preview limit");
                }

                int[] logicalSize = logicalScreenSize(reader.getStreamMetadata());
                FrameInfo firstInfo = frameInfo(reader.getImageMetadata(0));
                int width = logicalSize[0] > 0 ? logicalSize[0] : firstInfo.left() + reader.getWidth(0);
                int height = logicalSize[1] > 0 ? logicalSize[1] : firstInfo.top() + reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height * count > MAX_SOURCE_PIXELS) {
                    throw new IOException("GIF dimensions are outside the preview limit");
                }

                BufferedImage canvas = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                List<byte[]> frames = new ArrayList<>(count);
                List<Integer> delays = new ArrayList<>(count);
                FrameInfo previousInfo = null;
                BufferedImage restorePrevious = null;

                for (int index = 0; index < count; index++) {
                    if (previousInfo != null) {
                        if ("restoreToBackgroundColor".equals(previousInfo.disposal())) {
                            clear(canvas, previousInfo);
                        } else if ("restoreToPrevious".equals(previousInfo.disposal()) && restorePrevious != null) {
                            canvas = copy(restorePrevious);
                        }
                    }

                    FrameInfo info = frameInfo(reader.getImageMetadata(index));
                    restorePrevious = "restoreToPrevious".equals(info.disposal()) ? copy(canvas) : null;
                    BufferedImage frame = reader.read(index);
                    Graphics2D graphics = canvas.createGraphics();
                    try {
                        graphics.setComposite(AlphaComposite.SrcOver);
                        graphics.drawImage(frame, info.left(), info.top(), null);
                    } finally {
                        graphics.dispose();
                    }

                    frames.add(toPng(scaleToPreview(canvas)));
                    delays.add(Math.max(20, info.delayHundredths() * 10));
                    previousInfo = info;
                }
                return new DecodedGif(List.copyOf(frames), List.copyOf(delays));
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage scaleToPreview(BufferedImage source) {
        double scale = Math.min(1d, Math.min(
                (double) MAX_PREVIEW_WIDTH / source.getWidth(),
                (double) MAX_PREVIEW_HEIGHT / source.getHeight()));
        if (scale >= 1d) {
            return copy(source);
        }
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private static void clear(BufferedImage canvas, FrameInfo info) {
        Graphics2D graphics = canvas.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(info.left(), info.top(), info.width(), info.height());
        } finally {
            graphics.dispose();
        }
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("Unable to encode GIF preview frame");
        }
        return output.toByteArray();
    }

    private static int[] logicalScreenSize(IIOMetadata metadata) {
        Node root = metadataTree(metadata, "javax_imageio_gif_stream_1.0");
        Node descriptor = child(root, "LogicalScreenDescriptor");
        return new int[] {
            integerAttribute(descriptor, "logicalScreenWidth", -1),
            integerAttribute(descriptor, "logicalScreenHeight", -1)
        };
    }

    private static FrameInfo frameInfo(IIOMetadata metadata) {
        Node root = metadataTree(metadata, "javax_imageio_gif_image_1.0");
        Node descriptor = child(root, "ImageDescriptor");
        Node control = child(root, "GraphicControlExtension");
        return new FrameInfo(
                integerAttribute(descriptor, "imageLeftPosition", 0),
                integerAttribute(descriptor, "imageTopPosition", 0),
                integerAttribute(descriptor, "imageWidth", 0),
                integerAttribute(descriptor, "imageHeight", 0),
                integerAttribute(control, "delayTime", 10),
                stringAttribute(control, "disposalMethod", "none"));
    }

    private static Node metadataTree(IIOMetadata metadata, String name) {
        if (metadata == null) {
            return null;
        }
        try {
            return metadata.getAsTree(name);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static Node child(Node parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) {
                return node;
            }
        }
        return null;
    }

    private static int integerAttribute(Node node, String name, int fallback) {
        String value = stringAttribute(node, name, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String stringAttribute(Node node, String name, String fallback) {
        if (node == null) {
            return fallback;
        }
        NamedNodeMap attributes = node.getAttributes();
        Node attribute = attributes == null ? null : attributes.getNamedItem(name);
        return attribute == null ? fallback : attribute.getNodeValue();
    }

    record DecodedGif(List<byte[]> pngFrames, List<Integer> delaysMs) {
        DecodedGif {
            pngFrames = List.copyOf(pngFrames);
            delaysMs = List.copyOf(delaysMs);
            if (pngFrames.isEmpty() || pngFrames.size() != delaysMs.size()) {
                throw new IllegalArgumentException("GIF frames and delays must be non-empty and aligned");
            }
        }
    }

    private record FrameInfo(int left, int top, int width, int height, int delayHundredths, String disposal) {
    }
}
