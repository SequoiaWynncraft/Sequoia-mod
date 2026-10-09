package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.seqwawa.seq.map.*;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class GatheringMapModeTest {
    @Test
    void territorySearchFitsInsideCurrentMapAreaAndDeactivationClearsFocus() {
        var settings = WorldMapSettings.getInstance();
        boolean expanded = settings.sidebarPanelExpanded(WorldMapSidebarPanel.MAP_AND_TERRITORY);
        String selected = settings.selectedTerritoryName();
        var centers = new ArrayList<double[]>();
        try {
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.MAP_AND_TERRITORY, true);
            var mode = new GatheringMapMode(settings, new GatheringMapMode.Actions() {
                public void center(double x, double z, double scale) { centers.add(new double[] {x, z, scale}); }
                public void startDragging() { fail(); }
                public void copy(String coordinates) { fail(); }
                public void renderPlayer(UiCanvas canvas, MapViewport viewport) { fail(); }
            });
            var territory = GuildTerritoryService.getInstance().index().territories().getFirst();
            float inputY = mode.sidebarLayout().territoryInputY();
            var viewport = new MapViewport(0, 0, 1, 230, 0, 520, 700);
            var frame = new WorldMapFrame(1000, 700, 20, inputY + 5, viewport, false);
            assertTrue(mode.click(frame));
            assertTrue(mode.charTyped(territory.name()));
            assertTrue(mode.keyPressed(GLFW.GLFW_KEY_ENTER));
            assertEquals(1, centers.size());
            assertEquals(territory.centerX(), centers.getFirst()[0]);
            assertEquals(territory.centerZ(), centers.getFirst()[1]);
            double expected = Math.max(MapViewport.MIN_PIXELS_PER_BLOCK, Math.min(1.25,
                    Math.min(520 / Math.max(1, territory.bounds().maxX() - territory.bounds().minX()),
                             700 / Math.max(1, territory.bounds().maxZ() - territory.bounds().minZ())) * .48));
            assertEquals(expected, centers.getFirst()[2], 1e-9);
            mode.click(frame);
            mode.deactivate();
            assertFalse(mode.charTyped("discarded"));
            assertFalse(mode.keyPressed(GLFW.GLFW_KEY_ENTER));
            mode.close();
        } finally {
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.MAP_AND_TERRITORY, expanded);
            settings.setSelectedTerritoryName(selected);
        }
    }
}
