package com.seqwawa.seq.render;

import com.seqwawa.seq.utils.RankGradientAnimation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.EmptyArea;
import net.minecraft.client.gui.font.TextRenderable;
import org.joml.Matrix4f;

/**
 * Separates a pill's letters from its background in depth, without changing the
 * font's render mode. Both layers therefore obey the same occlusion as the name.
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
        if (RankGradientAnimation.isBadgeLabelColor(glyph.style().getColor())) {
            foreground.acceptGlyph(glyph);
            return;
        }
        ordinary.acceptGlyph(glyph);
    }

    @Override
    public void acceptEffect(TextRenderable effect) {
        ordinary.acceptEffect(effect);
    }

    @Override
    public void acceptEmptyArea(EmptyArea area) {
        ordinary.acceptEmptyArea(area);
    }
}
