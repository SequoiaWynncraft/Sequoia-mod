package com.seqwawa.seq.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The parts of a WebP file that showing it takes: its canvas, and, when it is animated,
 * its frames, each with where it goes, how long it stays, and how it meets the frame
 * before it.
 * <p>
 * A WebP is a RIFF file of chunks. An animated one opens with a {@code VP8X} chunk
 * giving the canvas, then an {@code ANMF} chunk per frame, holding that frame's
 * placement and its own image chunks: {@code VP8L} for a lossless frame, or
 * {@code VP8 } with an optional {@code ALPH} for a lossy one. Each frame is handed out as
 * a WebP file of its own, which any WebP reader can decode, so the animation itself,
 * which readers do not agree on, is put together here.
 *
 * @see <a href="https://developers.google.com/speed/webp/docs/riff_container">WebP container specification</a>
 */
final class WebpFile {

    /**
     * One frame: its rectangle on the canvas, how long it is shown, whether it is
     * blended over the canvas or replaces what it covers, whether its rectangle is
     * cleared once it has been shown, and the frame as a WebP file of its own.
     */
    record AnimationFrame(
            int x,
            int y,
            int width,
            int height,
            int durationMillis,
            boolean blend,
            boolean disposeToBackground,
            byte[] standalone) {}

    private static final int VP8X_ANIMATION = 0x02;
    private static final int VP8X_ALPHA = 0x10;
    private static final int ANMF_HEADER = 16;

    final int canvasWidth;
    final int canvasHeight;
    /** The frames of an animated WebP, in order; empty for a still one. */
    final List<AnimationFrame> frames;

    private WebpFile(int canvasWidth, int canvasHeight, List<AnimationFrame> frames) {
        this.canvasWidth = canvasWidth;
        this.canvasHeight = canvasHeight;
        this.frames = frames;
    }

    boolean animated() {
        return !frames.isEmpty();
    }

    static boolean isWebp(byte[] bytes) {
        return bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P';
    }

    /**
     * Reads the canvas and frames of {@code bytes}. A still WebP is described with its
     * canvas only, which a lossy or lossless one without the extended header leaves
     * out; it is read whole instead.
     *
     * @throws IOException when the file is not a well-formed WebP
     */
    static WebpFile parse(byte[] bytes) throws IOException {
        if (!isWebp(bytes)) {
            throw new IOException("not a WebP file");
        }
        int end = Math.min(bytes.length, 8 + (int) Math.min(Integer.MAX_VALUE - 8, uint32(bytes, 4)));
        int canvasWidth = 0;
        int canvasHeight = 0;
        boolean animated = false;
        List<AnimationFrame> frames = new ArrayList<>();
        for (int chunk = 12; chunk + 8 <= end; ) {
            String type = fourCc(bytes, chunk);
            int size = chunkSize(bytes, chunk, end);
            int payload = chunk + 8;
            switch (type) {
                case "VP8X" -> {
                    requireSize(size, 10, type);
                    animated = (bytes[payload] & VP8X_ANIMATION) != 0;
                    canvasWidth = 1 + uint24(bytes, payload + 4);
                    canvasHeight = 1 + uint24(bytes, payload + 7);
                }
                case "ANMF" -> {
                    if (animated) {
                        frames.add(frame(bytes, payload, size));
                    }
                }
                default -> {
                    // Colour profiles, metadata and the animation's loop count and
                    // background, which browsers draw as transparent too.
                }
            }
            chunk = payload + size + (size & 1);
        }
        if (animated && frames.isEmpty()) {
            throw new IOException("animated WebP has no frames");
        }
        return new WebpFile(canvasWidth, canvasHeight, List.copyOf(frames));
    }

