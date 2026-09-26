package com.seqwawa.seq.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4fc;

/**
 * Colours a glyph quad by its edges instead of in one colour: the vertices left of
 * {@code middle} take {@code leftRgb}, the others {@code rightRgb}, and the GPU grades
 * the quad between them. Only the main quad is recoloured, recognised by the colour
 * it arrives in; a shadow keeps its own. Alpha always stays as the text pass set it.
 */
final class GradedVertexConsumer implements VertexConsumer {
    private static final int ALPHA_MASK = 0xFF000000;
    private static final int RGB_MASK = 0x00FFFFFF;

    private final VertexConsumer delegate;
    private final int mainColor;
    private final float middle;
    private final int leftRgb;
    private final int rightRgb;
    private boolean leftVertex;

    GradedVertexConsumer(VertexConsumer delegate, int mainColor, float middle, int leftRgb, int rightRgb) {
        this.delegate = delegate;
        this.mainColor = mainColor;
        this.middle = middle;
        this.leftRgb = leftRgb;
        this.rightRgb = rightRgb;
    }

    /** Glyphs position their corners in text space, which is where left and right are known. */
    @Override
    public VertexConsumer addVertex(Matrix4fc pose, float x, float y, float z) {
        leftVertex = x < middle;
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
        delegate.setColor(argb == mainColor
                ? (argb & ALPHA_MASK) | ((leftVertex ? leftRgb : rightRgb) & RGB_MASK)
                : argb);
        return this;
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
