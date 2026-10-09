package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import com.seqwawa.seq.map.MapViewport;
import org.junit.jupiter.api.Test;

class WorldMapFrameTest {
    @Test
    void tooltipsStayInsideMapWhenInsightsAreExpandedOrCollapsed() {
        assertEquals(512, frame(520, 740).tooltipX(230));
        assertEquals(734, frame(742, 960).tooltipX(230));
    }

    @Test
    void narrowMapKeepsTooltipAtItsLeftInset() {
        assertEquals(238, frame(100, 320).tooltipX(230));
    }

    private WorldMapFrame frame(float mapWidth, float mouseX) {
        return new WorldMapFrame(1000, 700, mouseX, 100,
                new MapViewport(0, 0, 1, 230, 0, mapWidth, 700), false);
    }
}
