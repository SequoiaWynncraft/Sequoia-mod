package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.accessors.SheetGlyphBounds;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/** Drives the painter the way Minecraft's glyph renderer calls it, corner by corner. */
class GradientPainterTest {

    private static final ColorRamp GRADIENT = ColorRamp.of(List.of(0x000000, 0xFFFFFF));
    private static final int ALPHA = 0x80000000;
    private static final int PLAIN = ALPHA | 0x123456;

    /** A six-pixel bitmap drawn from the origin, eight pixels tall from four above the baseline. */
    private static final Bounds SIX_WIDE = new Bounds(0f, 6f, -4f, 4f);
    private static final float BOLD_OFFSET = 1f;
    private static final float SHADOW_OFFSET = 1f;

    @Test
    void coloursEachCornerByWhereItFallsOnTheGradient() {
        TextColor block = block(GRADIENT);

        // 0.6 of the way along a black-to-white ramp is 0x99 in every channel.
        assertEquals(
                List.of(
                        ALPHA, ALPHA, ALPHA | 0x262626, ALPHA | 0x262626,
                        ALPHA, ALPHA, ALPHA | 0x999999, ALPHA | 0x999999),
                draw(Style.EMPTY.withColor(block)),
                "the shadow, darkened, then the block itself, each graded from its left edge to its right");
    }

    @Test
    void leavesEveryOtherGlyphAsItIs() {
        List<Integer> plain = List.of(PLAIN, PLAIN, PLAIN, PLAIN, PLAIN, PLAIN, PLAIN, PLAIN);

        assertEquals(plain, draw(Style.EMPTY.withColor(TextColor.fromRgb(0x55FFFF))), "another mod's colour");
        assertEquals(plain, draw(Style.EMPTY), "uncoloured text");
        assertEquals(
                plain,
                draw(Style.EMPTY.withColor(RankGradientAnimation.markDecorationColor(TextColor.fromRgb(0x1F2126)))),
                "a pill's lettering");
        assertEquals(plain, draw(Style.EMPTY.withColor(block(ColorRamp.of(0x4CB4FA)))), "a solid role");
    }

    @Test
    void keepsTheirOwnConsumerForEveryOtherGlyph() {
        // Sodium only writes glyphs straight into its buffers through the consumer it
        // handed out; ordinary text has to keep that one to stay on its fast path.
        VertexConsumer given = recorder(new ArrayList<>());

        assertSame(given, begin(given, Style.EMPTY.withColor(TextColor.fromRgb(0x55FFFF)), true));
        assertSame(given, begin(given, Style.EMPTY, true));
        assertSame(given, begin(given, Style.EMPTY.withColor(block(ColorRamp.of(0x4CB4FA))), true), "a solid role");
        assertNotSame(given, begin(given, Style.EMPTY.withColor(block(GRADIENT)), true));
    }

    @Test
    void drawsAGlyphFlatRatherThanBlendItThroughAShaderMarker() {
        // Green running from 240 down past Wynncraft's movement marker at 235: every
        // corner would be safe, but the pixels between them would be painted white.
        List<Integer> drawn = draw(Style.EMPTY.withColor(block(ColorRamp.of(List.of(0x10F010, 0x10D810)))));

        int shadow = drawn.getFirst() & 0xFFFFFF;
        int main = drawn.getLast() & 0xFFFFFF;
        assertEquals(List.of(shadow, shadow, shadow, shadow), rgb(drawn.subList(0, 4)), "a flat shadow");
        assertEquals(List.of(main, main, main, main), rgb(drawn.subList(4, 8)), "and a flat glyph");
        assertEquals(RankGradientAnimation.darkened(main), shadow);
        assertFalse(WynncraftTextShaderColor.crossesMarker(main, main), "in a colour the shader leaves alone");
        assertFalse(WynncraftTextShaderColor.crossesMarker(shadow, shadow));
    }

