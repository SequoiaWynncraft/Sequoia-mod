package com.seqwawa.seq.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.utils.BridgeMedia;
import java.net.URI;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class BridgeImageRowsTest {

    private static final int LINE_HEIGHT = 9;

    @Test
    void drawsASmallPictureAtItsOwnSizeInScreenPixelsNeverStretched() {
        // Three screen pixels to a chat pixel: 300 pixels are 100 chat pixels.
        BridgeImageRows.Layout layout = BridgeImageRows.layout(300, 150, 3d, 318, 70, LINE_HEIGHT);

        assertEquals(new BridgeImageRows.Layout(100, 50, 6), layout, "fifty pixels and the padding take six lines");
    }

    @Test
    void shrinksALargePictureToTheLinesItMayTakeKeepingItsShape() {
        BridgeImageRows.Layout layout = BridgeImageRows.layout(1200, 800, 2d, 318, 70, LINE_HEIGHT);

        assertEquals(70, layout.height());
        assertEquals(105, layout.width());
        assertEquals(8, layout.rows());
    }

    @Test
    void shrinksAWidePictureToTheWidthOfChat() {
        BridgeImageRows.Layout layout = BridgeImageRows.layout(4000, 400, 1d, 200, 70, LINE_HEIGHT);

        assertEquals(200, layout.width());
        assertEquals(20, layout.height());
        assertEquals(3, layout.rows());
    }

    @Test
    void recognisesThePictureAMessageStandsFor() {
        BridgeMedia.Picture picture = new BridgeMedia.Picture(
                URI.create("https://media.discordapp.net/attachments/1/2/cat.gif"), BridgeMedia.Kind.ANIMATED, "cat.gif");

        Component message = BridgeImageRows.message(picture, Component.literal("| "));

        assertEquals("https://media.discordapp.net/attachments/1/2/cat.gif", BridgeImageRows.pictureKey(message));
        assertTrue(message.getString().endsWith("[GIF] cat.gif"), "a label wherever it is not laid out as the GIF");
        assertNull(BridgeImageRows.pictureKey(Component.literal("[GIF] cat.gif")));
    }

    @Test
    void opensOnlyThePictureItselfAndNeverAnUnsafeOne() {
        BridgeMedia.Picture safe = new BridgeMedia.Picture(
                URI.create("https://media.discordapp.net/attachments/1/2/cat.gif"), BridgeMedia.Kind.ANIMATED, "cat.gif");
        BridgeMedia.Picture unsafe = new BridgeMedia.Picture(
                URI.create("https://evil.example/cat.gif"), BridgeMedia.Kind.ANIMATED, "cat.gif");

        assertEquals(
                safe.fetch(),
                ((net.minecraft.network.chat.ClickEvent.OpenUrl) BridgeImageRows.linkStyle(safe).getClickEvent()).uri());
        assertNull(BridgeImageRows.linkStyle(unsafe).getClickEvent());
        assertNull(BridgeImageRows.pictureKey(BridgeImageRows.message(unsafe, Component.empty())),
                "and is never laid out as a picture");
    }
}
