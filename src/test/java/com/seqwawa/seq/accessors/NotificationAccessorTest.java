package com.seqwawa.seq.accessors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.ComponentTextEditor;
import com.seqwawa.seq.utils.RankGradientAnimation;
import com.seqwawa.seq.utils.WynnPillGlyphs;
import java.util.ArrayList;
import java.util.List;
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
    void placesEveryBlockOnTheGradientAtThePixelItIsDrawnAt() {
        NotificationAccessor.GradientPill pill = gradientPill(GRADIENT);

        List<Drawn> blocks = layout(pill).stream()
                .filter(drawn -> drawn.glyph() == WynnPillGlyphs.BACKGROUND)
                .toList();
        assertEquals(LABEL.length(), blocks.size(), "one block per character");
        for (Drawn block : blocks) {
            RankGradientAnimation.Shade shade = RankGradientAnimation.shade(block.color());
            assertNotNull(shade, "a gradient block is graded");
            assertEquals(block.x() - pill.axisStart(), shade.origin(), 1e-6, "block at " + block.x());
        }
        Drawn last = blocks.getLast();
        assertEquals(
                last.x() - pill.axisStart() + NotificationAccessor.PILL_BG_WIDTH,
                pill.axis().length(),
                1e-6,
                "the gradient ends where the last block does");
    }

    @Test
    void reportsHowFarThePillAdvances() {
        NotificationAccessor.GradientPill pill = gradientPill(GRADIENT);
        List<Drawn> drawn = layout(pill);

        assertEquals(advanceOf(pill), pill.advance());
        assertEquals(NotificationAccessor.PILL_CORNER_ADVANCE, pill.axisStart(), "the gradient starts after the corner");
        assertEquals(WynnPillGlyphs.CORNER_LEFT, drawn.getFirst().glyph());
    }

    @Test
    void measuresTheGapToANameDrawnOneSpaceAfterIt() {
        NotificationAccessor.GradientPill pill = gradientPill(GRADIENT);

        float nameStart = pill.advance() + 4;
        assertEquals(nameStart - pill.axisStart() - pill.axis().length(), pill.gapTo(4), 1e-6);
    }

    @Test
    void stillReadsAsItsLabelSpaceIncluded() {
        // Nametags recognise a tag they already rewrote by reading its pill back.
        String text = gradientPill(GRADIENT).component().getString();
        List<WynnPillGlyphs.Pill> pills = WynnPillGlyphs.findPills(text);

        assertEquals(1, pills.size());
        assertEquals(new WynnPillGlyphs.Pill(0, text.length(), "upper strategist"), pills.getFirst());
    }

    @Test
    void drawsInWynncraftsFontOnly() {
        ComponentTextEditor.flatten(gradientPill(GRADIENT).component()).forEach(fragment ->
                assertEquals(FontDescription.DEFAULT, fragment.style().getFont(), "font of " + fragment.text()));
    }

    @Test
    void drawsASolidRoleFlat() {
        NotificationAccessor.GradientPill pill = gradientPill(SOLID);

        layout(pill).stream()
                .filter(drawn -> drawn.glyph() == WynnPillGlyphs.BACKGROUND)
                .forEach(block -> {
                    assertEquals(0x4CB4FA, block.color().getValue());
                    assertNull(RankGradientAnimation.shade(block.color()), "one colour has nothing to grade");
                });
    }

    @Test
    void keepsTheLabelOutOfTheGradient() {
        layout(gradientPill(GRADIENT)).stream()
                .filter(drawn -> WynnPillGlyphs.decodeGlyph(drawn.glyph()) != 0)
                .forEach(letter -> assertEquals(LABEL_COLOR, letter.color()));
    }

    private static NotificationAccessor.GradientPill gradientPill(ColorRamp ramp) {
        return NotificationAccessor.gradientPill(LABEL, ramp, ramp, LABEL_COLOR, null, null);
    }

    /** One glyph as it is drawn: where, what, and in which colour. */
    private record Drawn(int x, char glyph, TextColor color) {}

    /** Walks the pill glyph by glyph the way Minecraft lays text out. */
    private static List<Drawn> layout(NotificationAccessor.GradientPill pill) {
        int x = 0;
        List<Drawn> drawn = new ArrayList<>();
        for (ComponentTextEditor.Fragment fragment : ComponentTextEditor.flatten(pill.component())) {
            for (char glyph : fragment.text().toCharArray()) {
                if (glyph != WynnPillGlyphs.TEXT_OFFSET && glyph != WynnPillGlyphs.SEPARATOR) {
                    drawn.add(new Drawn(x, glyph, fragment.style().getColor()));
                }
                x += advance(glyph);
            }
        }
        return drawn;
    }

    private static int advanceOf(NotificationAccessor.GradientPill pill) {
        int x = 0;
        for (char glyph : pill.component().getString().toCharArray()) {
            x += advance(glyph);
        }
        return x;
    }

    /**
     * Advances as Wynncraft's font defines them: its {@code chat/banner} blocks and
     * corners, and its {@code chat/five} letters. A bitmap glyph advances one pixel past
     * its width.
     */
    private static int advance(char glyph) {
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