    @Test
    void checksTheBlendTheGpuDrawsRatherThanTheGradientItStandsFor() {
        // A stop in the middle of the glyph bends the gradient round the marker, but
        // the GPU blends straight from one edge's colour to the other's, through it.
        ColorRamp bent = ColorRamp.of(List.of(0x00FA00, 0x00FA96, 0x00DC96));
        TextColor block = RankGradientAnimation.axis(bent, bent, RankGradientAnimation.Target.RANK_BADGE, 6f)
                .colorAt(0f, 6f, null);
        assertFalse(WynncraftTextShaderColor.crossesMarker(0x00FA00, 0x00FA96), "safe up to the stop");
        assertFalse(WynncraftTextShaderColor.crossesMarker(0x00FA96, 0x00DC96), "and past it");

        List<Integer> main = rgb(draw(Style.EMPTY.withColor(block)).subList(4, 8));

        assertEquals(List.of(0x00FA96, 0x00FA96, 0x00FA96, 0x00FA96), main, "drawn flat, in its middle colour");
    }

    @Test
    void checksABoldGlyphsSecondCopyWhereItIsDrawn() {
        // The copy sits a pixel further along than the glyph's reported bounds, which
        // is where this gradient reaches the marker: green 231 at the glyph's own right
        // edge, 236 at the copy's, with the 232 to 235 of the marker in between.
        ColorRamp ramp = ColorRamp.of(List.of(0x40C810, 0x40FA10));
        TextColor block = RankGradientAnimation.axis(ramp, ramp, RankGradientAnimation.Target.RANK_BADGE, 10f)
                .colorAt(0f, 6f, null);

        List<Integer> drawn = rgb(draw(Style.EMPTY.withColor(block).withBold(true), SIX_WIDE, false));

        assertEquals(8, drawn.size(), "the glyph and its copy, with no shadow, as on a nametag");
        assertEquals(List.of(drawn.getFirst()), drawn.stream().distinct().toList(), "all drawn flat");
    }

    @Test
    void coloursSlantedCornersWhereItalicsPutThem() {
        // Four pixels above the baseline an italic edge leans two pixels right, and
        // four below it not at all.
        List<Integer> main = rgb(draw(Style.EMPTY.withColor(block(GRADIENT)).withItalic(true)).subList(4, 8));

        assertEquals(List.of(0x333333, 0x000000, 0x999999, 0xCCCCCC), main, "top left, bottom left, bottom right, top right");
    }

    @Test
    void leavesAShadowInItsOwnColourAsItIs() {
        List<Integer> drawn = draw(Style.EMPTY.withColor(block(GRADIENT)).withShadowColor(0xFF112233));

        assertEquals(List.of(PLAIN, PLAIN, PLAIN, PLAIN), drawn.subList(0, 4), "the shadow the style asked for");
        assertEquals(List.of(ALPHA, ALPHA, ALPHA | 0x999999, ALPHA | 0x999999), drawn.subList(4, 8), "a graded glyph");
    }

    @Test
    void keepsBlendingAGlyphWhoseColoursNeverMeetAMarker() {
        RankGradientAnimation.Shade clear = new RankGradientAnimation.Shade(
                ColorRamp.of(List.of(0x10F060, 0x10D860)), 0d, 10d, Double.NaN);

        assertEquals(
                -1,
                RankGradientAnimation.markerSafeFlatColor(clear, 0f, 6f, 0f, 0f, Float.NaN, true),
                "too much blue to be a marker");
    }

    @Test
    void picksUpASettingChangeOnTheNextGlyphDrawn() {
        // A still glyph keeps its worked-out colours from one frame to the next, but only
        // for as long as the settings they were worked out under.
        Style style = Style.EMPTY.withColor(RankGradientAnimation.axis(
                        GRADIENT, ColorRamp.of(0xFF00FF), RankGradientAnimation.Target.RANK_BADGE, 10f)
                .colorAt(0f, 6f, null));

        assertEquals(ALPHA | 0x999999, draw(style).getLast());
        withPerUserColors(false, () -> assertEquals(
                PLAIN, draw(style).getLast(), "the solid role palette is drawn flat, as resolved"));
        assertEquals(ALPHA | 0x999999, draw(style).getLast(), "and back again");
    }

    @Test
    void movesAnAnimatedGlyphOnceAFrame() {
        Style style = Style.EMPTY.withColor(block(GRADIENT));

        withAnimation(() -> {
            RankGradientAnimation.beginFrame(later(60_000L));
            List<Integer> first = draw(style);
            later(3000L);
            assertEquals(first, draw(style), "drawn again within the same frame, it has not moved");

            // 4.25 s at 20 px/s: a quarter of the way round a ten-pixel two-stop gradient.
            RankGradientAnimation.beginFrame(later(1250L));
            assertNotEquals(first, draw(style), "the next frame has");
        });
    }

