package com.seqwawa.seq.accessors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.ComponentTextEditor;
import com.seqwawa.seq.utils.WynnPillGlyphs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.TextColor;
import org.junit.jupiter.api.Test;

class NotificationAccessorTest {

    private static final ColorRamp GRADIENT = ColorRamp.of(List.of(0x000000, 0xFFFFFF));
    private static final ColorRamp SOLID = ColorRamp.of(0x4CB4FA);
    private static final TextColor LABEL_COLOR = TextColor.fromRgb(0x1F2126);

    /** Has a space, so a bare block is covered as well as blocks under letters. */
    private static final String LABEL = "Upper Strategist";

    @Test
    void aPixelColumnPillAdvancesExactlyAsFarAsTheBlockPill() {
        assertEquals(layout(blockPill()).advance(), layout(columnPill()).advance());
    }

    @Test
    void itsColumnsFillExactlyThePixelsTheBlocksDid() {
        assertEquals(layout(blockPill()).backgroundPixels(), layout(columnPill()).backgroundPixels());
    }

    @Test
    void itsLettersAndCornersLandWhereTheBlockPillPutsThem() {
        assertEquals(layout(blockPill()).otherGlyphs(), layout(columnPill()).otherGlyphs());
    }

    @Test
    void itStillReadsAsItsLabelSpaceIncluded() {
        // Nametags recognise a tag they already rewrote by reading its pill back.
        String text = columnPill().getString();
        List<WynnPillGlyphs.Pill> pills = WynnPillGlyphs.findPills(text);

        assertEquals(1, pills.size(), "the columns must not split the pill: " + pills);
        assertEquals(new WynnPillGlyphs.Pill(0, text.length(), "upper strategist"), pills.getFirst());
        assertEquals(WynnPillGlyphs.findPills(blockPill().getString()).getFirst().label(), pills.getFirst().label());
    }

    @Test
    void onlyTheColumnsUseTheModFont() {
        for (ComponentTextEditor.Fragment fragment : ComponentTextEditor.flatten(columnPill())) {
            FontDescription expected = fragment.text().contains(NotificationAccessor.PILL_COLUMN)
                    ? NotificationAccessor.PILL_COLUMN_FONT
                    : FontDescription.DEFAULT;
            assertEquals(expected, fragment.style().getFont(), "font of " + fragment.text());
        }
    }

    @Test
    void aSolidRoleKeepsWynncraftsBlocks() {
        String text = NotificationAccessor.smoothWynnPill(LABEL, SOLID, SOLID, LABEL_COLOR, null, null)
                .getString();

        assertTrue(text.indexOf(WynnPillGlyphs.BACKGROUND) >= 0);
        assertFalse(text.contains(NotificationAccessor.PILL_COLUMN));
    }

    @Test
    void aGradientOnEitherPaletteGetsColumns() {
        // Per-user colours switch live between the two palettes, so a solid display
        // palette over a gradient role palette still has to be able to show the ramp.
        String text = NotificationAccessor.smoothWynnPill(LABEL, SOLID, GRADIENT, LABEL_COLOR, null, null)
                .getString();

        assertTrue(text.contains(NotificationAccessor.PILL_COLUMN));
        assertFalse(text.indexOf(WynnPillGlyphs.BACKGROUND) >= 0);
    }

    private static Component blockPill() {
        return NotificationAccessor.wynnPill(LABEL, GRADIENT, GRADIENT, LABEL_COLOR, null, null);
    }

    private static Component columnPill() {
        return NotificationAccessor.smoothWynnPill(LABEL, GRADIENT, GRADIENT, LABEL_COLOR, null, null);
    }

    /**
     * Where a pill draws, walked glyph by glyph the way Minecraft lays text out.
     *
     * @param advance          how far the pill moves the text after it
     * @param backgroundPixels every pixel column a background block or column fills
     * @param otherGlyphs      each corner and letter, as {@code x:codepoint}
     */
    private record Layout(int advance, Set<Integer> backgroundPixels, List<String> otherGlyphs) {}

    private static Layout layout(Component pill) {
        int x = 0;
        Set<Integer> backgroundPixels = new TreeSet<>();
        List<String> otherGlyphs = new ArrayList<>();
        for (ComponentTextEditor.Fragment fragment : ComponentTextEditor.flatten(pill)) {
            FontDescription font = fragment.style().getFont();
            for (char glyph : fragment.text().toCharArray()) {
                if (NotificationAccessor.PILL_COLUMN_FONT.equals(font)) {
                    if (glyph == NotificationAccessor.PILL_COLUMN.charAt(0)) {
                        backgroundPixels.add(x);
                    }
                } else if (glyph == WynnPillGlyphs.BACKGROUND) {
                    for (int pixel = 0; pixel < NotificationAccessor.PILL_BG_WIDTH; pixel++) {
                        backgroundPixels.add(x + pixel);
                    }
                } else if (glyph != WynnPillGlyphs.TEXT_OFFSET && glyph != WynnPillGlyphs.SEPARATOR) {
                    otherGlyphs.add(x + ":" + Integer.toHexString(glyph));
                }
                x += advance(font, glyph);
            }
        }
        return new Layout(x, backgroundPixels, otherGlyphs);
    }

    /**
     * Advances as the fonts define them: Wynncraft's {@code chat/banner} blocks and
     * corners and {@code chat/five} letters, and the mod's {@code rank_pill} font. A
     * bitmap glyph advances one pixel past its width.
     */
    private static int advance(FontDescription font, char glyph) {
        if (NotificationAccessor.PILL_COLUMN_FONT.equals(font)) {
            if (glyph == NotificationAccessor.PILL_COLUMN.charAt(0)) {
                return 2;
            }
            if (glyph == NotificationAccessor.PILL_COLUMN_STEP_BACK.charAt(0)) {
                return -1;
            }
            throw new AssertionError("unexpected column font glyph " + Integer.toHexString(glyph));
        }
        if (WynnPillGlyphs.decodeGlyph(glyph) != 0) {
            return 6;
        }
        return switch (glyph) {
            case WynnPillGlyphs.BACKGROUND -> 7;
            case WynnPillGlyphs.CORNER_LEFT, WynnPillGlyphs.CORNER_RIGHT -> 3;
            case WynnPillGlyphs.TEXT_OFFSET -> -7;
            case WynnPillGlyphs.SEPARATOR -> -1;
            default -> throw new AssertionError("unexpected pill glyph " + Integer.toHexString(glyph));
        };
    }
}
