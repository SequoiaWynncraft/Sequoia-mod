package com.seqwawa.seq.mixins;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.seqwawa.seq.accessors.SheetGlyphBounds;
import com.seqwawa.seq.utils.GradientPainter;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Tells {@link GradientPainter} which glyph is about to be drawn, in screens and in the
 * world alike, and writes a decoration glyph through its recolouring consumer.
 */
@Mixin(targets = "net.minecraft.client.gui.font.glyphs.BakedSheetGlyph$GlyphInstance")
public abstract class GlyphInstanceMixin {
    @Shadow
    @Final
    float x;

    @Shadow
    @Final
    private BakedSheetGlyph glyph;

    @Shadow
    @Final
    Style style;

    @Shadow
    @Final
    private float boldOffset;

    @Shadow
    @Final
    float shadowOffset;

    @Shadow
    abstract boolean hasShadow();

    @ModifyVariable(
            method = "render(Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/vertex/VertexConsumer;IZ)V",
            at = @At("HEAD"),
            argsOnly = true)
    private VertexConsumer seq$beginGradient(VertexConsumer consumer) {
        return GradientPainter.begin(
                consumer, style, x, (SheetGlyphBounds) (Object) glyph, boldOffset, hasShadow(), shadowOffset);
    }
}
