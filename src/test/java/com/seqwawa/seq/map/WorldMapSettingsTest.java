package com.seqwawa.seq.map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WorldMapSettingsTest {
    @Test
    void retainsSidebarStateForTheClientSession() {
        WorldMapSettings settings = WorldMapSettings.getInstance();
        boolean originalInsights = settings.insightsSidebarOpen();
        boolean originalPanel = settings.sidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS);
        boolean originalTotemPanel = settings.sidebarPanelExpanded(WorldMapSidebarPanel.TOTEM_SOLVER);
        boolean originalTotemSolver = settings.gatheringTotemSolverEnabled();
        GatheringTotemSearchTarget originalTarget = settings.gatheringTotemSearchTarget();
        boolean originalHulls = settings.showGatheringTotemHulls();
        boolean originalPlayerRadius = settings.showGatheringTotemPlayerRadius();
        boolean originalNodeReach = settings.showGatheringTotemNodeReach();
        boolean originalCoveredNodes = settings.showGatheringTotemCoveredNodes();
        boolean originalOtherSpots = settings.showOtherOptimalGatheringTotems();
        boolean originalIngredientRadii = settings.showIngredientWaypointRadii();
        boolean originalProximityColors = settings.colorIngredientWaypointRadiiByProximity();
        MapDisplayMode originalDisplayMode = settings.displayMode();
        try {
            settings.setInsightsSidebarOpen(false);
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS, false);
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.TOTEM_SOLVER, false);
            settings.setGatheringTotemSolverEnabled(true);
            settings.setGatheringTotemSearchTarget(GatheringTotemSearchTarget.SELECTED_CLUSTER);
            settings.setShowGatheringTotemHulls(false);
            settings.setShowGatheringTotemPlayerRadius(false);
            settings.setShowGatheringTotemNodeReach(false);
            settings.setShowGatheringTotemCoveredNodes(false);
            settings.setShowOtherOptimalGatheringTotems(false);
            settings.setShowIngredientWaypointRadii(true);
            settings.setColorIngredientWaypointRadiiByProximity(false);
            settings.setDisplayMode(MapDisplayMode.INGREDIENTS);

            assertFalse(settings.insightsSidebarOpen());
            assertFalse(settings.sidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS));
            assertFalse(settings.sidebarPanelExpanded(WorldMapSidebarPanel.TOTEM_SOLVER));
            assertTrue(settings.gatheringTotemSolverEnabled());
            assertEquals(GatheringTotemSearchTarget.SELECTED_CLUSTER, settings.gatheringTotemSearchTarget());
            assertFalse(settings.showGatheringTotemHulls());
            assertFalse(settings.showGatheringTotemPlayerRadius());
            assertFalse(settings.showGatheringTotemNodeReach());
            assertFalse(settings.showGatheringTotemCoveredNodes());
            assertFalse(settings.showOtherOptimalGatheringTotems());
            assertTrue(settings.showIngredientWaypointRadii());
            assertFalse(settings.colorIngredientWaypointRadiiByProximity());
            assertEquals(MapDisplayMode.INGREDIENTS, settings.displayMode());

            settings.setInsightsSidebarOpen(true);
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS, true);
            assertTrue(settings.insightsSidebarOpen());
            assertTrue(settings.sidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS));
        } finally {
            settings.setInsightsSidebarOpen(originalInsights);
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS, originalPanel);
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.TOTEM_SOLVER, originalTotemPanel);
            settings.setGatheringTotemSolverEnabled(originalTotemSolver);
            settings.setGatheringTotemSearchTarget(originalTarget);
            settings.setShowGatheringTotemHulls(originalHulls);
            settings.setShowGatheringTotemPlayerRadius(originalPlayerRadius);
            settings.setShowGatheringTotemNodeReach(originalNodeReach);
            settings.setShowGatheringTotemCoveredNodes(originalCoveredNodes);
            settings.setShowOtherOptimalGatheringTotems(originalOtherSpots);
            settings.setShowIngredientWaypointRadii(originalIngredientRadii);
            settings.setColorIngredientWaypointRadiiByProximity(originalProximityColors);
            settings.setDisplayMode(originalDisplayMode);
        }
    }
}
