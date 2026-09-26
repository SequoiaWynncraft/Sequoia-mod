package com.seqwawa.seq.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The colour a glyph's main quad is drawn in, which tells it apart from its shadow's.
 * The glyph class is package-private, hence the accessor.
 */
@Mixin(targets = "net.minecraft.client.gui.font.glyphs.BakedSheetGlyph$GlyphInstance")
public interface GlyphInstanceAccessor {
    @Accessor("color")
    int seq$color();
}
