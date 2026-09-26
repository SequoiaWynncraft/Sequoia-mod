package com.seqwawa.seq.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.mixins.GlyphInstanceAccessor;
import com.seqwawa.seq.utils.ColorRamp;
import com.seqwawa.seq.utils.RankGradientAnimation;
import com.seqwawa.seq.utils.WynncraftTextShaderColor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class GradientGlyphsTest {

    private static final ColorRamp GRADIENT = ColorRamp.of(List.of(0x000000, 0xFFFFFF));
    private static final int ALPHA = 0x80000000;

    @Test
    void coloursEachCornerByWhereItFallsOnTheGradient() {
        RankGradientAnimation.Pinned<TextColor> block = RankGradientAnimation.pin(() -> RankGradientAnimation.axis(
                        GRADIENT, GRADIENT, RankGradientAnimation.Target.RANK_BADGE, 10f)
                .colorAt(0f, 6f, null));
        try {
            List<Integer> drawn = new ArrayList<>();
            TextRenderable.Styled glyph = drawableBlock(Style.EMPTY.withColor(block.value()), true);

            GradientGlyphs.wrap(glyph).render(new Matrix4f(), recorder(drawn), 0, false);

            // 0.6 of the way along a black-to-white ramp is 0x99 in every channel.
            assertEquals(
                    List.of(
                            ALPHA, ALPHA, ALPHA | 0x262626, ALPHA | 0x262626,
                            ALPHA, ALPHA, ALPHA | 0x999999, ALPHA | 0x999999),
                    drawn,
                    "the shadow, darkened, then the block itself, each graded from its left edge to its right");
        } finally {
            RankGradientAnimation.release(block.colors());
        }
    }

    @Test
    void drawsAGlyphFlatRatherThanBlendItThroughAShaderMarker() {
        // Green running from 240 down past Wynncraft's movement marker at 235: every
        // corner would be safe, but the pixels between them would be painted white.
        ColorRamp throughMarker = ColorRamp.of(List.of(0x10F010, 0x10D810));
        RankGradientAnimation.Pinned<TextColor> block = RankGradientAnimation.pin(() -> RankGradientAnimation.axis(
                        throughMarker, throughMarker, RankGradientAnimation.Target.RANK_BADGE, 10f)
                .colorAt(0f, 6f, null));
        try {
            List<Integer> drawn = new ArrayList<>();
            GradientGlyphs.wrap(drawableBlock(Style.EMPTY.withColor(block.value()), true))
                    .render(new Matrix4f(), recorder(drawn), 0, false);

            int shadow = drawn.getFirst() & 0xFFFFFF;
            int main = drawn.getLast() & 0xFFFFFF;
            assertEquals(List.of(shadow, shadow, shadow, shadow), rgb(drawn.subList(0, 4)), "a flat shadow");
            assertEquals(List.of(main, main, main, main), rgb(drawn.subList(4, 8)), "and a flat glyph");
            assertEquals(GradedVertexConsumer.darkened(main), shadow);
            assertFalse(WynncraftTextShaderColor.crossesMarker(main, main), "in a colour the shader leaves alone");
            assertFalse(WynncraftTextShaderColor.crossesMarker(shadow, shadow));
        } finally {
            RankGradientAnimation.release(block.colors());
        }
    }

    @Test
    void keepsBlendingAGlyphWhoseColoursNeverMeetAMarker() {
        ColorRamp clear = ColorRamp.of(List.of(0x10F060, 0x10D860));

        assertEquals(
                -1,
                GradedVertexConsumer.flatColor(
                        new RankGradientAnimation.Shade(clear, 0d, 10d, Double.NaN), 0f, 7f),
                "too much blue for green 235 to be a marker");
    }

    @Test
    void leavesAFlatGlyphAsItIs() {
        ColorRamp solid = ColorRamp.of(0x4CB4FA);
        RankGradientAnimation.Pinned<TextColor> block = RankGradientAnimation.pin(() -> RankGradientAnimation.axis(
                        solid, solid, RankGradientAnimation.Target.RANK_BADGE, 10f)
                .colorAt(0f, 6f, null));
        try {
            TextRenderable.Styled glyph = drawableBlock(Style.EMPTY.withColor(block.value()), true);

            assertSame(glyph, GradientGlyphs.wrap(glyph));
        } finally {
            RankGradientAnimation.release(block.colors());
        }
    }

    @Test
    void leavesEveryOtherGlyphAsItIs() {
        TextRenderable.Styled plain = drawableBlock(Style.EMPTY.withColor(TextColor.fromRgb(0x55FFFF)), true);
        TextRenderable.Styled uncoloured = drawableBlock(Style.EMPTY, true);

        assertSame(plain, GradientGlyphs.wrap(plain));
        assertSame(uncoloured, GradientGlyphs.wrap(uncoloured));
    }

    @Test
    void darkensAShadowAsMinecraftDoes() {
        assertEquals(0x3F2600, GradedVertexConsumer.darkened(0xFF9903));
    }

    /**
     * A six-pixel block glyph at the origin, drawn as {@code BakedSheetGlyph} draws one:
     * its shadow quad one pixel down and right, then its own, each corner by corner and
     * left edge first.
     */
    private static TextRenderable.Styled drawableBlock(Style style, boolean shadow) {
        return (TextRenderable.Styled) Proxy.newProxyInstance(
                TextRenderable.Styled.class.getClassLoader(),
                new Class<?>[] {TextRenderable.Styled.class, GlyphInstanceAccessor.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "style" -> style;
                    case "seq$x" -> 0f;
                    case "seq$shadowOffset" -> 1f;
                    case "seq$hasShadow" -> shadow;
                    case "left" -> 0f;
                    case "right" -> shadow ? 7f : 6f;
                    case "render" -> {
                        VertexConsumer consumer = (VertexConsumer) args[1];
                        if (shadow) {
                            quad(consumer, (Matrix4f) args[0], 1f);
                        }
                        quad(consumer, (Matrix4f) args[0], 0f);
                        yield null;
                    }
                    default -> throw new AssertionError("unexpected call: " + method.getName());
                });
    }

    private static void quad(VertexConsumer consumer, Matrix4f pose, float left) {
        consumer.addVertex(pose, left, 0f, 0f).setColor(ALPHA | 0x123456);
        consumer.addVertex(pose, left, 7f, 0f).setColor(ALPHA | 0x123456);
        consumer.addVertex(pose, left + 6f, 7f, 0f).setColor(ALPHA | 0x123456);
        consumer.addVertex(pose, left + 6f, 0f, 0f).setColor(ALPHA | 0x123456);
    }

    private static List<Integer> rgb(List<Integer> argb) {
        return argb.stream().map(color -> color & 0xFFFFFF).toList();
    }

    /** Records the colour of every vertex drawn through it. */
    private static VertexConsumer recorder(List<Integer> colors) {
        return (VertexConsumer) Proxy.newProxyInstance(
                VertexConsumer.class.getClassLoader(),
                new Class<?>[] {VertexConsumer.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("setColor") && args.length == 1) {
                        colors.add((Integer) args[0]);
                    }
                    return proxy;
                });
    }
}
