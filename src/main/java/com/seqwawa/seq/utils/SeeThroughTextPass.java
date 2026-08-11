package com.seqwawa.seq.utils;

/**
 * Marks the stretch of rendering in which Minecraft draws a nametag through terrain.
 * <p>
 * Every visible nametag is drawn twice: once with the depth test off so it shows
 * through walls, at half alpha and over a dark box, and once depth tested at full
 * alpha on top. Styled text takes its alpha from whichever pass is drawing it, so a
 * decoration in a fixed colour comes out at two different opacities, and the seam
 * between them moves with the camera: the parts of the tag the depth test rejects
 * keep only the faint copy. On plain text that reads as the usual "seen through a
 * wall" dimming, but a rank pill is a solid block, so it reads as flickering.
 * <p>
 * Set by {@code FontMixin} around the see-through pass and read by {@code
 * FontPreparedTextBuilderMixin}, which draws Sequoia's own decorations at full alpha
 * in both passes so they look the same however the depth test falls. Text is laid out
 * on the render thread, so a plain field is enough.
 */
public final class SeeThroughTextPass {

    private static boolean active;

    private SeeThroughTextPass() {}

    public static void begin() {
        active = true;
    }

    public static void end() {
        active = false;
    }

    /** True while the see-through copy of a nametag is being laid out. */
    public static boolean isActive() {
        return active;
    }
}
