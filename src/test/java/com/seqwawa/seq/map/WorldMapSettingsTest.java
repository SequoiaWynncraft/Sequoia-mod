package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldMapSettingsTest {
    @Test
    void retainsSidebarStateForTheClientSession() {
        WorldMapSettings settings = WorldMapSettings.getInstance();
        boolean originalInsights = settings.insightsSidebarOpen();
        boolean originalPanel = settings.sidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS);
        try {
            settings.setInsightsSidebarOpen(false);
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS, false);

            assertFalse(settings.insightsSidebarOpen());
            assertFalse(settings.sidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS));

            settings.setInsightsSidebarOpen(true);
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS, true);
            assertTrue(settings.insightsSidebarOpen());
            assertTrue(settings.sidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS));
        } finally {
            settings.setInsightsSidebarOpen(originalInsights);
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS, originalPanel);
        }
    }
}
