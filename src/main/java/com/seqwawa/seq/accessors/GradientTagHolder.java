package com.seqwawa.seq.accessors;

/**
 * Lets a {@code TextColor} carry the rank decoration it was minted for, so a glyph drawn
 * in that colour finds its decoration without a registry lookup, and the decoration
 * goes away with the text that uses it. Added to {@code TextColor} by
 * {@code TextColorMixin}.
 */
public interface GradientTagHolder {
    Object seq$gradientTag();

    void seq$setGradientTag(Object tag);
}
