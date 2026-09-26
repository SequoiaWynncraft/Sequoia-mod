package com.seqwawa.seq.accessors;

/**
 * A glyph bitmap's edges, in font pixels from the point it is drawn at: where Minecraft
 * places its corners before italics shear them or bold thickens them.
 */
public interface SheetGlyphBounds {
    float seq$left();

    float seq$right();

    float seq$up();

    float seq$down();
}
