package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class SeqBadgeNametagRenderSupportTest {
    @Test
    void keepsWynntilsAndLowerBadgeOffsetsDistinct() {
        assertEquals(25f, SeqBadgeNametagRenderSupport.WYNNTILS_DEFAULT_BADGE_Y_OFFSET);
        assertEquals(10f, SeqBadgeNametagRenderSupport.LOWER_BADGE_Y_OFFSET);
        assertNotEquals(
                SeqBadgeNametagRenderSupport.WYNNTILS_DEFAULT_BADGE_Y_OFFSET,
                SeqBadgeNametagRenderSupport.LOWER_BADGE_Y_OFFSET);
    }
}
