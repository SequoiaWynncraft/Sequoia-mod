package com.seqwawa.seq.ui;

import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.nanovg.NVGPaint;
import org.lwjgl.system.MemoryUtil;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.ClusterScoreMode;
import com.seqwawa.seq.map.GatheringAnalysisScope;
import com.seqwawa.seq.map.GatheringClusterCache;
import com.seqwawa.seq.map.GatheringMapImageService;
import com.seqwawa.seq.map.GatheringNode;
import com.seqwawa.seq.map.GatheringNodeCluster;
import com.seqwawa.seq.map.GatheringNodeService;
import com.seqwawa.seq.map.GatheringProfession;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.map.GuildTerritoryService;
import com.seqwawa.seq.map.MapCalibration;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapDisplayMode;
import com.seqwawa.seq.map.MapViewport;
import com.seqwawa.seq.map.WorldEventDefinition;
import com.seqwawa.seq.map.WorldEventDisplayFilter;
import com.seqwawa.seq.map.WorldEventFilters;
import com.seqwawa.seq.map.WorldEventLocation;
import com.seqwawa.seq.map.WorldEventMarkerHitTester;
import com.seqwawa.seq.map.WorldEventService;
import com.seqwawa.seq.map.WorldMapSettings;
import com.seqwawa.seq.map.WorldMapSidebarPanel;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.map.GatheringMapImageService.TileKey;
import com.seqwawa.seq.map.GatheringMapImageService.TileSet;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.nvg.NVGContext;
import com.seqwawa.seq.utils.rendering.nvg.NVGWrapper;

import static org.lwjgl.nanovg.NanoVG.*;

public class WorldMapScreen extends Screen {
    private static final float SIDEBAR_WIDTH = 230;
    private static final float INSIGHTS_SIDEBAR_WIDTH = 250;
    private static final float INSIGHTS_RAIL_WIDTH = 28;
    private static final float PADDING = 12;
    private static final float BUTTON_HEIGHT = 24;
    private static final float TOGGLE_HEIGHT = 22;
    private static final float INPUT_HEIGHT = 24;
    private static final float SIDEBAR_HEADER_HEIGHT = 44;
    private static final float SIDEBAR_PANEL_TOP = 166;
    private static final float SIDEBAR_SCROLL_STEP = 28;
    private static final float PANEL_HEADER_HEIGHT = 28;
    private static final float PANEL_GAP = 10;
    private static final float PANEL_LABEL_WIDTH = 116;
    private static final float PANEL_SUMMARY_WIDTH = 50;
    private static final float SPLIT_CONTROL_GAP = 4;
    private static final long CENTER_PLAYER_WARNING_DURATION_MS = 6_767;
    private static final float CLUSTER_DETAIL_HEIGHT = 110;
    private static final float NODE_DETAIL_HEIGHT = 58;
    private static final float TERRITORY_DETAIL_HEIGHT = 76;
    private static final float RESOURCE_DROPDOWN_ROW_HEIGHT = 20;
    private static final int RESOURCE_DROPDOWN_VISIBLE_ROWS = 8;
    private static final int TERRITORY_DROPDOWN_VISIBLE_ROWS = 8;
    private static final int WORLD_EVENT_DROPDOWN_VISIBLE_ROWS = 8;
    private static final float WORLD_EVENT_DETAIL_HEIGHT = 122;
    private static final String WORLD_EVENT_MARKER_ASSET = "world_event";
    private static final float MIN_HULL_PADDING_PX = 4f;
    private static final float MAX_HULL_PADDING_PX = 12f;
    private static final int HULL_SMOOTHING_PASSES = 2;
    private static final double MIN_PIXELS_PER_BLOCK = 0.035;
    private static final double MAX_PIXELS_PER_BLOCK = 2.5;
    private static final double NODE_DETAIL_PIXELS_PER_BLOCK = 0.42;
    private static final double CLUSTER_BADGE_PIXELS_PER_BLOCK = 0.65;
    private static final double TERRITORY_FOCUS_MAX_PIXELS_PER_BLOCK = 1.25;
    private static final int SIDEBAR_CLUSTER_LIMIT = 5;
    private static final Color SIDEBAR_COLOR = new Color(18, 18, 24, 235);
    private static final Color MAP_TINT = new Color(4, 7, 10, 32);
    private static final Color HEADER_COLOR = new Color(28, 28, 38, 230);
    private static final Color CONTROL_COLOR = new Color(42, 42, 54, 220);
    private static final Color CONTROL_HOVER = new Color(62, 62, 82, 235);
    private static final Color CONTROL_ACTIVE = new Color(92, 74, 138, 235);
    private static final Color BORDER_COLOR = new Color(92, 92, 115, 180);
    private static final Color TEXT_COLOR = new Color(240, 240, 245, 255);
    private static final Color SUBTEXT_COLOR = new Color(175, 175, 190, 255);
    private static final Color TITLE_COLOR = new Color(170, 145, 230, 255);
    private static final Color PLAYER_COLOR = new Color(255, 255, 255, 255);
    private static final Color SELECTED_CLUSTER_COLOR = new Color(235, 58, 58, 255);
    private static final Color TERRITORY_COLOR = new Color(75, 194, 205, 175);
    private static final Color SELECTED_TERRITORY_COLOR = new Color(255, 204, 82, 235);
    private static final Color WORLD_EVENT_COLOR = new Color(62, 190, 218, 245);
    private static final Color TRACKED_WORLD_EVENT_COLOR = new Color(255, 194, 72, 250);

    private final Screen parent;
    private final GatheringNodeService nodeService = GatheringNodeService.getInstance();
    private final GuildTerritoryService territoryService = GuildTerritoryService.getInstance();
    private final GatheringMapImageService mapImageService = GatheringMapImageService.getInstance();
    private final WorldMapSettings mapSettings = WorldMapSettings.getInstance();
    private final GatheringClusterCache clusterCache = GatheringClusterCache.getInstance();
    private final WorldEventService worldEventService = WorldEventService.getInstance();
    private final EnumMap<GatheringProfession, Boolean> professionToggles = new EnumMap<>(GatheringProfession.class);

    private double centerX = (MapCalibration.MIN_WORLD_X + MapCalibration.MAX_WORLD_X) / 2.0;
    private double centerZ = (MapCalibration.MIN_WORLD_Z + MapCalibration.MAX_WORLD_Z) / 2.0;
    private double pixelsPerBlock = 0.08;
    private boolean initializedViewport;
    private boolean draggingMap;
    private boolean resourceDropdownOpen;
    private boolean resourceInputFocused;
    private int resourceDropdownScroll;
    private boolean territoryDropdownOpen;
    private boolean territoryInputFocused;
    private int territoryDropdownScroll;
    private boolean worldEventDropdownOpen;
    private boolean worldEventInputFocused;
    private boolean worldEventDropdownTrackedOnly;
    private int worldEventDropdownScroll;
    private float sidebarScroll;
    private float sidebarContentHeight;
    private boolean insightsSidebarOpen;
    private long centerPlayerWarningUntilMs;
    private String resourceSearch = "";
    private String territorySearch = "";
    private String worldEventSearch = "";
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
    private MapDisplayMode displayMode = MapDisplayMode.GATHERING;
    private WorldEventDisplayFilter worldEventDisplayFilter = WorldEventDisplayFilter.ALL;
    private List<WorldEventDefinition> allWorldEvents = List.of();
    private List<WorldEventDefinition> visibleWorldEvents = List.of();
    private WorldEventDefinition hoveredWorldEvent;
    private int hoveredWorldEventLocationIndex = -1;
    private WorldEventDefinition selectedWorldEvent;
    private List<GatheringNode> cachedSourceNodes = List.of();
    private List<GatheringNode> cachedFilteredNodes = List.of();
    private List<GatheringNodeCluster> cachedClusters = List.of();
    private Map<String, Integer> cachedTerritoryNodeCounts = Map.of();
    private int selectedTerritoryMatchingNodeCount;
    private final Map<GatheringNodeCluster, ClusterOutlineShape> clusterOutlineShapes = new IdentityHashMap<>();
    private double clusterOutlineScale = Double.NaN;
    private List<String> cachedResourceOptions = List.of();
    private String cachedClusterKey = "";
    private long cachedSettingsVersion = -1;
    private int mapImageHandle;
    private boolean mapImageLoadAttempted;
    private long loadedMapImageVersion = -1;
    private final Map<TileKey, Integer> tileImageHandles = new HashMap<>();
    private String loadedTileVersion = "";
    private long loadedTileContentVersion = -1;
    private TileRange cachedVisibleTileRange;
    private TileRange cachedPrefetchTileRange;
    private List<TileKey> cachedVisibleTiles = List.of();
    private List<TileKey> cachedPrefetchTiles = List.of();
    private long lastTileRequestAtMs;
    private float nvgMouseX;
    private float nvgMouseY;

    public WorldMapScreen(Screen parent) {
        super(Component.literal("Sequoia Map"));
        this.parent = parent;
        professionToggles.putAll(mapSettings.professionToggles());
        selectedResourceFilters.addAll(mapSettings.resourceFilters());
        showClusters = mapSettings.showClusters();
        showTerritories = mapSettings.showTerritories();
        showTerritoryNames = mapSettings.showTerritoryNames();
        showDebugInfo = mapSettings.showDebugInfo();
        clusterScoreMode = mapSettings.clusterScoreMode();
        gatheringAnalysisScope = mapSettings.gatheringAnalysisScope();
        displayMode = mapSettings.displayMode();
        worldEventDisplayFilter = mapSettings.worldEventDisplayFilter();
        insightsSidebarOpen = mapSettings.insightsSidebarOpen();
        territoryService.loadBundledTerritories();
        territoryIndex = territoryService.index();
        restoreSelectedTerritory();
        nodeService.loadBundledNodes();
        mapImageService.requestLoad();
        SeqClient.getWorldEventManager().requestMapRefresh();
    }

    @Override
    public void removed() {
        NVGContext.renderDeferred(nvg -> {
            if (mapImageHandle != 0) {
                nvgDeleteImage(nvg, mapImageHandle);
                mapImageHandle = 0;
            }
            clearTileImageHandles(nvg);
        });
        super.removed();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        nvgMouseX = NVGContext.mouseX(mouseX);
        nvgMouseY = NVGContext.mouseY(mouseY);

        float screenWidth = uiScreenWidth();
        float screenHeight = uiScreenHeight();
        showDebugInfo = mapSettings.showDebugInfo();
        float mapX = SIDEBAR_WIDTH;
        float mapY = 0;
        float mapW = Math.max(1, screenWidth - SIDEBAR_WIDTH - insightsSidebarInset());
        float mapH = Math.max(1, screenHeight);

        if (!initializedViewport) {
            initializedViewport = true;
            fitFullMap(mapW, mapH);
        }

        if (displayMode == MapDisplayMode.GATHERING) {
            refreshClusterAnalysisIfNeeded();
        } else {
            refreshWorldEvents();
        }
        MapViewport viewport = new MapViewport(centerX, centerZ, pixelsPerBlock, mapX, mapY, mapW, mapH);
        NVGContext.renderDeferred(nvg -> renderNvg(nvg, viewport));
    }

    private void renderNvg(long nvg, MapViewport viewport) {
        String fontName = SeqClient.getFontManager().getSelectedFont();
        nvgFontFace(nvg, fontName);

        renderMapBackground(nvg, viewport);
        NVGWrapper.drawRect(nvg, viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight(), MAP_TINT);
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            renderWorldEvents(nvg, viewport);
            renderPlayer(nvg, viewport);
            renderSidebar(nvg);
            renderInsightsSidebar(nvg);
            return;
        }

