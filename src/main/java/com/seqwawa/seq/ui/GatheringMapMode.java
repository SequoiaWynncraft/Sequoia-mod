package com.seqwawa.seq.ui;

import com.seqwawa.seq.map.GatheringMapImageService;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WorldMapUi.*;

import java.awt.Color;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.function.Consumer;
import org.lwjgl.glfw.GLFW;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.ClusterScoreMode;
import com.seqwawa.seq.map.GatheringAnalysisScope;
import com.seqwawa.seq.map.GatheringClusterCache;
import com.seqwawa.seq.map.GatheringNode;
import com.seqwawa.seq.map.GatheringNodeCluster;
import com.seqwawa.seq.map.GatheringNodeService;
import com.seqwawa.seq.map.GatheringNodeSource;
import com.seqwawa.seq.map.GatheringProfession;
import com.seqwawa.seq.map.GatheringTotemHitTester;
import com.seqwawa.seq.map.GatheringTotemSearchTarget;
import com.seqwawa.seq.map.GatheringTotemSolver;
import com.seqwawa.seq.map.GatheringTotemSolver.Placement;
import com.seqwawa.seq.map.GatheringTotemSolver.Position;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.map.GuildTerritoryService;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapDisplayMode;
import com.seqwawa.seq.map.MapViewport;
import com.seqwawa.seq.map.WorldMapSettings;
import com.seqwawa.seq.map.WorldMapSidebarPanel;
import com.seqwawa.seq.utils.rendering.UiCanvas;

/** Gathering mode owns analysis, filters, selections, solver and input. */
final class GatheringMapMode {
    private static final float SIDEBAR_WIDTH = 230;
    private static final float INSIGHTS_SIDEBAR_WIDTH = 250;

    private static final float PADDING = 12;
    private static final float BUTTON_HEIGHT = 24;
    private static final float TOGGLE_HEIGHT = 22;
    private static final float INPUT_HEIGHT = 24;

    private static final float SIDEBAR_PANEL_TOP = 92;

    private static final float PANEL_HEADER_HEIGHT = 28;
    private static final float PANEL_GAP = 10;

    private static final float SPLIT_CONTROL_GAP = 4;

    private static final float CLUSTER_DETAIL_HEIGHT = 110;
    private static final float NODE_DETAIL_HEIGHT = 58;
    private static final float TERRITORY_DETAIL_HEIGHT = 76;
    private static final float RESOURCE_DROPDOWN_ROW_HEIGHT = 20;
    private static final int RESOURCE_DROPDOWN_VISIBLE_ROWS = 8;
    private static final int TERRITORY_DROPDOWN_VISIBLE_ROWS = 8;

    private static final double MIN_PIXELS_PER_BLOCK = MapViewport.MIN_PIXELS_PER_BLOCK;
    private static final double MAX_PIXELS_PER_BLOCK = MapViewport.MAX_PIXELS_PER_BLOCK;
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double NODE_DETAIL_PIXELS_PER_BLOCK = 0.42;
    private static final double CLUSTER_BADGE_PIXELS_PER_BLOCK = 0.65;
    private static final double TERRITORY_FOCUS_MAX_PIXELS_PER_BLOCK = 1.25;

    private static final int SIDEBAR_CLUSTER_LIMIT = 5;
    private final Function<GatheringNodeSource, List<GatheringNode>> nodes;
    private final Function<GatheringNodeSource, String> nodeStatus;
    private final Consumer<GatheringNodeSource> refreshNodes;
    private GatheringNodeSource cachedNodeSource;
    private final GuildTerritoryService territoryService = GuildTerritoryService.getInstance();
    private GatheringTotemSession.Request cachedTotemRequest;
    private final GatheringTotemPanel totemPanel;
    private final GatheringClusterCache clusterCache = GatheringClusterCache.getInstance();
    private final EnumMap<GatheringProfession, Boolean> professionToggles = new EnumMap<>(GatheringProfession.class);
    private boolean resourceDropdownOpen;
    private boolean resourceInputFocused;
    private int resourceDropdownScroll;
    private boolean territoryDropdownOpen;
    private boolean territoryInputFocused;
    private int territoryDropdownScroll;
    private String resourceSearch = "";
    private String territorySearch = "";
    private final Set<String> selectedResourceFilters = new TreeSet<>();
    private GatheringNode hoveredNode;
    private GatheringNode selectedNode;
    private GatheringNodeCluster hoveredCluster;
    private GatheringNodeCluster selectedCluster;
    private GuildTerritoryIndex territoryIndex = GuildTerritoryIndex.EMPTY;
    private GuildTerritory hoveredTerritory;
    private GuildTerritory selectedTerritory;
    private boolean showClusters = true;
    private boolean showTerritories;
    private boolean showTerritoryNames;
    private boolean showDebugInfo;
    private ClusterScoreMode clusterScoreMode = ClusterScoreMode.FOUR_TICK;
    private GatheringAnalysisScope gatheringAnalysisScope = GatheringAnalysisScope.ALL;
    private List<GatheringNode> cachedSourceNodes = List.of();
    private List<GatheringNode> cachedFilteredNodes = List.of();
    private List<GatheringNodeCluster> cachedClusters = List.of();
    private Placement hoveredGatheringTotemPlacement;
    private Map<String, Integer> cachedTerritoryNodeCounts = Map.of();
    private int selectedTerritoryMatchingNodeCount;
    private List<String> cachedResourceOptions = List.of();
    private String cachedClusterKey = "";
    private long cachedSettingsVersion = -1;
    private long gatheringAnalysisVersion;

    interface Actions {
        void center(double x, double z, double scale);
        void startDragging();
        void copy(String coordinates);
        void renderPlayer(UiCanvas canvas, MapViewport viewport);
    }
    private final GatheringClusterLayer clusterLayer = new GatheringClusterLayer();
    private final Actions actions;
    private final WorldMapSettings mapSettings;
    private final MapSidebar sidebar = new MapSidebar();
    private WorldMapFrame frame;
    GatheringMapMode(WorldMapSettings settings, Actions actions) {
        this(settings, actions, GatheringNodeService.getInstance()::nodes,
                GatheringNodeService.getInstance()::status, GatheringNodeService.getInstance()::requestRefresh);
    }

