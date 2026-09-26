package com.seqwawa.seq.mixins;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Where a glyph is drawn and whether it casts a shadow, which is what colouring its
 * corners along a gradient needs. The glyph class is package-private, hence the
 * accessor.
 */
@Mixin(targets = "net.minecraft.client.gui.font.glyphs.BakedSheetGlyph$GlyphInstance")
public interface GlyphInstanceAccessor {
    /** The glyph's origin, in the same text space its corners are placed in. */
    @Accessor("x")
    float seq$x();

    @Accessor("shadowOffset")
    float seq$shadowOffset();

    /** Whether a shadow quad is drawn before the glyph itself. */
    @Invoker("hasShadow")
    boolean seq$hasShadow();
}
