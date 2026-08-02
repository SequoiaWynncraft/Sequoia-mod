package com.seqwawa.seq.halcyon;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HalcyonRingRendererTest {
    @Test
    void usesReducedRingGeometry() {
        assertEquals(96, HalcyonRingRenderer.SEGMENTS);
    }
}
