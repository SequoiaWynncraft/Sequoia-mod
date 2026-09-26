package com.seqwawa.seq.utils;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.accessors.SheetGlyphBounds;
import net.minecraft.network.chat.Style;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fc;

/**
 * Colours the corners of rank decoration glyphs by where they fall on their gradient,
 * as each glyph is written, in screens and in the world alike.
 * <p>
 * A decoration glyph is written through one shared recolouring consumer, which sees
 * each corner's position and replaces the colour it is written in. Every other glyph
 * keeps the consumer it was given, so ordinary text is untouched and costs one field
 * read.
 * <p>
 * Sodium writes glyphs straight into its own buffers, skipping the vanilla code that
 * places each corner. It only does so for a consumer it can write into directly, which
 * the recolouring one is not, so a decoration glyph always takes the vanilla path.
 * <p>
 * Minecraft draws a glyph as its shadow quads, then its own; the glyph mixins report
 * where one gives way to the other. A shadow its style gives a colour of its own keeps
 * that colour, as it would without a gradient.
 * <p>
 * Glyphs are only ever drawn on the render thread, one at a time, so the glyph being
 * drawn is kept in plain fields and nothing is allocated per glyph.
 */
public final class GradientPainter {
    private static final int ALPHA_MASK = 0xFF000000;

    private static final Recolouring RECOLOURING = new Recolouring();

    /** The decoration glyph being drawn, or {@code null} while any other glyph is. */
    private static RankGradientAnimation.Glyph glyph;
    private static float glyphX;
    private static float shadowOffset;
    private static boolean gradedShadow;
    private static boolean shadow;

    private GradientPainter() {}

    /**
     * A glyph drawn in {@code style} at {@code x}, from a bitmap with {@code bounds}, is
     * about to be written to {@code consumer}. Returns what to write it to instead: the
     * recolouring consumer for a decoration showing its gradient, or {@code consumer}
     * itself.
     */
    public static VertexConsumer begin(
            VertexConsumer consumer,
            Style style,
            float x,
            SheetGlyphBounds bounds,
            float boldOffset,
            boolean hasShadow,
            float shadowOffset) {
        RankGradientAnimation.Glyph candidate = RankGradientAnimation.glyphOf(style.getColor());
        if (candidate == null) {
            glyph = null;
            return consumer;
        }
        // A shadow given a colour of its own is drawn in it, as Minecraft draws it.
        boolean gradedShadow = hasShadow && style.getShadowColor() == null;
        boolean italic = style.isItalic();
        if (!candidate.prepare(
                bounds.seq$left(),
                bounds.seq$right(),
                italic ? shear(bounds.seq$up()) : 0f,
                italic ? shear(bounds.seq$down()) : 0f,
                style.isBold() ? boldOffset : Float.NaN,
                gradedShadow)) {
            glyph = null;
            return consumer;
        }
        glyph = candidate;
        glyphX = x;
        GradientPainter.shadowOffset = shadowOffset;
        GradientPainter.gradedShadow = gradedShadow;
        shadow = false;
        RECOLOURING.delegate = consumer;
        return RECOLOURING;
    }

    /** How far italics push the edge {@code y} pixels from the baseline to the right, as Minecraft does. */
    private static float shear(float y) {
        return 1f - 0.25f * y;
    }

    /** The quads that follow are the glyph's shadow. */
    public static void shadowQuads() {
        shadow = true;
    }

    /** The quads that follow are the glyph itself. */
    public static void mainQuads() {
        shadow = false;
    }

    /** The colour a corner at {@code vertexX} takes: its place on the gradient, or {@code argb} as it was. */
    static int color(float vertexX, int argb) {
        RankGradientAnimation.Glyph current = glyph;
        if (current == null || shadow && !gradedShadow) {
            return argb;
        }
        // A shadow takes the colour of the pixel it shadows, darkened as Minecraft darkens one.
        int rgb = shadow
                ? RankGradientAnimation.darkened(current.rgbAt(vertexX - shadowOffset - glyphX))
                : current.rgbAt(vertexX - glyphX);
        return (argb & ALPHA_MASK) | rgb;
    }

    /** Passes a decoration glyph's corners on, each in its colour on the gradient. */
    private static final class Recolouring implements VertexConsumer {
        private VertexConsumer delegate;
        private float vertexX;

        /** Glyphs place their corners in text space, which is where the gradient is measured. */
        @Override
        public VertexConsumer addVertex(Matrix4fc pose, float x, float y, float z) {
            vertexX = x;
            delegate.addVertex(pose, x, y, z);
            return this;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            delegate.setColor(color(vertexX, argb));
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(color(vertexX, ARGB.color(alpha, red, green, blue)));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            delegate.setLineWidth(width);
            return this;
        }
    }
}
