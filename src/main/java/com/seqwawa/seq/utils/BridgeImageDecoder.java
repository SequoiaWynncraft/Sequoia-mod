package com.seqwawa.seq.utils;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;
import org.w3c.dom.Node;

/**
 * Decodes a picture sent over the bridge into the frames chat draws, already shrunk
 * to the size they will be drawn at.
 * <p>
 * A GIF stores each frame as a patch over the previous one, with a rule for what to do
 * with that patch before the next ({@code disposalMethod}), so its frames are
 * composited here into whole pictures. Every frame is kept, at its own delay, unless
 * they would not fit in the memory a picture is allowed; then they are drawn a little
 * smaller first, and only then thinned out. Every other format Java reads is one frame,
 * turned upright when a photo's EXIF data says it was taken on its side.
 */
public final class BridgeImageDecoder {

    /** A picture's frames, each shown for its delay; a still picture has one. */
    public record Decoded(int sourceWidth, int sourceHeight, List<Frame> frames) {
        public boolean animated() {
            return frames.size() > 1;
        }
    }

    /** One frame, and how long it stays up. */
    public record Frame(BufferedImage image, int delayMillis) {}

    /** Frames past this many are not read at all; the loop restarts early. */
    static final int MAX_SOURCE_FRAMES = 1000;

    /** Anything larger on a side is refused rather than decoded. */
    static final int MAX_SOURCE_SIDE = 16384;

    /**
     * The largest image held whole: an animation's canvas and its frames, and a WebP,
     * which cannot be read at a fraction of its size. Sizes are checked before anything
     * is decoded, against what a large GIF ever needs rather than what a file claims.
     */
    static final long MAX_DECODED_PIXELS = 4096L * 4096;

    /**
     * Canvas pixels put together over a whole animation, which bounds how long a file
     * made to stall the decoder can do so; past it, the loop restarts early.
     */
    static final long MAX_ANIMATION_WORK_PIXELS = 400_000_000L;

    /** How far an animation that is too heavy is shrunk before frames are dropped instead. */
    private static final double MIN_ANIMATION_SCALE = 0.5d;

    /** Browsers show frame delays of 10 ms or less as 100 ms; so does this. */
    private static final int MAX_UNSET_DELAY_MILLIS = 10;
    private static final int DEFAULT_DELAY_MILLIS = 100;

    private static final String GIF_METADATA = "javax_imageio_gif_image_1.0";
    private static final String GIF_STREAM_METADATA = "javax_imageio_gif_stream_1.0";

    /**
     * Created directly rather than found through ImageIO's plugin registry, which only
     * sees the game's own class path, not the libraries nested in this mod.
     */
    private static final ImageReaderSpi WEBP = new WebPImageReaderSpi();

    private BridgeImageDecoder() {}

    /**
     * Decodes {@code bytes}, shrinking every frame to fit inside
     * {@code maxWidth x maxHeight} pixels, and keeping all frames together within
     * {@code maxBytes} as 32-bit pixels. Pictures already smaller keep their size.
     *
     * @throws IOException when the bytes are not a picture Java can read
     */
    public static Decoded decode(byte[] bytes, int maxWidth, int maxHeight, long maxBytes) throws IOException {
        if (!isSupportedPicture(bytes)) {
            // Java's other readers, for BMP, TIFF and the like, are never handed a
            // download from chat.
            throw new IOException("not a GIF, PNG, JPEG or WebP picture");
        }
        if (isGif(bytes)) {
            return decodeGif(bytes, maxWidth, maxHeight, maxBytes);
        }
        if (WebpFile.isWebp(bytes)) {
            return decodeWebp(bytes, maxWidth, maxHeight, maxBytes, BridgeImageDecoder::readWebp);
        }
        return decodeStill(bytes, maxWidth, maxHeight);
    }

    /** Reads one WebP image, for {@link #decodeWebp}; a seam for tests. */
    @FunctionalInterface
    interface WebpReader {
        /** The image in {@code webp}, refused unread when it claims more than {@code maxPixels}. */
        BufferedImage read(byte[] webp, long maxPixels) throws IOException;
    }

