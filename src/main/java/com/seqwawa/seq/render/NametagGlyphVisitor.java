package com.seqwawa.seq.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.mixins.GlyphInstanceAccessor;
import com.seqwawa.seq.utils.RankGradientAnimation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.joml.Matrix4f;

/**
 * Separates a pill's letters from its background in depth, without changing the
 * font's render mode. Both layers therefore obey the same occlusion as the name.
 * <p>
 * A gradient pill's pixel columns are also graded from edge to edge rather than drawn
 * in one colour each. Up close a nametag blows one pixel of the font up to many on
 * screen, and flat columns then read as a row of bands instead of a gradient.
 */
public final class NametagGlyphVisitor implements Font.GlyphVisitor {
    // Font uses the same text-space distance between ordinary glyphs and shadows.
    private static final float LABEL_DEPTH = 0.03f;

    private final Font.GlyphVisitor ordinary;
    private final Font.GlyphVisitor foreground;

    public NametagGlyphVisitor(Font.GlyphVisitor ordinary, Font.GlyphVisitor foreground) {
        this.ordinary = ordinary;
        this.foreground = foreground;
    }

    public static Matrix4f foregroundPose(Matrix4f pose) {
        return new Matrix4f(pose).translate(0f, 0f, LABEL_DEPTH);
    }

    @Override
    public void acceptGlyph(TextRenderable.Styled glyph) {
        TextColor color = glyph.style().getColor();
        if (RankGradientAnimation.isBadgeLabelColor(color)) {
            foreground.acceptGlyph(glyph);
            return;
        }
        ordinary.acceptGlyph(graded(glyph, color));
    }

    @Override
    public void acceptEffect(TextRenderable effect) {
        ordinary.acceptEffect(effect);
    }

    @Override
    public void acceptEmptyArea(EmptyArea area) {
        ordinary.acceptEmptyArea(area);
    }

    /** {@code glyph} graded between its edge colours when it is a pill column, else itself. */
    static TextRenderable.Styled graded(TextRenderable.Styled glyph, TextColor color) {
        if (!(glyph instanceof GlyphInstanceAccessor instance)) {
            return glyph;
        }
        RankGradientAnimation.Span span = RankGradientAnimation.animateSpan(color);
        return span == null ? glyph : new GradedGlyph(glyph, instance.seq$color(), span);
    }

    /** A glyph drawn exactly as {@code glyph} is, except graded across between its edge colours. */
    private record GradedGlyph(TextRenderable.Styled glyph, int mainColor, RankGradientAnimation.Span span)
            implements TextRenderable.Styled {

        @Override
        public void render(Matrix4f pose, VertexConsumer consumer, int packedLight, boolean flag) {
            float middle = (glyph.left() + glyph.right()) / 2f;
            glyph.render(
                    pose,
                    new GradedVertexConsumer(consumer, mainColor, middle, span.leftRgb(), span.rightRgb()),
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
