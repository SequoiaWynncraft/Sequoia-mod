package com.seqwawa.seq.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.utils.RankGradientAnimation;
import com.seqwawa.seq.utils.WynncraftTextShaderColor;
import org.joml.Matrix4fc;

/**
 * Colours each corner of a glyph by where it falls on the glyph's gradient, so the GPU
 * grades every quad from edge to edge and the gradient runs on across glyphs without a
 * seam.
 * <p>
 * A glyph emits its shadow quads first, then its own; the shadow takes the gradient at
 * the pixel it shadows, darkened as Minecraft darkens a shadow. Alpha always stays as
 * the text pass set it.
 * <p>
 * Wynncraft's text shader reads colours pixel by pixel, and paints white any pixel whose
 * blended colour lands on one of its markers. A glyph whose blend, or its shadow's,
 * would pass through one is drawn in its middle colour instead, which is always safe.
 * Across one glyph a gradient barely changes, so that flat glyph is next to invisible.
 */
final class GradedVertexConsumer implements VertexConsumer {
    private static final int ALPHA_MASK = 0xFF000000;
    private static final int VERTICES_PER_QUAD = 4;
    private static final int GRADED = -1;

    private final VertexConsumer delegate;
    private final RankGradientAnimation.Shade shade;
    private final float glyphX;
    private final int shadowQuads;
    private final float shadowOffset;
    /** The one colour the glyph is drawn in when blending it would cross a marker, or {@link #GRADED}. */
    private final int flatRgb;
    private int vertices;
    private float x;
    /** A quad's two left and two right corners share an x; each is only sampled once. */
    private float sampledX = Float.NaN;
    private int sampledRgb;

    /**
     * @param left  where the glyph's quads start, relative to its origin
     * @param right where they end, shadow included
     */
    GradedVertexConsumer(
            VertexConsumer delegate,
            RankGradientAnimation.Shade shade,
            float glyphX,
            float left,
            float right,
            int shadowQuads,
            float shadowOffset) {
        this.delegate = delegate;
        this.shade = shade;
        this.glyphX = glyphX;
        this.shadowQuads = shadowQuads;
        this.shadowOffset = shadowOffset;
        this.flatRgb = flatColor(shade, left, right);
    }

    /**
     * The glyph's middle colour when blending across it would cross a marker, sampled
     * at both edges and the middle so a ramp stop inside the glyph is not missed.
     */
    static int flatColor(RankGradientAnimation.Shade shade, float left, float right) {
        int start = shade.rgbAt(left);
        int middle = shade.rgbAt((left + right) / 2f);
        int end = shade.rgbAt(right);
        boolean crosses = WynncraftTextShaderColor.crossesMarker(start, middle)
                || WynncraftTextShaderColor.crossesMarker(middle, end)
                || WynncraftTextShaderColor.crossesMarker(darkened(start), darkened(middle))
                || WynncraftTextShaderColor.crossesMarker(darkened(middle), darkened(end));
        return crosses ? middle : GRADED;
    }

    /** Glyphs place their corners in text space, which is where the gradient is measured. */
    @Override
    public VertexConsumer addVertex(Matrix4fc pose, float x, float y, float z) {
        this.x = x;
        vertices++;
        delegate.addVertex(pose, x, y, z);
        return this;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        vertices++;
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        boolean shadow = (vertices - 1) / VERTICES_PER_QUAD < shadowQuads;
        int rgb = shadow ? darkened(rgbAt(x - shadowOffset)) : rgbAt(x);
        delegate.setColor((argb & ALPHA_MASK) | rgb);
        return this;
    }

    private int rgbAt(float vertexX) {
        if (flatRgb != GRADED) {
            return flatRgb;
        }
        if (vertexX != sampledX) {
            sampledX = vertexX;
            sampledRgb = shade.rgbAt(vertexX - glyphX);
        }
        return sampledRgb;
    }

    /** A shadow's colour, as Minecraft works it out: each channel at a quarter. */
    static int darkened(int rgb) {
        return (((rgb >> 16) & 0xFF) / 4) << 16 | (((rgb >> 8) & 0xFF) / 4) << 8 | (rgb & 0xFF) / 4;
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        delegate.setColor(red, green, blue, alpha);
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
