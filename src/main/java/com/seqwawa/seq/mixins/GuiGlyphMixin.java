package com.seqwawa.seq.mixins;

import com.seqwawa.seq.render.GradientGlyphs;
import net.minecraft.client.gui.font.TextRenderable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Grades rank decoration glyphs corner by corner in screens, chat above all, as each
 * glyph of a prepared text is handed to the GUI renderer to be drawn.
 */
@Mixin(targets = "net.minecraft.client.gui.render.GuiRenderer$1")
public abstract class GuiGlyphMixin {
    @ModifyVariable(
            method = "acceptGlyph(Lnet/minecraft/client/gui/font/TextRenderable$Styled;)V",
            at = @At("HEAD"),
            argsOnly = true)
    private TextRenderable.Styled seq$gradeDecorationGlyph(TextRenderable.Styled glyph) {
        return GradientGlyphs.wrap(glyph);
    }
}
