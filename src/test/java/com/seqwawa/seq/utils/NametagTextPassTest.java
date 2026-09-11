package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

class NametagTextPassTest {

    /** What vanilla draws the two copies of a visible nametag with. */
    private static final int SEE_THROUGH_PASS = 0x80FFFFFF;
    private static final int DEPTH_TESTED_PASS = 0xFFFFFFFF;

    private static final int RANK_GREEN = 0x2ECC71;

    /**
     * A badge lays a letter glyph on top of a background block, so it must be drawn in
     * the copy that ignores the depth test — where nothing can be compared — and only
     * there. Drawn in both, the two glyphs land at one depth and flicker against each
     * other as the camera moves.
     */
    @Test
    void aBadgeIsDrawnOnlyInTheCopyThatIgnoresDepth() {
        assertEquals(
                ARGB.color(255, RANK_GREEN),
                NametagTextPass.badgeColor(ARGB.color(ARGB.alpha(SEE_THROUGH_PASS), RANK_GREEN), true),
                "the see-through copy draws the badge, at full opacity");
        assertEquals(
                0,
                ARGB.alpha(NametagTextPass.badgeColor(ARGB.color(ARGB.alpha(DEPTH_TESTED_PASS), RANK_GREEN), false)),
                "the depth-tested copy leaves it to the one already drawn");
    }

    /**
     * A sneaking player's tag is submitted once, and depth tested, so that single copy
     * has to draw the badge.
     */
    @Test
    void aTagDrawnOnlyOnceStillCarriesItsBadge() {
        int single = ARGB.color(ARGB.alpha(SEE_THROUGH_PASS), RANK_GREEN);

        assertEquals(ARGB.color(255, RANK_GREEN), NametagTextPass.badgeColor(single, false));
    }

    @Test
    void theMarkersNest() {
        assertEquals(false, NametagTextPass.isDrawingNametags());
        NametagTextPass.beginNametags();
        NametagTextPass.beginSeeThrough();
        NametagTextPass.beginSeeThrough();
        NametagTextPass.endSeeThrough();
        assertEquals(true, NametagTextPass.isSeeThrough(), "an inner pass must not close the outer one");
        NametagTextPass.endSeeThrough();
        NametagTextPass.endNametags();
        assertEquals(false, NametagTextPass.isSeeThrough());
        assertEquals(false, NametagTextPass.isDrawingNametags());
    }
}
