package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

class BridgeImageDecoderTest {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;
    private static final long PLENTY = 64L * 1024 * 1024;

    @Test
    void compositesEachGifFrameOverTheOneBeforeAtItsOwnDelay() throws IOException {
        byte[] gif = gif(List.of(
                new GifFrame(filled(4, 4, RED), 0, 0, 5, "none"),
                new GifFrame(filled(2, 2, BLUE), 2, 2, 0, "none"),
                new GifFrame(filled(1, 1, BLUE), 0, 0, 7, "none")));

        BridgeImageDecoder.Decoded decoded = BridgeImageDecoder.decode(gif, 100, 100, PLENTY);

        assertEquals(3, decoded.frames().size());
        assertEquals(List.of(50, 100, 70), delays(decoded), "a delay of nothing plays as browsers play it");
        BufferedImage second = decoded.frames().get(1).image();
        assertEquals(RED, second.getRGB(0, 0), "the first frame shows through");
        assertEquals(BLUE, second.getRGB(3, 3), "under the patch the second frame adds");
        assertEquals(BLUE, decoded.frames().get(2).image().getRGB(3, 3), "which stays for the third");
    }

    @Test
    void thinsAGifThatWouldNotFitOnlyOnceItIsDrawnAsSmallAsAllowed() throws IOException {
        List<GifFrame> frames = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            frames.add(new GifFrame(filled(10, 10, index % 2 == 0 ? RED : BLUE), 0, 0, 5, "none"));
        }

        // Room for two frames at half size.
        BridgeImageDecoder.Decoded decoded = BridgeImageDecoder.decode(gif(frames), 10, 10, 4L * 5 * 5 * 2);