    /** A six-pixel block at the start of a ten-pixel gradient. */
    private static TextColor block(ColorRamp ramp) {
        return RankGradientAnimation.axis(ramp, ramp, RankGradientAnimation.Target.RANK_BADGE, 10f)
                .colorAt(0f, 6f, null);
    }

    private static VertexConsumer begin(VertexConsumer consumer, Style style, boolean shadow) {
        return GradientPainter.begin(consumer, style, 0f, SIX_WIDE, BOLD_OFFSET, shadow, SHADOW_OFFSET);
    }

    /** {@link #draw(Style, Bounds, boolean)} for a six-pixel glyph with its shadow, as in chat. */
    private static List<Integer> draw(Style style) {
        return draw(style, SIX_WIDE, true);
    }

    /**
     * The colours written for a glyph at the origin, drawn as Minecraft's
     * {@code renderChar} draws one: its shadow quads one pixel down and right, then its
     * own, a bold glyph's each followed by a copy a pixel further on, and every corner
     * arriving in {@link #PLAIN}.
     */
    private static List<Integer> draw(Style style, Bounds bounds, boolean shadow) {
        List<Integer> colors = new ArrayList<>();
        VertexConsumer consumer = begin(recorder(colors), style, shadow);
        boolean bold = style.isBold();
        if (shadow) {
            GradientPainter.shadowQuads();
            quad(consumer, bounds, style, SHADOW_OFFSET, bold);
            if (bold) {
                quad(consumer, bounds, style, BOLD_OFFSET + SHADOW_OFFSET, true);
            }
        }
        GradientPainter.mainQuads();
        quad(consumer, bounds, style, 0f, bold);
        if (bold) {
            quad(consumer, bounds, style, BOLD_OFFSET, true);
        }
        return colors;
    }

    /** One quad, cornered as Minecraft's private {@code BakedSheetGlyph.render} corners it. */
    private static void quad(VertexConsumer consumer, Bounds bounds, Style style, float x, boolean bold) {
        float shearTop = style.isItalic() ? 1f - 0.25f * bounds.up() : 0f;
        float shearBottom = style.isItalic() ? 1f - 0.25f * bounds.down() : 0f;
        float thickness = bold ? 0.1f : 0f;
        float left = x + bounds.left();
        float right = x + bounds.right();
        Matrix4f pose = new Matrix4f();
        for (float corner : new float[] {
            left + shearTop - thickness, left + shearBottom - thickness,
            right + shearBottom + thickness, right + shearTop + thickness
        }) {
            consumer.addVertex(pose, corner, 0f, 0f).setColor(PLAIN).setUv(0f, 0f).setLight(0);
        }
    }

    /** Records the colour of every corner written to it, as a vertex buffer would take it. */
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

    private static List<Integer> rgb(List<Integer> argb) {
        return argb.stream().map(color -> color & 0xFFFFFF).toList();
    }

    /** A glyph bitmap's edges, as the glyph mixin reads them. */
    private record Bounds(float left, float right, float up, float down) implements SheetGlyphBounds {
        @Override
        public float seq$left() {
            return left;
        }

        @Override
        public float seq$right() {
            return right;
        }

        @Override
        public float seq$up() {
            return up;
        }

        @Override
        public float seq$down() {
            return down;
        }
    }

    /** Moments later than any the animation clock has seen, each later than the last. */
    private static long lastMoment = System.nanoTime() / 1_000_000L;

    private static synchronized long later(long millis) {
        lastMoment = Math.max(lastMoment, RankGradientAnimation.clockMillis()) + millis;
        return lastMoment;
    }

    private static void withAnimation(Runnable body) {
        Setting.BooleanSetting previous = SeqClient.animateRankGradientsSetting;
        try {
            SeqClient.animateRankGradientsSetting =
                    new Setting.BooleanSetting("animate_rank_gradients", "chat", true);
            body.run();
        } finally {
            SeqClient.animateRankGradientsSetting = previous;
        }
    }

    private static void withPerUserColors(boolean enabled, Runnable body) {
        Setting.BooleanSetting previous = SeqClient.usePerUserColorsSetting;
        try {
            SeqClient.usePerUserColorsSetting = new Setting.BooleanSetting("use_per_user_colors", "chat", enabled);
            body.run();
        } finally {
            SeqClient.usePerUserColorsSetting = previous;
        }
    }
}