    /**
     * A WebP, still or animated. An animated one's frames are each decoded on their own
     * and put together here, over a transparent canvas, as browsers do: blended over
     * what is there or replacing it, and cleared afterwards when a frame asks to be.
     */
    static Decoded decodeWebp(byte[] bytes, int maxWidth, int maxHeight, long maxBytes, WebpReader reader)
            throws IOException {
        WebpFile file = WebpFile.parse(bytes);
        if (!file.animated()) {
            BufferedImage image = reader.read(bytes, MAX_DECODED_PIXELS);
            checkSize(image.getWidth(), image.getHeight());
            return new Decoded(
                    image.getWidth(), image.getHeight(), List.of(new Frame(fit(image, maxWidth, maxHeight), 0)));
        }

        int readable = checkAnimation(file.canvasWidth, file.canvasHeight, file.frames.size());
        Plan plan = Plan.of(file.canvasWidth, file.canvasHeight, file.frames.size(), maxWidth, maxHeight, maxBytes);
        BufferedImage canvas = new BufferedImage(file.canvasWidth, file.canvasHeight, BufferedImage.TYPE_INT_ARGB);
        List<Frame> kept = new ArrayList<>();
        for (int index = 0; index < readable; index++) {
            WebpFile.AnimationFrame frame = file.frames.get(index);
            BufferedImage patch;
            try {
                if (frame.x() + frame.width() > file.canvasWidth || frame.y() + frame.height() > file.canvasHeight) {
                    throw new IOException("WebP frame lies outside its canvas");
                }
                // A frame is only ever as large as the rectangle it declares.
                patch = reader.read(frame.standalone(), (long) frame.width() * frame.height());
            } catch (IOException | RuntimeException broken) {
                // A damaged frame ends the animation; the frames before it still play.
                if (kept.isEmpty()) {
                    throw broken instanceof IOException io ? io : new IOException(broken);
                }
                break;
            }

            Graphics2D graphics = canvas.createGraphics();
            graphics.setComposite(frame.blend() ? AlphaComposite.SrcOver : AlphaComposite.Src);
            graphics.drawImage(patch, frame.x(), frame.y(), frame.width(), frame.height(), null);
            graphics.dispose();

            int delay = delayMillis(frame.durationMillis());
            if (index % plan.stride() == 0 && kept.size() < plan.maxFrames()) {
                kept.add(new Frame(fit(canvas, plan.width(), plan.height()), delay));
            } else if (!kept.isEmpty()) {
                Frame last = kept.removeLast();
                kept.add(new Frame(last.image(), last.delayMillis() + delay));
            }

            if (frame.disposeToBackground()) {
                Graphics2D clear = canvas.createGraphics();
                clear.setComposite(AlphaComposite.Clear);
                clear.fillRect(frame.x(), frame.y(), frame.width(), frame.height());
                clear.dispose();
            }
        }
        return new Decoded(file.canvasWidth, file.canvasHeight, List.copyOf(kept));
    }