        assertEquals(2, decoded.frames().size());
        assertEquals(5, decoded.frames().getFirst().image().getWidth());
        assertEquals(List.of(250, 250), delays(decoded), "keeping the animation's length");
        assertEquals(10, decoded.sourceWidth());
    }

    @Test
    void keepsEveryFrameByDrawingThemSmallerWhenThatIsEnough() {
        long allAtFullSize = 4L * 100 * 100 * 100;

        assertEquals(new BridgeImageDecoder.Plan(100, 100, 1, 100),
                BridgeImageDecoder.Plan.of(100, 100, 100, 100, 100, allAtFullSize));
        assertEquals(new BridgeImageDecoder.Plan(50, 50, 1, 100),
                BridgeImageDecoder.Plan.of(100, 100, 100, 100, 100, allAtFullSize / 4));
        BridgeImageDecoder.Plan thinned = BridgeImageDecoder.Plan.of(100, 100, 100, 100, 100, allAtFullSize / 16);
        assertEquals(50, thinned.width(), "never drawn smaller than half");
        assertEquals(4, thinned.stride());
    }

    @Test
    void shrinksAStillPictureToFitAndNeverEnlargesOne() throws IOException {
        BridgeImageDecoder.Decoded large = BridgeImageDecoder.decode(png(filled(400, 200, RED)), 100, 100, PLENTY);
        BridgeImageDecoder.Decoded small = BridgeImageDecoder.decode(png(filled(40, 20, RED)), 100, 100, PLENTY);

        assertFalse(large.animated());
        assertEquals(100, large.frames().getFirst().image().getWidth());
        assertEquals(50, large.frames().getFirst().image().getHeight());
        assertEquals(400, large.sourceWidth());
        assertEquals(40, small.frames().getFirst().image().getWidth());
        assertEquals(RED, large.frames().getFirst().image().getRGB(50, 25));
    }

    @Test
    void turnsAPhotoTakenOnItsSideUpright() throws IOException {
        // Red on the left, blue on the right, as the sensor saw it; held turned, so
        // the left is the top.
        BufferedImage sensor = filled(32, 16, BLUE);
        Graphics2D graphics = sensor.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 16, 16);
        graphics.dispose();
        byte[] photo = withExifOrientation(jpeg(sensor), 6);

        BridgeImageDecoder.Decoded decoded = BridgeImageDecoder.decode(photo, 100, 100, PLENTY);

        BufferedImage upright = decoded.frames().getFirst().image();
        assertEquals(16, decoded.sourceWidth());
        assertEquals(32, decoded.sourceHeight());
        assertEquals(16, upright.getWidth());
        assertTrue(isMostly(upright.getRGB(8, 8), RED), "the top is red");
        assertTrue(isMostly(upright.getRGB(8, 24), BLUE), "the bottom blue");
    }

    @Test
    void refusesBytesThatAreNoPicture() {
        assertThrows(IOException.class, () -> BridgeImageDecoder.decode(new byte[] {1, 2, 3, 4, 5, 6}, 10, 10, PLENTY));
    }

    @Test
    void turnsEachWayExifDescribes() {
        // A 2x1 picture: A then B.
        BufferedImage picture = new BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB);
        picture.setRGB(0, 0, RED);
        picture.setRGB(1, 0, BLUE);

        BufferedImage clockwise = ExifOrientation.apply(picture, 6);
        BufferedImage anticlockwise = ExifOrientation.apply(picture, 8);
        BufferedImage mirrored = ExifOrientation.apply(picture, 2);

        assertEquals(List.of(RED, BLUE), List.of(clockwise.getRGB(0, 0), clockwise.getRGB(0, 1)));
        assertEquals(List.of(BLUE, RED), List.of(anticlockwise.getRGB(0, 0), anticlockwise.getRGB(0, 1)));
        assertEquals(List.of(BLUE, RED), List.of(mirrored.getRGB(0, 0), mirrored.getRGB(1, 0)));
        assertEquals(ExifOrientation.UPRIGHT, ExifOrientation.of(new byte[] {1, 2, 3}));
    }

    private record GifFrame(BufferedImage image, int left, int top, int delayCentis, String disposal) {}

    private static List<Integer> delays(BridgeImageDecoder.Decoded decoded) {
        return decoded.frames().stream().map(BridgeImageDecoder.Frame::delayMillis).toList();
    }

    private static BufferedImage filled(int width, int height, int argb) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(argb, true));
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    private static boolean isMostly(int argb, int expected) {
        int red = argb >> 16 & 0xFF;
        int blue = argb & 0xFF;
        return expected == RED ? red > 180 && blue < 80 : blue > 180 && red < 80;
    }

    private static byte[] png(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static byte[] jpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    /**
     * {@code jpeg} with an EXIF block recording {@code orientation}, placed after its JFIF
     * block, which Java's reader wants straight after the start marker.
     */
    private static byte[] withExifOrientation(byte[] jpeg, int orientation) {
        byte[] exif = {
            (byte) 0xFF, (byte) 0xE1, 0, 34,
            'E', 'x', 'i', 'f', 0, 0,
            'M', 'M', 0, 42, 0, 0, 0, 8,
            0, 1,
            0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0,
            0, 0, 0, 0
        };
        int at = 2;
        if ((jpeg[2] & 0xFF) == 0xFF && (jpeg[3] & 0xFF) == 0xE0) {
            at += 2 + ((jpeg[4] & 0xFF) << 8 | (jpeg[5] & 0xFF));
        }
        byte[] tagged = new byte[jpeg.length + exif.length];
        System.arraycopy(jpeg, 0, tagged, 0, at);
        System.arraycopy(exif, 0, tagged, at, exif.length);
        System.arraycopy(jpeg, at, tagged, at + exif.length, jpeg.length - at);
        return tagged;
    }

    /** An animated GIF of {@code frames}, the first of which sets its size. */
    private static byte[] gif(List<GifFrame> frames) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.prepareWriteSequence(null);
            for (GifFrame frame : frames) {
                IIOMetadata metadata = writer.getDefaultImageMetadata(
                        ImageTypeSpecifier.createFromRenderedImage(frame.image()), null);
                String format = metadata.getNativeMetadataFormatName();
                IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
                IIOMetadataNode control = childOrNew(root, "GraphicControlExtension");
                control.setAttribute("disposalMethod", frame.disposal());
                control.setAttribute("userInputFlag", "FALSE");
                control.setAttribute("transparentColorFlag", "FALSE");
                control.setAttribute("delayTime", String.valueOf(frame.delayCentis()));
                control.setAttribute("transparentColorIndex", "0");
                IIOMetadataNode descriptor = childOrNew(root, "ImageDescriptor");
                descriptor.setAttribute("imageLeftPosition", String.valueOf(frame.left()));
                descriptor.setAttribute("imageTopPosition", String.valueOf(frame.top()));
                descriptor.setAttribute("imageWidth", String.valueOf(frame.image().getWidth()));
                descriptor.setAttribute("imageHeight", String.valueOf(frame.image().getHeight()));
                descriptor.setAttribute("interlaceFlag", "FALSE");
                metadata.setFromTree(format, root);
                writer.writeToSequence(new IIOImage(frame.image(), null, metadata), null);
            }
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static IIOMetadataNode childOrNew(IIOMetadataNode parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (name.equals(node.getNodeName())) {
                return (IIOMetadataNode) node;
            }
        }
        IIOMetadataNode child = new IIOMetadataNode(name);
        parent.appendChild(child);
        return child;
    }
}
