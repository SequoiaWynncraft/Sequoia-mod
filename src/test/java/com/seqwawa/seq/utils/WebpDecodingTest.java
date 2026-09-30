package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Animated WebP, as Discord's GIF picker sends it. Frames are put together here, so
 * that is tested with frames whose "image" is just a colour; decoding real WebP images
 * is tested on the tiny ones browsers probe their WebP support with.
 */
class WebpDecodingTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final int GREEN = 0xFF00FF00;
    private static final int CLEAR = 0x00000000;
    private static final long PLENTY = 64L * 1024 * 1024;

    private static final int NO_BLEND = 0x02;
    private static final int DISPOSE = 0x01;

    /** 1x1 lossless, lossy with alpha, and animated WebPs, from Modernizr's feature probes. */
    private static final byte[] LOSSLESS = Base64.getDecoder().decode(
            "UklGRhoAAABXRUJQVlA4TA0AAAAvAAAAEAcQERGIiP4HAA==");
    private static final byte[] LOSSY_WITH_ALPHA = Base64.getDecoder().decode(
            "UklGRkoAAABXRUJQVlA4WAoAAAAQAAAAAAAAAAAAQUxQSAwAAAARBxAR/Q9ERP8DAABWUDggGAAAABQBAJ0BKgEAAQAAAP4AAA3AAP7mtQAAAA==");
    private static final byte[] ANIMATED = Base64.getDecoder().decode(
            "UklGRlIAAABXRUJQVlA4WAoAAAASAAAAAAAAAAAAQU5JTQYAAAD/////AABBTk1GJgAAAAAAAAAAAAAAAAAAAGQAAABWUDhMDQAAAC8AAAAQBxAREYiI/gcA");

    @Test
    void placesEachFrameAndPlaysItForItsOwnDuration() throws IOException {
        byte[] webp = animated(4, 4,
                anmf(0, 0, 4, 4, 50, 0, colour(4, 4, RED)),
                anmf(2, 2, 2, 2, 0, 0, colour(2, 2, BLUE)));

        BridgeImageDecoder.Decoded decoded = decode(webp);

        assertEquals(List.of(50, 100), delays(decoded), "no duration plays as browsers play it");
        BufferedImage second = decoded.frames().get(1).image();
        assertEquals(RED, second.getRGB(0, 0));
        assertEquals(BLUE, second.getRGB(3, 3));
        assertEquals(4, decoded.sourceWidth());
    }

    @Test
    void aFrameThatDoesNotBlendReplacesWhatItCovers() throws IOException {
        byte[] replacing = animated(4, 4,
                anmf(0, 0, 4, 4, 50, 0, colour(4, 4, RED)),
                anmf(2, 2, 2, 2, 50, NO_BLEND, colour(2, 2, CLEAR)));
        byte[] blending = animated(4, 4,
                anmf(0, 0, 4, 4, 50, 0, colour(4, 4, RED)),
                anmf(2, 2, 2, 2, 50, 0, colour(2, 2, CLEAR)));

        assertEquals(CLEAR, decode(replacing).frames().get(1).image().getRGB(3, 3) >>> 24 << 24);
        assertEquals(RED, decode(blending).frames().get(1).image().getRGB(3, 3));
    }

    @Test
    void aFrameThatDisposesOfItselfIsClearedBeforeTheNext() throws IOException {
        byte[] webp = animated(4, 4,
                anmf(2, 2, 2, 2, 50, DISPOSE, colour(2, 2, BLUE)),
                anmf(0, 0, 2, 2, 50, 0, colour(2, 2, GREEN)));

        BufferedImage second = decode(webp).frames().get(1).image();

        assertEquals(GREEN, second.getRGB(0, 0));
        assertEquals(0, second.getRGB(3, 3) >>> 24, "the blue frame is gone");
    }

    @Test
    void decodesRealLossyFramesWithTheirAlpha() throws IOException {
        // The probe's own chunks, played as two frames of an animation.
        byte[] alpha = chunkOf(LOSSY_WITH_ALPHA, "ALPH");
        byte[] lossy = chunkOf(LOSSY_WITH_ALPHA, "VP8 ");
        byte[] lossless = chunkOf(LOSSLESS, "VP8L");
        byte[] webp = animated(1, 1,
                anmf(0, 0, 1, 1, 70, 0, alpha, lossy),
                anmf(0, 0, 1, 1, 30, NO_BLEND, lossless));

        BridgeImageDecoder.Decoded decoded = BridgeImageDecoder.decode(webp, 10, 10, PLENTY);

        assertEquals(List.of(70, 30), delays(decoded));
        assertEquals(1, decoded.frames().getFirst().image().getWidth());
    }

    @Test
    void decodesRealStillAndAnimatedWebp() throws IOException {
        assertEquals(1, BridgeImageDecoder.decode(LOSSLESS, 10, 10, PLENTY).frames().size());
        assertEquals(1, BridgeImageDecoder.decode(LOSSY_WITH_ALPHA, 10, 10, PLENTY).sourceWidth());
        assertEquals(List.of(100), delays(BridgeImageDecoder.decode(ANIMATED, 10, 10, PLENTY)));
    }

    @Test
    void wrapsALossyFrameAndItsAlphaInAnExtendedHeader() {
        byte[] alpha = chunk("ALPH", new byte[] {1, 2, 3});
        byte[] image = chunk("VP8 ", new byte[] {4, 5});

        byte[] standalone = WebpFile.standalone(3, 2, alpha, image);

        assertEquals("RIFF", ascii(standalone, 0));
        assertEquals(standalone.length - 8, uint32(standalone, 4), "the RIFF size covers the rest");
        assertEquals("WEBP", ascii(standalone, 8));
        assertEquals("VP8X", ascii(standalone, 12));
        assertEquals(0x10, standalone[20], "declaring alpha");
        assertArrayEquals(new byte[] {2, 0, 0, 1, 0, 0}, Arrays.copyOfRange(standalone, 24, 30), "3x2, less one");
        assertEquals("ALPH", ascii(standalone, 30));
        assertEquals("VP8 ", ascii(standalone, 30 + alpha.length));
    }

    @Test
    void refusesAFrameLargerThanTheRectangleItDeclares() {
        // Declared 2x2, carrying a 4x4 image: never decoded past what it claims.
        byte[] lying = animated(4, 4, anmf(0, 0, 2, 2, 50, 0, colour(4, 4, RED)));

        assertThrows(IOException.class, () -> decode(lying));
    }

    @Test
    void refusesACanvasTooLargeToHold() {
        byte[] huge = animated(5000, 5000, anmf(0, 0, 1, 1, 50, 0, colour(1, 1, RED)));

        assertThrows(IOException.class, () -> decode(huge));
    }

    @Test
    void decodesOnlyWhatStartsLikeAGifPngJpegOrWebp() {
        byte[] bitmap = "BM......................".getBytes(StandardCharsets.US_ASCII);

        assertFalse(BridgeImageDecoder.isSupportedPicture(bitmap));
        assertThrows(IOException.class, () -> BridgeImageDecoder.decode(bitmap, 10, 10, PLENTY));
        assertTrue(BridgeImageDecoder.isSupportedPicture(LOSSLESS));
    }

    @Test
    void refusesWhatIsNoWebp() {
        assertFalse(WebpFile.isWebp("GIF89a......".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(WebpFile.isWebp(LOSSLESS));
        byte[] truncated = Arrays.copyOf(ANIMATED, ANIMATED.length - 10);
        assertThrows(IOException.class, () -> WebpFile.parse(truncated));
    }

    // ── A made-up frame image: its size and one colour, read back by the fake reader ──

    private static byte[] colour(int width, int height, int argb) {
        return chunk("VP8L", new byte[] {
            (byte) width, (byte) height, (byte) (argb >>> 24), (byte) (argb >> 16), (byte) (argb >> 8), (byte) argb
        });
    }

    private static BridgeImageDecoder.Decoded decode(byte[] webp) throws IOException {
        return BridgeImageDecoder.decodeWebp(webp, 100, 100, PLENTY, WebpDecodingTest::readColour);
    }

    private static BufferedImage readColour(byte[] standalone, long maxPixels) throws IOException {
        if ((long) standalone[20] * standalone[21] > maxPixels) {
            throw new IOException("larger than the frame's rectangle");
        }
        byte[] payload = Arrays.copyOfRange(standalone, 20, 26);
        int argb = (payload[2] & 0xFF) << 24 | (payload[3] & 0xFF) << 16 | (payload[4] & 0xFF) << 8 | payload[5] & 0xFF;
        BufferedImage image = new BufferedImage(payload[0], payload[1], BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    // ── WebP containers ──

    private static byte[] animated(int width, int height, byte[]... frames) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes("WEBP".getBytes(StandardCharsets.US_ASCII));
        body.writeBytes(chunk("VP8X", concat(new byte[] {0x12, 0, 0, 0}, uint24(width - 1), uint24(height - 1))));
        body.writeBytes(chunk("ANIM", new byte[] {0, 0, 0, 0, 0, 0}));
        for (byte[] frame : frames) {
            body.writeBytes(frame);
        }
        return concat("RIFF".getBytes(StandardCharsets.US_ASCII), uint32Bytes(body.size()), body.toByteArray());
    }

    private static byte[] anmf(int x, int y, int width, int height, int duration, int flags, byte[]... chunks) {
        byte[] header = concat(
                uint24(x / 2), uint24(y / 2), uint24(width - 1), uint24(height - 1), uint24(duration),
                new byte[] {(byte) flags});
        return chunk("ANMF", concat(header, concat(chunks)));
    }

    private static byte[] chunk(String fourCc, byte[] payload) {
        byte[] padding = new byte[payload.length & 1];
        return concat(fourCc.getBytes(StandardCharsets.US_ASCII), uint32Bytes(payload.length), payload, padding);
    }

    /** The chunk {@code fourCc} of a still WebP, header and padding included. */
    private static byte[] chunkOf(byte[] webp, String fourCc) {
        for (int at = 12; at + 8 <= webp.length; ) {
            int size = (int) uint32(webp, at + 4);
            int length = 8 + size + (size & 1);
            if (ascii(webp, at).equals(fourCc)) {
                return Arrays.copyOfRange(webp, at, at + length);
            }
            at += length;
        }
        throw new IllegalArgumentException(fourCc);
    }

    private static List<Integer> delays(BridgeImageDecoder.Decoded decoded) {
        return decoded.frames().stream().map(BridgeImageDecoder.Frame::delayMillis).toList();
    }

    private static byte[] uint24(int value) {
        return new byte[] {(byte) value, (byte) (value >> 8), (byte) (value >> 16)};
    }

    private static byte[] uint32Bytes(int value) {
        return new byte[] {(byte) value, (byte) (value >> 8), (byte) (value >> 16), (byte) (value >>> 24)};
    }

    private static long uint32(byte[] bytes, int at) {
        return (bytes[at] & 0xFFL) | (bytes[at + 1] & 0xFFL) << 8 | (bytes[at + 2] & 0xFFL) << 16
                | (bytes[at + 3] & 0xFFL) << 24;
    }

    private static String ascii(byte[] bytes, int at) {
        return new String(bytes, at, 4, StandardCharsets.US_ASCII);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
