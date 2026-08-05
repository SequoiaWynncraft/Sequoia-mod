package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.junit.jupiter.api.Test;

class ChatGifDecoderTest {
    @Test
    void convertsGifToBoundedPngFrames() throws Exception {
        BufferedImage source = new BufferedImage(500, 250, BufferedImage.TYPE_INT_ARGB);
        source.setRGB(0, 0, Color.MAGENTA.getRGB());
        ByteArrayOutputStream gif = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(source, "gif", gif));

        ChatGifDecoder.DecodedGif decoded = ChatGifDecoder.decode(gif.toByteArray());

        assertEquals(1, decoded.pngFrames().size());
        BufferedImage preview = ImageIO.read(new java.io.ByteArrayInputStream(decoded.pngFrames().getFirst()));
        assertEquals(360, preview.getWidth());
        assertEquals(180, preview.getHeight());
        assertEquals(1, decoded.delaysMs().size());
    }

    @Test
    void preservesAnimatedFramesAndTheirDelays() throws Exception {
        BufferedImage red = solidFrame(Color.RED);
        BufferedImage blue = solidFrame(Color.BLUE);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);
            writer.prepareWriteSequence(null);
            writeFrame(writer, red, 5);
            writeFrame(writer, blue, 10);
            writer.endWriteSequence();
        } finally {
            writer.dispose();
        }

        ChatGifDecoder.DecodedGif decoded = ChatGifDecoder.decode(bytes.toByteArray());

        assertEquals(2, decoded.pngFrames().size());
        assertEquals(java.util.List.of(50, 100), decoded.delaysMs());
    }

    private static BufferedImage solidFrame(Color color) {
        BufferedImage frame = new BufferedImage(8, 6, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D graphics = frame.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, frame.getWidth(), frame.getHeight());
        } finally {
            graphics.dispose();
        }
        return frame;
    }

    private static void writeFrame(ImageWriter writer, BufferedImage image, int delayHundredths) throws Exception {
        IIOMetadata metadata = writer.getDefaultImageMetadata(
                ImageTypeSpecifier.createFromRenderedImage(image), writer.getDefaultWriteParam());
        String format = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
        IIOMetadataNode control = findOrCreate(root, "GraphicControlExtension");
        control.setAttribute("disposalMethod", "none");
        control.setAttribute("userInputFlag", "FALSE");
        control.setAttribute("transparentColorFlag", "FALSE");
        control.setAttribute("delayTime", Integer.toString(delayHundredths));
        control.setAttribute("transparentColorIndex", "0");
        metadata.setFromTree(format, root);
        writer.writeToSequence(new javax.imageio.IIOImage(image, null, metadata), writer.getDefaultWriteParam());
    }

    private static IIOMetadataNode findOrCreate(IIOMetadataNode root, String name) {
        for (int index = 0; index < root.getLength(); index++) {
            if (name.equals(root.item(index).getNodeName())) {
                return (IIOMetadataNode) root.item(index);
            }
        }
        IIOMetadataNode child = new IIOMetadataNode(name);
        root.appendChild(child);
        return child;
    }
}
