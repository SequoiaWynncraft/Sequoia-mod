package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CheapestQueueEffectTest {
    @Test
    void flashOnlyFadesAndEndsWithoutStrobing() {
        float previous = 1;
        for (long elapsed = 0; elapsed <= CheapestQueueEffect.DURATION_MS; elapsed += 10) {
            float opacity = CheapestQueueEffect.flashOpacity(elapsed, 1);
            assertTrue(opacity >= 0 && opacity <= previous);
            previous = opacity;
        }
        assertEquals(0, CheapestQueueEffect.flashOpacity(CheapestQueueEffect.FLASH_MS, 1));
        assertEquals(0, CheapestQueueEffect.textOpacity(CheapestQueueEffect.DURATION_MS));
    }

    @Test
    void minecraftScreenEffectsControlCanRemoveOrReduceFlash() {
        assertEquals(0, CheapestQueueEffect.flashOpacity(0, 0));
        assertEquals(0, CheapestQueueEffect.flashOpacity(0, -1));
        assertEquals(
                CheapestQueueEffect.flashOpacity(0, 1) / 2,
                CheapestQueueEffect.flashOpacity(0, 0.5f));
        assertTrue(CheapestQueueEffect.flashOpacity(0, 2) <= 1);
    }
}