        renderTerritories(nvg, viewport);
        boolean clusterMode = shouldRenderClusters();
        if (clusterMode) {
            hoveredNode = null;
        }
        if (!showClusters || cachedClusters.isEmpty()) {
            hoveredCluster = null;
        }
        if (showClusters && !cachedClusters.isEmpty()) {
            renderClusterHulls(nvg, viewport, !draggingMap);
            if (clusterMode) {
                renderClusterBadges(nvg, viewport, true);
            }
        }
        if (!clusterMode) {
            renderNodes(nvg, viewport, cachedFilteredNodes);
            if (shouldRenderClusterBadges()) {
                renderClusterBadges(nvg, viewport, false);
            }
        }
        renderPlayer(nvg, viewport);
        renderTerritoryNames(nvg, viewport);
        if (!draggingMap && hoveredCluster != null && (clusterMode || hoveredNode == null)) {
            renderClusterTooltip(nvg, hoveredCluster);
        } else if (!draggingMap && hoveredNode == null && hoveredTerritory != null) {
            renderTerritoryTooltip(nvg, hoveredTerritory);
        }
        renderSidebar(nvg);
        renderInsightsSidebar(nvg);
    }

    private void renderTerritories(long nvg, MapViewport viewport) {
        hoveredTerritory = null;
        if (!showTerritories) {
            return;
        }
        if (!draggingMap && viewport.isInsideScreen(nvgMouseX, nvgMouseY)) {
            hoveredTerritory = territoryIndex.territoryAt(
                    viewport.screenToWorldX(nvgMouseX),
                    viewport.screenToWorldZ(nvgMouseY));
        }
        MapBounds visibleBounds = viewport.visibleBounds();
        nvgScissor(nvg, viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (GuildTerritory territory : territoryIndex.territories()) {
            MapBounds bounds = territory.bounds();
            if (!intersects(visibleBounds, bounds)) {
                continue;
            }
            float x = viewport.worldToScreenX(bounds.minX());
            float y = viewport.worldToScreenZ(bounds.minZ());
            float width = viewport.worldToScreenX(bounds.maxX()) - x;
            float height = viewport.worldToScreenZ(bounds.maxZ()) - y;
            boolean selected = territory.equals(selectedTerritory);
            boolean hovered = territory.equals(hoveredTerritory);
            Color color = selected ? SELECTED_TERRITORY_COLOR : TERRITORY_COLOR;
            if (selected || hovered) {
                int alpha = selected ? 38 : 24;
                NVGWrapper.drawRect(nvg, x, y, width, height, new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            }
            NVGWrapper.drawRectOutline(
                    nvg,
                    x,
                    y,
                    width,
                    height,
                    selected || hovered ? 1.8f : 0.8f,
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), selected || hovered ? 235 : 115));
        }
        nvgResetScissor(nvg);
    }

    private void refreshWorldEvents() {
        allWorldEvents = worldEventService.snapshot().events();
        visibleWorldEvents = WorldEventFilters.visibleEvents(
                allWorldEvents,
                worldEventDisplayFilter,
                SeqClient.getConfigManager().trackedWorldEventIds());
        selectedWorldEvent = WorldEventFilters.retainVisibleSelection(selectedWorldEvent, visibleWorldEvents);
    }

    private void renderWorldEvents(long nvg, MapViewport viewport) {
        hoveredWorldEvent = null;
        hoveredWorldEventLocationIndex = -1;
        boolean allowHover = !draggingMap && viewport.isInsideScreen(nvgMouseX, nvgMouseY);
        MapBounds visibleBounds = viewport.visibleBounds();
        Set<String> tracked = SeqClient.getConfigManager().trackedWorldEventIds();
        AssetManager.Asset markerAsset = worldEventMarkerAsset();

        if (allowHover) {
            List<WorldEventMarkerHitTester.Candidate> candidates = new ArrayList<>();
            for (WorldEventDefinition event : visibleWorldEvents) {
                for (int locationIndex = 0; locationIndex < event.locations().size(); locationIndex++) {
                    WorldEventLocation location = event.locations().get(locationIndex);
                    if (!visibleBounds.contains(location.x(), location.z())) {
                        continue;
                    }
                    float x = viewport.worldToScreenX(location.x());
                    float y = viewport.worldToScreenZ(location.z());
                    candidates.add(new WorldEventMarkerHitTester.Candidate(
                            event,
                            locationIndex,
                            Math.hypot(nvgMouseX - x, nvgMouseY - y)));
                }
            }
            WorldEventMarkerHitTester.Candidate closest = WorldEventMarkerHitTester.closest(candidates, 9);
            if (closest != null) {
                hoveredWorldEvent = closest.event();
                hoveredWorldEventLocationIndex = closest.locationIndex();
            }
        }

        nvgScissor(nvg, viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (WorldEventDefinition event : visibleWorldEvents) {
            boolean eventSelected = selectedWorldEvent != null && selectedWorldEvent.runId().equals(event.runId());
            boolean eventTracked = tracked.contains(event.internalName());
            for (int locationIndex = 0; locationIndex < event.locations().size(); locationIndex++) {
                WorldEventLocation location = event.locations().get(locationIndex);
                if (!visibleBounds.contains(location.x(), location.z())) {
                    continue;
                }
                float x = viewport.worldToScreenX(location.x());
                float y = viewport.worldToScreenZ(location.z());
                float areaRadius = (float) (location.radius() * viewport.pixelsPerBlock());
                if (areaRadius >= 5) {
                    Color areaColor = eventTracked ? TRACKED_WORLD_EVENT_COLOR : WORLD_EVENT_COLOR;
                    drawCircleOutline(nvg, x, y, areaRadius, 1, new Color(
                            areaColor.getRed(), areaColor.getGreen(), areaColor.getBlue(), eventSelected ? 150 : 65));
                }

                Color markerColor = eventTracked ? TRACKED_WORLD_EVENT_COLOR : WORLD_EVENT_COLOR;
                boolean highlighted = eventSelected || (event.equals(hoveredWorldEvent) && locationIndex == hoveredWorldEventLocationIndex);
                if (markerAsset == null) {
                    drawCircle(nvg, x, y, highlighted ? 8 : 7, new Color(0, 0, 0, 190));
                    drawCircle(nvg, x, y, highlighted ? 5.5f : 4.5f, eventSelected ? PLAYER_COLOR : markerColor);
                } else {
                    float outerRadius = highlighted ? 9 : 8;
                    float assetSize = highlighted ? 12 : 11;
                    drawCircle(nvg, x, y, outerRadius, new Color(0, 0, 0, 210));
                    drawCircle(nvg, x, y, outerRadius - 1.5f, markerColor);
                    NVGWrapper.drawImage(
                            nvg,
                            markerAsset,
                            x - assetSize / 2,
                            y - assetSize / 2,
                            assetSize,
                            assetSize,
                            255);
                    if (eventSelected) {
                        drawCircleOutline(nvg, x, y, outerRadius + 1, 1.5f, PLAYER_COLOR);
                    }
                }
            }
        }
        nvgResetScissor(nvg);
        if (hoveredWorldEvent != null) {
            renderWorldEventTooltip(nvg, hoveredWorldEvent, hoveredWorldEventLocationIndex);
        }
    }

    private void renderTerritoryNames(long nvg, MapViewport viewport) {
        if (!showTerritories || !showTerritoryNames) {
            return;
        }
        MapBounds visibleBounds = viewport.visibleBounds();
        for (GuildTerritory territory : territoryIndex.territories()) {
            MapBounds bounds = territory.bounds();
            if (!intersects(visibleBounds, bounds)) {
                continue;
            }
            float x = viewport.worldToScreenX(bounds.minX());
            float y = viewport.worldToScreenZ(bounds.minZ());
            float width = viewport.worldToScreenX(bounds.maxX()) - x;
            float height = viewport.worldToScreenZ(bounds.maxZ()) - y;
            TerritoryLabelLayout label = fitTerritoryLabel(nvg, territory.name(), width - 8, height - 6);
            if (label == null) {
                continue;
            }

            float clipX = Math.max(x, viewport.screenX());
            float clipY = Math.max(y, viewport.screenY());
            float clipMaxX = Math.min(x + width, viewport.screenX() + viewport.screenWidth());
            float clipMaxY = Math.min(y + height, viewport.screenY() + viewport.screenHeight());
            if (clipMaxX <= clipX || clipMaxY <= clipY) {
                continue;
            }

            Color textColor = territory.equals(selectedTerritory)
                    ? SELECTED_TERRITORY_COLOR
                    : territory.equals(hoveredTerritory) ? new Color(185, 247, 250, 255) : TEXT_COLOR;
            float totalHeight = label.lines().size() * label.lineHeight();
            float lineY = y + (height - totalHeight) / 2f + label.lineHeight() / 2f;
            nvgSave(nvg);
            nvgScissor(nvg, clipX, clipY, clipMaxX - clipX, clipMaxY - clipY);
            for (String line : label.lines()) {
                drawText(
                        nvg,
                        x + width / 2f + 1,
                        lineY + 1,
                        label.fontSize(),
                        line,
                        new Color(0, 0, 0, 210),
                        NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
                drawText(
                        nvg,
                        x + width / 2f,
                        lineY,
                        label.fontSize(),
                        line,
                        textColor,
                        NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
                lineY += label.lineHeight();
            }
            nvgRestore(nvg);
        }
    }

    private static TerritoryLabelLayout fitTerritoryLabel(long nvg, String name, float maxWidth, float maxHeight) {
        if (maxWidth < 4 || maxHeight < 6) {
            return null;
        }
        for (float fontSize = 11; fontSize >= 6; fontSize--) {
            nvgFontSize(nvg, fontSize);
            List<String> lines = wrapTerritoryName(nvg, name, maxWidth);
            float lineHeight = fontSize + 2;
            if (!lines.isEmpty() && lines.size() * lineHeight <= maxHeight) {
                return new TerritoryLabelLayout(lines, fontSize, lineHeight);
            }
        }
        return null;
    }

    private static List<String> wrapTerritoryName(long nvg, String name, float maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (textWidth(nvg, candidate) <= maxWidth) {
                currentLine.setLength(0);
                currentLine.append(candidate);
                continue;
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
            }
            if (textWidth(nvg, word) <= maxWidth) {
                currentLine.append(word);
                continue;
            }
            List<String> pieces = splitTerritoryWord(nvg, word, maxWidth);
            if (pieces.isEmpty()) {
                return List.of();
            }
            lines.addAll(pieces.subList(0, pieces.size() - 1));
            currentLine.append(pieces.getLast());
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return List.copyOf(lines);
    }

    private static List<String> splitTerritoryWord(long nvg, String word, float maxWidth) {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (int index = 0; index < word.length(); index++) {
            char character = word.charAt(index);
            String candidate = piece.toString() + character;
            if (textWidth(nvg, candidate) <= maxWidth) {
                piece.append(character);
                continue;
            }
            if (piece.isEmpty()) {
                return List.of();
            }
            pieces.add(piece.toString());
            piece.setLength(0);
            if (textWidth(nvg, String.valueOf(character)) > maxWidth) {
                return List.of();
            }
            piece.append(character);
        }
        if (!piece.isEmpty()) {
            pieces.add(piece.toString());
        }
        return pieces;
    }

    private static boolean intersects(MapBounds left, MapBounds right) {
        return left.maxX() >= right.minX()
                && left.minX() <= right.maxX()
                && left.maxZ() >= right.minZ()
                && left.minZ() <= right.maxZ();
    }

    private void renderMapBackground(long nvg, MapViewport viewport) {
        int image = mapImageHandle(nvg);
        if (image != 0) {
            renderFullMapImage(nvg, viewport, image);
        }
        renderMapTiles(nvg, viewport);
    }

    private void renderFullMapImage(long nvg, MapViewport viewport, int image) {
        float x = viewport.worldToScreenX(MapCalibration.MIN_WORLD_X);
        float y = viewport.worldToScreenZ(MapCalibration.MIN_WORLD_Z);
        float width = viewport.worldToScreenX(MapCalibration.MAX_WORLD_X) - x;
        float height = viewport.worldToScreenZ(MapCalibration.MAX_WORLD_Z) - y;
        if (width <= 0 || height <= 0) {
            return;
        }

        try {
            nvgScissor(nvg, viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
            try (NVGPaint paint = NVGPaint.calloc()) {
                nvgImagePattern(nvg, x, y, width, height, 0, image, 1f, paint);
                nvgBeginPath(nvg);
                nvgRect(nvg, x, y, width, height);
                nvgFillPaint(nvg, paint);
                nvgFill(nvg);
                nvgClosePath(nvg);
            }
        } finally {
            nvgResetScissor(nvg);
        }
    }

    private void renderMapTiles(long nvg, MapViewport viewport) {
        var manifest = mapImageService.manifest().orElse(null);
        TileSet tileSet = manifest == null ? null : manifest.tiles();
        if (tileSet == null || !"tiles".equalsIgnoreCase(manifest.preferredMode())) {
            if (!tileImageHandles.isEmpty()) {
                clearTileImageHandles(nvg);
                loadedTileVersion = "";
            }
            resetTileRangeCache();
            return;
        }
        if (!manifest.version().equals(loadedTileVersion)) {
            clearTileImageHandles(nvg);
            loadedTileVersion = manifest.version();
            resetTileRangeCache();
        }

        TileRange visibleRange = visibleTileRange(viewport, tileSet, 0);
        TileRange prefetchRange = visibleTileRange(viewport, tileSet, 1);
        boolean visibleRangeChanged = !visibleRange.equals(cachedVisibleTileRange);
        boolean prefetchRangeChanged = !prefetchRange.equals(cachedPrefetchTileRange);
        if (visibleRangeChanged) {
            cachedVisibleTileRange = visibleRange;
            cachedVisibleTiles = tilesInRange(visibleRange);
        }
        if (prefetchRangeChanged) {
            cachedPrefetchTileRange = prefetchRange;
            cachedPrefetchTiles = tilesInRange(prefetchRange);
        }

        long now = System.currentTimeMillis();
        if (visibleRangeChanged || prefetchRangeChanged || now - lastTileRequestAtMs >= 1_000L) {
            mapImageService.requestTiles(cachedVisibleTiles, cachedPrefetchTiles);
            lastTileRequestAtMs = now;
        }

        long tileContentVersion = mapImageService.tileVersion();
        boolean loadMissingTileHandles = visibleRangeChanged || tileContentVersion != loadedTileContentVersion;

        nvgScissor(nvg, viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        try {
            for (TileKey key : cachedVisibleTiles) {
                int tileImage = tileImageHandle(nvg, key, loadMissingTileHandles);
                if (tileImage != 0) {
                    renderTile(nvg, viewport, tileSet, key, tileImage);
                }
            }
        } finally {
            nvgResetScissor(nvg);
        }
        loadedTileContentVersion = tileContentVersion;
    }

    private int tileImageHandle(long nvg, TileKey key, boolean loadMissing) {
        Integer existing = tileImageHandles.get(key);
        if (existing != null) {
            return existing;
        }
        if (!loadMissing) {
            return 0;
        }
        byte[] imageBytes = mapImageService.cachedTileBytes(key);
        if (imageBytes == null || imageBytes.length == 0) {
            return 0;
        }
        var byteBuffer = MemoryUtil.memAlloc(imageBytes.length);
        try {
            byteBuffer.put(imageBytes);
            byteBuffer.flip();
            int handle = NVGWrapper.loadImageFromInputStream(nvg, byteBuffer);
            tileImageHandles.put(key, handle);
            return handle;
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[GatheringMap] Could not load map tile {}.", key.id(), exception);
            return 0;
        } finally {
            MemoryUtil.memFree(byteBuffer);
        }
    }

    private void renderTile(long nvg, MapViewport viewport, TileSet tileSet, TileKey key, int image) {
        int pixelX0 = key.x() * tileSet.tileSize();
        int pixelY0 = key.y() * tileSet.tileSize();
        int pixelX1 = Math.min(tileSet.width(), pixelX0 + tileSet.tileSize());
        int pixelY1 = Math.min(tileSet.height(), pixelY0 + tileSet.tileSize());
        double worldX0 = imageToWorldX(pixelX0, tileSet.width());
        double worldZ0 = imageToWorldZ(pixelY0, tileSet.height());
        double worldX1 = imageToWorldX(pixelX1, tileSet.width());
        double worldZ1 = imageToWorldZ(pixelY1, tileSet.height());
        float x = viewport.worldToScreenX(worldX0);
        float y = viewport.worldToScreenZ(worldZ0);
        float width = viewport.worldToScreenX(worldX1) - x;
        float height = viewport.worldToScreenZ(worldZ1) - y;
        if (width <= 0 || height <= 0) {
            return;
        }

        try (NVGPaint paint = NVGPaint.calloc()) {
            nvgImagePattern(nvg, x, y, width, height, 0, image, 1f, paint);
            nvgBeginPath(nvg);
            nvgRect(nvg, x, y, width, height);
            nvgFillPaint(nvg, paint);
            nvgFill(nvg);
            nvgClosePath(nvg);
        }
    }

    private static TileRange visibleTileRange(MapViewport viewport, TileSet tileSet, int margin) {
        double minImageX = clampImageX(MapCalibration.worldToImageX(viewport.minWorldX(), tileSet.width()), tileSet);
        double maxImageX = clampImageX(MapCalibration.worldToImageX(viewport.maxWorldX(), tileSet.width()), tileSet);
        double minImageY = clampImageY(MapCalibration.worldToImageZ(viewport.minWorldZ(), tileSet.height()), tileSet);
        double maxImageY = clampImageY(MapCalibration.worldToImageZ(viewport.maxWorldZ(), tileSet.height()), tileSet);
        int minX = clampTile((int) Math.floor(minImageX / tileSet.tileSize()) - margin, tileSet.columns());
        int maxX = clampTile((int) Math.floor(maxImageX / tileSet.tileSize()) + margin, tileSet.columns());
        int minY = clampTile((int) Math.floor(minImageY / tileSet.tileSize()) - margin, tileSet.rows());
        int maxY = clampTile((int) Math.floor(maxImageY / tileSet.tileSize()) + margin, tileSet.rows());
        return new TileRange(minX, maxX, minY, maxY);
    }

    private static List<TileKey> tilesInRange(TileRange range) {
        List<TileKey> tiles = new ArrayList<>();
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                tiles.add(new TileKey(x, y));
            }
        }
        return tiles;
    }

    private static double imageToWorldX(double imageX, int imageWidth) {
        return MapCalibration.MIN_WORLD_X
                + (imageX / imageWidth) * (MapCalibration.MAX_WORLD_X - MapCalibration.MIN_WORLD_X);
    }

    private static double imageToWorldZ(double imageY, int imageHeight) {
        return MapCalibration.MIN_WORLD_Z
                + (imageY / imageHeight) * (MapCalibration.MAX_WORLD_Z - MapCalibration.MIN_WORLD_Z);
    }

    private static double clampImageX(double value, TileSet tileSet) {
        return Math.max(0, Math.min(tileSet.width() - 1, value));
    }

    private static double clampImageY(double value, TileSet tileSet) {
        return Math.max(0, Math.min(tileSet.height() - 1, value));
    }

    private static int clampTile(int value, int count) {
        return Math.max(0, Math.min(count - 1, value));
    }

    private void clearTileImageHandles(long nvg) {
        for (int handle : tileImageHandles.values()) {
            nvgDeleteImage(nvg, handle);
        }
        tileImageHandles.clear();
    }

    private void resetTileRangeCache() {
        cachedVisibleTileRange = null;
        cachedPrefetchTileRange = null;
        cachedVisibleTiles = List.of();
        cachedPrefetchTiles = List.of();
        loadedTileContentVersion = -1;
        lastTileRequestAtMs = 0;
    }

    private int mapImageHandle(long nvg) {
        long imageVersion = mapImageService.version();
        if (mapImageHandle != 0 && loadedMapImageVersion == imageVersion) {
            return mapImageHandle;
        }
        if (mapImageHandle != 0) {
            nvgDeleteImage(nvg, mapImageHandle);
            mapImageHandle = 0;
        }
        if (mapImageLoadAttempted && loadedMapImageVersion == imageVersion) {
            return 0;
        }
        mapImageLoadAttempted = true;

        try {
            byte[] imageBytes = mapImageService.imageBytes();
            if (imageBytes.length == 0) {
                return 0;
            }
            var byteBuffer = MemoryUtil.memAlloc(imageBytes.length);
            try {
                byteBuffer.put(imageBytes);
                byteBuffer.flip();
                mapImageHandle = NVGWrapper.loadImageFromInputStream(nvg, byteBuffer);
                loadedMapImageVersion = mapImageService.version();
            } finally {
                MemoryUtil.memFree(byteBuffer);
            }
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn(
                    "[GatheringMap] Could not load {} map image.",
                    mapImageService.imageSource().name().toLowerCase(Locale.ROOT),
                    exception);
            mapImageHandle = 0;
            loadedMapImageVersion = imageVersion;
        }
        return mapImageHandle;
    }

    private void renderClusterHulls(long nvg, MapViewport viewport, boolean allowHover) {
        hoveredCluster = null;
        float bestHoverDistance = 18f;
        nvgScissor(nvg, viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (int index = cachedClusters.size() - 1; index >= 0; index--) {
            GatheringNodeCluster cluster = cachedClusters.get(index);
            float x = viewport.worldToScreenX(cluster.centerX());
            float y = viewport.worldToScreenZ(cluster.centerZ());
            ClusterOutlineShape outline = clusterOutlineShape(cluster, viewport.pixelsPerBlock());
            if (!outline.isVisible(viewport, x, y)) {
                continue;
            }
            float radius = clusterRadius(cluster);
            float distance = allowHover ? (float) Math.hypot(nvgMouseX - x, nvgMouseY - y) : Float.MAX_VALUE;
            boolean hovered = allowHover
                    && (distance <= Math.max(12, radius + 3)
                            || isPointInsideCluster(outline, x, y, nvgMouseX, nvgMouseY));
            if (hovered && distance < bestHoverDistance) {
                bestHoverDistance = distance;
                hoveredCluster = cluster;
            }
            boolean selected = cluster == selectedCluster;
            renderClusterOutline(nvg, viewport, cluster, outline, x, y, selected, selected || hovered);
        }
        nvgResetScissor(nvg);
    }

    private void renderClusterBadges(long nvg, MapViewport viewport, boolean overviewMode) {
        nvgScissor(nvg, viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        MapBounds visibleBounds = viewport.visibleBounds();
        GatheringNodeCluster hoveredBadge = null;
        GatheringNodeCluster selectedBadge = null;
        for (int index = cachedClusters.size() - 1; index >= 0; index--) {
            GatheringNodeCluster cluster = cachedClusters.get(index);
            if (!visibleBounds.contains(cluster.centerX(), cluster.centerZ())) {
                continue;
            }
            boolean selected = cluster == selectedCluster;
            boolean hovered = cluster == hoveredCluster;
            if (!overviewMode && !selected && !hovered) {
                continue;
            }
            float x = viewport.worldToScreenX(cluster.centerX());
            float y = viewport.worldToScreenZ(cluster.centerZ());
            if (hovered) {
                hoveredBadge = cluster;
                continue;
            }
            if (selected) {
                selectedBadge = cluster;
                continue;
            }
            drawClusterMarker(nvg, x, y, clusterRadius(cluster), cluster, false, false);
        }
        if (selectedBadge != null) {
            float x = viewport.worldToScreenX(selectedBadge.centerX());
            float y = viewport.worldToScreenZ(selectedBadge.centerZ());
            drawClusterMarker(nvg, x, y, clusterRadius(selectedBadge), selectedBadge, true, true);
        }
        if (hoveredBadge != null) {
            float x = viewport.worldToScreenX(hoveredBadge.centerX());
            float y = viewport.worldToScreenZ(hoveredBadge.centerZ());
            boolean selected = hoveredBadge == selectedCluster;
            drawClusterMarker(nvg, x, y, clusterRadius(hoveredBadge), hoveredBadge, selected, true);
        }
        nvgResetScissor(nvg);
    }

    private void renderClusterOutline(
            long nvg,
            MapViewport viewport,
            GatheringNodeCluster cluster,
            ClusterOutlineShape outline,
            float centerScreenX,
            float centerScreenY,
            boolean selected,
            boolean highlighted) {
        if (outline.points().isEmpty()) {
            return;
        }
        Color color = selected ? SELECTED_CLUSTER_COLOR : cluster.profession().color();
        Color fill = new Color(color.getRed(), color.getGreen(), color.getBlue(), highlighted ? 48 : 18);
        Color stroke = new Color(color.getRed(), color.getGreen(), color.getBlue(), highlighted ? 220 : 105);

        nvgBeginPath(nvg);
        ScreenPoint first = outline.points().getFirst();
        nvgMoveTo(nvg, centerScreenX + first.x(), centerScreenY + first.y());
        for (int index = 1; index < outline.points().size(); index++) {
            ScreenPoint point = outline.points().get(index);
            nvgLineTo(nvg, centerScreenX + point.x(), centerScreenY + point.y());
        }
        if (outline.points().size() > 2) {
            nvgClosePath(nvg);
            var fillColor = NVGContext.nvgColor(fill);
            nvgFillColor(nvg, fillColor);
            nvgFill(nvg);
            fillColor.free();
        }
        var strokeColor = NVGContext.nvgColor(stroke);
        nvgStrokeWidth(nvg, hullStrokeWidthForZoom(viewport.pixelsPerBlock(), highlighted));
        nvgStrokeColor(nvg, strokeColor);
        nvgStroke(nvg);
        strokeColor.free();
    }

    private void drawClusterMarker(long nvg, float x, float y, float radius, GatheringNodeCluster cluster, boolean selected, boolean highlighted) {
        Color color = selected ? SELECTED_CLUSTER_COLOR : cluster.profession().color();
        drawCircle(nvg, x, y, radius + 3, new Color(0, 0, 0, highlighted ? 205 : 150));
        drawCircle(nvg, x, y, radius, new Color(color.getRed(), color.getGreen(), color.getBlue(), highlighted ? 250 : 220));
        drawText(nvg, x, y + 1, clusterCountTextSize(cluster), String.valueOf(cluster.nodeCount()), TEXT_COLOR, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
    }

    private void renderNodes(long nvg, MapViewport viewport, List<GatheringNode> nodes) {
        hoveredNode = null;
        float bestHoverDistance = 10f;
        boolean allowHover = !draggingMap;
        MapBounds visibleBounds = viewport.visibleBounds();
        nvgScissor(nvg, viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (GatheringNode node : nodes) {
            if (!visibleBounds.contains(node.x(), node.z())) {
                continue;
            }
            float x = viewport.worldToScreenX(node.x());
            float y = viewport.worldToScreenZ(node.z());
            float radius = (float) Math.max(1.5, Math.min(4.0, pixelsPerBlock * 12.0));
            float distance = allowHover ? (float) Math.hypot(nvgMouseX - x, nvgMouseY - y) : Float.MAX_VALUE;
            boolean hovered = allowHover && distance <= Math.max(8, radius + 3);
            if (hovered && distance < bestHoverDistance) {
                bestHoverDistance = distance;
                hoveredNode = node;
            }
            boolean selected = selectedNode == node || (selectedCluster != null && selectedCluster.nodes().contains(node));
            Color color = selected ? PLAYER_COLOR : node.profession().color();
            drawCircle(nvg, x, y, selected || hovered ? Math.min(radius + 1.8f, 5.6f) : radius, new Color(0, 0, 0, 160));
            drawCircle(nvg, x, y, radius, color);
        }
        nvgResetScissor(nvg);
        if (hoveredNode != null) {
            renderNodeTooltip(nvg, hoveredNode);
        }
    }

    private void renderPlayer(long nvg, MapViewport viewport) {
        if (SeqClient.mc.player == null) {
            return;
        }
        double x = SeqClient.mc.player.getX();
        double z = SeqClient.mc.player.getZ();
        MapBounds visibleBounds = viewport.visibleBounds();
        if (!visibleBounds.contains(x, z)) {
            return;
        }
        float sx = viewport.worldToScreenX(x);
        float sy = viewport.worldToScreenZ(z);
        drawCircle(nvg, sx, sy, 8, new Color(0, 0, 0, 180));
        drawCircle(nvg, sx, sy, 5, PLAYER_COLOR);
    }

    private void renderSidebar(long nvg) {
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            renderWorldEventSidebar(nvg);
            return;
        }
        float screenHeight = uiScreenHeight();
        sidebarScroll = clampSidebarScroll(sidebarScroll, screenHeight);
        SidebarLayout layout = sidebarLayout();
        NVGWrapper.drawRect(nvg, 0, 0, SIDEBAR_WIDTH, screenHeight, SIDEBAR_COLOR);
        NVGWrapper.drawRect(nvg, 0, 0, SIDEBAR_WIDTH, SIDEBAR_HEADER_HEIGHT, HEADER_COLOR);
        drawText(nvg, SIDEBAR_WIDTH / 2f, 22, 18, "Sequoia Map", TITLE_COLOR, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);

        drawButton(nvg, PADDING, layout.backY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, "Back", false);
        drawButton(nvg, PADDING, layout.centerY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, centerPlayerButtonLabel(), false);
        drawMapModeControl(nvg, layout.modeY());
        nvgScissor(nvg, 0, SIDEBAR_PANEL_TOP, SIDEBAR_WIDTH, Math.max(0, screenHeight - SIDEBAR_PANEL_TOP));
        renderPanelHeader(
                nvg,
                sidebarY(layout.mapPanelY()),
                "Map & Territory",
                selectedTerritory == null ? gatheringAnalysisScope.label() : selectedTerritory.name(),
                WorldMapSidebarPanel.MAP_AND_TERRITORY);
        if (panelExpanded(WorldMapSidebarPanel.MAP_AND_TERRITORY)) {
            renderTerritoryToggles(nvg, sidebarY(layout.territoryToggleY()));
            drawText(nvg, PADDING, sidebarY(layout.scopeLabelY()), 12, "Gathering Scope", SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            drawScopeControl(nvg, sidebarY(layout.scopeY()));
            drawText(nvg, PADDING, sidebarY(layout.territoryLabelY()), 12, "Territory", SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            renderSearchInput(
                    nvg,
                    sidebarY(layout.territoryInputY()),
                    territoryDropdownOpen,
                    territoryInputFocused,
                    territorySearch,
                    selectedTerritory == null ? "Find territory" : selectedTerritory.name());
        }

        renderPanelHeader(
                nvg,
                sidebarY(layout.analysisPanelY()),
                "Gathering Analysis",
                showClusters ? clusterScoreMode.label() : "Nodes",
                WorldMapSidebarPanel.GATHERING_ANALYSIS);
        if (panelExpanded(WorldMapSidebarPanel.GATHERING_ANALYSIS)) {
            renderGatheringAnalysisToggles(nvg, sidebarY(layout.clustersY()));
        }

        renderPanelHeader(
                nvg,
                sidebarY(layout.filtersPanelY()),
                "Resource Filters",
                selectedResourceFilters.isEmpty() ? "All" : selectedResourceFilters.size() + " selected",
                WorldMapSidebarPanel.RESOURCE_FILTERS);
        if (panelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS)) {
            drawText(nvg, PADDING, sidebarY(layout.resourceLabelY()), 12, "Resource", SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            renderSearchInput(
                    nvg,
                    sidebarY(layout.resourceInputY()),
                    resourceDropdownOpen,
                    resourceInputFocused,
                    resourceSearch,
                    selectedResourceLabel());
            drawText(nvg, PADDING, sidebarY(layout.professionLabelY()), 12, "Professions", SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            float professionY = sidebarY(layout.professionStartY());
            for (GatheringProfession profession : gatheringProfessions()) {
                boolean active = professionToggles.getOrDefault(profession, true);
                drawToggle(nvg, PADDING, professionY, SIDEBAR_WIDTH - PADDING * 2, TOGGLE_HEIGHT, profession, active);
                professionY += TOGGLE_HEIGHT + 6;
            }
        }

        if (resourceDropdownOpen) {
            renderResourceDropdown(nvg, sidebarY(layout.resourceInputY()) + INPUT_HEIGHT);
        }
        if (territoryDropdownOpen) {
            renderTerritoryDropdown(nvg, sidebarY(layout.territoryInputY()) + INPUT_HEIGHT);
        }
        sidebarContentHeight = layout.endY() + PADDING;
        sidebarScroll = clampSidebarScroll(sidebarScroll, screenHeight);
        nvgResetScissor(nvg);
        renderSidebarScrollbar(nvg, screenHeight);
    }

    private void renderWorldEventSidebar(long nvg) {
        float screenHeight = uiScreenHeight();
        sidebarScroll = clampSidebarScroll(sidebarScroll, screenHeight);
        WorldEventSidebarLayout layout = worldEventSidebarLayout();
        NVGWrapper.drawRect(nvg, 0, 0, SIDEBAR_WIDTH, screenHeight, SIDEBAR_COLOR);
        NVGWrapper.drawRect(nvg, 0, 0, SIDEBAR_WIDTH, SIDEBAR_HEADER_HEIGHT, HEADER_COLOR);
        drawText(nvg, SIDEBAR_WIDTH / 2f, 22, 18, "Sequoia Map", TITLE_COLOR, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);

        drawButton(nvg, PADDING, layout.backY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, "Back", false);
        drawButton(nvg, PADDING, layout.centerY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, centerPlayerButtonLabel(), false);
        drawMapModeControl(nvg, layout.modeY());
        nvgScissor(nvg, 0, SIDEBAR_PANEL_TOP, SIDEBAR_WIDTH, Math.max(0, screenHeight - SIDEBAR_PANEL_TOP));

        renderPanelHeader(
                nvg,
                sidebarY(layout.displayPanelY()),
                "Event Display",
                worldEventDisplayFilter.label(),
                WorldMapSidebarPanel.EVENT_DISPLAY);
        if (panelExpanded(WorldMapSidebarPanel.EVENT_DISPLAY)) {
            drawText(nvg, PADDING, sidebarY(layout.filterLabelY()), 12, "Visible Events", SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            drawWorldEventFilterControl(nvg, sidebarY(layout.filterY()));
        }

        renderPanelHeader(
                nvg,
                sidebarY(layout.trackingPanelY()),
                "Tracking",
                SeqClient.getConfigManager().trackedWorldEventIds().size() + " tracked",
                WorldMapSidebarPanel.EVENT_TRACKING);
        if (panelExpanded(WorldMapSidebarPanel.EVENT_TRACKING)) {
            drawWorldEventTrackingListControl(nvg, sidebarY(layout.eventFilterY()));
            renderSearchInput(
                    nvg,
                    sidebarY(layout.eventInputY()),
                    worldEventDropdownOpen,
                    worldEventInputFocused,
                    worldEventSearch,
                    trackedWorldEventLabel());
        }

        if (worldEventDropdownOpen) {
            renderWorldEventDropdown(nvg, sidebarY(layout.eventInputY()) + INPUT_HEIGHT);
        }
        sidebarContentHeight = layout.endY() + PADDING;
        sidebarScroll = clampSidebarScroll(sidebarScroll, screenHeight);
        nvgResetScissor(nvg);
        renderSidebarScrollbar(nvg, screenHeight);
    }

    private void renderInsightsSidebar(long nvg) {
        float screenWidth = uiScreenWidth();
        float screenHeight = uiScreenHeight();
        if (!insightsSidebarOpen) {
            drawText(
                    nvg,
                    screenWidth - INSIGHTS_RAIL_WIDTH / 2f,
                    10 + BUTTON_HEIGHT / 2f,
                    16,
                    "<",
                    TEXT_COLOR,
                    NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
            return;
        }

        float x = screenWidth - INSIGHTS_SIDEBAR_WIDTH;
        InsightsLayout layout = insightsLayout();
        NVGWrapper.drawRect(nvg, x, 0, INSIGHTS_SIDEBAR_WIDTH, screenHeight, SIDEBAR_COLOR);
        NVGWrapper.drawRect(nvg, x, 0, INSIGHTS_SIDEBAR_WIDTH, SIDEBAR_HEADER_HEIGHT, HEADER_COLOR);
        drawText(nvg, x + PADDING, 22, 16, "Insights", TITLE_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawButton(nvg, x + INSIGHTS_SIDEBAR_WIDTH - PADDING - 24, 10, 24, BUTTON_HEIGHT, ">", false);
        nvgScissor(nvg, x, SIDEBAR_HEADER_HEIGHT, INSIGHTS_SIDEBAR_WIDTH, Math.max(0, screenHeight - SIDEBAR_HEADER_HEIGHT));
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            renderWorldEventInsights(nvg, x, layout);
        } else {
            renderGatheringInsights(nvg, x, screenHeight, layout);
        }
        nvgResetScissor(nvg);
    }

    private void renderGatheringInsights(long nvg, float x, float screenHeight, InsightsLayout layout) {
        float contentX = x + PADDING;
        float contentWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        drawInsightsSectionTitle(nvg, contentX, layout.overviewY(), "Overview");
        drawInsightRow(nvg, contentX, layout.overviewY() + 18, contentWidth, "Scope", gatheringAnalysisScope.label());
        drawInsightRow(nvg, contentX, layout.overviewY() + 34, contentWidth, "Matching nodes", String.valueOf(cachedFilteredNodes.size()));
        drawInsightRow(nvg, contentX, layout.overviewY() + 50, contentWidth, "Clusters", String.valueOf(cachedClusters.size()));
        if (showDebugInfo) {
            drawInsightRow(nvg, contentX, layout.overviewY() + 66, contentWidth, "Map source", displayMapImageSource());
            drawInsightRow(nvg, contentX, layout.overviewY() + 82, contentWidth, "HQ status", mapImageService.hqStatus());
        }

        if (selectedTerritory != null) {
            drawInsightsSectionTitle(nvg, contentX, layout.territoryY() - 8, "Territory");
            renderSelectedTerritoryDetail(nvg, contentX, layout.territoryY() + 4, contentWidth, selectedTerritory);
        }

        GatheringNodeCluster clusterDetail = selectedCluster != null ? selectedCluster : hoveredCluster;
        GatheringNode nodeDetail = selectedNode != null ? selectedNode : hoveredNode;
        drawInsightsSectionTitle(nvg, contentX, layout.entityY() - 8, "Selection");
        if (clusterDetail != null) {
            renderClusterDetail(nvg, contentX, layout.entityY() + 4, contentWidth, clusterDetail);
        } else if (nodeDetail != null) {
            renderNodeDetail(nvg, contentX, layout.entityY() + 4, contentWidth, nodeDetail);
        } else {
            drawFittedText(
                    nvg,
                    contentX,
                    layout.entityY() + 18,
                    11,
                    "Hover or select a node, cluster, or territory",
                    SUBTEXT_COLOR,
                    contentWidth,
                    NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        }

        if (!showClusters || cachedClusters.isEmpty()) {
            return;
        }
        float topY = layout.topClustersY();
        drawInsightsSectionTitle(nvg, contentX, topY, "Top Clusters");
        topY += 12;
        int availableRows = Math.max(0, (int) ((screenHeight - topY - PADDING) / 40));
        int rowCount = Math.min(Math.min(SIDEBAR_CLUSTER_LIMIT, cachedClusters.size()), availableRows);
        for (int index = 0; index < rowCount; index++) {
            GatheringNodeCluster cluster = cachedClusters.get(index);
            boolean active = cluster == selectedCluster;
            NVGWrapper.drawRect(nvg, contentX, topY, contentWidth, 34, active ? CONTROL_ACTIVE : CONTROL_COLOR);
            NVGWrapper.drawRectOutline(nvg, contentX, topY, contentWidth, 34, 1, BORDER_COLOR);
            drawFittedText(nvg, contentX + 8, topY + 11, 11, "#" + (index + 1) + " " + cluster.resource() + " | " + cluster.score() + "%", TEXT_COLOR, contentWidth - 16, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            drawFittedText(nvg, contentX + 8, topY + 26, 10, cluster.nodeCount() + " nodes | " + Math.round(cluster.averageSpacing()) + "m", SUBTEXT_COLOR, contentWidth - 16, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            topY += 40;
        }
    }

    private void renderWorldEventInsights(long nvg, float x, InsightsLayout layout) {
        float contentX = x + PADDING;
        float contentWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        long visibleCount = allWorldEvents.stream().filter(WorldEventDefinition::isVisible).count();
        drawInsightsSectionTitle(nvg, contentX, layout.overviewY(), "Overview");
        drawInsightRow(nvg, contentX, layout.overviewY() + 18, contentWidth, "Visible", visibleWorldEvents.size() + " shown / " + visibleCount + " active");
        drawInsightRow(nvg, contentX, layout.overviewY() + 34, contentWidth, "Tracked", String.valueOf(SeqClient.getConfigManager().trackedWorldEventIds().size()));
        drawInsightRow(nvg, contentX, layout.overviewY() + 50, contentWidth, "API", worldEventService.status());

        WorldEventDefinition detail = selectedWorldEvent != null ? selectedWorldEvent : hoveredWorldEvent;
        drawInsightsSectionTitle(nvg, contentX, layout.eventDetailY() - 8, "Selection");
        if (detail != null) {
            renderWorldEventDetail(nvg, contentX, layout.eventDetailY() + 4, contentWidth, detail, selectedWorldEvent != null);
        } else {
            drawFittedText(nvg, contentX, layout.eventDetailY() + 18, 11, "Hover or select a world event", SUBTEXT_COLOR, contentWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        }
    }

    private void drawInsightsSectionTitle(long nvg, float x, float y, String label) {
        drawText(nvg, x, y, 12, label, SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    private void drawInsightRow(long nvg, float x, float y, float width, String label, String value) {
        drawFittedText(nvg, x, y, 10, label, SUBTEXT_COLOR, width * 0.42f, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + width, y, 10, value, TEXT_COLOR, width * 0.58f, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
    }

    private void renderClusterDetail(long nvg, float x, float y, float width, GatheringNodeCluster cluster) {
        NVGWrapper.drawRect(nvg, x, y, width, CLUSTER_DETAIL_HEIGHT, new Color(28, 28, 38, 210));
        NVGWrapper.drawRectOutline(nvg, x, y, width, CLUSTER_DETAIL_HEIGHT, 1, BORDER_COLOR);
        float textWidth = width - 16;
        drawFittedText(nvg, x + 8, y + 17, 14, cluster.resource(), TEXT_COLOR, textWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 36, 12, cluster.nodeCount() + " nodes | score " + cluster.score() + "%", SUBTEXT_COLOR, textWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 55, 12, Math.round(cluster.averageSpacing()) + "m spacing", SUBTEXT_COLOR, textWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 74, 12, cluster.profession().name(), cluster.profession().color(), textWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 93, 12, clusterCoords(cluster), SUBTEXT_COLOR, textWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    private void renderNodeDetail(long nvg, float x, float y, float width, GatheringNode node) {
        NVGWrapper.drawRect(nvg, x, y, width, NODE_DETAIL_HEIGHT, new Color(28, 28, 38, 210));
        NVGWrapper.drawRectOutline(nvg, x, y, width, NODE_DETAIL_HEIGHT, 1, BORDER_COLOR);
        drawFittedText(nvg, x + 8, y + 17, 14, node.resource(), TEXT_COLOR, width - 16, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 38, 12, nodeCoords(node), SUBTEXT_COLOR, width - 16, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    private void renderWorldEventDetail(
            long nvg,
            float x,
            float y,
            float width,
            WorldEventDefinition event,
            boolean allowTrackingButton) {
        float textWidth = width - 16;
        NVGWrapper.drawRect(nvg, x, y, width, WORLD_EVENT_DETAIL_HEIGHT, new Color(28, 28, 38, 220));
        NVGWrapper.drawRectOutline(nvg, x, y, width, WORLD_EVENT_DETAIL_HEIGHT, 1, BORDER_COLOR);
        drawFittedText(nvg, x + 8, y + 16, 14, event.name(), TEXT_COLOR, textWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        String metadata = worldEventMetadata(event);
        drawFittedText(nvg, x + 8, y + 35, 11, metadata, SUBTEXT_COLOR, textWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 53, 11, worldEventScheduleLabel(event.schedule()), SUBTEXT_COLOR, textWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        String locationLabel = event.locations().size() == 1
                ? worldEventCoordinates(event.locations().getFirst())
                : event.locations().size() + " possible locations";
        drawFittedText(nvg, x + 8, y + 71, 11, locationLabel, SUBTEXT_COLOR, textWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        if (allowTrackingButton) {
            boolean tracked = SeqClient.getConfigManager().trackedWorldEventIds().contains(event.internalName());
            drawButton(
                    nvg,
                    x + 8,
                    y + 88,
                    width - 16,
                    24,
                    tracked ? "Untrack Event" : "Track Event",
                    tracked);
        }
    }

    private void drawMapModeControl(long nvg, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / MapDisplayMode.values().length;
        for (int index = 0; index < MapDisplayMode.values().length; index++) {
            MapDisplayMode mode = MapDisplayMode.values()[index];
            float x = PADDING + index * segmentWidth;
            boolean active = displayMode == mode;
            boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, segmentWidth, BUTTON_HEIGHT);
            NVGWrapper.drawRect(nvg, x, y, segmentWidth, BUTTON_HEIGHT, active ? CONTROL_ACTIVE : hovered ? CONTROL_HOVER : CONTROL_COLOR);
            NVGWrapper.drawRectOutline(nvg, x, y, segmentWidth, BUTTON_HEIGHT, 1, BORDER_COLOR);
            drawText(nvg, x + segmentWidth / 2f, y + BUTTON_HEIGHT / 2f, 11, mode.label(), TEXT_COLOR, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        }
    }

    private void drawWorldEventFilterControl(long nvg, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / WorldEventDisplayFilter.values().length;
        for (int index = 0; index < WorldEventDisplayFilter.values().length; index++) {
            WorldEventDisplayFilter filter = WorldEventDisplayFilter.values()[index];
            float x = PADDING + index * segmentWidth;
            boolean active = worldEventDisplayFilter == filter;
            boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, segmentWidth, BUTTON_HEIGHT);
            NVGWrapper.drawRect(nvg, x, y, segmentWidth, BUTTON_HEIGHT, active ? CONTROL_ACTIVE : hovered ? CONTROL_HOVER : CONTROL_COLOR);
            NVGWrapper.drawRectOutline(nvg, x, y, segmentWidth, BUTTON_HEIGHT, 1, BORDER_COLOR);
            drawText(nvg, x + segmentWidth / 2f, y + BUTTON_HEIGHT / 2f, 11, filter.label(), TEXT_COLOR, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        }
    }

    private void drawWorldEventTrackingListControl(long nvg, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / 2f;
        drawTrackingListSegment(nvg, PADDING, y, segmentWidth, "All Events", !worldEventDropdownTrackedOnly);
        drawTrackingListSegment(
                nvg,
                PADDING + segmentWidth,
                y,
                segmentWidth,
                "Tracked Only",
                worldEventDropdownTrackedOnly);
    }

    private void drawTrackingListSegment(long nvg, float x, float y, float width, String label, boolean active) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, width, BUTTON_HEIGHT);
        NVGWrapper.drawRect(nvg, x, y, width, BUTTON_HEIGHT, active ? CONTROL_ACTIVE : hovered ? CONTROL_HOVER : CONTROL_COLOR);
        NVGWrapper.drawRectOutline(nvg, x, y, width, BUTTON_HEIGHT, 1, BORDER_COLOR);
        drawFittedText(
                nvg,
                x + width / 2f,
                y + BUTTON_HEIGHT / 2f,
                11,
                label,
                TEXT_COLOR,
                width - 10,
                NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
    }

    private void renderPanelHeader(
            long nvg,
            float y,
            String label,
            String summary,
            WorldMapSidebarPanel panel) {
        boolean expanded = panelExpanded(panel);
        boolean hovered = isHovered(
                nvgMouseX,
                nvgMouseY,
                PADDING,
                y,
                SIDEBAR_WIDTH - PADDING * 2,
                PANEL_HEADER_HEIGHT);
        NVGWrapper.drawRect(
                nvg,
                PADDING,
                y,
                SIDEBAR_WIDTH - PADDING * 2,
                PANEL_HEADER_HEIGHT,
                hovered ? CONTROL_HOVER : new Color(33, 33, 44, 235));
        NVGWrapper.drawRectOutline(
                nvg,
                PADDING,
                y,
                SIDEBAR_WIDTH - PADDING * 2,
                PANEL_HEADER_HEIGHT,
                1,
                BORDER_COLOR);
        drawText(
                nvg,
                PADDING + 10,
                y + PANEL_HEADER_HEIGHT / 2f,
                12,
                expanded ? "v" : ">",
                SUBTEXT_COLOR,
                NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        drawFittedText(
                nvg,
                PADDING + 22,
                y + PANEL_HEADER_HEIGHT / 2f,
                12,
                label,
                TEXT_COLOR,
                PANEL_LABEL_WIDTH,
                NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(
                nvg,
                SIDEBAR_WIDTH - PADDING - 8,
                y + PANEL_HEADER_HEIGHT / 2f,
                10,
                summary,
                SUBTEXT_COLOR,
                PANEL_SUMMARY_WIDTH,
                NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
    }

    private boolean panelExpanded(WorldMapSidebarPanel panel) {
        return mapSettings.sidebarPanelExpanded(panel);
    }

    private void togglePanel(WorldMapSidebarPanel panel) {
        boolean expanded = !panelExpanded(panel);
        mapSettings.setSidebarPanelExpanded(panel, expanded);
        sidebarScroll = 0;
        if (!expanded) {
            if (panel == WorldMapSidebarPanel.MAP_AND_TERRITORY) {
                closeTerritorySearch();
            } else if (panel == WorldMapSidebarPanel.RESOURCE_FILTERS) {
                closeResourceSearch();
            } else if (panel == WorldMapSidebarPanel.EVENT_TRACKING) {
                closeWorldEventSearch();
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

    private void renderTerritoryToggles(long nvg, float y) {
        float fullWidth = SIDEBAR_WIDTH - PADDING * 2;
        if (!showTerritories) {
            drawButton(nvg, PADDING, y, fullWidth, BUTTON_HEIGHT, "Territory Borders Off", false);
            return;
        }
        float splitWidth = (fullWidth - SPLIT_CONTROL_GAP) / 2f;
        drawButton(nvg, PADDING, y, splitWidth, BUTTON_HEIGHT, "Borders On", true);
        drawButton(
                nvg,
                PADDING + splitWidth + SPLIT_CONTROL_GAP,
                y,
                splitWidth,
                BUTTON_HEIGHT,
                showTerritoryNames ? "Names On" : "Names Off",
                showTerritoryNames);
    }

    private void renderGatheringAnalysisToggles(long nvg, float y) {
        float fullWidth = SIDEBAR_WIDTH - PADDING * 2;
        if (!showClusters) {
            drawButton(nvg, PADDING, y, fullWidth, BUTTON_HEIGHT, "Gathering Clusters Off", false);
            return;
        }
        float splitWidth = (fullWidth - SPLIT_CONTROL_GAP) / 2f;
        drawButton(nvg, PADDING, y, splitWidth, BUTTON_HEIGHT, "Clusters On", true);
        drawButton(
                nvg,
                PADDING + splitWidth + SPLIT_CONTROL_GAP,
                y,
                splitWidth,
                BUTTON_HEIGHT,
                clusterScoreMode.label(),
                true);
    }

    private void renderSearchInput(
            long nvg,
            float y,
            boolean dropdownOpen,
            boolean inputFocused,
            String search,
            String unfocusedValue) {
        NVGWrapper.drawRect(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, INPUT_HEIGHT, dropdownOpen ? CONTROL_HOVER : CONTROL_COLOR);
        NVGWrapper.drawRectOutline(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, INPUT_HEIGHT, 1, BORDER_COLOR);
        String value = inputFocused ? search : unfocusedValue;
        Color valueColor = value == null || value.isBlank() ? SUBTEXT_COLOR : TEXT_COLOR;
        String displayValue = value == null || value.isBlank() ? "Search" : value;
        float inputTextWidth = SIDEBAR_WIDTH - PADDING * 2 - 30;
        drawFittedText(nvg, PADDING + 8, y + INPUT_HEIGHT / 2f, 12, displayValue, valueColor, inputTextWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        if (inputFocused) {
            float cursorX = PADDING + 10 + Math.min(textWidth(nvg, value), inputTextWidth);
            drawText(nvg, cursorX, y + INPUT_HEIGHT / 2f, 12, "|", TEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        }
        drawText(nvg, SIDEBAR_WIDTH - PADDING - 10, y + INPUT_HEIGHT / 2f, 12, dropdownOpen ? "^" : "v", SUBTEXT_COLOR, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
    }

    private void drawScopeControl(long nvg, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / GatheringAnalysisScope.values().length;
        for (int index = 0; index < GatheringAnalysisScope.values().length; index++) {
            GatheringAnalysisScope scope = GatheringAnalysisScope.values()[index];
            float x = PADDING + index * segmentWidth;
            boolean enabled = scope != GatheringAnalysisScope.SELECTED_TERRITORY || selectedTerritory != null;
            boolean active = gatheringAnalysisScope == scope;
            boolean hovered = enabled && isHovered(nvgMouseX, nvgMouseY, x, y, segmentWidth, BUTTON_HEIGHT);
            Color background = active ? CONTROL_ACTIVE : hovered ? CONTROL_HOVER : CONTROL_COLOR;
            if (!enabled) {
                background = new Color(background.getRed(), background.getGreen(), background.getBlue(), 105);
            }
            NVGWrapper.drawRect(nvg, x, y, segmentWidth, BUTTON_HEIGHT, background);
            NVGWrapper.drawRectOutline(nvg, x, y, segmentWidth, BUTTON_HEIGHT, 1, BORDER_COLOR);
            drawFittedText(
                    nvg,
                    x + segmentWidth / 2f,
                    y + BUTTON_HEIGHT / 2f,
                    10,
                    scope.label(),
                    enabled ? TEXT_COLOR : SUBTEXT_COLOR,
                    segmentWidth - 8,
                    NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        }
    }

    private void renderSelectedTerritoryDetail(
            long nvg,
            float x,
            float y,
            float width,
            GuildTerritory territory) {
        NVGWrapper.drawRect(nvg, x, y, width, TERRITORY_DETAIL_HEIGHT, new Color(28, 28, 38, 210));
        NVGWrapper.drawRectOutline(nvg, x, y, width, TERRITORY_DETAIL_HEIGHT, 1, SELECTED_TERRITORY_COLOR);
        float detailWidth = width - 16;
        int totalNodes = cachedTerritoryNodeCounts.getOrDefault(territory.name(), 0);
        int matchingNodes = selectedTerritoryMatchingNodeCount;
        drawFittedText(nvg, x + 8, y + 17, 14, territory.name(), TEXT_COLOR, detailWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 36, 11, totalNodes + " total nodes | " + matchingNodes + " matching", SUBTEXT_COLOR, detailWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 56, 10, territoryBoundsLabel(territory), SUBTEXT_COLOR, detailWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    private void drawButton(long nvg, float x, float y, float w, float h, String label, boolean active) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, h);
        NVGWrapper.drawRect(nvg, x, y, w, h, active ? CONTROL_ACTIVE : hovered ? CONTROL_HOVER : CONTROL_COLOR);
        NVGWrapper.drawRectOutline(nvg, x, y, w, h, 1, BORDER_COLOR);
        drawText(nvg, x + w / 2f, y + h / 2f, 12, label, TEXT_COLOR, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
    }

    private void drawToggle(long nvg, float x, float y, float w, float h, GatheringProfession profession, boolean active) {
        drawButton(nvg, x, y, w, h, displayProfession(profession), active);
        drawCircle(nvg, x + 13, y + h / 2f, 4, profession.color());
    }

    private void renderResourceDropdown(long nvg, float y) {
        List<String> resources = resourceDropdownOptions();
        int visibleRows = Math.min(RESOURCE_DROPDOWN_VISIBLE_ROWS, resources.size());
        resourceDropdownScroll = clampResourceDropdownScroll(resourceDropdownScroll, resources.size());
        float x = PADDING;
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float height = Math.max(1, visibleRows) * RESOURCE_DROPDOWN_ROW_HEIGHT;
        NVGWrapper.drawRect(nvg, x, y, width, height, new Color(22, 22, 30, 248));
        NVGWrapper.drawRectOutline(nvg, x, y, width, height, 1, BORDER_COLOR);
        if (resources.isEmpty()) {
            drawText(nvg, x + 8, y + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f, 11, "No matches", SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            return;
        }
        for (int index = 0; index < visibleRows; index++) {
            String resource = resources.get(resourceDropdownScroll + index);
            boolean selected = resource.isBlank()
                    ? selectedResourceFilters.isEmpty()
                    : selectedResourceFilters.contains(resource);
            boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT, width, RESOURCE_DROPDOWN_ROW_HEIGHT);
            if (selected || hovered) {
                NVGWrapper.drawRect(nvg, x + 1, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + 1, width - 2, RESOURCE_DROPDOWN_ROW_HEIGHT - 2, selected ? CONTROL_ACTIVE : CONTROL_HOVER);
            }
            String label = resource.isBlank() ? "All resources" : resource;
            drawFittedText(nvg, x + 8, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f, 11, label, resource.isBlank() ? SUBTEXT_COLOR : TEXT_COLOR, width - 16, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        }
        if (resources.size() > visibleRows) {
            String range = (resourceDropdownScroll + 1) + "-" + (resourceDropdownScroll + visibleRows) + "/" + resources.size();
            drawText(nvg, x + width - 8, y + height - 7, 9, range, SUBTEXT_COLOR, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        }
    }

    private void renderTerritoryDropdown(long nvg, float y) {
        List<GuildTerritory> territories = territoryDropdownOptions();
        int visibleRows = Math.min(TERRITORY_DROPDOWN_VISIBLE_ROWS, territories.size());
        territoryDropdownScroll = clampDropdownScroll(territoryDropdownScroll, territories.size(), TERRITORY_DROPDOWN_VISIBLE_ROWS);
        float x = PADDING;
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float height = Math.max(1, visibleRows) * RESOURCE_DROPDOWN_ROW_HEIGHT;
        NVGWrapper.drawRect(nvg, x, y, width, height, new Color(22, 22, 30, 248));
        NVGWrapper.drawRectOutline(nvg, x, y, width, height, 1, BORDER_COLOR);
        if (territories.isEmpty()) {
            drawText(nvg, x + 8, y + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f, 11, "No matches", SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            return;
        }
        for (int index = 0; index < visibleRows; index++) {
            GuildTerritory territory = territories.get(territoryDropdownScroll + index);
            boolean selected = territory.equals(selectedTerritory);
            boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT, width, RESOURCE_DROPDOWN_ROW_HEIGHT);
            if (selected || hovered) {
                NVGWrapper.drawRect(nvg, x + 1, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + 1, width - 2, RESOURCE_DROPDOWN_ROW_HEIGHT - 2, selected ? CONTROL_ACTIVE : CONTROL_HOVER);
            }
            drawFittedText(nvg, x + 8, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f, 11, territory.name(), TEXT_COLOR, width - 16, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        }
        if (territories.size() > visibleRows) {
            String range = (territoryDropdownScroll + 1) + "-" + (territoryDropdownScroll + visibleRows) + "/" + territories.size();
            drawText(nvg, x + width - 8, y + height - 7, 9, range, SUBTEXT_COLOR, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        }
    }

    private void renderWorldEventDropdown(long nvg, float y) {
        List<WorldEventDefinition> events = worldEventDropdownOptions();
        int visibleRows = Math.min(WORLD_EVENT_DROPDOWN_VISIBLE_ROWS, events.size());
        worldEventDropdownScroll = clampDropdownScroll(
                worldEventDropdownScroll,
                events.size(),
                WORLD_EVENT_DROPDOWN_VISIBLE_ROWS);
        float x = PADDING;
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float height = Math.max(1, visibleRows) * RESOURCE_DROPDOWN_ROW_HEIGHT;
        Set<String> tracked = SeqClient.getConfigManager().trackedWorldEventIds();
        NVGWrapper.drawRect(nvg, x, y, width, height, new Color(22, 22, 30, 248));
        NVGWrapper.drawRectOutline(nvg, x, y, width, height, 1, BORDER_COLOR);
        if (events.isEmpty()) {
            drawText(nvg, x + 8, y + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f, 11, "No matches", SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            return;
        }
        for (int index = 0; index < visibleRows; index++) {
            WorldEventDefinition event = events.get(worldEventDropdownScroll + index);
            boolean selected = tracked.contains(event.internalName());
            boolean hovered = isHovered(
                    nvgMouseX,
                    nvgMouseY,
                    x,
                    y + index * RESOURCE_DROPDOWN_ROW_HEIGHT,
                    width,
                    RESOURCE_DROPDOWN_ROW_HEIGHT);
            if (selected || hovered) {
                NVGWrapper.drawRect(
                        nvg,
                        x + 1,
                        y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + 1,
                        width - 2,
                        RESOURCE_DROPDOWN_ROW_HEIGHT - 2,
                        selected ? CONTROL_ACTIVE : CONTROL_HOVER);
            }
            String label = (selected ? "[x] " : "[ ] ") + event.name();
            drawFittedText(
                    nvg,
                    x + 8,
                    y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f,
                    11,
                    label,
                    event.isVisible() ? TEXT_COLOR : SUBTEXT_COLOR,
                    width - 16,
                    NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        }
        if (events.size() > visibleRows) {
            String range = (worldEventDropdownScroll + 1) + "-" + (worldEventDropdownScroll + visibleRows) + "/" + events.size();
            drawText(nvg, x + width - 8, y + height - 7, 9, range, SUBTEXT_COLOR, NVG_ALIGN_RIGHT | NVG_ALIGN_MIDDLE);
        }
    }

    private void renderSidebarScrollbar(long nvg, float screenHeight) {
        float viewportHeight = Math.max(0, screenHeight - SIDEBAR_PANEL_TOP);
        float maxScroll = sidebarMaxScroll(screenHeight);
        if (maxScroll <= 0 || viewportHeight <= 0) {
            return;
        }
        float trackX = SIDEBAR_WIDTH - 5;
        float trackY = SIDEBAR_PANEL_TOP + 4;
        float trackHeight = viewportHeight - 8;
        float scrollableContentHeight = Math.max(viewportHeight, sidebarContentHeight - SIDEBAR_PANEL_TOP);
        float thumbHeight = Math.max(24, trackHeight * (viewportHeight / scrollableContentHeight));
        float thumbY = trackY + (trackHeight - thumbHeight) * (sidebarScroll / maxScroll);
        NVGWrapper.drawRect(nvg, trackX, trackY, 3, trackHeight, new Color(255, 255, 255, 28));
        NVGWrapper.drawRect(nvg, trackX, thumbY, 3, thumbHeight, new Color(255, 255, 255, 110));
    }

    private String centerPlayerButtonLabel() {
        return System.currentTimeMillis() < centerPlayerWarningUntilMs ? "Leave housing bum !" : "Center Player";
    }

    private boolean copyHoveredCoordinates(float mx, float my, float sidebarMy, float screenWidth, float screenHeight) {
        float insightsX = insightsSidebarX(screenWidth);
        InsightsLayout insights = insightsLayout();
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            if (insightsSidebarOpen
                    && selectedWorldEvent != null
                    && isHovered(
                            mx,
                            my,
                            insightsX + PADDING,
                            insights.eventDetailY() + 4,
                            INSIGHTS_SIDEBAR_WIDTH - PADDING * 2,
                            WORLD_EVENT_DETAIL_HEIGHT)) {
                copyToClipboard(worldEventCoordinates(selectedWorldEvent.locations().getFirst()));
                return true;
            }
            MapViewport viewport = mapViewport(screenWidth, screenHeight);
            if (viewport.isInsideScreen(mx, my)
                    && hoveredWorldEvent != null
                    && hoveredWorldEventLocationIndex >= 0) {
                copyToClipboard(worldEventCoordinates(
                        hoveredWorldEvent.locations().get(hoveredWorldEventLocationIndex)));
                return true;
            }
            return false;
        }
        GatheringNodeCluster clusterDetail = selectedCluster != null ? selectedCluster : hoveredCluster;
        GatheringNode detail = selectedNode != null ? selectedNode : hoveredNode;
        float detailY = insights.entityY() + 4;
        float detailX = insightsX + PADDING;
        float detailWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        if (insightsSidebarOpen
                && clusterDetail != null
                && isHovered(mx, my, detailX, detailY, detailWidth, CLUSTER_DETAIL_HEIGHT)) {
            copyToClipboard(clusterCoords(clusterDetail));
            return true;
        }
        if (insightsSidebarOpen
                && clusterDetail == null
                && detail != null
                && isHovered(mx, my, detailX, detailY, detailWidth, NODE_DETAIL_HEIGHT)) {
            copyToClipboard(nodeCoords(detail));
            return true;
        }

        MapViewport viewport = mapViewport(screenWidth, screenHeight);
        if (!viewport.isInsideScreen(mx, my)) {
            return false;
        }
        if (hoveredNode != null) {
            copyToClipboard(nodeCoords(hoveredNode));
            return true;
        }
        if (hoveredCluster != null) {
            copyToClipboard(clusterCoords(hoveredCluster));
            return true;
        }
        return false;
    }

    private void copyToClipboard(String text) {
        SeqClient.mc.keyboardHandler.setClipboard(text);
    }

    private static String nodeCoords(GatheringNode node) {
        return node.x() + " " + node.y() + " " + node.z();
    }

    private static String clusterCoords(GatheringNodeCluster cluster) {
        return Math.round(cluster.centerX()) + " " + Math.round(cluster.centerZ());
    }

    private void renderNodeTooltip(long nvg, GatheringNode node) {
        String title = node.resource() + " Lv. " + node.level();
        String subtitle = nodeCoords(node);
        float x = tooltipX(180);
        float y = Math.max(8, nvgMouseY + 12);
        NVGWrapper.drawRect(nvg, x, y, 180, 42, new Color(18, 18, 24, 235));
        NVGWrapper.drawRectOutline(nvg, x, y, 180, 42, 1, BORDER_COLOR);
        drawFittedText(nvg, x + 8, y + 15, 12, title, TEXT_COLOR, 164, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 31, 11, subtitle, SUBTEXT_COLOR, 164, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    private void renderClusterTooltip(long nvg, GatheringNodeCluster cluster) {
        String title = cluster.resource() + " | score " + cluster.score() + "%";
        String subtitle = cluster.nodeCount() + " nodes | " + Math.round(cluster.averageSpacing()) + "m";
        float x = tooltipX(200);
        float y = Math.max(8, nvgMouseY + 12);
        NVGWrapper.drawRect(nvg, x, y, 200, 42, new Color(18, 18, 24, 235));
        NVGWrapper.drawRectOutline(nvg, x, y, 200, 42, 1, BORDER_COLOR);
        drawFittedText(nvg, x + 8, y + 15, 12, title, TEXT_COLOR, 184, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 31, 11, subtitle, SUBTEXT_COLOR, 184, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    private void renderTerritoryTooltip(long nvg, GuildTerritory territory) {
        String subtitle = cachedTerritoryNodeCounts.getOrDefault(territory.name(), 0) + " gathering nodes";
        float x = tooltipX(200);
        float y = Math.max(8, nvgMouseY + 12);
        NVGWrapper.drawRect(nvg, x, y, 200, 42, new Color(18, 18, 24, 235));
        NVGWrapper.drawRectOutline(nvg, x, y, 200, 42, 1, BORDER_COLOR);
        drawFittedText(nvg, x + 8, y + 15, 12, territory.name(), TEXT_COLOR, 184, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 31, 11, subtitle, SUBTEXT_COLOR, 184, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    private void renderWorldEventTooltip(long nvg, WorldEventDefinition event, int locationIndex) {
        WorldEventLocation location = event.locations().get(Math.max(0, locationIndex));
        String locationLabel = event.locations().size() > 1
                ? "Possible " + (locationIndex + 1) + "/" + event.locations().size()
                        + ": " + worldEventCoordinates(location)
                : worldEventCoordinates(location);
        String subtitle = worldEventScheduleLabel(event.schedule()) + " | " + locationLabel;
        float x = tooltipX(210);
        float y = Math.max(8, nvgMouseY + 12);
        NVGWrapper.drawRect(nvg, x, y, 210, 42, new Color(18, 18, 24, 235));
        NVGWrapper.drawRectOutline(nvg, x, y, 210, 42, 1, BORDER_COLOR);
        drawFittedText(nvg, x + 8, y + 15, 12, event.name(), TEXT_COLOR, 194, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 31, 11, subtitle, SUBTEXT_COLOR, 194, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    private float tooltipX(float width) {
        float screenWidth = uiScreenWidth();
        return Math.max(
                SIDEBAR_WIDTH + 8,
                Math.min(nvgMouseX + 12, screenWidth - insightsSidebarInset() - width - 8));
    }

    private void refreshClusterAnalysisIfNeeded() {
        List<GatheringNode> sourceNodes = nodeService.nodes();
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
        refreshSelectedTerritoryMatchingCount();
        clusterOutlineShapes.clear();
        clusterOutlineScale = Double.NaN;
        hoveredNode = null;
        hoveredCluster = null;
        clearInvalidSelections();
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
        return showClusters && !cachedClusters.isEmpty() && pixelsPerBlock < NODE_DETAIL_PIXELS_PER_BLOCK;
    }

    private boolean shouldRenderClusterBadges() {
        return showClusters && !cachedClusters.isEmpty() && pixelsPerBlock < CLUSTER_BADGE_PIXELS_PER_BLOCK;
    }

    private static float clusterRadius(GatheringNodeCluster cluster) {
        return (float) (Math.max(6, Math.min(13, 5 + Math.sqrt(cluster.nodeCount()) * 1.5)) * 0.8);
    }

    private static float clusterCountTextSize(GatheringNodeCluster cluster) {
        return (float) Math.max(8.0, Math.min(10.5, 7.4 + Math.sqrt(cluster.nodeCount()) * 0.42));
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

    private List<WorldEventDefinition> worldEventDropdownOptions() {
        String query = worldEventInputFocused ? worldEventSearch : "";
        return WorldEventFilters.trackingOptions(
                allWorldEvents,
                SeqClient.getConfigManager().trackedWorldEventIds(),
                worldEventDropdownTrackedOnly,
                query);
    }

    private String trackedWorldEventLabel() {
        int tracked = SeqClient.getConfigManager().trackedWorldEventIds().size();
        return tracked == 0 ? "Manage tracked events" : tracked + " tracked events";
    }

    private static String worldEventMetadata(WorldEventDefinition event) {
        List<String> metadata = new ArrayList<>();
        if (event.level() != null) {
            metadata.add("Lv. " + event.level());
        }
        if (event.difficulty() != null) {
            metadata.add(displayEnumValue(event.difficulty()));
        }
        if (event.length() != null) {
            metadata.add(displayEnumValue(event.length()));
        }
        return metadata.isEmpty() ? "World event" : String.join(" | ", metadata);
    }

    private static String displayEnumValue(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.isEmpty() ? "" : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String worldEventScheduleLabel(Instant schedule) {
        if (schedule == null) {
            return "Not scheduled";
        }
        Instant now = Instant.now();
        if (!schedule.isAfter(now)) {
            long minutes = Math.max(0, Duration.between(schedule, now).toMinutes());
            return minutes == 0 ? "Started" : "Started " + minutes + "m ago";
        }
        long seconds = Duration.between(now, schedule).getSeconds();
        return "Starts in " + Math.max(1, (seconds + 59) / 60) + "m";
    }

    private static String worldEventCoordinates(WorldEventLocation location) {
        return Math.round(location.x()) + " " + Math.round(location.y()) + " " + Math.round(location.z());
    }

    private static AssetManager.Asset worldEventMarkerAsset() {
        return SeqClient.assetManager == null ? null : SeqClient.assetManager.getAsset(WORLD_EVENT_MARKER_ASSET);
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

    private static boolean isPointInsideCluster(
            ClusterOutlineShape outline,
            float centerScreenX,
            float centerScreenY,
            float screenX,
            float screenY) {
        if (outline.points().size() < 3) {
            return false;
        }
        float localX = screenX - centerScreenX;
        float localY = screenY - centerScreenY;
        boolean inside = false;
        for (int index = 0, previous = outline.points().size() - 1;
                index < outline.points().size();
                previous = index++) {
            float currentX = outline.points().get(index).x();
            float currentY = outline.points().get(index).y();
            float previousX = outline.points().get(previous).x();
            float previousY = outline.points().get(previous).y();
            boolean intersects = (currentY > localY) != (previousY > localY)
                    && localX < (previousX - currentX) * (localY - currentY) / (previousY - currentY) + currentX;
            if (intersects) {
                inside = !inside;
            }
        }
        return inside;
    }

    private ClusterOutlineShape clusterOutlineShape(GatheringNodeCluster cluster, double scale) {
        if (Double.compare(clusterOutlineScale, scale) != 0) {
            clusterOutlineShapes.clear();
            clusterOutlineScale = scale;
        }
        return clusterOutlineShapes.computeIfAbsent(cluster, ignored -> buildClusterOutlineShape(cluster, scale));
    }

    private static ClusterOutlineShape buildClusterOutlineShape(GatheringNodeCluster cluster, double scale) {
        List<ScreenPoint> points = cluster.outline().stream()
                .map(point -> new ScreenPoint(
                        (float) ((point.x() - cluster.centerX()) * scale),
                        (float) ((point.z() - cluster.centerZ()) * scale)))
                .toList();
        if (points.size() < 3) {
            return ClusterOutlineShape.from(points);
        }

        List<ScreenPoint> displayPoints = expandFromCentroid(points, hullPaddingForZoom(scale));
        for (int pass = 0; pass < HULL_SMOOTHING_PASSES; pass++) {
            displayPoints = chaikinClosedPass(displayPoints);
        }
        return ClusterOutlineShape.from(displayPoints);
    }

    private static float hullPaddingForZoom(double pixelsPerBlock) {
        double t = Math.max(0, Math.min(1, (pixelsPerBlock - 0.3) / 0.9));
        return (float) (MIN_HULL_PADDING_PX + (MAX_HULL_PADDING_PX - MIN_HULL_PADDING_PX) * t);
    }

    private static float hullStrokeWidthForZoom(double pixelsPerBlock, boolean highlighted) {
        double t = Math.max(0, Math.min(1, (NODE_DETAIL_PIXELS_PER_BLOCK - pixelsPerBlock) / NODE_DETAIL_PIXELS_PER_BLOCK));
        float baseWidth = (float) (0.8 + t * 1.2);
        return highlighted ? baseWidth + 0.9f : baseWidth;
    }

    private static List<ScreenPoint> expandFromCentroid(List<ScreenPoint> points, float padding) {
        float centerX = 0;
        float centerY = 0;
        for (ScreenPoint point : points) {
            centerX += point.x();
            centerY += point.y();
        }
        centerX /= points.size();
        centerY /= points.size();

        final float finalCenterX = centerX;
        final float finalCenterY = centerY;
        return points.stream()
                .map(point -> {
                    float dx = point.x() - finalCenterX;
                    float dy = point.y() - finalCenterY;
                    float length = (float) Math.hypot(dx, dy);
                    if (length == 0) {
                        return point;
                    }
                    float scale = (length + padding) / length;
                    return new ScreenPoint(finalCenterX + dx * scale, finalCenterY + dy * scale);
                })
                .toList();
    }

    private static List<ScreenPoint> chaikinClosedPass(List<ScreenPoint> points) {
        java.util.ArrayList<ScreenPoint> smoothed = new java.util.ArrayList<>(points.size() * 2);
        for (int index = 0; index < points.size(); index++) {
            ScreenPoint point = points.get(index);
            ScreenPoint next = points.get((index + 1) % points.size());
            smoothed.add(new ScreenPoint(point.x() * 0.75f + next.x() * 0.25f, point.y() * 0.75f + next.y() * 0.25f));
            smoothed.add(new ScreenPoint(point.x() * 0.25f + next.x() * 0.75f, point.y() * 0.25f + next.y() * 0.75f));
        }
        return smoothed;
    }

    private void fitFullMap(float mapW, float mapH) {
        double xScale = mapW / (MapCalibration.MAX_WORLD_X - MapCalibration.MIN_WORLD_X);
        double zScale = mapH / (MapCalibration.MAX_WORLD_Z - MapCalibration.MIN_WORLD_Z);
        pixelsPerBlock = clamp(Math.min(xScale, zScale) * 0.92, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
    }

    private boolean centerOnPlayer() {
        if (SeqClient.mc.player == null) {
            return false;
        }
        double playerX = SeqClient.mc.player.getX();
        double playerZ = SeqClient.mc.player.getZ();
        if (!MapCalibration.fullBounds().contains(playerX, playerZ)) {
            return false;
        }
        centerX = playerX;
        centerZ = playerZ;
        pixelsPerBlock = Math.max(pixelsPerBlock, 0.18);
        return true;
    }

    @Override
    public boolean mouseClicked(@NotNull MouseButtonEvent click, boolean outsideScreen) {
        float mx = scaledMouseX(click.x());
        float my = scaledMouseY(click.y());
        float sidebarMy = my + sidebarScroll;
        float screenWidth = uiScreenWidth();
        float screenHeight = uiScreenHeight();

        if (click.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (copyHoveredCoordinates(mx, my, sidebarMy, screenWidth, screenHeight)) {
                return true;
            }
            return super.mouseClicked(click, outsideScreen);
        }
        if (click.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(click, outsideScreen);
        }

        if (mx >= 0 && mx <= SIDEBAR_WIDTH && my < SIDEBAR_HEADER_HEIGHT) {
            return true;
        }
        if (mouseClickedInsights(mx, my, screenWidth, screenHeight)) {
            return true;
        }
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            if (mouseClickedWorldEvents(mx, my, sidebarMy, screenWidth, screenHeight)) {
                return true;
            }
            return super.mouseClicked(click, outsideScreen);
        }
        SidebarLayout layout = sidebarLayout();

        if (territoryDropdownOpen) {
            List<GuildTerritory> territories = territoryDropdownOptions();
            int visibleRows = Math.min(TERRITORY_DROPDOWN_VISIBLE_ROWS, territories.size());
            float dropdownY = layout.territoryInputY() - sidebarScroll + INPUT_HEIGHT;
            if (visibleRows > 0 && isHovered(mx, my, PADDING, dropdownY, SIDEBAR_WIDTH - PADDING * 2, visibleRows * RESOURCE_DROPDOWN_ROW_HEIGHT)) {
                int optionIndex = Math.min(visibleRows - 1, Math.max(0, (int) ((my - dropdownY) / RESOURCE_DROPDOWN_ROW_HEIGHT)));
                selectTerritory(territories.get(territoryDropdownScroll + optionIndex), true);
                closeTerritorySearch();
                return true;
            }
        }
        if (resourceDropdownOpen) {
            List<String> resources = resourceDropdownOptions();
            int visibleRows = Math.min(RESOURCE_DROPDOWN_VISIBLE_ROWS, resources.size());
            float dropdownY = layout.resourceInputY() - sidebarScroll + INPUT_HEIGHT;
            if (visibleRows > 0 && isHovered(mx, my, PADDING, dropdownY, SIDEBAR_WIDTH - PADDING * 2, visibleRows * RESOURCE_DROPDOWN_ROW_HEIGHT)) {
                int optionIndex = Math.min(visibleRows - 1, Math.max(0, (int) ((my - dropdownY) / RESOURCE_DROPDOWN_ROW_HEIGHT)));
                toggleResourceFilter(resources.get(resourceDropdownScroll + optionIndex), true);
                return true;
            }
        }

        if (isHovered(mx, my, PADDING, layout.backY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(parent);
            return true;
        }
        if (isHovered(mx, my, PADDING, layout.centerY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            if (!centerOnPlayer()) {
                centerPlayerWarningUntilMs = System.currentTimeMillis() + CENTER_PLAYER_WARNING_DURATION_MS;
            }
            return true;
        }
        if (isHovered(mx, my, PADDING, layout.modeY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            setDisplayMode(mapModeAt(mx));
            return true;
        }
        if (isHovered(mx, sidebarMy, PADDING, layout.mapPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.MAP_AND_TERRITORY);
            return true;
        }
        if (isHovered(mx, sidebarMy, PADDING, layout.analysisPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.GATHERING_ANALYSIS);
            return true;
        }
        if (isHovered(mx, sidebarMy, PADDING, layout.filtersPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.RESOURCE_FILTERS);
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
                && isHovered(mx, sidebarMy, PADDING, layout.territoryInputY(), SIDEBAR_WIDTH - PADDING * 2, INPUT_HEIGHT)) {
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
        if (layout.resourceInputY() >= 0
                && isHovered(mx, sidebarMy, PADDING, layout.resourceInputY(), SIDEBAR_WIDTH - PADDING * 2, INPUT_HEIGHT)) {
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

        MapViewport viewport = mapViewport(screenWidth, screenHeight);
        if (viewport.isInsideScreen(mx, my)) {
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
            draggingMap = true;
            hoveredNode = null;
            hoveredCluster = null;
            hoveredTerritory = null;
            closeSearchDropdowns();
            return true;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    private boolean mouseClickedInsights(float mx, float my, float screenWidth, float screenHeight) {
        if (!insightsSidebarOpen) {
            if (isHovered(
                    mx,
                    my,
                    screenWidth - INSIGHTS_RAIL_WIDTH,
                    10,
                    INSIGHTS_RAIL_WIDTH,
                    BUTTON_HEIGHT)) {
                setInsightsSidebarOpen(true);
                return true;
            }
            return false;
        }

        float x = insightsSidebarX(screenWidth);
        if (mx < x || mx > screenWidth) {
            return false;
        }
        if (isHovered(
                mx,
                my,
                x + INSIGHTS_SIDEBAR_WIDTH - PADDING - 24,
                10,
                24,
                BUTTON_HEIGHT)) {
            setInsightsSidebarOpen(false);
            return true;
        }

        InsightsLayout layout = insightsLayout();
        float contentX = x + PADDING;
        float contentWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            if (selectedWorldEvent != null
                    && isHovered(
                            mx,
                            my,
                            contentX + 8,
                            layout.eventDetailY() + 92,
                            contentWidth - 16,
                            24)) {
                toggleTrackedWorldEvent(selectedWorldEvent, false);
            }
            return true;
        }

        if (showClusters && !cachedClusters.isEmpty()) {
            float rowY = layout.topClustersY() + 12;
            int availableRows = Math.max(0, (int) ((screenHeight - rowY - PADDING) / 40));
            int rowCount = Math.min(Math.min(SIDEBAR_CLUSTER_LIMIT, cachedClusters.size()), availableRows);
            for (int index = 0; index < rowCount; index++) {
                if (isHovered(mx, my, contentX, rowY, contentWidth, 34)) {
                    selectedCluster = cachedClusters.get(index);
                    selectedNode = null;
                    centerX = selectedCluster.centerX();
                    centerZ = selectedCluster.centerZ();
                    pixelsPerBlock = Math.max(pixelsPerBlock, 0.20);
                    return true;
                }
                rowY += 40;
            }
        }
        return true;
    }

    private void setInsightsSidebarOpen(boolean open) {
        if (insightsSidebarOpen == open) {
            return;
        }
        insightsSidebarOpen = open;
        mapSettings.setInsightsSidebarOpen(open);
        draggingMap = false;
    }

    private boolean mouseClickedWorldEvents(
            float mx,
            float my,
            float sidebarMy,
            float screenWidth,
            float screenHeight) {
        WorldEventSidebarLayout layout = worldEventSidebarLayout();
        if (worldEventDropdownOpen) {
            List<WorldEventDefinition> events = worldEventDropdownOptions();
            int visibleRows = Math.min(WORLD_EVENT_DROPDOWN_VISIBLE_ROWS, events.size());
            float dropdownY = layout.eventInputY() - sidebarScroll + INPUT_HEIGHT;
            if (visibleRows > 0
                    && isHovered(
                            mx,
                            my,
                            PADDING,
                            dropdownY,
                            SIDEBAR_WIDTH - PADDING * 2,
                            visibleRows * RESOURCE_DROPDOWN_ROW_HEIGHT)) {
                int optionIndex = Math.min(
                        visibleRows - 1,
                        Math.max(0, (int) ((my - dropdownY) / RESOURCE_DROPDOWN_ROW_HEIGHT)));
                toggleTrackedWorldEvent(events.get(worldEventDropdownScroll + optionIndex), true);
                return true;
            }
        }
        if (isHovered(mx, my, PADDING, layout.backY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(parent);
            return true;
        }
        if (isHovered(mx, my, PADDING, layout.centerY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            if (!centerOnPlayer()) {
                centerPlayerWarningUntilMs = System.currentTimeMillis() + CENTER_PLAYER_WARNING_DURATION_MS;
            }
            return true;
        }
        if (isHovered(mx, my, PADDING, layout.modeY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            setDisplayMode(mapModeAt(mx));
            return true;
        }
        if (isHovered(mx, sidebarMy, PADDING, layout.displayPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.EVENT_DISPLAY);
            return true;
        }
        if (isHovered(mx, sidebarMy, PADDING, layout.trackingPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.EVENT_TRACKING);
            return true;
        }
        if (layout.filterY() >= 0
                && isHovered(mx, sidebarMy, PADDING, layout.filterY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            WorldEventDisplayFilter filter = worldEventFilterAt(mx);
            if (filter != null) {
                worldEventDisplayFilter = filter;
                mapSettings.setWorldEventDisplayFilter(filter);
                refreshWorldEvents();
            }
            return true;
        }
        if (layout.eventFilterY() >= 0 && isHovered(
                mx,
                sidebarMy,
                PADDING,
                layout.eventFilterY(),
                SIDEBAR_WIDTH - PADDING * 2,
                BUTTON_HEIGHT)) {
            float midpoint = PADDING + (SIDEBAR_WIDTH - PADDING * 2) / 2f;
            worldEventDropdownTrackedOnly = mx >= midpoint;
            worldEventDropdownOpen = true;
            worldEventInputFocused = true;
            worldEventDropdownScroll = 0;
            return true;
        }
        if (layout.eventInputY() >= 0
                && isHovered(mx, sidebarMy, PADDING, layout.eventInputY(), SIDEBAR_WIDTH - PADDING * 2, INPUT_HEIGHT)) {
            boolean shouldOpen = !worldEventDropdownOpen;
            closeResourceSearch();
            closeTerritorySearch();
            worldEventInputFocused = shouldOpen;
            worldEventDropdownOpen = shouldOpen;
            worldEventSearch = "";
            worldEventDropdownScroll = 0;
            return true;
        }
        if (worldEventDropdownOpen) {
            closeWorldEventSearch();
            return true;
        }

        MapViewport viewport = mapViewport(screenWidth, screenHeight);
        if (viewport.isInsideScreen(mx, my)) {
            selectedWorldEvent = hoveredWorldEvent;
            draggingMap = true;
            hoveredWorldEvent = null;
            hoveredWorldEventLocationIndex = -1;
            closeSearchDropdowns();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        draggingMap = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingMap) {
            centerX -= NVGContext.mouseDelta(deltaX) / pixelsPerBlock;
            centerZ -= NVGContext.mouseDelta(deltaY) / pixelsPerBlock;
            hoveredNode = null;
            hoveredCluster = null;
            hoveredTerritory = null;
            hoveredWorldEvent = null;
            hoveredWorldEventLocationIndex = -1;
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float mx = scaledMouseX(mouseX);
        float my = scaledMouseY(mouseY);
        if (displayMode == MapDisplayMode.WORLD_EVENTS && worldEventDropdownOpen) {
            WorldEventSidebarLayout eventLayout = worldEventSidebarLayout();
            List<WorldEventDefinition> events = worldEventDropdownOptions();
            int visibleRows = Math.min(WORLD_EVENT_DROPDOWN_VISIBLE_ROWS, events.size());
            float dropdownY = eventLayout.eventInputY() - sidebarScroll + INPUT_HEIGHT;
            if (isHovered(
                    mx,
                    my,
                    PADDING,
                    dropdownY,
                    SIDEBAR_WIDTH - PADDING * 2,
                    visibleRows * RESOURCE_DROPDOWN_ROW_HEIGHT)) {
                worldEventDropdownScroll = clampDropdownScroll(
                        worldEventDropdownScroll + (scrollY > 0 ? -1 : 1),
                        events.size(),
                        WORLD_EVENT_DROPDOWN_VISIBLE_ROWS);
                return true;
            }
        }
        SidebarLayout layout = sidebarLayout();
        if (territoryDropdownOpen) {
            List<GuildTerritory> territories = territoryDropdownOptions();
            int visibleRows = Math.min(TERRITORY_DROPDOWN_VISIBLE_ROWS, territories.size());
            float dropdownY = layout.territoryInputY() - sidebarScroll + INPUT_HEIGHT;
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
            float dropdownY = layout.resourceInputY() - sidebarScroll + INPUT_HEIGHT;
            if (isHovered(mx, my, PADDING, dropdownY, SIDEBAR_WIDTH - PADDING * 2, visibleRows * RESOURCE_DROPDOWN_ROW_HEIGHT)) {
                resourceDropdownScroll = clampResourceDropdownScroll(resourceDropdownScroll + (scrollY > 0 ? -1 : 1), resources.size());
                return true;
            }
        }
        float screenWidth = uiScreenWidth();
        float screenHeight = uiScreenHeight();
        if (mx >= insightsSidebarX(screenWidth) && mx <= screenWidth) {
            return true;
        }
        if (mx >= 0 && mx <= SIDEBAR_WIDTH && my >= SIDEBAR_PANEL_TOP && my <= screenHeight) {
            sidebarScroll = clampSidebarScroll(sidebarScroll - (float) scrollY * SIDEBAR_SCROLL_STEP, screenHeight);
            return true;
        }
        MapViewport viewport = mapViewport(screenWidth, screenHeight);
        if (!viewport.isInsideScreen(mx, my)) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        double worldX = viewport.screenToWorldX(mx);
        double worldZ = viewport.screenToWorldZ(my);
        double factor = scrollY > 0 ? 1.15 : 1.0 / 1.15;
        pixelsPerBlock = clamp(pixelsPerBlock * factor, MIN_PIXELS_PER_BLOCK, MAX_PIXELS_PER_BLOCK);
        centerX = worldX - (mx - (viewport.screenX() + viewport.screenWidth() / 2.0)) / pixelsPerBlock;
        centerZ = worldZ - (my - (viewport.screenY() + viewport.screenHeight() / 2.0)) / pixelsPerBlock;
        return true;
    }

    @Override
    public boolean keyPressed(@NotNull KeyEvent keyEvent) {
        int keyCode = keyEvent.key();
        if (worldEventInputFocused) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                closeWorldEventSearch();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                applyWorldEventAutocompleteSelection();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!worldEventSearch.isEmpty()) {
                    worldEventSearch = worldEventSearch.substring(0, worldEventSearch.length() - 1);
                }
                worldEventDropdownOpen = true;
                worldEventDropdownScroll = 0;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_DELETE) {
                worldEventSearch = "";
                worldEventDropdownOpen = true;
                worldEventDropdownScroll = 0;
                return true;
            }
            Character typedCharacter = searchCharacter(keyEvent);
            if (typedCharacter != null) {
                worldEventSearch += typedCharacter;
                worldEventDropdownOpen = true;
                worldEventDropdownScroll = 0;
                return true;
            }
            return true;
        }
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
            Character typedCharacter = searchCharacter(keyEvent);
            if (typedCharacter != null) {
                territorySearch += typedCharacter;
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
            Character typedCharacter = searchCharacter(keyEvent);
            if (typedCharacter != null) {
                resourceSearch += typedCharacter;
                resourceDropdownOpen = true;
                resourceDropdownScroll = 0;
                return true;
            }
            return true;
        }
        if ((resourceDropdownOpen || territoryDropdownOpen || worldEventDropdownOpen)
                && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeSearchDropdowns();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    private static void drawCircle(long nvg, float x, float y, float radius, Color color) {
        nvgBeginPath(nvg);
        nvgCircle(nvg, x, y, radius);
        var nvgColor = NVGContext.nvgColor(color);
        nvgFillColor(nvg, nvgColor);
        nvgFill(nvg);
        nvgClosePath(nvg);
        nvgColor.free();
    }

    private static void drawCircleOutline(long nvg, float x, float y, float radius, float width, Color color) {
        nvgBeginPath(nvg);
        nvgCircle(nvg, x, y, radius);
        var nvgColor = NVGContext.nvgColor(color);
        nvgStrokeWidth(nvg, width);
        nvgStrokeColor(nvg, nvgColor);
        nvgStroke(nvg);
        nvgClosePath(nvg);
        nvgColor.free();
    }

    private static void drawText(long nvg, float x, float y, float size, String text, Color color, int align) {
        nvgFontSize(nvg, size);
        nvgTextAlign(nvg, align);
        var nvgColor = NVGContext.nvgColor(color);
        nvgFillColor(nvg, nvgColor);
        nvgText(nvg, x, y, text);
        nvgColor.free();
    }

    private static void drawSidebarText(long nvg, float x, float y, float size, String text, Color color) {
        drawFittedText(nvg, x, y, size, text, color, SIDEBAR_WIDTH - x - PADDING, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    private static void drawFittedText(long nvg, float x, float y, float size, String text, Color color, float maxWidth, int align) {
        nvgFontSize(nvg, size);
        String fitted = fitText(nvg, text, maxWidth);
        drawText(nvg, x, y, size, fitted, color, align);
    }

    private static String fitText(long nvg, String text, float maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (textWidth(nvg, text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        if (textWidth(nvg, ellipsis) > maxWidth) {
            return "";
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = text.substring(0, mid).stripTrailing() + ellipsis;
            if (textWidth(nvg, candidate) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low).stripTrailing() + ellipsis;
    }

    private static float textWidth(long nvg, String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        float[] bounds = new float[4];
        return nvgTextBounds(nvg, 0, 0, text, bounds);
    }

    private static String displayProfession(GatheringProfession profession) {
        String lower = profession.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String displayMapImageSource() {
        return switch (mapImageService.imageSource()) {
            case NONE -> "none";
            case FALLBACK -> "fallback";
            case CACHED_HQ -> "cached HQ";
        };
    }

    private float scaledMouseX(double rawX) {
        return NVGContext.mouseX(rawX);
    }

    private float scaledMouseY(double rawY) {
        return NVGContext.mouseY(rawY);
    }

    private static float uiScreenWidth() {
        return NVGContext.screenWidth();
    }

    private static float uiScreenHeight() {
        return NVGContext.screenHeight();
    }

    private static boolean isHovered(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static Character searchCharacter(KeyEvent keyEvent) {
        Character typedCharacter = TextInputHelper.getTypedCharacter(keyEvent);
        if (typedCharacter != null && TextInputHelper.isPrintableCharacter(typedCharacter)) {
            return Character.toUpperCase(typedCharacter);
        }

        int keyCode = keyEvent.key();
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return (char) ('A' + (keyCode - GLFW.GLFW_KEY_A));
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            return (char) ('0' + (keyCode - GLFW.GLFW_KEY_KP_0));
        }
        return switch (keyCode) {
            case GLFW.GLFW_KEY_SPACE -> ' ';
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> '-';
            case GLFW.GLFW_KEY_APOSTROPHE -> '\'';
            default -> null;
        };
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

    private void applyWorldEventAutocompleteSelection() {
        String search = worldEventSearch.trim();
        WorldEventDefinition match = allWorldEvents.stream()
                .filter(event -> event.name().equalsIgnoreCase(search))
                .findFirst()
                .orElse(null);
        if (match == null) {
            List<WorldEventDefinition> options = worldEventDropdownOptions();
            match = options.isEmpty() ? null : options.getFirst();
        }
        if (match != null) {
            toggleTrackedWorldEvent(match, true);
        }
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
        centerX = territory.centerX();
        centerZ = territory.centerZ();
        float screenWidth = uiScreenWidth();
        float screenHeight = uiScreenHeight();
        double width = Math.max(1, territory.bounds().maxX() - territory.bounds().minX());
        double height = Math.max(1, territory.bounds().maxZ() - territory.bounds().minZ());
        double xScale = Math.max(1, screenWidth - SIDEBAR_WIDTH - insightsSidebarInset()) / width;
        double zScale = Math.max(1, screenHeight) / height;
        pixelsPerBlock = clamp(
                Math.min(xScale, zScale) * 0.48,
                MIN_PIXELS_PER_BLOCK,
                TERRITORY_FOCUS_MAX_PIXELS_PER_BLOCK);
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

    private MapDisplayMode mapModeAt(float mouseX) {
        float segmentWidth = (SIDEBAR_WIDTH - PADDING * 2) / MapDisplayMode.values().length;
        int index = (int) ((mouseX - PADDING) / segmentWidth);
        return index >= 0 && index < MapDisplayMode.values().length ? MapDisplayMode.values()[index] : null;
    }

    private WorldEventDisplayFilter worldEventFilterAt(float mouseX) {
        float segmentWidth = (SIDEBAR_WIDTH - PADDING * 2) / WorldEventDisplayFilter.values().length;
        int index = (int) ((mouseX - PADDING) / segmentWidth);
        return index >= 0 && index < WorldEventDisplayFilter.values().length
                ? WorldEventDisplayFilter.values()[index]
                : null;
    }

    private void setDisplayMode(MapDisplayMode mode) {
        if (mode == null || mode == displayMode) {
            return;
        }
        displayMode = mode;
        mapSettings.setDisplayMode(mode);
        sidebarScroll = 0;
        draggingMap = false;
        selectedNode = null;
        selectedCluster = null;
        hoveredNode = null;
        hoveredCluster = null;
        hoveredTerritory = null;
        selectedWorldEvent = null;
        hoveredWorldEvent = null;
        hoveredWorldEventLocationIndex = -1;
        closeSearchDropdowns();
        if (mode == MapDisplayMode.WORLD_EVENTS) {
            SeqClient.getWorldEventManager().requestMapRefresh();
            refreshWorldEvents();
        }
    }

    private void closeSearchDropdowns() {
        closeResourceSearch();
        closeTerritorySearch();
        closeWorldEventSearch();
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

    private void closeWorldEventSearch() {
        worldEventDropdownOpen = false;
        worldEventInputFocused = false;
        worldEventSearch = "";
        worldEventDropdownScroll = 0;
    }

    private void toggleTrackedWorldEvent(WorldEventDefinition event, boolean keepOpen) {
        boolean tracked = SeqClient.getConfigManager().trackedWorldEventIds().contains(event.internalName());
        SeqClient.getConfigManager().setWorldEventTracked(event.internalName(), !tracked);
        worldEventDropdownOpen = keepOpen;
        worldEventInputFocused = keepOpen;
        if (!keepOpen) {
            worldEventSearch = "";
            worldEventDropdownScroll = 0;
        }
        refreshWorldEvents();
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
        return Math.max(0, Math.min(scroll, Math.max(0, optionCount - visibleRows)));
    }

    private float clampSidebarScroll(float scroll, float screenHeight) {
        return (float) clamp(scroll, 0, sidebarMaxScroll(screenHeight));
    }

    private float sidebarMaxScroll(float screenHeight) {
        float viewportHeight = Math.max(0, screenHeight - SIDEBAR_PANEL_TOP);
        return Math.max(0, sidebarContentHeight - SIDEBAR_PANEL_TOP - viewportHeight);
    }

    private float sidebarY(float contentY) {
        return contentY - sidebarScroll;
    }

    private float insightsSidebarInset() {
        return insightsSidebarOpen ? INSIGHTS_SIDEBAR_WIDTH : 0;
    }

    private float insightsSidebarX(float screenWidth) {
        return screenWidth - insightsSidebarInset();
    }

    private MapViewport mapViewport(float screenWidth, float screenHeight) {
        return new MapViewport(
                centerX,
                centerZ,
                pixelsPerBlock,
                SIDEBAR_WIDTH,
                0,
                Math.max(1, screenWidth - SIDEBAR_WIDTH - insightsSidebarInset()),
                screenHeight);
    }

    private InsightsLayout insightsLayout() {
        float overviewY = 60;
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            return new InsightsLayout(overviewY, -1, -1, -1, overviewY + 82);
        }
        float y = overviewY + (showDebugInfo ? 106 : 74);
        float territoryY = -1;
        if (selectedTerritory != null) {
            territoryY = y;
            y += TERRITORY_DETAIL_HEIGHT + 26;
        }
        float entityY = y;
        y += CLUSTER_DETAIL_HEIGHT + 26;
        return new InsightsLayout(overviewY, territoryY, entityY, y, -1);
    }

    private SidebarLayout sidebarLayout() {
        float y = 58;
        float backY = y;
        y += BUTTON_HEIGHT + 8;
        float centerY = y;
        y += BUTTON_HEIGHT + 18;
        float modeY = y;
        y += BUTTON_HEIGHT + 18;
        float mapPanelY = y;
        y += PANEL_HEADER_HEIGHT;
        float territoryToggleY = -1;
        float scopeLabelY = -1;
        float scopeY = -1;
        float territoryLabelY = -1;
        float territoryInputY = -1;
        if (panelExpanded(WorldMapSidebarPanel.MAP_AND_TERRITORY)) {
            y += 8;
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
        return new SidebarLayout(
                backY,
                centerY,
                modeY,
                mapPanelY,
                territoryToggleY,
                scopeLabelY,
                scopeY,
                territoryLabelY,
                territoryInputY,
                analysisPanelY,
                clustersY,
                filtersPanelY,
                resourceLabelY,
                resourceInputY,
                professionLabelY,
                professionStartY,
                y);
    }

    private WorldEventSidebarLayout worldEventSidebarLayout() {
        float y = 58;
        float backY = y;
        y += BUTTON_HEIGHT + 8;
        float centerY = y;
        y += BUTTON_HEIGHT + 18;
        float modeY = y;
        y += BUTTON_HEIGHT + 18;
        float displayPanelY = y;
        y += PANEL_HEADER_HEIGHT;
        float filterLabelY = -1;
        float filterY = -1;
        if (panelExpanded(WorldMapSidebarPanel.EVENT_DISPLAY)) {
            y += 8;
            filterLabelY = y;
            y += 12;
            filterY = y;
            y += BUTTON_HEIGHT + 8;
        }
        y += PANEL_GAP;
        float trackingPanelY = y;
        y += PANEL_HEADER_HEIGHT;
        float eventFilterY = -1;
        float eventInputY = -1;
        if (panelExpanded(WorldMapSidebarPanel.EVENT_TRACKING)) {
            y += 8;
            eventFilterY = y;
            y += BUTTON_HEIGHT + 8;
            eventInputY = y;
            y += INPUT_HEIGHT + 8;
        }
        return new WorldEventSidebarLayout(
                backY,
                centerY,
                modeY,
                displayPanelY,
                filterLabelY,
                filterY,
                trackingPanelY,
                eventFilterY,
                eventInputY,
                y);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ScreenPoint(float x, float y) {}

    private record TerritoryLabelLayout(List<String> lines, float fontSize, float lineHeight) {}

    private record SidebarLayout(
            float backY,
            float centerY,
            float modeY,
            float mapPanelY,
            float territoryToggleY,
            float scopeLabelY,
            float scopeY,
            float territoryLabelY,
            float territoryInputY,
            float analysisPanelY,
            float clustersY,
            float filtersPanelY,
            float resourceLabelY,
            float resourceInputY,
            float professionLabelY,
            float professionStartY,
            float endY) {}

    private record WorldEventSidebarLayout(
            float backY,
            float centerY,
            float modeY,
            float displayPanelY,
            float filterLabelY,
            float filterY,
            float trackingPanelY,
            float eventFilterY,
            float eventInputY,
            float endY) {}

    private record InsightsLayout(
            float overviewY,
            float territoryY,
            float entityY,
            float topClustersY,
            float eventDetailY) {}

    private record ClusterOutlineShape(
            List<ScreenPoint> points,
            float minX,
            float maxX,
            float minY,
            float maxY) {
        private static ClusterOutlineShape from(List<ScreenPoint> points) {
            if (points.isEmpty()) {
                return new ClusterOutlineShape(List.of(), 0, 0, 0, 0);
            }
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (ScreenPoint point : points) {
                minX = Math.min(minX, point.x());
                maxX = Math.max(maxX, point.x());
                minY = Math.min(minY, point.y());
                maxY = Math.max(maxY, point.y());
            }
            return new ClusterOutlineShape(List.copyOf(points), minX, maxX, minY, maxY);
        }

        private boolean isVisible(MapViewport viewport, float centerScreenX, float centerScreenY) {
            return centerScreenX + maxX >= viewport.screenX()
                    && centerScreenX + minX <= viewport.screenX() + viewport.screenWidth()
                    && centerScreenY + maxY >= viewport.screenY()
                    && centerScreenY + minY <= viewport.screenY() + viewport.screenHeight();
        }
    }

    private record TileRange(int minX, int maxX, int minY, int maxY) {}

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
