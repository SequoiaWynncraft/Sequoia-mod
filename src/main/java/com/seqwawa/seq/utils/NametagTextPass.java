package com.seqwawa.seq.utils;

import net.minecraft.util.ARGB;

/**
 * Which copy of a nametag is currently being laid out, and what a Sequoia decoration
 * should be drawn in for it.
 * <p>
 * Minecraft draws every visible nametag twice: once with the depth test off so it
 * shows through terrain, at half alpha over a dark box, and once depth tested at full
 * alpha on top. Plain text survives that happily. A rank badge does not: it is built
 * by laying a letter glyph exactly on top of a background block, and in the world
 * those two quads land at one depth, where the depth test decides between them by
 * floating-point margins that shift as the camera turns. That is what makes the badge
 * flicker, drop letters and fill with solid blocks, while the name beside it, which
 * overlays nothing, stays perfectly still.
 * <p>
 * So the badge is drawn once, in the copy that does not test depth, and skipped in the
 * copy that does. Nothing is left to compare, the badge renders exactly as it does in
 * chat, and a sneaking player — whose tag has only the depth-tested copy — still gets
 * it. Names and everything else are untouched.
 * <p>
 * Text is laid out on the render thread; the nesting depths also keep the markers
 * right if another renderer lays text out inside an active pass.
 */
public final class NametagTextPass {

    private static final ThreadLocal<Integer> NAMETAG_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final ThreadLocal<Integer> SEE_THROUGH_DEPTH = ThreadLocal.withInitial(() -> 0);

    private NametagTextPass() {}

    /** Marks the stretch in which Minecraft draws the nametags it collected. */
    public static void beginNametags() {
        NAMETAG_DEPTH.set(NAMETAG_DEPTH.get() + 1);
    }

    public static void endNametags() {
        leave(NAMETAG_DEPTH);
    }

    /** Marks one see-through copy being laid out. */
    public static void beginSeeThrough() {
        SEE_THROUGH_DEPTH.set(SEE_THROUGH_DEPTH.get() + 1);
    }

    public static void endSeeThrough() {
        leave(SEE_THROUGH_DEPTH);
    }

    private static void leave(ThreadLocal<Integer> depth) {
        int remaining = depth.get() - 1;
        if (remaining <= 0) {
            depth.remove();
        } else {
            depth.set(remaining);
        }
    }

    public static boolean isDrawingNametags() {
        return NAMETAG_DEPTH.get() > 0;
    }

    public static boolean isSeeThrough() {
        return SEE_THROUGH_DEPTH.get() > 0;
    }

    /**
     * The colour to draw a rank badge glyph in, given the colour {@code passColor} the
     * pass would use.
     *
     * @param seeThrough whether this is the copy drawn without a depth test
     * @return the badge's colour at full alpha, or a transparent one where this copy
     *         would only redraw what the see-through copy has already put on screen
     */
    public static int badgeColor(int passColor, boolean seeThrough) {
        if (seeThrough) {
            return ARGB.opaque(passColor);
        }
        // A tag drawn only once comes through here at less than full alpha; one drawn
        // twice reaches this copy opaque, and the see-through copy has drawn it.
        return ARGB.alpha(passColor) == 255 ? passColor & 0x00FFFFFF : ARGB.opaque(passColor);
    }
}
