package com.seqwawa.seq.mixins;

import com.seqwawa.seq.accessors.SheetGlyphBounds;
import com.seqwawa.seq.utils.GradientPainter;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Tells {@link GradientPainter} where a glyph's shadow gives way to the glyph itself,
 * and where the glyph's corners go.
 * <p>
 * {@code renderChar} draws the shadow quads (plain, then bold) before the glyph's own
 * (plain, then bold), each through the private {@code render}, which writes the four
 * corners. This hooks the calls rather than {@code render} itself, whose body Sodium
 * replaces with a faster one.
 */
@Mixin(BakedSheetGlyph.class)
public abstract class BakedSheetGlyphMixin implements SheetGlyphBounds {
    private static final String RENDER_QUAD =
            "Lnet/minecraft/client/gui/font/glyphs/BakedSheetGlyph;"
                    + "render(ZFFFLorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;IZI)V";

    @Shadow
    @Final
    private float left;

    @Shadow
    @Final
    private float right;

    @Shadow
    @Final
    private float up;

    @Shadow
    @Final
    private float down;

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

    @ModifyArg(method = "renderChar", at = @At(value = "INVOKE", target = RENDER_QUAD, ordinal = 0), index = 8)
    private int seq$shadowQuads(int light) {
        GradientPainter.shadowQuads();
        return light;
    }

    @ModifyArg(method = "renderChar", at = @At(value = "INVOKE", target = RENDER_QUAD, ordinal = 2), index = 8)
    private int seq$mainQuads(int light) {
        GradientPainter.mainQuads();
        return light;
    }
}