    private static AnimationFrame frame(byte[] bytes, int payload, int size) throws IOException {
        requireSize(size, ANMF_HEADER + 8, "ANMF");
        int x = 2 * uint24(bytes, payload);
        int y = 2 * uint24(bytes, payload + 3);
        int width = 1 + uint24(bytes, payload + 6);
        int height = 1 + uint24(bytes, payload + 9);
        int duration = uint24(bytes, payload + 12);
        int flags = bytes[payload + 15];
        // Bit 1 set means "do not blend"; bit 0 set means "dispose to background".
        boolean blend = (flags & 0x02) == 0;
        boolean dispose = (flags & 0x01) != 0;

        int alpha = -1;
        int image = -1;
        int end = payload + size;
        for (int chunk = payload + ANMF_HEADER; chunk + 8 <= end; ) {
            String type = fourCc(bytes, chunk);
            int chunkSize = chunkSize(bytes, chunk, end);
            if (type.equals("ALPH")) {
                alpha = chunk;
            } else if (type.equals("VP8 ") || type.equals("VP8L")) {
                image = chunk;
            }
            chunk += 8 + chunkSize + (chunkSize & 1);
        }
        if (image < 0) {
            throw new IOException("WebP frame has no image");
        }
        boolean lossy = fourCc(bytes, image).equals("VP8 ");
        byte[] standalone = lossy && alpha >= 0
                ? standalone(width, height, chunk(bytes, alpha, end), chunk(bytes, image, end))
                : standalone(0, 0, chunk(bytes, image, end));
        return new AnimationFrame(x, y, width, height, duration, blend, dispose, standalone);
    }

    /**
     * A WebP file holding {@code chunks}, with an extended header declaring alpha when
     * a size is given: the only way a lossy image carries its separate alpha chunk.
     */
    static byte[] standalone(int width, int height, byte[]... chunks) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(new byte[] {'W', 'E', 'B', 'P'});
        if (width > 0 && height > 0) {
            body.writeBytes(new byte[] {'V', 'P', '8', 'X'});
            writeUint32(body, 10);
            body.writeBytes(new byte[] {(byte) VP8X_ALPHA, 0, 0, 0});
            writeUint24(body, width - 1);
            writeUint24(body, height - 1);
        }
        for (byte[] chunk : chunks) {
            body.writeBytes(chunk);
        }
        ByteArrayOutputStream file = new ByteArrayOutputStream(body.size() + 8);
        file.writeBytes(new byte[] {'R', 'I', 'F', 'F'});
        writeUint32(file, body.size());
        file.writeBytes(body.toByteArray());
        return file.toByteArray();
    }

    /** The chunk starting at {@code start}, header and padding included. */
    private static byte[] chunk(byte[] bytes, int start, int end) throws IOException {
        int size = chunkSize(bytes, start, end);
        int length = 8 + size + (size & 1);
        byte[] chunk = new byte[length];
        System.arraycopy(bytes, start, chunk, 0, Math.min(length, bytes.length - start));
        return chunk;
    }

    private static int chunkSize(byte[] bytes, int chunk, int end) throws IOException {
        long size = uint32(bytes, chunk + 4);
        if (size > end - chunk - 8) {
            throw new IOException("WebP chunk " + fourCc(bytes, chunk) + " runs past the file");
        }
        return (int) size;
    }

    private static void requireSize(int size, int minimum, String type) throws IOException {
        if (size < minimum) {
            throw new IOException("WebP chunk " + type + " is too short");
        }
    }

    private static String fourCc(byte[] bytes, int at) {
        return new String(bytes, at, 4, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static int uint24(byte[] bytes, int at) {
        return (bytes[at] & 0xFF) | (bytes[at + 1] & 0xFF) << 8 | (bytes[at + 2] & 0xFF) << 16;
    }

    private static long uint32(byte[] bytes, int at) {
        return (uint24(bytes, at) | (long) (bytes[at + 3] & 0xFF) << 24) & 0xFFFFFFFFL;
    }

    private static void writeUint24(ByteArrayOutputStream out, int value) {
        out.write(value & 0xFF);
        out.write(value >> 8 & 0xFF);
        out.write(value >> 16 & 0xFF);
    }

    private static void writeUint32(ByteArrayOutputStream out, int value) {
        writeUint24(out, value);
        out.write(value >>> 24 & 0xFF);
    }
}
