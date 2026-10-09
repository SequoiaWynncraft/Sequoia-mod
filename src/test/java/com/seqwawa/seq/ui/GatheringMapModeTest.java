package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.seqwawa.seq.map.*;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.FontManager;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
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

    @Test
    void switchesSourcesRendersOnlySelectedDataAndRefreshesAfterReentry() {
        var settings = WorldMapSettings.getInstance();
        boolean clusters = settings.showClusters(), territories = settings.showTerritories();
        boolean names = settings.showTerritoryNames(), solver = settings.gatheringTotemSolverEnabled();
        var scope = settings.gatheringAnalysisScope();
        var originalSource = settings.gatheringNodeSource();
        boolean mapPanelExpanded = settings.sidebarPanelExpanded(WorldMapSidebarPanel.MAP_AND_TERRITORY);
        var resources = Set.copyOf(settings.resourceFilters());
        var professions = Map.copyOf(settings.professionToggles());
        FontManager previousFont = SeqClient.fontManager;
        try {
            SeqClient.fontManager = new FontManager();
            settings.setGatheringNodeSource(GatheringNodeSource.STATIC);
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.MAP_AND_TERRITORY, true);
            settings.setShowClusters(false);
            settings.setShowTerritories(false);
            settings.setShowTerritoryNames(false);
            settings.setGatheringTotemSolverEnabled(false);
            settings.setGatheringAnalysisScope(GatheringAnalysisScope.ALL);
            settings.setResourceFilters(Set.of());
            for (var profession : GatheringProfession.values()) settings.setProfessionEnabled(profession, true);
            var currentNodes = new AtomicReference<List<GatheringNode>>(List.of());
            var status = new AtomicReference<>("Loading nodes...");
            var apiNodes = new AtomicReference<List<GatheringNode>>(List.of());
            var requestedSources = new ArrayList<GatheringNodeSource>();
            var refreshes = new AtomicInteger();
            var mode = new GatheringMapMode(settings, new GatheringMapMode.Actions() {
                public void center(double x, double z, double scale) { fail(); }
                public void startDragging() {}
                public void copy(String coordinates) { fail(); }
                public void renderPlayer(UiCanvas canvas, MapViewport viewport) {}
            }, source -> source == GatheringNodeSource.STATIC ? currentNodes.get() : apiNodes.get(),
                    source -> source == GatheringNodeSource.STATIC ? status.get() : "Loading API nodes...",
                    source -> { requestedSources.add(source); refreshes.incrementAndGet(); });
            var texts = new ArrayList<String>();
            var rectangles = new ArrayList<float[]>();
            var canvas = (UiCanvas) Proxy.newProxyInstance(UiCanvas.class.getClassLoader(),
                    new Class<?>[] {UiCanvas.class}, (proxy, method, args) -> {
                        if (method.getName().equals("drawText")) texts.add((String) args[0]);
                        if (method.getName().equals("fillRect")) rectangles.add(new float[] {
                                (float) args[0], (float) args[1], (float) args[2], (float) args[3]});
                        return null;
                    });
            var viewport = new MapViewport(0, 0, 1, 230, 0, 520, 700);
            var frame = new WorldMapFrame(1000, 700, -1, -1, viewport, false);
            mode.refresh(frame);
            assertEquals(1, refreshes.get());
            assertEquals(List.of(GatheringNodeSource.STATIC), requestedSources);
            mode.renderMap(canvas, frame);
            assertTrue(texts.contains("Loading nodes..."));
            assertTrue(rectangles.isEmpty());

            status.set("Node load failed; retrying...");
            texts.clear();
            mode.refresh(frame);
            mode.renderMap(canvas, frame);
            assertTrue(texts.contains("Node load failed; retrying..."));

            currentNodes.set(List.of(new GatheringNode(0, 64, 0, 0, "NODE", "COPPER", 1)));
            status.set("Loaded 1 nodes");
            texts.clear();
            mode.refresh(frame);
            mode.renderMap(canvas, frame);
            assertEquals(2, rectangles.size(), "The newly fetched node is drawn with its outline.");
            assertEquals(viewport.worldToScreenX(0), rectangles.getFirst()[0] + rectangles.getFirst()[2] / 2);
            assertFalse(texts.contains("Loaded 1 nodes"), "A populated map does not show an empty-state overlay.");
            mode.renderInsights(canvas, frame, 750);
            assertTrue(texts.contains("Loaded 1 nodes"), "API status remains available in insights.");

            // Select a static node, then change source through the actual sidebar hit bounds.
            var hover = new WorldMapFrame(1000, 700, viewport.worldToScreenX(0), viewport.worldToScreenZ(0), viewport, false);
            mode.renderMap(canvas, hover);
            assertTrue(mode.click(hover));
            texts.clear();
            mode.renderInsights(canvas, frame, 750);
            assertFalse(texts.contains("Hover or select a node, cluster, or territory"));
            float sourceY = mode.sidebarLayout().sourceY() + 5;
            assertTrue(mode.click(new WorldMapFrame(1000, 700, 200, sourceY, viewport, false)));
            assertEquals(GatheringNodeSource.WYNN_API, settings.gatheringNodeSource());
            assertEquals(GatheringNodeSource.WYNN_API, requestedSources.getLast());
            rectangles.clear();
            texts.clear();
            mode.renderMap(canvas, frame);
            mode.renderInsights(canvas, frame, 750);
            assertTrue(rectangles.stream().noneMatch(rect -> rect[2] < 20 && rect[3] < 20),
                    "Static markers are not shown while the selected API source is empty.");
            assertTrue(texts.contains("Loading API nodes..."));
            assertTrue(texts.contains("Hover or select a node, cluster, or territory"), "Source change clears old selection.");
            assertEquals(GatheringAnalysisScope.ALL, settings.gatheringAnalysisScope());
            assertEquals(Set.of(), settings.resourceFilters());

            assertTrue(mode.click(new WorldMapFrame(1000, 700, 20, sourceY, viewport, false)));
            assertEquals(GatheringNodeSource.STATIC, settings.gatheringNodeSource());
            apiNodes.set(List.of(new GatheringNode(100, 64, 0, 0, "NODE", "COPPER", 1)));
            rectangles.clear();
            mode.refresh(frame);
            mode.renderMap(canvas, frame);
            assertEquals(2, rectangles.size(), "Late API data cannot change the selected static map.");
            assertEquals(viewport.worldToScreenX(0), rectangles.getFirst()[0] + rectangles.getFirst()[2] / 2);

            var shortFrame = new WorldMapFrame(1000, 300, 20, 200, viewport, false);
            mode.renderSidebar(canvas, shortFrame, "Center Player");
            assertTrue(mode.scroll(shortFrame, -2.25)); // 63 pixels: button spans y85..109, clipped at y92.
            assertFalse(mode.click(new WorldMapFrame(1000, 300, 200, 88, viewport, false)));
            assertEquals(GatheringNodeSource.STATIC, settings.gatheringNodeSource(),
                    "The clipped part of the source selector must not accept clicks.");
            assertTrue(mode.click(new WorldMapFrame(1000, 300, 200, 100, viewport, false)));
            assertEquals(GatheringNodeSource.WYNN_API, settings.gatheringNodeSource(),
                    "The visible portion uses the same scrolled hit bounds as rendering.");
            assertTrue(mode.click(new WorldMapFrame(1000, 300, 20, 100, viewport, false)));
            assertEquals(GatheringNodeSource.STATIC, settings.gatheringNodeSource());

            int beforeReentry = refreshes.get();
            mode.deactivate();
            currentNodes.set(List.of(new GatheringNode(50, 64, 0, 0, "NODE", "COPPER", 1)));
            rectangles.clear();
            mode.refresh(frame);
            assertEquals(beforeReentry + 1, refreshes.get(), "Switching back requests a throttled refresh again.");
            mode.renderMap(canvas, frame);
            assertEquals(2, rectangles.size());
            assertEquals(viewport.worldToScreenX(50), rectangles.getFirst()[0] + rectangles.getFirst()[2] / 2);
            mode.close();
        } finally {
            SeqClient.fontManager = previousFont;
            settings.setShowClusters(clusters);
            settings.setShowTerritories(territories);
            settings.setShowTerritoryNames(names);
            settings.setGatheringTotemSolverEnabled(solver);
            settings.setGatheringAnalysisScope(scope);
            settings.setGatheringNodeSource(originalSource);
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.MAP_AND_TERRITORY, mapPanelExpanded);
            settings.setResourceFilters(resources);
            professions.forEach(settings::setProfessionEnabled);
        }
    }

}