    GatheringMapMode(WorldMapSettings settings, Actions actions, Function<GatheringNodeSource, List<GatheringNode>> nodes,
            Function<GatheringNodeSource, String> nodeStatus, Consumer<GatheringNodeSource> refreshNodes) {
        this.nodes = nodes;
        this.nodeStatus = nodeStatus;
        this.refreshNodes = refreshNodes;
        this.mapSettings = settings; this.actions = actions;
        this.cachedNodeSource = settings.gatheringNodeSource();
        this.totemPanel = new GatheringTotemPanel(mapSettings,
            new GatheringTotemSession(request -> CompletableFuture.supplyAsync(() -> GatheringTotemSolver.solveAll(
                    request.nodes(), request.resources(), request.territory(), request.clusterNodes())),
                    java.time.Clock.systemUTC(), exception -> SeqClient.LOGGER.warn(
                            "[GatheringMap] Gathering totem optimization failed.", exception)));

        professionToggles.putAll(mapSettings.professionToggles());
        selectedResourceFilters.addAll(mapSettings.resourceFilters());
        showClusters = mapSettings.showClusters();
        showTerritories = mapSettings.showTerritories();
        showTerritoryNames = mapSettings.showTerritoryNames();
        showDebugInfo = mapSettings.showDebugInfo();
        clusterScoreMode = mapSettings.clusterScoreMode();
        gatheringAnalysisScope = mapSettings.gatheringAnalysisScope();
        territoryService.loadBundledTerritories();
        territoryIndex = territoryService.index();
        restoreSelectedTerritory();

    }
    void refresh(WorldMapFrame frame) {
        this.frame = frame;
        GatheringNodeSource source = mapSettings.gatheringNodeSource();
        if (source != cachedNodeSource) {
            cachedNodeSource = source;
            selectedNode = null;
            selectedCluster = null;
            clearHover();
            hoveredGatheringTotemPlacement = null;
            cachedTotemRequest = null;
            totemPanel.reset();
            cachedClusterKey = "";
        }
        refreshNodes.accept(source);
        showDebugInfo = mapSettings.showDebugInfo();
        refreshClusterAnalysisIfNeeded(); refreshGatheringTotemPlacement();
    }
    void clearHover() { hoveredNode = null; hoveredCluster = null; hoveredTerritory = null; }
    void deactivate() { sidebar.reset(); selectedNode = null; selectedCluster = null; clearHover(); closeSearchDropdowns(); }
    void close() { totemPanel.reset(); hoveredGatheringTotemPlacement = null; }
    void closeSearchDropdowns() { closeResourceSearch(); closeTerritorySearch(); }
    void renderInsights(UiCanvas canvas, WorldMapFrame frame, float x) { this.frame = frame; renderGatheringInsights(canvas, x, frame.height(), insightsLayout()); }
    private void drawButton(UiCanvas canvas, float x, float y, float w, float h, String label, boolean active) { MapSidebar.button(canvas, x, y, w, h, label, active, frame); }
    private static boolean isHovered(float mx, float my, float x, float y, float w, float h) { return MapSidebar.hit(mx, my, x, y, w, h); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(value, max)); }
    private float tooltipX(float width) { return frame.tooltipX(width); }
    private String displayMapImageSource() {
        return switch (GatheringMapImageService.getInstance().imageSource()) {
            case NONE -> "none";
            case CACHED_TILES -> "cached tiles";
            case CACHED_HQ -> "cached HQ";
        };
    }

    private void renderNodes(UiCanvas canvas, MapViewport viewport, List<GatheringNode> nodes) {
        hoveredNode = null;
        float bestHoverDistance = 10f;
        boolean allowHover = !frame.dragging();
        MapBounds visibleBounds = viewport.visibleBounds();
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (GatheringNode node : nodes) {
            if (!visibleBounds.contains(node.x(), node.z())) {
                continue;
            }
            float x = viewport.worldToScreenX(node.x());
            float y = viewport.worldToScreenZ(node.z());
            float radius = (float) Math.max(1.5, Math.min(4.0, frame.viewport().pixelsPerBlock() * 12.0));
            float distance = allowHover ? (float) markerDistance(frame.mouseX() - x, frame.mouseY() - y) : Float.MAX_VALUE;
            boolean hovered = allowHover && distance <= Math.max(8, radius + 3);
            if (hovered && distance < bestHoverDistance) {
                bestHoverDistance = distance;
                hoveredNode = node;
            }
            boolean selected = selectedNode == node || (selectedCluster != null && selectedCluster.nodes().contains(node));
            Color color = selected ? color(MAP_PLAYER) : node.profession().color();
            drawSquareMarker(canvas, x, y, selected || hovered ? Math.min(radius + 1.8f, 5.6f) : radius,
                    color(BACKGROUND_MODAL_OVERLAY));
            drawSquareMarker(canvas, x, y, radius, color);
        }
        canvas.resetScissor();
        if (hoveredNode != null) {
            renderNodeTooltip(canvas, hoveredNode);
        }
    }

    private void renderGatheringTotemPlacements(UiCanvas canvas, MapViewport viewport) {
        if (!totemPanel.enabled() || totemPanel.placements().isEmpty()) {
            hoveredGatheringTotemPlacement = null;
            return;
        }
        List<Placement> visiblePlacements = visibleGatheringTotemPlacements();
        hoveredGatheringTotemPlacement = !frame.dragging() && viewport.isInsideScreen(frame.mouseX(), frame.mouseY())
                ? gatheringTotemPlacementAt(
                        visiblePlacements,
                        viewport,
                        frame.mouseX(),
                        frame.mouseY())
                : null;
        for (Placement placement : visiblePlacements) {
            if (placement != totemPanel.selected()) {
                renderGatheringTotemPlacement(
                        canvas,
                        viewport,
                        placement,
                        false,
                        placement == hoveredGatheringTotemPlacement);
            }
        }
        if (totemPanel.selected() != null) {
            renderGatheringTotemPlacement(
                    canvas,
                    viewport,
                    totemPanel.selected(),
                    true,
                    totemPanel.selected() == hoveredGatheringTotemPlacement);
        }
    }

    private void renderGatheringTotemPlacement(
            UiCanvas canvas,
            MapViewport viewport,
            Placement placement,
            boolean selected,
            boolean hovered) {
        List<Position> hull = placement.validCenterHull();
        if (hull.isEmpty()) {
            return;
        }
        List<UiCanvas.Point> screenHull = hull.stream()
                .map(position -> new UiCanvas.Point(
                        viewport.worldToScreenX(position.x()),
                        viewport.worldToScreenZ(position.z())))
                .toList();

        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        float x = viewport.worldToScreenX(placement.x());
        float z = viewport.worldToScreenZ(placement.z());
        if (selected && totemPanel.layerEnabled(1)) {
            drawCircleOutline(
                    canvas,
                    x,
                    z,
                    (float) (GatheringTotemSolver.TOTEM_RADIUS * viewport.pixelsPerBlock()),
                    hovered ? 2.4f : 1.8f,
                    color(MAP_TOTEM_RANGE));
        }
        if (selected && totemPanel.layerEnabled(2)) {
            drawDashedCircle(
                    canvas,
                    x,
                    z,
                    (float) (GatheringTotemSolver.EFFECTIVE_NODE_RADIUS * viewport.pixelsPerBlock()),
                    color(MAP_TOTEM_REACH));
        }

        if (totemPanel.layerEnabled(0)) {
            Color hullColor = selected ? color(MAP_TOTEM) : color(MAP_TOTEM_MUTED);
            if (screenHull.size() == 1) {
                drawSquareMarker(canvas, screenHull.getFirst().x(), screenHull.getFirst().y(), hovered ? 7 : 5, hullColor);
            } else {
                boolean closed = screenHull.size() > 2;
                Color fill = selected && closed
                        ? color(MAP_TOTEM)
                        : null;
                canvas.fillAndStrokePolygon(
                        screenHull,
                        fill,
                        hullColor,
                        selected ? (hovered ? 2.8f : 2) : (hovered ? 2 : 1.2f),
                        closed);
            }
        }

        if (selected && totemPanel.layerEnabled(3)) {
            for (GatheringNode node : placement.coveredNodes()) {
                if (!viewport.visibleBounds().contains(node.x(), node.z())) {
                    continue;
                }
                drawSquareMarker(
                        canvas,
                        viewport.worldToScreenX(node.x()),
                        viewport.worldToScreenZ(node.z()),
                        2.5f,
                        color(MAP_TOTEM));
            }
        }
        drawTotemMarker(canvas, x, z, selected || hovered ? 22 : 18,
                selected ? color(MAP_PLAYER) : color(MAP_TOTEM_MUTED), selected || hovered);
        canvas.resetScissor();
    }

    private static void drawDashedCircle(UiCanvas canvas, float x, float y, float radius, Color color) {
        int segments = 48;
        canvas.beginPath();
        for (int segment = 0; segment < segments; segment += 2) {
            double startAngle = TWO_PI * segment / segments;
            double endAngle = TWO_PI * (segment + 1) / segments;
            canvas.moveTo(
                    x + (float) Math.cos(startAngle) * radius,
                    y + (float) Math.sin(startAngle) * radius);
            canvas.lineTo(
                    x + (float) Math.cos(endAngle) * radius,
                    y + (float) Math.sin(endAngle) * radius);
        }
        canvas.strokePath(1.5f, color);
    }

    private List<Placement> visibleGatheringTotemPlacements() {
        if (totemPanel.layerEnabled(4) || totemPanel.selected() == null) {
            return totemPanel.placements();
        }
        return List.of(totemPanel.selected());
    }

    void renderSidebar(UiCanvas canvas, WorldMapFrame frame, String centerLabel) {
        this.frame = frame;
        float screenHeight = frame.height();
        SidebarLayout layout = sidebarLayout();
        sidebar.contentHeight(layout.endY() + PADDING, screenHeight);
        sidebar.renderBase(canvas, frame, MapDisplayMode.GATHERING, centerLabel);
        canvas.scissor(0, SIDEBAR_PANEL_TOP, SIDEBAR_WIDTH, Math.max(0, screenHeight - SIDEBAR_PANEL_TOP));
        sidebar.panelHeader(
                canvas,
                sidebar.y(layout.mapPanelY()),
                "Map & Territory",
                selectedTerritory == null ? gatheringAnalysisScope.label() : selectedTerritory.name(),
                WorldMapSidebarPanel.MAP_AND_TERRITORY, frame, mapSettings);
        if (panelExpanded(WorldMapSidebarPanel.MAP_AND_TERRITORY)) {
            drawText(canvas, PADDING, sidebar.y(layout.sourceLabelY()), 12, "Node source", color(MAP_SUBTEXT), TextAlignment.LEFT);
            float sourceWidth = (SIDEBAR_WIDTH - PADDING * 2 - SPLIT_CONTROL_GAP) / 2;
            drawButton(canvas, PADDING, sidebar.y(layout.sourceY()), sourceWidth, BUTTON_HEIGHT,
                    GatheringNodeSource.STATIC.label(), cachedNodeSource == GatheringNodeSource.STATIC);
            drawButton(canvas, PADDING + sourceWidth + SPLIT_CONTROL_GAP, sidebar.y(layout.sourceY()), sourceWidth, BUTTON_HEIGHT,
                    GatheringNodeSource.WYNN_API.label(), cachedNodeSource == GatheringNodeSource.WYNN_API);
            renderTerritoryToggles(canvas, sidebar.y(layout.territoryToggleY()));
            drawText(canvas, PADDING, sidebar.y(layout.scopeLabelY()), 12, "Gathering Scope", color(MAP_SUBTEXT), TextAlignment.LEFT);
            drawScopeControl(canvas, sidebar.y(layout.scopeY()));
            drawText(canvas, PADDING, sidebar.y(layout.territoryLabelY()), 12, "Territory", color(MAP_SUBTEXT), TextAlignment.LEFT);
            MapSidebar.searchInput(
                    canvas,
                    sidebar.y(layout.territoryInputY()),
                    territoryDropdownOpen,
                    territoryInputFocused,
                    territorySearch,
                    selectedTerritory == null ? "Find territory" : selectedTerritory.name(), frame);
        }

        sidebar.panelHeader(
                canvas,
                sidebar.y(layout.analysisPanelY()),
                "Gathering Analysis",
                showClusters ? clusterScoreMode.label() : "Nodes",
                WorldMapSidebarPanel.GATHERING_ANALYSIS, frame, mapSettings);
        if (panelExpanded(WorldMapSidebarPanel.GATHERING_ANALYSIS)) {
            renderGatheringAnalysisToggles(canvas, sidebar.y(layout.clustersY()));
        }

        sidebar.panelHeader(
                canvas,
                sidebar.y(layout.filtersPanelY()),
                "Resource Filters",
                selectedResourceFilters.isEmpty() ? "All" : selectedResourceFilters.size() + " selected",
                WorldMapSidebarPanel.RESOURCE_FILTERS, frame, mapSettings);
        if (panelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS)) {
            drawText(canvas, PADDING, sidebar.y(layout.resourceLabelY()), 12, "Resource", color(MAP_SUBTEXT), TextAlignment.LEFT);
            MapSidebar.searchInput(
                    canvas,
                    sidebar.y(layout.resourceInputY()),
                    resourceDropdownOpen,
                    resourceInputFocused,
                    resourceSearch,
                    selectedResourceLabel(), frame);
            drawText(canvas, PADDING, sidebar.y(layout.professionLabelY()), 12, "Professions", color(MAP_SUBTEXT), TextAlignment.LEFT);
            float professionY = sidebar.y(layout.professionStartY());
            for (GatheringProfession profession : gatheringProfessions()) {
                boolean active = professionToggles.getOrDefault(profession, true);
                drawToggle(canvas, PADDING, professionY, SIDEBAR_WIDTH - PADDING * 2, TOGGLE_HEIGHT, profession, active);
                professionY += TOGGLE_HEIGHT + 6;
            }
        }

        sidebar.panelHeader(
                canvas,
                sidebar.y(layout.totemPanelY()),
                "Totem Solver",
                totemPanel.enabled() ? "On" : "Off",
                WorldMapSidebarPanel.TOTEM_SOLVER, frame, mapSettings);
        if (panelExpanded(WorldMapSidebarPanel.TOTEM_SOLVER)) {
            totemPanel.render(canvas, totemSolverLayout(layout.totemPanelY()).shifted(-sidebar.scroll()),
                    frame.mouseX(), frame.mouseY(), gatheringTotemScopeSummary(), selectedResourceLabel(), selectedCluster != null);
        }

        if (resourceDropdownOpen) {
            renderResourceDropdown(canvas, sidebar.y(layout.resourceInputY()) + INPUT_HEIGHT);
        }
        if (territoryDropdownOpen) {
            renderTerritoryDropdown(canvas, sidebar.y(layout.territoryInputY()) + INPUT_HEIGHT);
        }
        canvas.resetScissor();
        sidebar.renderScrollbar(canvas, screenHeight);
    }

    private void renderGatheringInsights(UiCanvas canvas, float x, float screenHeight, InsightsLayout layout) {
        float contentX = x + PADDING;
        float contentWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        drawInsightsSectionTitle(canvas, contentX, layout.overviewY(), "Overview");
        drawInsightRow(canvas, contentX, layout.overviewY() + 18, contentWidth, "Scope", gatheringAnalysisScope.label());
        drawInsightRow(canvas, contentX, layout.overviewY() + 34, contentWidth, "Matching nodes", String.valueOf(cachedFilteredNodes.size()));
        drawInsightRow(canvas, contentX, layout.overviewY() + 50, contentWidth, "Clusters", String.valueOf(cachedClusters.size()));
        drawInsightRow(canvas, contentX, layout.overviewY() + 66, contentWidth, cachedNodeSource.label(), nodeStatus.apply(cachedNodeSource));
        float overviewRowY = layout.overviewY() + 82;
        if (showDebugInfo) {
            drawInsightRow(canvas, contentX, overviewRowY, contentWidth, "Map source", displayMapImageSource());
            drawInsightRow(canvas, contentX, overviewRowY + 16, contentWidth, "HQ status", GatheringMapImageService.getInstance().hqStatus());
            overviewRowY += 32;
        }
        if (totemPanel.enabled()) {
            String coverage = totemPanel.pending()
                    ? "Optimizing..."
                    : totemPanel.selected() == null
                            ? "No eligible nodes"
                            : totemPanel.selected().nodeCount()
                                    + (totemPanel.selected().clusterFocused() ? " nodes (cluster)" : " nodes")
                                    + (totemPanel.placements().size() > 1
                                            ? " · " + totemPanel.placements().size() + " spots"
                                            : "");
            String expectedYield = totemPanel.selected() == null
                    ? "-"
                    : String.format(Locale.ROOT, "%.1f items", totemPanel.selected().expectedItemsPerGather());
            String placementCoordinates = totemPanel.selected() == null
                    ? "-"
                    : Math.round(totemPanel.selected().x()) + " " + Math.round(totemPanel.selected().z());
            drawInsightRow(canvas, contentX, overviewRowY, contentWidth, "Totem (52 reach)", coverage);
            drawInsightRow(canvas, contentX, overviewRowY + 16, contentWidth, "Expected (30% double)", expectedYield);
            drawInsightRow(canvas, contentX, overviewRowY + 32, contentWidth, "Placement X Z", placementCoordinates);
        }

        if (selectedTerritory != null) {
            drawInsightsSectionTitle(canvas, contentX, layout.territoryY() - 8, "Territory");
            renderSelectedTerritoryDetail(canvas, contentX, layout.territoryY() + 4, contentWidth, selectedTerritory);
        }

        GatheringNodeCluster clusterDetail = selectedCluster != null ? selectedCluster : hoveredCluster;
        GatheringNode nodeDetail = selectedNode != null ? selectedNode : hoveredNode;
        drawInsightsSectionTitle(canvas, contentX, layout.entityY() - 8, "Selection");
        if (clusterDetail != null) {
            renderClusterDetail(canvas, contentX, layout.entityY() + 4, contentWidth, clusterDetail);
        } else if (nodeDetail != null) {
            renderNodeDetail(canvas, contentX, layout.entityY() + 4, contentWidth, nodeDetail);
        } else {
            drawFittedText(
                    canvas,
                    contentX,
                    layout.entityY() + 18,
                    11,
                    "Hover or select a node, cluster, or territory",
                    color(MAP_SUBTEXT),
                    contentWidth,
                    TextAlignment.LEFT);
        }

        if (!showClusters || cachedClusters.isEmpty()) {
            return;
        }
        float topY = layout.topClustersY();
        drawInsightsSectionTitle(canvas, contentX, topY, "Top Clusters");
        topY += 12;
        int availableRows = Math.max(0, (int) ((screenHeight - topY - PADDING) / 40));
        int rowCount = Math.min(Math.min(SIDEBAR_CLUSTER_LIMIT, cachedClusters.size()), availableRows);
        for (int index = 0; index < rowCount; index++) {
            GatheringNodeCluster cluster = cachedClusters.get(index);
            boolean active = cluster == selectedCluster;
            canvas.fillRect(contentX, topY, contentWidth, 34, active ? color(MAP_CONTROL_ACTIVE) : color(MAP_CONTROL));
            canvas.strokeRect(contentX, topY, contentWidth, 34, 1, color(MAP_BORDER));
            drawFittedText(canvas, contentX + 8, topY + 11, 11, "#" + (index + 1) + " " + cluster.resource() + " | " + cluster.score() + "%", color(MAP_TEXT), contentWidth - 16, TextAlignment.LEFT);
            drawFittedText(canvas, contentX + 8, topY + 26, 10, cluster.nodeCount() + " nodes | " + Math.round(cluster.averageSpacing()) + "m", color(MAP_SUBTEXT), contentWidth - 16, TextAlignment.LEFT);
            topY += 40;
        }
    }

    private void renderClusterDetail(UiCanvas canvas, float x, float y, float width, GatheringNodeCluster cluster) {
        canvas.fillRect(x, y, width, CLUSTER_DETAIL_HEIGHT, color(MAP_HEADER));
        canvas.strokeRect(x, y, width, CLUSTER_DETAIL_HEIGHT, 1, color(MAP_BORDER));
        float textWidth = width - 16;
        drawFittedText(canvas, x + 8, y + 17, 14, cluster.resource(), color(MAP_TEXT), textWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 36, 12, cluster.nodeCount() + " nodes | score " + cluster.score() + "%", color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 55, 12, Math.round(cluster.averageSpacing()) + "m spacing", color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 74, 12, cluster.profession().name(), cluster.profession().color(), textWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 93, 12, clusterCoords(cluster), color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
    }

    private void renderNodeDetail(UiCanvas canvas, float x, float y, float width, GatheringNode node) {
        canvas.fillRect(x, y, width, NODE_DETAIL_HEIGHT, color(MAP_HEADER));
        canvas.strokeRect(x, y, width, NODE_DETAIL_HEIGHT, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 17, 14, node.resource(), color(MAP_TEXT), width - 16, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 38, 12, nodeCoords(node), color(MAP_SUBTEXT), width - 16, TextAlignment.LEFT);
    }

    private boolean panelExpanded(WorldMapSidebarPanel panel) {
        return mapSettings.sidebarPanelExpanded(panel);
    }

    private void togglePanel(WorldMapSidebarPanel panel) {
        if (panel == WorldMapSidebarPanel.TOTEM_SOLVER) {
            totemPanel.setEnabled(!totemPanel.enabled());
            totemPanel.reset();
            hoveredGatheringTotemPlacement = null;
            return;
        }
        boolean expanded = !panelExpanded(panel);
        mapSettings.setSidebarPanelExpanded(panel, expanded);
        sidebar.reset();
        if (!expanded) {
            if (panel == WorldMapSidebarPanel.MAP_AND_TERRITORY) {
                closeTerritorySearch();
            } else if (panel == WorldMapSidebarPanel.RESOURCE_FILTERS) {
                closeResourceSearch();
            }
        }
    }

    private static List<GatheringProfession> gatheringProfessions() {
        return List.of(
                GatheringProfession.WOODCUTTING,
                GatheringProfession.MINING,
                GatheringProfession.FARMING,
                GatheringProfession.FISHING);
    }

    private void renderTerritoryToggles(UiCanvas canvas, float y) {
        float fullWidth = SIDEBAR_WIDTH - PADDING * 2;
        if (!showTerritories) {
            drawButton(canvas, PADDING, y, fullWidth, BUTTON_HEIGHT, "Territory Borders Off", false);
            return;
        }
        float splitWidth = (fullWidth - SPLIT_CONTROL_GAP) / 2f;
        drawButton(canvas, PADDING, y, splitWidth, BUTTON_HEIGHT, "Borders On", true);
        drawButton(
                canvas,
                PADDING + splitWidth + SPLIT_CONTROL_GAP,
                y,
                splitWidth,
                BUTTON_HEIGHT,
                showTerritoryNames ? "Names On" : "Names Off",
                showTerritoryNames);
    }

    private void renderGatheringAnalysisToggles(UiCanvas canvas, float y) {
        float fullWidth = SIDEBAR_WIDTH - PADDING * 2;
        if (!showClusters) {
            drawButton(canvas, PADDING, y, fullWidth, BUTTON_HEIGHT, "Gathering Clusters Off", false);
            return;
        }
        float splitWidth = (fullWidth - SPLIT_CONTROL_GAP) / 2f;
        drawButton(canvas, PADDING, y, splitWidth, BUTTON_HEIGHT, "Clusters On", true);
        drawButton(
                canvas,
                PADDING + splitWidth + SPLIT_CONTROL_GAP,
                y,
                splitWidth,
                BUTTON_HEIGHT,
                clusterScoreMode.label(),
                true);
    }

    private String gatheringTotemScopeSummary() {
        if (gatheringAnalysisScope == GatheringAnalysisScope.SELECTED_TERRITORY && selectedTerritory != null) {
            return selectedTerritory.name();
        }
        return gatheringAnalysisScope.label();
    }

    private void centerOnGatheringTotemPlacement() {
        if (totemPanel.selected() == null) {
            return;
        }
        double minX = totemPanel.selected().x() - GatheringTotemSolver.EFFECTIVE_NODE_RADIUS;
        double maxX = totemPanel.selected().x() + GatheringTotemSolver.EFFECTIVE_NODE_RADIUS;
        double minZ = totemPanel.selected().z() - GatheringTotemSolver.EFFECTIVE_NODE_RADIUS;
        double maxZ = totemPanel.selected().z() + GatheringTotemSolver.EFFECTIVE_NODE_RADIUS;
        for (Position position : totemPanel.selected().validCenterHull()) {
            minX = Math.min(minX, position.x());
            maxX = Math.max(maxX, position.x());
            minZ = Math.min(minZ, position.z());
            maxZ = Math.max(maxZ, position.z());
        }

        double availableWidth = Math.max(1, frame.viewport().screenWidth() - 48);
        double availableHeight = Math.max(1, frame.height() - 48);
        actions.center((minX + maxX) / 2.0, (minZ + maxZ) / 2.0, clamp(Math.min(availableWidth / Math.max(1, maxX - minX), availableHeight / Math.max(1, maxZ - minZ)), MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK));
    }

    private void drawScopeControl(UiCanvas canvas, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / GatheringAnalysisScope.values().length;
        for (int index = 0; index < GatheringAnalysisScope.values().length; index++) {
            GatheringAnalysisScope scope = GatheringAnalysisScope.values()[index];
            float x = PADDING + index * segmentWidth;
            boolean enabled = scope != GatheringAnalysisScope.SELECTED_TERRITORY || selectedTerritory != null;
            boolean active = gatheringAnalysisScope == scope;
            boolean hovered = enabled && isHovered(frame.mouseX(), frame.mouseY(), x, y, segmentWidth, BUTTON_HEIGHT);
            Color background = !enabled ? color(MAP_CONTROL_INACTIVE)
                    : active ? color(MAP_CONTROL_ACTIVE) : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL);
            canvas.fillRect(x, y, segmentWidth, BUTTON_HEIGHT, background);
            canvas.strokeRect(x, y, segmentWidth, BUTTON_HEIGHT, 1, color(MAP_BORDER));
            drawFittedText(
                    canvas,
                    x + segmentWidth / 2f,
                    y + BUTTON_HEIGHT / 2f,
                    10,
                    scope.label(),
                    enabled ? color(MAP_TEXT) : color(MAP_SUBTEXT),
                    segmentWidth - 8,
                    TextAlignment.CENTER);
        }
    }

    private void renderSelectedTerritoryDetail(
            UiCanvas canvas,
            float x,
            float y,
            float width,
            GuildTerritory territory) {
        canvas.fillRect(x, y, width, TERRITORY_DETAIL_HEIGHT, color(MAP_HEADER));
        canvas.strokeRect(x, y, width, TERRITORY_DETAIL_HEIGHT, 1, color(MAP_SELECTED_TERRITORY));
        float detailWidth = width - 16;
        int totalNodes = cachedTerritoryNodeCounts.getOrDefault(territory.name(), 0);
        int matchingNodes = selectedTerritoryMatchingNodeCount;
        drawFittedText(canvas, x + 8, y + 17, 14, territory.name(), color(MAP_TEXT), detailWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 36, 11, totalNodes + " total nodes | " + matchingNodes + " matching", color(MAP_SUBTEXT), detailWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 56, 10, territoryBoundsLabel(territory), color(MAP_SUBTEXT), detailWidth, TextAlignment.LEFT);
    }

    private void drawToggle(UiCanvas canvas, float x, float y, float w, float h, GatheringProfession profession, boolean active) {
        drawButton(canvas, x, y, w, h, displayProfession(profession), active);
        drawSquareMarker(canvas, x + 13, y + h / 2f, 4, profession.color());
    }

    private void renderResourceDropdown(UiCanvas canvas, float y) {
        List<String> resources = resourceDropdownOptions();
        resourceDropdownScroll = clampResourceDropdownScroll(resourceDropdownScroll, resources.size());
        DropdownMenu.list(canvas, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, RESOURCE_DROPDOWN_ROW_HEIGHT,
                resources.stream().map(value -> value.isBlank() ? "All resources" : value).toList(),
                i -> resources.get(i).isBlank() ? selectedResourceFilters.isEmpty() : selectedResourceFilters.contains(resources.get(i)),
                resourceDropdownScroll, RESOURCE_DROPDOWN_VISIBLE_ROWS, frame.mouseX(), frame.mouseY());
    }

    private void renderTerritoryDropdown(UiCanvas canvas, float y) {
        List<GuildTerritory> territories = territoryDropdownOptions();
        territoryDropdownScroll = clampDropdownScroll(territoryDropdownScroll, territories.size(), TERRITORY_DROPDOWN_VISIBLE_ROWS);
        DropdownMenu.list(canvas, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, RESOURCE_DROPDOWN_ROW_HEIGHT,
                territories.stream().map(GuildTerritory::name).toList(), i -> territories.get(i).equals(selectedTerritory),
                territoryDropdownScroll, TERRITORY_DROPDOWN_VISIBLE_ROWS, frame.mouseX(), frame.mouseY());
    }

    boolean copyHoveredCoordinates(WorldMapFrame frame, boolean insightsSidebarOpen, float insightsX) {
        this.frame = frame;
        float mx = frame.mouseX(), my = frame.mouseY();
        MapViewport viewport = frame.viewport();
        InsightsLayout insights = insightsLayout();
        if (totemPanel.enabled() && totemPanel.selected() != null) {
            float totemRowsY = insights.overviewY() + 74 + (showDebugInfo ? 32 : 0);
            if (insightsSidebarOpen
                    && isHovered(
                            mx,
                            my,
                            insightsX + PADDING,
                            totemRowsY,
                            INSIGHTS_SIDEBAR_WIDTH - PADDING * 2,
                            48)) {
                actions.copy(totemCoords(totemPanel.selected()));
                return true;
            }
            Placement clickedPlacement = viewport.isInsideScreen(mx, my)
                    ? gatheringTotemPlacementAt(visibleGatheringTotemPlacements(), viewport, mx, my)
                    : null;
            if (clickedPlacement != null) {
                actions.copy(totemCoords(clickedPlacement));
                return true;
            }
        }
        GatheringNodeCluster clusterDetail = selectedCluster != null ? selectedCluster : hoveredCluster;
        GatheringNode detail = selectedNode != null ? selectedNode : hoveredNode;
        float detailY = insights.entityY() + 4;
        float detailX = insightsX + PADDING;
        float detailWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        if (insightsSidebarOpen
                && clusterDetail != null
                && isHovered(mx, my, detailX, detailY, detailWidth, CLUSTER_DETAIL_HEIGHT)) {
            actions.copy(clusterCoords(clusterDetail));
            return true;
        }
        if (insightsSidebarOpen
                && clusterDetail == null
                && detail != null
                && isHovered(mx, my, detailX, detailY, detailWidth, NODE_DETAIL_HEIGHT)) {
            actions.copy(nodeCoords(detail));
            return true;
        }

        if (!viewport.isInsideScreen(mx, my)) {
            return false;
        }
        if (hoveredNode != null) {
            actions.copy(nodeCoords(hoveredNode));
            return true;
        }
        if (hoveredCluster != null) {
            actions.copy(clusterCoords(hoveredCluster));
            return true;
        }
        return false;
    }

    private static String nodeCoords(GatheringNode node) {
        return node.x() + " " + node.y() + " " + node.z();
    }

    private static String clusterCoords(GatheringNodeCluster cluster) {
        return Math.round(cluster.centerX()) + " " + Math.round(cluster.centerZ());
    }

    private static String totemCoords(Placement placement) {
        return Math.round(placement.x()) + " " + Math.round(placement.z());
    }

    private Placement gatheringTotemPlacementAt(
            List<Placement> placements,
            MapViewport viewport,
            float mouseX,
            float mouseY) {
        Placement closestMarker = null;
        double closestMarkerDistance = 12;
        for (Placement placement : placements) {
            float bestX = viewport.worldToScreenX(placement.x());
            float bestZ = viewport.worldToScreenZ(placement.z());
            double distance = markerDistance(mouseX - bestX, mouseY - bestZ);
            if (distance <= closestMarkerDistance) {
                closestMarker = placement;
                closestMarkerDistance = distance;
            }
        }
        if (closestMarker != null) {
            return closestMarker;
        }
        if (totemPanel.selected() != null
                && placements.contains(totemPanel.selected())
                && isGatheringTotemPlacementHovered(
                        totemPanel.selected(),
                        viewport,
                        mouseX,
                        mouseY)) {
            return totemPanel.selected();
        }
        for (Placement placement : placements) {
            if (placement == totemPanel.selected()) {
                continue;
            }
            if (isGatheringTotemPlacementHovered(placement, viewport, mouseX, mouseY)) {
                if (placement.validCenterHull().size() < 3) {
                    return placement;
                }
            }
        }
        return GatheringTotemHitTester.containingHull(
                placements,
                totemPanel.selected(),
                viewport.screenToWorldX(mouseX),
                viewport.screenToWorldZ(mouseY));
    }

    private static boolean isGatheringTotemPlacementHovered(
            Placement placement,
            MapViewport viewport,
            float mouseX,
            float mouseY) {
        List<Position> hull = placement.validCenterHull();
        if (hull.size() == 1) {
            return Math.hypot(
                            mouseX - viewport.worldToScreenX(hull.getFirst().x()),
                            mouseY - viewport.worldToScreenZ(hull.getFirst().z()))
                    <= 9;
        }
        if (hull.size() == 2) {
            return distanceToSegment(
                            mouseX,
                            mouseY,
                            viewport.worldToScreenX(hull.getFirst().x()),
                            viewport.worldToScreenZ(hull.getFirst().z()),
                            viewport.worldToScreenX(hull.getLast().x()),
                            viewport.worldToScreenZ(hull.getLast().z()))
                    <= 6;
        }
        return GatheringTotemHitTester.contains(
                hull,
                viewport.screenToWorldX(mouseX),
                viewport.screenToWorldZ(mouseY));
    }

    private static double distanceToSegment(
            double pointX,
            double pointY,
            double startX,
            double startY,
            double endX,
            double endY) {
        double dx = endX - startX;
        double dy = endY - startY;
        if (dx == 0 && dy == 0) {
            return Math.hypot(pointX - startX, pointY - startY);
        }
        double t = clamp(
                ((pointX - startX) * dx + (pointY - startY) * dy) / (dx * dx + dy * dy),
                0,
                1);
        return Math.hypot(pointX - (startX + t * dx), pointY - (startY + t * dy));
    }

    private void renderNodeTooltip(UiCanvas canvas, GatheringNode node) {
        String title = node.resource() + " Lv. " + node.level();
        String subtitle = nodeCoords(node);
        float x = tooltipX(180);
        float y = Math.max(8, frame.mouseY() + 12);
        canvas.fillRect(x, y, 180, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 180, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, title, color(MAP_TEXT), 164, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 11, subtitle, color(MAP_SUBTEXT), 164, TextAlignment.LEFT);
    }

    private void renderGatheringTotemTooltip(UiCanvas canvas, Placement placement) {
        int index = totemPanel.placements().indexOf(placement);
        String title = "#"
                + (index < 0 ? "?" : index + 1)
                + " of "
                + totemPanel.placements().size()
                + " · "
                + placement.nodeCount()
                + " nodes";
        String subtitle = totemCoords(placement) + " · Right-click to copy";
        float x = tooltipX(210);
        float y = Math.max(8, frame.mouseY() + 12);
        canvas.fillRect(x, y, 210, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 210, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, title, color(MAP_TEXT), 194, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 11, subtitle, color(MAP_SUBTEXT), 194, TextAlignment.LEFT);
    }

    private void renderClusterTooltip(UiCanvas canvas, GatheringNodeCluster cluster) {
        String title = cluster.resource() + " | score " + cluster.score() + "%";
        String subtitle = cluster.nodeCount() + " nodes | " + Math.round(cluster.averageSpacing()) + "m";
        float x = tooltipX(200);
        float y = Math.max(8, frame.mouseY() + 12);
        canvas.fillRect(x, y, 200, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 200, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, title, color(MAP_TEXT), 184, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 11, subtitle, color(MAP_SUBTEXT), 184, TextAlignment.LEFT);
    }

    private void renderTerritoryTooltip(UiCanvas canvas, GuildTerritory territory) {
        String subtitle = cachedTerritoryNodeCounts.getOrDefault(territory.name(), 0) + " gathering nodes";
        float x = tooltipX(200);
        float y = Math.max(8, frame.mouseY() + 12);
        canvas.fillRect(x, y, 200, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 200, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, territory.name(), color(MAP_TEXT), 184, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 11, subtitle, color(MAP_SUBTEXT), 184, TextAlignment.LEFT);
    }

    private void refreshClusterAnalysisIfNeeded() {
        List<GatheringNode> sourceNodes = nodes.apply(cachedNodeSource);
        GuildTerritoryIndex currentTerritoryIndex = territoryService.index();
        boolean territoryIndexChanged = currentTerritoryIndex != territoryIndex;
        if (territoryIndexChanged) {
            territoryIndex = currentTerritoryIndex;
            restoreSelectedTerritory();
        }
        boolean sourceNodesChanged = sourceNodes != cachedSourceNodes;
        long settingsVersion = mapSettings.version();
        String key = clusterKey();
        if (sourceNodes == cachedSourceNodes && settingsVersion == cachedSettingsVersion && key.equals(cachedClusterKey)) {
            return;
        }
        cachedSourceNodes = sourceNodes;
        if (sourceNodesChanged || territoryIndexChanged) {
            cachedTerritoryNodeCounts = countNodesByTerritory(sourceNodes);
        }
        cachedSettingsVersion = settingsVersion;
        cachedClusterKey = key;
        GatheringClusterCache.Result result = clusterCache.getOrCompute(
                sourceNodes,
                selectedResourceFilters,
                professionToggles,
                territoryIndex,
                gatheringAnalysisScope,
                selectedTerritory == null ? null : selectedTerritory.name(),
                clusterScoreMode,
                mapSettings.clusterEps(),
                mapSettings.clusterMinSamples());
        cachedFilteredNodes = result.filteredNodes();
        cachedResourceOptions = result.resourceOptions();
        cachedClusters = result.clusters();
        gatheringAnalysisVersion++;
        refreshSelectedTerritoryMatchingCount();
        clusterLayer.clear();
        hoveredNode = null;
        hoveredCluster = null;
        clearInvalidSelections();
    }

    private String gatheringTotemKey() {
        String clusterSelection = totemPanel.target() == GatheringTotemSearchTarget.SELECTED_CLUSTER
                ? selectedCluster == null
                        ? "none"
                        : selectedCluster.id()
                                + ":"
                                + selectedCluster.resource()
                                + ":"
                                + selectedCluster.nodes().hashCode()
                : "global";
        return cachedClusterKey
                + "|analysis:"
                + gatheringAnalysisVersion
                + "|totem:"
                + totemPanel.target().name()
                + "|"
                + clusterSelection;
    }

    private Position currentPlayerTotemPosition() {
        if (SeqClient.mc.player == null) {
            return null;
        }
        return new Position(SeqClient.mc.player.getX(), SeqClient.mc.player.getZ());
    }

    private String clusterKey() {
        return String.join("\u0000", selectedResourceFilters).toLowerCase(Locale.ROOT)
                + "|"
                + professionToggles.getOrDefault(GatheringProfession.WOODCUTTING, true)
                + professionToggles.getOrDefault(GatheringProfession.MINING, true)
                + professionToggles.getOrDefault(GatheringProfession.FARMING, true)
                + professionToggles.getOrDefault(GatheringProfession.FISHING, true)
                + "|"
                + territoryIndex.contentHash()
                + "|"
                + gatheringAnalysisScope.name()
                + "|"
                + (selectedTerritory == null ? "" : selectedTerritory.name())
                + "|"
                + clusterScoreMode.name();
    }

    private boolean shouldRenderClusters() {
        return showClusters && !cachedClusters.isEmpty() && frame.viewport().pixelsPerBlock() < NODE_DETAIL_PIXELS_PER_BLOCK;
    }

    private boolean shouldRenderClusterBadges() {
        return showClusters && !cachedClusters.isEmpty() && frame.viewport().pixelsPerBlock() < CLUSTER_BADGE_PIXELS_PER_BLOCK;
    }

    private List<String> resourceDropdownOptions() {
        String query = resourceInputFocused ? resourceSearch.trim().toLowerCase(Locale.ROOT) : "";
        if (query.isEmpty()) {
            return java.util.stream.Stream.concat(java.util.stream.Stream.of(""), cachedResourceOptions.stream()).toList();
        }
        java.util.stream.Stream<String> allResourcesMatch = query.length() >= 3 && "all resources".startsWith(query)
                ? java.util.stream.Stream.of("")
                : java.util.stream.Stream.empty();
        List<String> prefixMatches = cachedResourceOptions.stream()
                .filter(resource -> resource.toLowerCase(Locale.ROOT).startsWith(query))
                .toList();
        List<String> substringMatches = cachedResourceOptions.stream()
                .filter(resource -> {
                    String normalized = resource.toLowerCase(Locale.ROOT);
                    return !normalized.startsWith(query) && normalized.contains(query);
                })
                .toList();
        return java.util.stream.Stream.concat(
                        allResourcesMatch,
                        java.util.stream.Stream.concat(prefixMatches.stream(), substringMatches.stream()))
                .toList();
    }

    private List<GuildTerritory> territoryDropdownOptions() {
        String query = territoryInputFocused ? territorySearch.trim().toLowerCase(Locale.ROOT) : "";
        if (query.isEmpty()) {
            return territoryIndex.territories();
        }
        List<GuildTerritory> prefixMatches = territoryIndex.territories().stream()
                .filter(territory -> territory.name().toLowerCase(Locale.ROOT).startsWith(query))
                .toList();
        List<GuildTerritory> substringMatches = territoryIndex.territories().stream()
                .filter(territory -> {
                    String name = territory.name().toLowerCase(Locale.ROOT);
                    return !name.startsWith(query) && name.contains(query);
                })
                .toList();
        return java.util.stream.Stream.concat(prefixMatches.stream(), substringMatches.stream()).toList();
    }

    private void restoreSelectedTerritory() {
        String selectedName = mapSettings.selectedTerritoryName();
        selectedTerritory = territoryIndex.territory(selectedName);
        if (selectedName != null && selectedTerritory == null) {
            mapSettings.setSelectedTerritoryName(null);
            gatheringAnalysisScope = GatheringAnalysisScope.ALL;
            mapSettings.setGatheringAnalysisScope(gatheringAnalysisScope);
        }
    }

    private static int territoryNodeCount(GuildTerritory territory, List<GatheringNode> nodes) {
        if (territory == null || nodes == null || nodes.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (GatheringNode node : nodes) {
            if (territory.contains(node.x(), node.z())) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Integer> countNodesByTerritory(List<GatheringNode> nodes) {
        Map<String, Integer> counts = new HashMap<>();
        for (GuildTerritory territory : territoryIndex.territories()) {
            int count = territoryNodeCount(territory, nodes);
            if (count > 0) {
                counts.put(territory.name(), count);
            }
        }
        return Map.copyOf(counts);
    }

    private void refreshSelectedTerritoryMatchingCount() {
        selectedTerritoryMatchingNodeCount = territoryNodeCount(selectedTerritory, cachedFilteredNodes);
    }

    private static String territoryBoundsLabel(GuildTerritory territory) {
        MapBounds bounds = territory.bounds();
        return Math.round(bounds.minX()) + ", " + Math.round(bounds.minZ()) + " to "
                + Math.round(bounds.maxX()) + ", " + Math.round(bounds.maxZ());
    }

    private String selectedResourceLabel() {
        if (selectedResourceFilters.isEmpty()) {
            return "All resources";
        }
        if (selectedResourceFilters.size() == 1) {
            return selectedResourceFilters.iterator().next();
        }
        return selectedResourceFilters.size() + " resources";
    }

    private void clearInvalidSelections() {
        if (selectedNode != null && !cachedFilteredNodes.contains(selectedNode)) {
            selectedNode = null;
        }
        if (selectedCluster != null && cachedClusters.stream().noneMatch(cluster -> cluster == selectedCluster)) {
            selectedCluster = null;
        }
    }

    private static double markerDistance(double deltaX, double deltaY) {
        return Math.max(Math.abs(deltaX), Math.abs(deltaY));
    }

    private static String displayProfession(GatheringProfession profession) {
        String lower = profession.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private void applyResourceAutocompleteSelection() {
        String search = resourceSearch.trim();
        if (search.isEmpty()) {
            toggleResourceFilter("", false);
            return;
        }
        if (search.length() >= 3 && "all resources".startsWith(search.toLowerCase(Locale.ROOT))) {
            toggleResourceFilter("", false);
            return;
        }
        String exactMatch = cachedResourceOptions.stream()
                .filter(resource -> resource.equalsIgnoreCase(search))
                .findFirst()
                .orElse(null);
        if (exactMatch != null) {
            toggleResourceFilter(exactMatch, false);
            return;
        }
        List<String> options = resourceDropdownOptions();
        if (!options.isEmpty()) {
            toggleResourceFilter(options.get(0), false);
        }
    }

    private void applyTerritoryAutocompleteSelection() {
        String search = territorySearch.trim();
        GuildTerritory match = territoryIndex.territories().stream()
                .filter(territory -> territory.name().equalsIgnoreCase(search))
                .findFirst()
                .orElse(null);
        if (match == null) {
            List<GuildTerritory> options = territoryDropdownOptions();
            match = options.isEmpty() ? null : options.getFirst();
        }
        if (match != null) {
            selectTerritory(match, true);
        }
        closeTerritorySearch();
    }

    private void selectTerritory(GuildTerritory territory, boolean centerOnTerritory) {
        selectedTerritory = territory;
        mapSettings.setSelectedTerritoryName(territory == null ? null : territory.name());
        selectedNode = null;
        selectedCluster = null;
        cachedClusterKey = "";
        refreshSelectedTerritoryMatchingCount();
        if (territory == null || !centerOnTerritory) {
            return;
        }
        float screenWidth = frame.width();
        float screenHeight = frame.height();
        double width = Math.max(1, territory.bounds().maxX() - territory.bounds().minX());
        double height = Math.max(1, territory.bounds().maxZ() - territory.bounds().minZ());
        double xScale = Math.max(1, frame.viewport().screenWidth()) / width;
        double zScale = Math.max(1, screenHeight) / height;
        actions.center(territory.centerX(), territory.centerZ(), clamp(Math.min(xScale, zScale) * 0.48, MIN_PIXELS_PER_BLOCK, TERRITORY_FOCUS_MAX_PIXELS_PER_BLOCK));
    }

    private GatheringAnalysisScope scopeAt(float mouseX) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / GatheringAnalysisScope.values().length;
        int index = (int) ((mouseX - PADDING) / segmentWidth);
        if (index < 0 || index >= GatheringAnalysisScope.values().length) {
            return null;
        }
        return GatheringAnalysisScope.values()[index];
    }

    private void closeResourceSearch() {
        resourceDropdownOpen = false;
        resourceInputFocused = false;
        resourceSearch = "";
        resourceDropdownScroll = 0;
    }

    private void closeTerritorySearch() {
        territoryDropdownOpen = false;
        territoryInputFocused = false;
        territorySearch = "";
        territoryDropdownScroll = 0;
    }

    private void toggleResourceFilter(String resource, boolean keepOpen) {
        String nextResource = resource == null ? "" : resource;
        if (nextResource.isBlank()) {
            selectedResourceFilters.clear();
        } else if (!selectedResourceFilters.add(nextResource)) {
            selectedResourceFilters.remove(nextResource);
        }
        mapSettings.setResourceFilters(selectedResourceFilters);
        if (!keepOpen) {
            resourceSearch = "";
        }
        resourceDropdownOpen = keepOpen;
        resourceInputFocused = keepOpen;
        resourceDropdownScroll = 0;
        selectedNode = null;
        selectedCluster = null;
        cachedClusterKey = "";
    }

    private static int clampResourceDropdownScroll(int scroll, int optionCount) {
        return clampDropdownScroll(scroll, optionCount, RESOURCE_DROPDOWN_VISIBLE_ROWS);
    }

    private static int clampDropdownScroll(int scroll, int optionCount, int visibleRows) {
        return DropdownMenu.clampScroll(scroll, optionCount, visibleRows);
    }

    private InsightsLayout insightsLayout() {
        float overviewY = 60;
        float y = overviewY + (showDebugInfo ? 122 : 90);
        if (totemPanel.enabled()) {
            y += 48;
        }
        float territoryY = -1;
        if (selectedTerritory != null) {
            territoryY = y;
            y += TERRITORY_DETAIL_HEIGHT + 26;
        }
        float entityY = y;
        y += CLUSTER_DETAIL_HEIGHT + 26;
        return new InsightsLayout(overviewY, territoryY, entityY, y, -1);
    }

    SidebarLayout sidebarLayout() {
        float y = 58;
        float centerY = y;
        y += BUTTON_HEIGHT + 18;
        float mapPanelY = y;
        y += PANEL_HEADER_HEIGHT;
        float sourceLabelY = -1;
        float sourceY = -1;
        float territoryToggleY = -1;
        float scopeLabelY = -1;
        float scopeY = -1;
        float territoryLabelY = -1;
        float territoryInputY = -1;
        if (panelExpanded(WorldMapSidebarPanel.MAP_AND_TERRITORY)) {
            y += 8;
            sourceLabelY = y;
            y += 12;
            sourceY = y;
            y += BUTTON_HEIGHT + 14;
            territoryToggleY = y;
            y += BUTTON_HEIGHT + 14;
            scopeLabelY = y;
            y += 12;
            scopeY = y;
            y += BUTTON_HEIGHT + 14;
            territoryLabelY = y;
            y += 12;
            territoryInputY = y;
            y += INPUT_HEIGHT + 8;
        }
        y += PANEL_GAP;
        float analysisPanelY = y;
        y += PANEL_HEADER_HEIGHT;
        float clustersY = -1;
        if (panelExpanded(WorldMapSidebarPanel.GATHERING_ANALYSIS)) {
            y += 8;
            clustersY = y;
            y += BUTTON_HEIGHT + 8;
        }
        y += PANEL_GAP;
        float filtersPanelY = y;
        y += PANEL_HEADER_HEIGHT;
        float resourceLabelY = -1;
        float resourceInputY = -1;
        float professionLabelY = -1;
        float professionStartY = -1;
        if (panelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS)) {
            y += 8;
            resourceLabelY = y;
            y += 12;
            resourceInputY = y;
            y += INPUT_HEIGHT + 14;
            professionLabelY = y;
            y += 12;
            professionStartY = y;
            y += (TOGGLE_HEIGHT + 6) * gatheringProfessions().size();
        }
        y += PANEL_GAP;
        float totemPanelY = y;
        y = totemSolverLayout(totemPanelY).endY();
        return new SidebarLayout(
                centerY,
                mapPanelY,
                sourceLabelY,
                sourceY,
                territoryToggleY,
                scopeLabelY,
                scopeY,
                territoryLabelY,
                territoryInputY,
                analysisPanelY,
                clustersY,
                totemPanelY,
                filtersPanelY,
                resourceLabelY,
                resourceInputY,
                professionLabelY,
                professionStartY,
                y);
    }

    private GatheringTotemPanel.Layout totemSolverLayout(float panelY) {
        return GatheringTotemPanel.layout(panelY, panelExpanded(WorldMapSidebarPanel.TOTEM_SOLVER));
    }

    private GatheringTotemSession.Request totemRequest() {
        String key = gatheringTotemKey();
        if (cachedTotemRequest != null && cachedTotemRequest.key().equals(key)) return cachedTotemRequest;
        boolean clusterTarget = totemPanel.target() == GatheringTotemSearchTarget.SELECTED_CLUSTER;
        cachedTotemRequest = new GatheringTotemSession.Request(key, cachedFilteredNodes, selectedResourceFilters,
                gatheringAnalysisScope == GatheringAnalysisScope.SELECTED_TERRITORY ? selectedTerritory : null,
                clusterTarget && selectedCluster != null ? selectedCluster.nodes() : List.of(),
                clusterTarget && selectedCluster == null);
        return cachedTotemRequest;
    }

    private void refreshGatheringTotemPlacement() {
        if (!totemPanel.enabled()) {
            cachedTotemRequest = null;
            totemPanel.reset();
            hoveredGatheringTotemPlacement = null;
            return;
        }
        Placement before = totemPanel.selected();
        totemPanel.refresh(totemRequest(), currentPlayerTotemPosition());
        if (before != totemPanel.selected()) hoveredGatheringTotemPlacement = null;
    }

    private void refreshGatheringTotemPlacementNow() {
        if (!totemPanel.enabled()) return;
        hoveredGatheringTotemPlacement = null;
        totemPanel.refreshNow(totemRequest(), currentPlayerTotemPosition());
    }

    record SidebarLayout(
            float centerY,
            float mapPanelY,
            float sourceLabelY,
            float sourceY,
            float territoryToggleY,
            float scopeLabelY,
            float scopeY,
            float territoryLabelY,
            float territoryInputY,
            float analysisPanelY,
            float clustersY,
            float totemPanelY,
            float filtersPanelY,
            float resourceLabelY,
            float resourceInputY,
            float professionLabelY,
            float professionStartY,
            float endY) {}

    private record InsightsLayout(
            float overviewY,
            float territoryY,
            float entityY,
            float topClustersY,
            float eventDetailY) {}

    boolean click(WorldMapFrame frame) {
        this.frame = frame;
        float mx = frame.mouseX(), my = frame.mouseY(), sidebarMy = my + sidebar.scroll();
        float screenWidth = frame.width(), screenHeight = frame.height();
        SidebarLayout layout = sidebarLayout();

        if (territoryDropdownOpen) {
            List<GuildTerritory> territories = territoryDropdownOptions();
            int visibleRows = Math.min(TERRITORY_DROPDOWN_VISIBLE_ROWS, territories.size());
            float dropdownY = layout.territoryInputY() - sidebar.scroll() + INPUT_HEIGHT;
            int optionIndex = DropdownMenu.optionAt(mx, my, PADDING, dropdownY, SIDEBAR_WIDTH - PADDING * 2,
                    RESOURCE_DROPDOWN_ROW_HEIGHT, visibleRows, territoryDropdownScroll);
            if (optionIndex >= 0 && optionIndex < territories.size()) {
                selectTerritory(territories.get(optionIndex), true);
                closeTerritorySearch();
                return true;
            }
        }
        if (resourceDropdownOpen) {
            List<String> resources = resourceDropdownOptions();
            int visibleRows = Math.min(RESOURCE_DROPDOWN_VISIBLE_ROWS, resources.size());
            float dropdownY = layout.resourceInputY() - sidebar.scroll() + INPUT_HEIGHT;
            int optionIndex = DropdownMenu.optionAt(mx, my, PADDING, dropdownY, SIDEBAR_WIDTH - PADDING * 2,
                    RESOURCE_DROPDOWN_ROW_HEIGHT, visibleRows, resourceDropdownScroll);
            if (optionIndex >= 0 && optionIndex < resources.size()) {
                toggleResourceFilter(resources.get(optionIndex), true);
                return true;
            }
        }

        if (isHovered(mx, sidebarMy, PADDING, layout.mapPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.MAP_AND_TERRITORY);
            return true;
        }
        if (isHovered(mx, sidebarMy, PADDING, layout.analysisPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.GATHERING_ANALYSIS);
            return true;
        }
        if (isHovered(mx, sidebarMy, PADDING, layout.totemPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.TOTEM_SOLVER);
            return true;
        }
        if (isHovered(mx, sidebarMy, PADDING, layout.filtersPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.RESOURCE_FILTERS);
            return true;
        }
        if (layout.sourceY() >= 0 && my >= SIDEBAR_PANEL_TOP && my <= screenHeight
                && isHovered(mx, sidebarMy, PADDING, layout.sourceY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            float sourceWidth = (SIDEBAR_WIDTH - PADDING * 2 - SPLIT_CONTROL_GAP) / 2;
            GatheringNodeSource source = mx <= PADDING + sourceWidth ? GatheringNodeSource.STATIC
                    : mx >= PADDING + sourceWidth + SPLIT_CONTROL_GAP ? GatheringNodeSource.WYNN_API : null;
            if (source != null && source != mapSettings.gatheringNodeSource()) {
                mapSettings.setGatheringNodeSource(source);
                if (SeqClient.getConfigManager() != null) SeqClient.getConfigManager().save();
                closeSearchDropdowns();
                refresh(frame);
            }
            return true;
        }
        float territoryToggleWidth = SIDEBAR_WIDTH - PADDING * 2;
        if (layout.territoryToggleY() >= 0
                && isHovered(mx, sidebarMy, PADDING, layout.territoryToggleY(), territoryToggleWidth, BUTTON_HEIGHT)) {
            if (!showTerritories) {
                showTerritories = true;
                mapSettings.setShowTerritories(true);
                return true;
            }
            float splitWidth = (territoryToggleWidth - SPLIT_CONTROL_GAP) / 2f;
            if (mx <= PADDING + splitWidth) {
                showTerritories = false;
                mapSettings.setShowTerritories(false);
                hoveredTerritory = null;
            } else if (mx >= PADDING + splitWidth + SPLIT_CONTROL_GAP) {
                showTerritoryNames = !showTerritoryNames;
                mapSettings.setShowTerritoryNames(showTerritoryNames);
            }
            return true;
        }
        if (layout.scopeY() >= 0
                && isHovered(mx, sidebarMy, PADDING, layout.scopeY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            GatheringAnalysisScope scope = scopeAt(mx);
            if (scope != null && (scope != GatheringAnalysisScope.SELECTED_TERRITORY || selectedTerritory != null)) {
                gatheringAnalysisScope = scope;
                mapSettings.setGatheringAnalysisScope(scope);
                selectedNode = null;
                selectedCluster = null;
                cachedClusterKey = "";
            }
            return true;
        }
        if (layout.territoryInputY() >= 0
                && isHovered(mx, sidebarMy, PADDING, layout.territoryInputY(), SequoiaUiStyle.searchWidth(SIDEBAR_WIDTH - PADDING * 2), INPUT_HEIGHT)) {
            boolean shouldOpen = !territoryDropdownOpen;
            closeResourceSearch();
            territoryInputFocused = shouldOpen;
            territoryDropdownOpen = shouldOpen;
            territorySearch = "";
            territoryDropdownScroll = 0;
            return true;
        }
        if (layout.clustersY() >= 0
                && isHovered(mx, sidebarMy, PADDING, layout.clustersY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            if (!showClusters) {
                showClusters = true;
                mapSettings.setShowClusters(true);
                selectedCluster = null;
                selectedNode = null;
                return true;
            }
            float fullWidth = SIDEBAR_WIDTH - PADDING * 2;
            float splitWidth = (fullWidth - SPLIT_CONTROL_GAP) / 2f;
            if (mx <= PADDING + splitWidth) {
                showClusters = false;
                mapSettings.setShowClusters(false);
                selectedCluster = null;
                selectedNode = null;
            } else if (mx >= PADDING + splitWidth + SPLIT_CONTROL_GAP) {
                clusterScoreMode = clusterScoreMode.next();
                mapSettings.setClusterScoreMode(clusterScoreMode);
                selectedCluster = null;
                cachedClusterKey = "";
            }
            return true;
        }
        GatheringTotemPanel.Layout totemLayout = totemSolverLayout(layout.totemPanelY());
        if (totemPanel.click(totemLayout, mx, sidebarMy,
                this::refreshGatheringTotemPlacementNow, this::centerOnGatheringTotemPlacement,
                placement -> actions.copy(totemCoords(placement)))) return true;
        if (layout.resourceInputY() >= 0
                && isHovered(mx, sidebarMy, PADDING, layout.resourceInputY(), SequoiaUiStyle.searchWidth(SIDEBAR_WIDTH - PADDING * 2), INPUT_HEIGHT)) {
            boolean shouldOpen = !resourceDropdownOpen;
            closeTerritorySearch();
            resourceInputFocused = shouldOpen;
            resourceDropdownOpen = shouldOpen;
            resourceSearch = "";
            resourceDropdownScroll = 0;
            return true;
        }
        if (resourceDropdownOpen || territoryDropdownOpen) {
            closeSearchDropdowns();
            return true;
        }

        if (layout.professionStartY() >= 0) {
            float toggleY = layout.professionStartY();
            for (GatheringProfession profession : gatheringProfessions()) {
                if (isHovered(mx, sidebarMy, PADDING, toggleY, SIDEBAR_WIDTH - PADDING * 2, TOGGLE_HEIGHT)) {
                    boolean enabled = !professionToggles.getOrDefault(profession, true);
                    professionToggles.put(profession, enabled);
                    mapSettings.setProfessionEnabled(profession, enabled);
                    selectedNode = null;
                    selectedCluster = null;
                    return true;
                }
                toggleY += TOGGLE_HEIGHT + 6;
            }
        }

        MapViewport viewport = frame.viewport();
        if (viewport.isInsideScreen(mx, my)) {
            Placement clickedPlacement = totemPanel.enabled()
                    ? gatheringTotemPlacementAt(visibleGatheringTotemPlacements(), viewport, mx, my)
                    : null;
            if (clickedPlacement != null) {
                totemPanel.select(clickedPlacement);
                hoveredGatheringTotemPlacement = clickedPlacement;
                closeSearchDropdowns();
                return true;
            }
            boolean clusterMode = shouldRenderClusters();
            GatheringNodeCluster clickedCluster = clusterMode || hoveredNode == null ? hoveredCluster : null;
            GatheringNode clickedNode = clusterMode ? null : hoveredNode;
            selectedCluster = clickedCluster;
            selectedNode = clickedNode;
            if (selectedNode != null) {
                selectedCluster = null;
            } else if (selectedCluster == null && hoveredTerritory != null) {
                selectTerritory(hoveredTerritory, false);
            }
            actions.startDragging();
            hoveredNode = null;
            hoveredCluster = null;
            hoveredTerritory = null;
            closeSearchDropdowns();
            return true;
        }
        return false;
    }

    boolean scroll(WorldMapFrame frame, double scrollY) {
        this.frame = frame;
        float mx = frame.mouseX(), my = frame.mouseY();
        SidebarLayout layout = sidebarLayout();
        if (territoryDropdownOpen) {
            List<GuildTerritory> territories = territoryDropdownOptions();
            int visibleRows = Math.min(TERRITORY_DROPDOWN_VISIBLE_ROWS, territories.size());
            float dropdownY = layout.territoryInputY() - sidebar.scroll() + INPUT_HEIGHT;
            if (isHovered(mx, my, PADDING, dropdownY, SIDEBAR_WIDTH - PADDING * 2, visibleRows * RESOURCE_DROPDOWN_ROW_HEIGHT)) {
                territoryDropdownScroll = clampDropdownScroll(
                        territoryDropdownScroll + (scrollY > 0 ? -1 : 1),
                        territories.size(),
                        TERRITORY_DROPDOWN_VISIBLE_ROWS);
                return true;
            }
        }
        if (resourceDropdownOpen) {
            List<String> resources = resourceDropdownOptions();
            int visibleRows = Math.min(RESOURCE_DROPDOWN_VISIBLE_ROWS, resources.size());
            float dropdownY = layout.resourceInputY() - sidebar.scroll() + INPUT_HEIGHT;
            if (isHovered(mx, my, PADDING, dropdownY, SIDEBAR_WIDTH - PADDING * 2, visibleRows * RESOURCE_DROPDOWN_ROW_HEIGHT)) {
                resourceDropdownScroll = clampResourceDropdownScroll(resourceDropdownScroll + (scrollY > 0 ? -1 : 1), resources.size());
                return true;
            }
        }
        GatheringTotemPanel.Layout totemLayout = totemSolverLayout(layout.totemPanelY());
        if (totemPanel.scroll(totemLayout, mx, my + sidebar.scroll(), scrollY > 0 ? -1 : 1)) return true;
        if (mx >= 0 && mx <= SIDEBAR_WIDTH && my >= SIDEBAR_PANEL_TOP && my <= frame.height()) {
            sidebar.scroll(scrollY, frame.height());
            return true;
        }
        return false;
    }

    boolean keyPressed(int keyCode) {
        if (territoryInputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeTerritorySearch();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyTerritoryAutocompleteSelection();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!territorySearch.isEmpty()) {
                    territorySearch = territorySearch.substring(0, territorySearch.length() - 1);
                }
                territoryDropdownOpen = true;
                territoryDropdownScroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                territorySearch = "";
                territoryDropdownOpen = true;
                territoryDropdownScroll = 0;
                return true;
            }
            return true;
        }
        if (resourceInputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                resourceDropdownOpen = false;
                resourceInputFocused = false;
                resourceSearch = "";
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyResourceAutocompleteSelection();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!resourceSearch.isEmpty()) {
                    resourceSearch = resourceSearch.substring(0, resourceSearch.length() - 1);
                }
                resourceDropdownOpen = true;
                resourceDropdownScroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                resourceSearch = "";
                resourceDropdownOpen = true;
                resourceDropdownScroll = 0;
                return true;
            }
            return true;
        }
        if ((resourceDropdownOpen || territoryDropdownOpen)
                && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeSearchDropdowns();
            return true;
        }
        return false;
    }

    boolean charTyped(String typedText) {
        if (territoryInputFocused) {
            if (typedText != null) {
                territorySearch += typedText;
                territoryDropdownOpen = true;
                territoryDropdownScroll = 0;
            }
            return true;
        }
        if (resourceInputFocused) {
            if (typedText != null) {
                resourceSearch += typedText;
                resourceDropdownOpen = true;
                resourceDropdownScroll = 0;
            }
            return true;
        }
        return false;
    }

    boolean clickInsights(WorldMapFrame frame, float x) {
        this.frame = frame;
        float mx = frame.mouseX(), my = frame.mouseY(), screenHeight = frame.height();
        InsightsLayout layout = insightsLayout();
        float contentX = x + PADDING, contentWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        if (showClusters && !cachedClusters.isEmpty()) {
            float rowY = layout.topClustersY() + 12;
            int availableRows = Math.max(0, (int) ((screenHeight - rowY - PADDING) / 40));
            int rowCount = Math.min(Math.min(SIDEBAR_CLUSTER_LIMIT, cachedClusters.size()), availableRows);
            for (int index = 0; index < rowCount; index++) {
                if (isHovered(mx, my, contentX, rowY, contentWidth, 34)) {
                    selectedCluster = cachedClusters.get(index);
                    selectedNode = null;
                    actions.center(selectedCluster.centerX(), selectedCluster.centerZ(), Math.max(frame.viewport().pixelsPerBlock(), 0.20));
                    return true;
                }
                rowY += 40;
            }
        }
        return true;
    }

    void renderMap(UiCanvas canvas, WorldMapFrame frame) {
        this.frame = frame;
        MapViewport viewport = frame.viewport();
        hoveredTerritory = GatheringTerritoryLayer.renderTerritories(canvas, frame, territoryIndex, selectedTerritory, showTerritories);
        boolean clusterMode = shouldRenderClusters();
        if (clusterMode) {
            hoveredNode = null;
        }
        if (!showClusters || cachedClusters.isEmpty()) {
            hoveredCluster = null;
        }
        if (showClusters && !cachedClusters.isEmpty()) {
            hoveredCluster = clusterLayer.renderHulls(canvas, frame, cachedClusters, selectedCluster, !frame.dragging());
            if (clusterMode) {
                clusterLayer.renderBadges(canvas, viewport, cachedClusters, selectedCluster, hoveredCluster, true);
            }
        }
        if (!clusterMode) {
            renderNodes(canvas, viewport, cachedFilteredNodes);
            if (shouldRenderClusterBadges()) {
                clusterLayer.renderBadges(canvas, viewport, cachedClusters, selectedCluster, hoveredCluster, false);
            }
        }
        renderGatheringTotemPlacements(canvas, viewport);
        if (cachedSourceNodes.isEmpty()) {
            float statusX = viewport.screenX() + viewport.screenWidth() / 2;
            float statusY = viewport.screenY() + viewport.screenHeight() / 2;
            drawFittedText(canvas, statusX, statusY, 14, nodeStatus.apply(cachedNodeSource), color(MAP_TEXT),
                    Math.max(0, viewport.screenWidth() - PADDING * 2), TextAlignment.CENTER);
        }
        actions.renderPlayer(canvas, viewport);
        GatheringTerritoryLayer.renderTerritoryNames(canvas, viewport, territoryIndex, selectedTerritory, hoveredTerritory, showTerritories, showTerritoryNames);
        if (!frame.dragging() && hoveredGatheringTotemPlacement != null) {
            renderGatheringTotemTooltip(canvas, hoveredGatheringTotemPlacement);
        } else if (!frame.dragging() && hoveredCluster != null && (clusterMode || hoveredNode == null)) {
            renderClusterTooltip(canvas, hoveredCluster);
        } else if (!frame.dragging() && hoveredNode == null && hoveredTerritory != null) {
            renderTerritoryTooltip(canvas, hoveredTerritory);
        }
    }

}
