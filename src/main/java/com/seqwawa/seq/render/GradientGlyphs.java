package com.seqwawa.seq.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.mixins.GlyphInstanceAccessor;
import com.seqwawa.seq.utils.RankGradientAnimation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.joml.Matrix4f;

/**
 * Draws rank decoration glyphs with their gradient applied corner by corner, both in
 * screens (chat) and in the world (nametags). Every other glyph passes through as it
 * is, after one colour lookup.
 */
public final class GradientGlyphs {

    private GradientGlyphs() {}

    /**
     * {@code glyph}, graded along its gradient when it is a decoration showing one, or
     * {@code glyph} itself.
     */
    public static TextRenderable.Styled wrap(TextRenderable.Styled glyph) {
        TextColor color = glyph.style().getColor();
        if (color == null || !(glyph instanceof GlyphInstanceAccessor instance)) {
            return glyph;
        }
        RankGradientAnimation.Shade shade = RankGradientAnimation.shade(color);
        if (shade == null) {
            return glyph;
        }
        int shadowQuads = instance.seq$hasShadow() ? (glyph.style().isBold() ? 2 : 1) : 0;
        return new ShadedGlyph(glyph, shade, instance.seq$x(), shadowQuads, instance.seq$shadowOffset());
    }

    /** A glyph drawn exactly as {@code glyph} is, except coloured corner by corner. */
    private record ShadedGlyph(
            TextRenderable.Styled glyph,
            RankGradientAnimation.Shade shade,
            float x,
            int shadowQuads,
            float shadowOffset)
            implements TextRenderable.Styled {

        @Override
        public void render(Matrix4f pose, VertexConsumer consumer, int packedLight, boolean flag) {
            glyph.render(
                    pose,
                    new GradedVertexConsumer(
                            consumer, shade, x, glyph.left() - x, glyph.right() - x, shadowQuads, shadowOffset),
                    packedLight,
                    flag);
        }

        @Override
        public RenderType renderType(Font.DisplayMode displayMode) {
            return glyph.renderType(displayMode);
        }

        @Override
        public GpuTextureView textureView() {
            return glyph.textureView();
        }

        @Override
        public RenderPipeline guiPipeline() {
            return glyph.guiPipeline();
        }

        @Override
        public float left() {
            return glyph.left();
        }

        @Override
        public float top() {
            return glyph.top();
        }

        @Override
        public float right() {
            return glyph.right();
        }

        @Override
        public float bottom() {
            return glyph.bottom();
        }

        @Override
        public Style style() {
            return glyph.style();
        }

        @Override
        public float activeLeft() {
            return glyph.activeLeft();
        }

        @Override
        public float activeTop() {
            return glyph.activeTop();
        }

        @Override
        public float activeRight() {
            return glyph.activeRight();
        }

        @Override
        public float activeBottom() {
            return glyph.activeBottom();
        }
    }
}
