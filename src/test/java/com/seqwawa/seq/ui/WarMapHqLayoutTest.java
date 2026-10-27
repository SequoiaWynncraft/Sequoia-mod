package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.MapBounds;
import org.junit.jupiter.api.Test;

class WarMapHqLayoutTest {
    private static final WarMapGeometry.WarMapLayout MAP = new WarMapGeometry.WarMapLayout(0, 0, 300, 300, 308, 100);

    @Test
    void crownClearsTheActualHoveredNameIncludingItsViewportClamping() {
        for (int centerY : new int[] {20, 150, 292}) {
            GuildTerritory territory = GuildTerritory.fromCorners("Bloom Trail", 120, centerY - 10, 180, centerY + 10);
            var label = WarMapGeometry.territoryNameBounds(territory, new MapBounds(0, 0, 300, 300), 0, 0, 1, MAP);
            var icon = WarMapGeometry.hqIconBounds(150, centerY, MAP, label);
            assertContainedAndSeparate(icon, label, MAP);
            if (centerY == 20) assertTrue(icon.y() >= label.y() + label.height() + 4);
            else assertTrue(icon.y() + icon.height() <= label.y() - 4);
        }
    }

    @Test
    void crownClearsTheFittedQueueName() {
        var territory = GuildTerritory.fromCorners("Bloom Trail", 120, 140, 180, 160);
        var fitted = WarQueueMapOverlay.warQueueLabelBounds(
                territory, new MapBounds(0, 0, 300, 300), 0, 0, 1, 25, 12);
        var label = new WarMapGeometry.WarMapButtonBounds(fitted.x(), fitted.y(), fitted.width(), fitted.height());
        assertContainedAndSeparate(WarMapGeometry.hqIconBounds(150, 150, MAP, label), label, MAP);
    }

    @Test
    void narrowMapKeepsCrownInsideAndOmitsItWhenThereIsNoRoom() {
        var narrow = new WarMapGeometry.WarMapLayout(10, 20, 40, 200, 58, 100);
        var label = new WarMapGeometry.WarMapButtonBounds(16, 110, 28, 20);
        assertContainedAndSeparate(WarMapGeometry.hqIconBounds(30, 120, narrow, label), label, narrow);
        var tiny = new WarMapGeometry.WarMapLayout(10, 20, 20, 50, 38, 100);
        assertNull(WarMapGeometry.hqIconBounds(20, 40, tiny, label));
    }

    @Test
    void offscreenHeadquartersDoesNotBecomeAnEdgeMarker() {
        assertNull(WarMapGeometry.hqIconBounds(-100, 150, MAP, null));
        assertNull(WarMapGeometry.hqIconBounds(150, 400, MAP, null));
    }

    private static void assertContainedAndSeparate(
            WarMapGeometry.WarMapButtonBounds icon, WarMapGeometry.WarMapButtonBounds label,
            WarMapGeometry.WarMapLayout map) {
        assertNotNull(icon);
        assertTrue(icon.x() >= map.mapX() && icon.x() + icon.width() <= map.mapX() + map.mapWidth());
        assertTrue(icon.y() >= map.mapY() + 34 && icon.y() + icon.height() <= map.mapY() + map.mapHeight());
        assertTrue(icon.x() + icon.width() <= label.x() || icon.x() >= label.x() + label.width()
                || icon.y() + icon.height() <= label.y() - 4 || icon.y() >= label.y() + label.height() + 4);
    }
}