    /** One WebP image, through TwelveMonkeys' reader: Java has none of its own. */
    private static BufferedImage readWebp(byte[] webp, long maxPixels) throws IOException {
        ImageReader reader = WEBP.createReaderInstance(null);
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(webp))) {
            reader.setInput(input, true, true);
            int width = reader.getWidth(0);
            int height = reader.getHeight(0);
            checkSize(width, height);
            if ((long) width * height > maxPixels) {
                throw new IOException("WebP image is " + width + "x" + height);
            }
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    private static Decoded decodeStill(byte[] bytes, int maxWidth, int maxHeight) throws IOException {
        int orientation = ExifOrientation.of(bytes);
        boolean sideways = ExifOrientation.swapsSides(orientation);
        // Fitted before it is turned, so the box is turned instead.
        int boxWidth = sideways ? maxHeight : maxWidth;
        int boxHeight = sideways ? maxWidth : maxHeight;

        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new IOException("unsupported picture format");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                checkSize(width, height);
                ImageReadParam param = reader.getDefaultReadParam();
                // A photo many times the size it is drawn at is read at a fraction of its
                // pixels, still twice what is needed, so the last step down stays smooth.
                int subsampling = (int) Math.max(1, Math.floor(Math.min(
                        (double) width / Math.max(1, boxWidth), (double) height / Math.max(1, boxHeight)) / 2));
                if (subsampling > 1) {
                    param.setSourceSubsampling(subsampling, subsampling, 0, 0);
                }
                BufferedImage image = reader.read(0, param);
                BufferedImage upright = ExifOrientation.apply(fit(image, boxWidth, boxHeight), orientation);
                return new Decoded(
                        sideways ? height : width, sideways ? width : height, List.of(new Frame(upright, 0)));
            } finally {
                reader.dispose();
            }
        }
    }

    private static Decoded decodeGif(byte[] bytes, int maxWidth, int maxHeight, long maxBytes) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            throw new IOException("no GIF reader available");
        }
        ImageReader reader = readers.next();
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(input, false);

            int count = frameCount(reader);
            int[] screen = logicalScreen(reader.getStreamMetadata());
            // Sizes come from the headers, so nothing too large is ever decoded.
            int canvasWidth = screen != null ? screen[0] : reader.getWidth(0);
            int canvasHeight = screen != null ? screen[1] : reader.getHeight(0);
            int limit = checkAnimation(canvasWidth, canvasHeight, count > 0 ? count : MAX_SOURCE_FRAMES);
            BufferedImage canvas = new BufferedImage(canvasWidth, canvasHeight, BufferedImage.TYPE_INT_ARGB);
            Plan plan = Plan.of(canvasWidth, canvasHeight, count, maxWidth, maxHeight, maxBytes);
            List<Frame> kept = new ArrayList<>();
            for (int index = 0; index < limit; index++) {
                BufferedImage patch;
                IIOMetadataNode metadata;
                try {
                    checkHeld(reader.getWidth(index), reader.getHeight(index));
                    patch = reader.read(index);
                    metadata = (IIOMetadataNode) reader.getImageMetadata(index).getAsTree(GIF_METADATA);
                } catch (IndexOutOfBoundsException end) {
                    break;
                } catch (IOException | RuntimeException broken) {
                    // A damaged frame ends the animation; the frames before it still play.
                    if (kept.isEmpty()) {
                        throw broken instanceof IOException io ? io : new IOException(broken);
                    }
                    break;
                }

                IIOMetadataNode descriptor = child(metadata, "ImageDescriptor");
                IIOMetadataNode control = child(metadata, "GraphicControlExtension");
                int left = intAttribute(descriptor, "imageLeftPosition", 0);
                int top = intAttribute(descriptor, "imageTopPosition", 0);
                String disposal = control == null ? "none" : control.getAttribute("disposalMethod");
                int delay = delayMillis(control);

                BufferedImage beforePatch = "restoreToPrevious".equals(disposal) ? copy(canvas) : null;
                Graphics2D graphics = canvas.createGraphics();
                graphics.drawImage(patch, left, top, null);
                graphics.dispose();

                boolean keep = index % plan.stride() == 0;
                if (keep && kept.size() >= plan.maxFrames()) {
                    // Out of room, which only a GIF too damaged to count its frames
                    // reaches: the loop restarts here.
                    break;
                }
                if (keep) {
                    kept.add(new Frame(fit(canvas, plan.width(), plan.height()), delay));
                } else if (!kept.isEmpty()) {
                    // A dropped frame's time goes to the one before it, so the animation
                    // keeps its original speed.
                    Frame last = kept.removeLast();
                    kept.add(new Frame(last.image(), last.delayMillis() + delay));
                }

                if ("restoreToBackgroundColor".equals(disposal)) {
                    Graphics2D clear = canvas.createGraphics();
                    clear.setComposite(AlphaComposite.Clear);
                    clear.fillRect(left, top, patch.getWidth(), patch.getHeight());
                    clear.dispose();
                } else if (beforePatch != null) {
                    canvas = beforePatch;
                }
            }
            if (kept.isEmpty()) {
                throw new IOException("GIF has no frames");
            }
            return new Decoded(canvasWidth, canvasHeight, List.copyOf(kept));
        } finally {
            reader.dispose();
        }
    }

    /**
     * How an animation is kept: its frames' size, and every {@code stride}-th frame up
     * to {@code maxFrames}. As large as fits, all frames if they can be had by drawing
     * them down to {@link #MIN_ANIMATION_SCALE} of that size, and otherwise that small
     * with as many frames as fit.
     */
    record Plan(int width, int height, int stride, int maxFrames) {

        static Plan of(int sourceWidth, int sourceHeight, int frameCount, int maxWidth, int maxHeight, long maxBytes) {
            int[] fitted = fittedSize(sourceWidth, sourceHeight, maxWidth, maxHeight);
            int frames = Math.max(1, frameCount);
            long bytes = 4L * fitted[0] * fitted[1] * frames;
            double scale = bytes <= maxBytes ? 1d : Math.max(MIN_ANIMATION_SCALE, Math.sqrt((double) maxBytes / bytes));
            int width = Math.max(1, (int) Math.floor(fitted[0] * scale));
            int height = Math.max(1, (int) Math.floor(fitted[1] * scale));
            long frameBytes = 4L * width * height;
            int maxFrames = (int) Math.max(1, Math.min(MAX_SOURCE_FRAMES, maxBytes / frameBytes));
            int stride = frameCount > maxFrames ? (frameCount + maxFrames - 1) / maxFrames : 1;
            return new Plan(width, height, stride, maxFrames);
        }
    }

    /** How large a picture is once shrunk, keeping its proportions, to fit {@code maxWidth x maxHeight}. */
    static int[] fittedSize(int width, int height, int maxWidth, int maxHeight) {
        double scale = Math.min(1d, Math.min(
                (double) Math.max(1, maxWidth) / width, (double) Math.max(1, maxHeight) / height));
        return new int[] {
            Math.max(1, (int) Math.round(width * scale)), Math.max(1, (int) Math.round(height * scale))
        };
    }

    /**
     * {@code image} shrunk to fit inside {@code maxWidth x maxHeight}, keeping its
     * proportions. It is halved while it is more than twice too large, each halving
     * averaging every pixel into the result, and then taken to size with a bicubic
     * pass, which keeps edges sharper than a bilinear one. Pixels are blended with
     * their alpha applied, so transparent pixels leave no dark fringe.
     */
    static BufferedImage fit(BufferedImage image, int maxWidth, int maxHeight) {
        int[] target = fittedSize(image.getWidth(), image.getHeight(), maxWidth, maxHeight);
        BufferedImage current = image;
        while (current.getWidth() / 2 >= target[0] && current.getHeight() / 2 >= target[1]) {
            current = resize(current, current.getWidth() / 2, current.getHeight() / 2,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        }
        if (current.getWidth() != target[0] || current.getHeight() != target[1]) {
            current = resize(current, target[0], target[1], RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        }
        return current == image ? copy(image) : current;
    }

    private static BufferedImage resize(BufferedImage image, int width, int height, Object interpolation) {
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(image, 0, 0, width, height, null);
        graphics.dispose();
        return resized;
    }

    private static BufferedImage copy(BufferedImage image) {
        BufferedImage copy = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = copy.createGraphics();
        graphics.setComposite(AlphaComposite.Src);
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return copy;
    }

    /**
     * How many frames will be read, at most {@link #MAX_SOURCE_FRAMES}, or zero when a
     * damaged file cannot be counted, in which case frames are read until one fails.
     */
    private static int frameCount(ImageReader reader) {
        try {
            return Math.min(reader.getNumImages(true), MAX_SOURCE_FRAMES);
        } catch (IOException | RuntimeException damaged) {
            return 0;
        }
    }

    /** The GIF's canvas size, or {@code null} when the header leaves it out. */
    private static int[] logicalScreen(IIOMetadata streamMetadata) {
        if (streamMetadata == null) {
            return null;
        }
        IIOMetadataNode root = (IIOMetadataNode) streamMetadata.getAsTree(GIF_STREAM_METADATA);
        IIOMetadataNode screen = child(root, "LogicalScreenDescriptor");
        int width = intAttribute(screen, "logicalScreenWidth", 0);
        int height = intAttribute(screen, "logicalScreenHeight", 0);
        return width > 0 && height > 0 ? new int[] {width, height} : null;
    }

    private static int delayMillis(IIOMetadataNode control) {
        // GIF delays are in hundredths of a second.
        return delayMillis(intAttribute(control, "delayTime", 0) * 10);
    }

    /** A frame's delay as browsers play it: 10 ms or less is taken to mean 100 ms. */
    private static int delayMillis(int millis) {
        return millis <= MAX_UNSET_DELAY_MILLIS ? DEFAULT_DELAY_MILLIS : millis;
    }

    private static IIOMetadataNode child(IIOMetadataNode parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) {
                return (IIOMetadataNode) node;
            }
        }
        return null;
    }

    private static int intAttribute(IIOMetadataNode node, String name, int fallback) {
        if (node == null || !node.hasAttribute(name)) {
            return fallback;
        }
        try {
            return Integer.parseInt(node.getAttribute(name));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /**
     * Refuses an animation canvas too large to hold, and returns how many of its frames
     * may be put together: {@code frames} at most.
     */
    private static int checkAnimation(int width, int height, int frames) throws IOException {
        checkHeld(width, height);
        return (int) Math.max(1, Math.min(frames, MAX_ANIMATION_WORK_PIXELS / ((long) width * height)));
    }

    /** Refuses an image to be held whole that is larger than {@link #MAX_DECODED_PIXELS}. */
    private static void checkHeld(int width, int height) throws IOException {
        checkSize(width, height);
        if ((long) width * height > MAX_DECODED_PIXELS) {
            throw new IOException("image is " + width + "x" + height);
        }
    }

    private static void checkSize(int width, int height) throws IOException {
        if (width <= 0 || height <= 0 || width > MAX_SOURCE_SIDE || height > MAX_SOURCE_SIDE) {
            throw new IOException("picture is " + width + "x" + height);
        }
    }

    /** Whether {@code bytes} start as a GIF, PNG, JPEG or WebP does, the only pictures decoded. */
    public static boolean isSupportedPicture(byte[] bytes) {
        return isGif(bytes) || isPng(bytes) || isJpeg(bytes) || WebpFile.isWebp(bytes);
    }

    private static boolean isPng(byte[] bytes) {
        return bytes.length >= 8
                && (bytes[0] & 0xFF) == 0x89
                && bytes[1] == 'P'
                && bytes[2] == 'N'
                && bytes[3] == 'G'
                && bytes[4] == 0x0D
                && bytes[5] == 0x0A
                && bytes[6] == 0x1A
                && bytes[7] == 0x0A;
    }

    private static boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF;
    }

    private static boolean isGif(byte[] bytes) {
        return bytes.length >= 6
                && bytes[0] == 'G'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == '8';
    }
}
