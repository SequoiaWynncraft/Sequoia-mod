package com.seqwawa.seq.ui.unlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** The pixel art itself: what sits in a tile must sit in its middle, with even space all round. */
class UnlockArtTest {

    private static final List<String> TILE_ICONS = List.of(
            "unlock_war", "unlock_raid", "unlock_icon_notg", "unlock_icon_nol", "unlock_icon_tcc",
            "unlock_icon_tna", "unlock_icon_twp");

    private static BufferedImage load(String name) throws IOException {
        try (InputStream in = UnlockArtTest.class.getResourceAsStream("/assets/seq/textures/unlock/" + name + ".png")) {
            assertNotNull(in, name);
            return ImageIO.read(in);
        }
    }

    private static boolean visible(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) != 0;
    }

    @Test
    void tileIconsAre16PixelsWithTheSameGapOnOppositeSides() throws IOException {
        for (String name : TILE_ICONS) {
            BufferedImage image = load(name);
            assertEquals(16, image.getWidth(), name);
            assertEquals(16, image.getHeight(), name);
            int left = 16, right = -1, top = 16, bottom = -1;
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    if (!visible(image, x, y)) continue;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                    top = Math.min(top, y);
                    bottom = Math.max(bottom, y);
                }
            }
            assertEquals(left, 15 - right, name + " is off centre left to right");
            assertEquals(top, 15 - bottom, name + " is off centre top to bottom");
        }
    }

    @Test
    void everyMedalIsAMirrorImageWithItsDiscCentredOnTheSixteenthPixel() throws IOException {
        for (String style : List.of("unlock_medal_", "unlock_medal_ribbon_", "unlock_medal_ring_")) {
            for (String tier : List.of("bronze", "silver", "gold", "platinum", "diamond", "obsidian", "mythril", "level", "flash")) {
                BufferedImage medal = load(style + tier);
                assertEquals(32, medal.getWidth(), style + tier);
                for (int y = 0; y < medal.getHeight(); y++) {
                    for (int x = 0; x < 16; x++) {
                        assertEquals(visible(medal, x, y), visible(medal, 31 - x, y), style + tier + " row " + y);
                    }
                }
            }
        }
    }

    @Test
    void theIvyKeepsCloseToTheMedalAndOffTheEmblem() throws IOException {
        // The medal sits 6 pixels into the 44-pixel square, its middle on the line at 22; the emblem fills
        // about 10 pixels round that.
        int margin = UnlockOverlay.IVY_INSET - UnlockOverlay.MedalStyle.IVY.reach();
        BufferedImage ivy = load("unlock_ivy");
        assertEquals(44, ivy.getWidth());
        assertEquals(44, ivy.getHeight());
        for (int y = 0; y < 44; y++) {
            for (int x = 0; x < 44; x++) {
                if (!visible(ivy, x, y)) continue;
                boolean inside = x >= margin && x < 44 - margin && y >= margin && y < 44 - margin;
                assertTrue(inside, "the ivy reaches past its square at " + x + ", " + y);
                assertTrue(Math.hypot(x + 0.5 - 22, y + 0.5 - 22) >= 10, "the ivy covers the emblem at " + x + ", " + y);
            }
        }
    }

    @Test
    void theTowerAndThePortalAreMirrorImages() throws IOException {
        for (String name : List.of("unlock_war", "unlock_raid")) {
            BufferedImage image = load(name);
            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 8; x++) {
                    assertEquals(visible(image, x, y), visible(image, 15 - x, y), name + " row " + y + ", column " + x);
                }
            }
        }
    }
}
