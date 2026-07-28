package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.managers.ThemeManager.withAlpha;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.mojang.authlib.GameProfile;
import java.awt.Color;
import java.nio.ByteBuffer;
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
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.ClusterScoreMode;
import com.seqwawa.seq.map.GatheringAnalysisScope;
import com.seqwawa.seq.map.GatheringClusterCache;
import com.seqwawa.seq.map.GatheringMapImageService;
import com.seqwawa.seq.map.GatheringNode;
import com.seqwawa.seq.map.GatheringNodeCluster;
import com.seqwawa.seq.map.GatheringNodeService;
import com.seqwawa.seq.map.GatheringProfession;
import com.seqwawa.seq.map.GatheringTotemHitTester;
import com.seqwawa.seq.map.GatheringTotemResults;
import com.seqwawa.seq.map.GatheringTotemSearchTarget;
import com.seqwawa.seq.map.GatheringTotemSolver;
import com.seqwawa.seq.map.GatheringTotemSolver.Placement;
import com.seqwawa.seq.map.GatheringTotemSolver.Position;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.map.GuildTerritoryService;
import com.seqwawa.seq.map.IngredientFarmSpot;
import com.seqwawa.seq.map.IngredientFarmSpotCatalog;
import com.seqwawa.seq.map.IngredientMapSelection;
import com.seqwawa.seq.map.IngredientMapCategory;
import com.seqwawa.seq.map.IngredientWaypointManager;
import com.seqwawa.seq.map.IngredientWaypointManager.Kind;
import com.seqwawa.seq.map.IngredientWaypointManager.Waypoint;
import com.seqwawa.seq.map.IngredientWaypointManager.WaypointIcon;
import com.seqwawa.seq.map.MapCalibration;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapDisplayMode;
import com.seqwawa.seq.map.MapFocus;
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
import com.seqwawa.seq.render.MinecraftGuiOverlay;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.MinecraftUiRenderer;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderMetrics;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WorldMapScreen extends Screen implements MinecraftGuiOverlay {
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
    private static final double TWO_PI = Math.PI * 2.0;
    private static final double NODE_DETAIL_PIXELS_PER_BLOCK = 0.42;
    private static final double CLUSTER_BADGE_PIXELS_PER_BLOCK = 0.65;
    private static final double TERRITORY_FOCUS_MAX_PIXELS_PER_BLOCK = 1.25;
    private static final double CONTEXT_FOCUS_MAX_PIXELS_PER_BLOCK = 0.75;
    private static final int SIDEBAR_CLUSTER_LIMIT = 5;
    private static final int TOTEM_RESULT_VISIBLE_ROWS = 4;
    private static final float TOTEM_RESULT_ROW_HEIGHT = 28;
    private static final float INGREDIENT_FARM_SPOT_CARD_HEIGHT = 56;
    private static final float INGREDIENT_FARM_SPOT_ROW_HEIGHT = 62;
    private static final float INGREDIENT_FARM_SPOT_CARD_PADDING = 8;
    private static final ItemStack TOTEM_MAP_ICON = new ItemStack(Items.TOTEM_OF_UNDYING);
    private static final long TOTEM_SOLVE_DEBOUNCE_MS = 200;
    private final Screen parent;
    private final MapFocus mapFocus;
    private final ItemStack mapFocusIcon;
    private final Supplier<PlayerSkin> mapFocusSkinLookup;
    private final List<FocusIconOverlay> focusIconOverlays = new ArrayList<>();
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
    private float ingredientFarmSpotScroll;
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
    private boolean gatheringTotemSolverEnabled;
    private boolean showGatheringTotemHulls = true;
    private boolean showGatheringTotemPlayerRadius = true;
    private boolean showGatheringTotemNodeReach = true;
    private boolean showGatheringTotemCoveredNodes = true;
    private boolean showOtherOptimalGatheringTotems = true;
    private boolean showTerritories;
    private boolean showTerritoryNames;
    private boolean showDebugInfo;
    private ClusterScoreMode clusterScoreMode = ClusterScoreMode.FOUR_TICK;
    private GatheringAnalysisScope gatheringAnalysisScope = GatheringAnalysisScope.ALL;
    private GatheringTotemSearchTarget gatheringTotemSearchTarget = GatheringTotemSearchTarget.ALL_FILTERED;
    private MapDisplayMode displayMode = MapDisplayMode.GATHERING;
    private WorldEventDisplayFilter worldEventDisplayFilter = WorldEventDisplayFilter.ALL;
    private List<WorldEventDefinition> allWorldEvents = List.of();
    private List<WorldEventDefinition> visibleWorldEvents = List.of();
    private Set<String> cachedTrackedWorldEventIds = Set.of();
    private long cachedWorldEventSnapshotVersion = -1;
    private WorldEventDisplayFilter cachedWorldEventDisplayFilter;
    private WorldEventDefinition hoveredWorldEvent;
    private int hoveredWorldEventLocationIndex = -1;
    private WorldEventDefinition selectedWorldEvent;
    private final IngredientMapSelection ingredientMapSelection = new IngredientMapSelection();
    private MapFocus.Marker hoveredFocusMarker;
    private MapFocus.Marker selectedFocusMarker;
    private IngredientMapCategory ingredientMapCategory = IngredientMapCategory.SPAWNS;
    private IngredientFarmSpot hoveredIngredientFarmSpot;
    private IngredientFarmSpot selectedIngredientFarmSpot;
    private List<GatheringNode> cachedSourceNodes = List.of();
    private List<GatheringNode> cachedFilteredNodes = List.of();
    private List<GatheringNodeCluster> cachedClusters = List.of();
    private List<Placement> gatheringTotemPlacements = List.of();
    private Placement gatheringTotemPlacement;
    private Placement hoveredGatheringTotemPlacement;
    private String selectedGatheringTotemPlacementKey;
    private int gatheringTotemResultScroll;
    private CompletableFuture<List<Placement>> pendingGatheringTotemSolve;
    private String pendingGatheringTotemKey = "";
    private String solvedGatheringTotemKey = "";
    private String observedGatheringTotemKey = "";
    private String gatheringTotemSolveError;
    private long gatheringTotemSolveNotBeforeMs;
    private long gatheringTotemRequestGeneration;
    private long pendingGatheringTotemGeneration;
    private Map<String, Integer> cachedTerritoryNodeCounts = Map.of();
    private int selectedTerritoryMatchingNodeCount;
    private final Map<GatheringNodeCluster, ClusterOutlineShape> clusterOutlineShapes = new IdentityHashMap<>();
    private double clusterOutlineScale = Double.NaN;
    private List<String> cachedResourceOptions = List.of();
    private String cachedClusterKey = "";
    private long cachedSettingsVersion = -1;
    private long gatheringAnalysisVersion;
    private UiImage mapImage;
    private boolean mapImageLoadAttempted;
    private long loadedMapImageVersion = -1;
    private final Map<TileKey, UiImage> tileImages = new HashMap<>();
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
        this(parent, null, ItemStack.EMPTY, null);
    }

    public WorldMapScreen(Screen parent, MapFocus mapFocus) {
        this(parent, mapFocus, ItemStack.EMPTY, null);
    }

    public WorldMapScreen(Screen parent, MapFocus mapFocus, ItemStack mapFocusIcon) {
        this(parent, mapFocus, mapFocusIcon, null);
    }

    public WorldMapScreen(
            Screen parent,
            MapFocus mapFocus,
            ItemStack mapFocusIcon,
            GameProfile mapFocusSkinProfile) {
        this(parent, mapFocus, mapFocusIcon, mapFocusSkinProfile, null);
    }

    public WorldMapScreen(Screen parent, IngredientFarmSpot farmSpot) {
        this(parent, null, ItemStack.EMPTY, null, farmSpot);
    }

    private WorldMapScreen(
            Screen parent,
            MapFocus mapFocus,
            ItemStack mapFocusIcon,
            GameProfile mapFocusSkinProfile,
            IngredientFarmSpot farmSpot) {
        super(Component.literal("Sequoia Map"));
        this.parent = parent;
        this.mapFocus = mapFocus;
        this.mapFocusIcon = mapFocusIcon == null ? ItemStack.EMPTY : mapFocusIcon.copy();
        this.mapFocusSkinLookup = mapFocusSkinProfile == null
                ? null
                : SeqClient.mc.getSkinManager().createLookup(mapFocusSkinProfile, false);
        this.selectedFocusMarker = mapFocus == null ? null : mapFocus.selectedMarker();
        this.selectedIngredientFarmSpot = farmSpot;
        if (selectedFocusMarker != null) {
            ingredientMapSelection.toggleSpawn(selectedFocusMarker.id());
        }
        if (selectedIngredientFarmSpot != null) {
            ingredientMapSelection.toggleTotem(selectedIngredientFarmSpot.id());
        }
        this.ingredientMapCategory =
                farmSpot == null ? IngredientMapCategory.SPAWNS : IngredientMapCategory.TOTEM_SPOTS;
        professionToggles.putAll(mapSettings.professionToggles());
        selectedResourceFilters.addAll(mapSettings.resourceFilters());
        showClusters = mapSettings.showClusters();
        gatheringTotemSolverEnabled = mapSettings.gatheringTotemSolverEnabled();
        gatheringTotemSearchTarget = mapSettings.gatheringTotemSearchTarget();
        showGatheringTotemHulls = mapSettings.showGatheringTotemHulls();
        showGatheringTotemPlayerRadius = mapSettings.showGatheringTotemPlayerRadius();
        showGatheringTotemNodeReach = mapSettings.showGatheringTotemNodeReach();
        showGatheringTotemCoveredNodes = mapSettings.showGatheringTotemCoveredNodes();
        showOtherOptimalGatheringTotems = mapSettings.showOtherOptimalGatheringTotems();
        showTerritories = mapSettings.showTerritories();
        showTerritoryNames = mapSettings.showTerritoryNames();
        showDebugInfo = mapSettings.showDebugInfo();
        clusterScoreMode = mapSettings.clusterScoreMode();
        gatheringAnalysisScope = mapSettings.gatheringAnalysisScope();
        displayMode = (mapFocus == null || mapFocus.markers().isEmpty()) && farmSpot == null
                ? mapSettings.displayMode()
                : MapDisplayMode.INGREDIENTS;
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
        resetGatheringTotemSolve();
        UiRenderer.renderResource(canvas -> {
            if (mapImage != null) {
                UiRenderer.deleteImage(mapImage);
                mapImage = null;
            }
            clearTileImages();
        });
        super.removed();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        nvgMouseX = MinecraftUiRenderer.mouseX(mouseX);
        nvgMouseY = MinecraftUiRenderer.mouseY(mouseY);

        float screenWidth = uiScreenWidth();
        float screenHeight = uiScreenHeight();
        showDebugInfo = mapSettings.showDebugInfo();
        float mapX = SIDEBAR_WIDTH;
        float mapY = 0;
        float mapW = Math.max(1, screenWidth - SIDEBAR_WIDTH - insightsSidebarInset());
        float mapH = Math.max(1, screenHeight);

        if (!initializedViewport) {
            initializedViewport = true;
            if (selectedIngredientFarmSpot != null) {
                centerOnIngredientFarmSpot(selectedIngredientFarmSpot);
            } else if (hasMapFocus()) {
                fitMapFocus(mapW, mapH);
            } else {
                fitFullMap(mapW, mapH);
            }
        }

        if (displayMode == MapDisplayMode.GATHERING) {
            refreshClusterAnalysisIfNeeded();
            refreshGatheringTotemPlacement();
        } else if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            refreshWorldEvents();
        }
        MapViewport viewport = new MapViewport(centerX, centerZ, pixelsPerBlock, mapX, mapY, mapW, mapH);
        UiRenderer.renderScreen(this, canvas -> renderNvg(canvas, viewport));
    }

    private void renderNvg(UiCanvas canvas, MapViewport viewport) {
        hoveredFocusMarker = null;
        hoveredIngredientFarmSpot = null;
        focusIconOverlays.clear();
        renderMapBackground(canvas, viewport);
        canvas.fillRect(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight(), color(MAP_TINT));
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            renderWorldEvents(canvas, viewport);
            renderPlayer(canvas, viewport);
            renderSidebar(canvas);
            renderInsightsSidebar(canvas);
            return;
        }
        if (displayMode == MapDisplayMode.INGREDIENTS) {
            if (ingredientMapCategory == IngredientMapCategory.TOTEM_SPOTS) {
                renderIngredientFarmSpotMarkers(canvas, viewport);
            } else {
                renderMapFocus(canvas, viewport);
            }
            renderPlayer(canvas, viewport);
            renderSidebar(canvas);
            renderInsightsSidebar(canvas);
            return;
        }

        renderTerritories(canvas, viewport);
        boolean clusterMode = shouldRenderClusters();
        if (clusterMode) {
            hoveredNode = null;
        }
        if (!showClusters || cachedClusters.isEmpty()) {
            hoveredCluster = null;
        }
        if (showClusters && !cachedClusters.isEmpty()) {
            renderClusterHulls(canvas, viewport, !draggingMap);
            if (clusterMode) {
                renderClusterBadges(canvas, viewport, true);
            }
        }
        if (!clusterMode) {
            renderNodes(canvas, viewport, cachedFilteredNodes);
            if (shouldRenderClusterBadges()) {
                renderClusterBadges(canvas, viewport, false);
            }
        }
        renderGatheringTotemPlacements(canvas, viewport);
        renderPlayer(canvas, viewport);
        renderTerritoryNames(canvas, viewport);
        if (!draggingMap && hoveredGatheringTotemPlacement != null) {
            renderGatheringTotemTooltip(canvas, hoveredGatheringTotemPlacement);
        } else if (!draggingMap && hoveredCluster != null && (clusterMode || hoveredNode == null)) {
            renderClusterTooltip(canvas, hoveredCluster);
        } else if (!draggingMap && hoveredNode == null && hoveredTerritory != null) {
            renderTerritoryTooltip(canvas, hoveredTerritory);
        }
        renderSidebar(canvas);
        renderInsightsSidebar(canvas);
    }

    private void renderMapFocus(UiCanvas canvas, MapViewport viewport) {
        if (!hasMapFocus()) {
            return;
        }

        MapBounds visibleBounds = viewport.visibleBounds();
        if (!draggingMap && viewport.isInsideScreen(nvgMouseX, nvgMouseY)) {
            double closestDistance = 10;
            for (MapFocus.Marker marker : mapFocus.markers()) {
                if (!visibleBounds.contains(marker.x(), marker.z())) {
                    continue;
                }
                double distance = Math.hypot(
                        nvgMouseX - viewport.worldToScreenX(marker.x()),
                        nvgMouseY - viewport.worldToScreenZ(marker.z()));
                if (distance <= closestDistance) {
                    hoveredFocusMarker = marker;
                    closestDistance = distance;
                }
            }
        }

        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (MapFocus.Marker marker : mapFocus.markers()) {
            if (!visibleBounds.contains(marker.x(), marker.z())) {
                continue;
            }
            float x = viewport.worldToScreenX(marker.x());
            float y = viewport.worldToScreenZ(marker.z());
            float areaRadius = (float) (marker.radius() * viewport.pixelsPerBlock());
            boolean selected = ingredientMapSelection.isSpawnSelected(marker.id());
            boolean hovered = marker.equals(hoveredFocusMarker);
            Color markerColor = selected ? color(MAP_SELECTED_TERRITORY) : color(ACCENT_PRIMARY);
            if (areaRadius >= 4) {
                drawCircle(canvas, x, y, areaRadius, withAlpha(markerColor, selected ? 34 : 20));
                drawCircleOutline(canvas, x, y, areaRadius, selected ? 1.5f : 1, withAlpha(markerColor, 145));
            }
            if (mapFocusIcon.isEmpty()) {
                float markerRadius = selected || hovered ? 4 : 3;
                drawCircle(canvas, x, y, markerRadius + 1.5f, color(BACKGROUND_MODAL_OVERLAY, 190));
                drawCircle(canvas, x, y, markerRadius, markerColor);
            } else {
                float iconSize = selected || hovered ? 22 : 18;
                float outlineRadius = iconSize / 2f + 1;
                drawCircle(canvas, x, y, outlineRadius, color(BACKGROUND_MODAL_OVERLAY, 145));
                if (selected || hovered) {
                    drawCircleOutline(canvas, x, y, outlineRadius, 1, markerColor);
                }
                focusIconOverlays.add(new FocusIconOverlay(
                        x - iconSize / 2f,
                        y - iconSize / 2f,
                        iconSize,
                        mapFocusIcon,
                        mapFocusSkinLookup));
            }
        }
        canvas.resetScissor();

        renderMapFocusBanner(canvas, viewport);
        if (hoveredFocusMarker != null) {
            renderMapFocusTooltip(canvas, hoveredFocusMarker);
        }
    }

    private void renderIngredientFarmSpotMarkers(UiCanvas canvas, MapViewport viewport) {
        List<IngredientFarmSpot> spots = IngredientFarmSpotCatalog.all();
        MapBounds visibleBounds = viewport.visibleBounds();
        if (!draggingMap && viewport.isInsideScreen(nvgMouseX, nvgMouseY)) {
            double closestDistance = 11;
            for (IngredientFarmSpot spot : spots) {
                if (!visibleBounds.contains(spot.x(), spot.z())) {
                    continue;
                }
                double distance = Math.hypot(
                        nvgMouseX - viewport.worldToScreenX(spot.x()),
                        nvgMouseY - viewport.worldToScreenZ(spot.z()));
                if (distance <= closestDistance) {
                    hoveredIngredientFarmSpot = spot;
                    closestDistance = distance;
                }
            }
        }

        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (IngredientFarmSpot spot : spots) {
            if (!visibleBounds.contains(spot.x(), spot.z())) {
                continue;
            }
            float x = viewport.worldToScreenX(spot.x());
            float y = viewport.worldToScreenZ(spot.z());
            boolean selected = ingredientMapSelection.isTotemSelected(spot.id());
            boolean hovered = spot.equals(hoveredIngredientFarmSpot);
            Color markerColor = selected ? color(MAP_SELECTED_TERRITORY) : color(ACCENT_PRIMARY);
            float areaRadius = (float) (spot.radius() * viewport.pixelsPerBlock());
            if (areaRadius >= 4) {
                drawCircle(canvas, x, y, areaRadius, withAlpha(markerColor, selected ? 34 : 20));
                drawCircleOutline(canvas, x, y, areaRadius, selected ? 1.5f : 1, withAlpha(markerColor, 145));
            }
            float iconSize = selected || hovered ? 22 : 18;
            float outlineRadius = iconSize / 2f + 1;
            drawCircle(canvas, x, y, outlineRadius, color(BACKGROUND_MODAL_OVERLAY, 190));
            drawCircleOutline(canvas, x, y, outlineRadius, selected || hovered ? 1.5f : 1, markerColor);
            focusIconOverlays.add(new FocusIconOverlay(
                    x - iconSize / 2f,
                    y - iconSize / 2f,
                    iconSize,
                    TOTEM_MAP_ICON,
                    null));
        }
        canvas.resetScissor();

        if (hoveredIngredientFarmSpot != null) {
            renderIngredientFarmSpotTooltip(canvas, hoveredIngredientFarmSpot);
        }
    }

    private void renderIngredientFarmSpotTooltip(UiCanvas canvas, IngredientFarmSpot spot) {
        String subtitle = spot.coordinates() + " · " + String.join(", ", spot.ingredients());
        float x = tooltipX(250);
        float y = Math.max(58, nvgMouseY + 12);
        canvas.fillRect(x, y, 250, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 250, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, spot.name(), color(MAP_TEXT), 234, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 10, subtitle, color(MAP_SUBTEXT), 234, TextAlignment.LEFT);
    }

    @Override
    public void renderMinecraftGuiOverlay(GuiGraphics guiGraphics, UiRenderMetrics metrics) {
        if (displayMode != MapDisplayMode.INGREDIENTS || focusIconOverlays.isEmpty()) {
            return;
        }
        float guiUnitsPerUiUnit = metrics.pixelRatio() / (float) metrics.minecraftGuiScale();
        for (FocusIconOverlay overlay : focusIconOverlays) {
            float itemScale = overlay.size() * guiUnitsPerUiUnit / 16f;
            guiGraphics.pose().pushMatrix();
            try {
                guiGraphics.pose().translate(
                        overlay.x() * guiUnitsPerUiUnit,
                        overlay.y() * guiUnitsPerUiUnit);
                guiGraphics.pose().scale(itemScale, itemScale);
                if (overlay.skinLookup() != null) {
                    PlayerFaceRenderer.draw(guiGraphics, overlay.skinLookup().get(), 0, 0, 16);
                } else {
                    guiGraphics.renderItem(overlay.stack(), 0, 0);
                }
            } finally {
                guiGraphics.pose().popMatrix();
            }
        }
    }

    private void renderMapFocusBanner(UiCanvas canvas, MapViewport viewport) {
        String title = mapFocus.title();
        String subtitle = mapFocus.markers().size() + (mapFocus.markers().size() == 1
                ? " spawn location"
                : " spawn locations");
        float width = Math.min(260, Math.max(170, textWidth(title, 12) + 32));
        float x = viewport.screenX() + (viewport.screenWidth() - width) / 2f;
        float y = viewport.screenY() + 10;
        canvas.fillRoundedRect(x, y, width, 42, 6, color(MAP_SIDEBAR, 235));
        canvas.strokeRect(x, y, width, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 10, y + 14, 12, title, color(MAP_TEXT), width - 20, TextAlignment.LEFT);
        drawFittedText(canvas, x + 10, y + 30, 10, subtitle, color(MAP_SUBTEXT), width - 20, TextAlignment.LEFT);
    }

    private void renderMapFocusTooltip(UiCanvas canvas, MapFocus.Marker marker) {
        String subtitle = marker.source() + " · " + marker.coordinates();
        float x = tooltipX(230);
        float y = Math.max(58, nvgMouseY + 12);
        canvas.fillRect(x, y, 230, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 230, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, marker.label(), color(MAP_TEXT), 214, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 10, subtitle, color(MAP_SUBTEXT), 214, TextAlignment.LEFT);
    }

    private void renderTerritories(UiCanvas canvas, MapViewport viewport) {
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
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
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
            Color color = selected ? color(MAP_SELECTED_TERRITORY) : color(MAP_TERRITORY);
            if (selected || hovered) {
                int alpha = selected ? 38 : 24;
                canvas.fillRect(x, y, width, height, withAlpha(color, alpha));
            }
            canvas.strokeRect(x,
                    y,
                    width,
                    height,
                    selected || hovered ? 1.8f : 0.8f,
                    withAlpha(color, selected || hovered ? 235 : 115));
        }
        canvas.resetScissor();
    }

    private void refreshWorldEvents() {
        WorldEventService.Snapshot snapshot = worldEventService.snapshot();
        Set<String> trackedWorldEventIds = SeqClient.getConfigManager().trackedWorldEventIds();
        if (snapshot.version() == cachedWorldEventSnapshotVersion
                && worldEventDisplayFilter == cachedWorldEventDisplayFilter
                && trackedWorldEventIds.equals(cachedTrackedWorldEventIds)) {
            return;
        }
        cachedWorldEventSnapshotVersion = snapshot.version();
        cachedWorldEventDisplayFilter = worldEventDisplayFilter;
        cachedTrackedWorldEventIds = trackedWorldEventIds;
        allWorldEvents = snapshot.events();
        visibleWorldEvents = WorldEventFilters.visibleEvents(
                allWorldEvents,
                worldEventDisplayFilter,
                cachedTrackedWorldEventIds);
        selectedWorldEvent = WorldEventFilters.retainVisibleSelection(selectedWorldEvent, visibleWorldEvents);
    }

    private void renderWorldEvents(UiCanvas canvas, MapViewport viewport) {
        hoveredWorldEvent = null;
        hoveredWorldEventLocationIndex = -1;
        boolean allowHover = !draggingMap && viewport.isInsideScreen(nvgMouseX, nvgMouseY);
        MapBounds visibleBounds = viewport.visibleBounds();
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

        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (WorldEventDefinition event : visibleWorldEvents) {
            boolean eventSelected = selectedWorldEvent != null && selectedWorldEvent.runId().equals(event.runId());
            boolean eventTracked = cachedTrackedWorldEventIds.contains(event.internalName());
            for (int locationIndex = 0; locationIndex < event.locations().size(); locationIndex++) {
                WorldEventLocation location = event.locations().get(locationIndex);
                if (!visibleBounds.contains(location.x(), location.z())) {
                    continue;
                }
                float x = viewport.worldToScreenX(location.x());
                float y = viewport.worldToScreenZ(location.z());
                float areaRadius = (float) (location.radius() * viewport.pixelsPerBlock());
                if (areaRadius >= 5) {
                    Color areaColor = eventTracked ? color(MAP_TRACKED_WORLD_EVENT) : color(MAP_WORLD_EVENT);
                    drawCircleOutline(canvas, x, y, areaRadius, 1, withAlpha(areaColor, eventSelected ? 150 : 65));
                }

                Color markerColor = eventTracked ? color(MAP_TRACKED_WORLD_EVENT) : color(MAP_WORLD_EVENT);
                boolean highlighted = eventSelected || (event.equals(hoveredWorldEvent) && locationIndex == hoveredWorldEventLocationIndex);
                if (markerAsset == null) {
                    drawCircle(canvas, x, y, highlighted ? 8 : 7, color(BACKGROUND_MODAL_OVERLAY, 190));
                    drawCircle(canvas, x, y, highlighted ? 5.5f : 4.5f, eventSelected ? color(MAP_PLAYER) : markerColor);
                } else {
                    float outerRadius = highlighted ? 9 : 8;
                    float assetSize = highlighted ? 12 : 11;
                    drawCircle(canvas, x, y, outerRadius, color(BACKGROUND_MODAL_OVERLAY, 210));
                    drawCircle(canvas, x, y, outerRadius - 1.5f, markerColor);
                    canvas.drawImage(
                            markerAsset.getImage(),
                            x - assetSize / 2,
                            y - assetSize / 2,
                            assetSize,
                            assetSize,
                            1f);
                    if (eventSelected) {
                        drawCircleOutline(canvas, x, y, outerRadius + 1, 1.5f, color(MAP_PLAYER));
                    }
                }
            }
        }
        canvas.resetScissor();
        if (hoveredWorldEvent != null) {
            renderWorldEventTooltip(canvas, hoveredWorldEvent, hoveredWorldEventLocationIndex);
        }
    }

    private void renderTerritoryNames(UiCanvas canvas, MapViewport viewport) {
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
            TerritoryLabelLayout label = fitTerritoryLabel(canvas, territory.name(), width - 8, height - 6);
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
                    ? color(MAP_SELECTED_TERRITORY)
                    : territory.equals(hoveredTerritory) ? color(MAP_TERRITORY_HOVER_TEXT) : color(MAP_TEXT);
            float totalHeight = label.lines().size() * label.lineHeight();
            float lineY = y + (height - totalHeight) / 2f + label.lineHeight() / 2f;
            canvas.save();
            canvas.scissor(clipX, clipY, clipMaxX - clipX, clipMaxY - clipY);
            for (String line : label.lines()) {
                drawText(
                        canvas,
                        x + width / 2f + 1,
                        lineY + 1,
                        label.fontSize(),
                        line,
                        color(BACKGROUND_MODAL_OVERLAY, 210),
                        TextAlignment.CENTER);
                drawText(
                        canvas,
                        x + width / 2f,
                        lineY,
                        label.fontSize(),
                        line,
                        textColor,
                        TextAlignment.CENTER);
                lineY += label.lineHeight();
            }
            canvas.restore();
        }
    }

    private static TerritoryLabelLayout fitTerritoryLabel(UiCanvas canvas, String name, float maxWidth, float maxHeight) {
        if (maxWidth < 4 || maxHeight < 6) {
            return null;
        }
        for (float fontSize = 11; fontSize >= 6; fontSize--) {
            List<String> lines = wrapTerritoryName(canvas, name, maxWidth, fontSize);
            float lineHeight = fontSize + 2;
            if (!lines.isEmpty() && lines.size() * lineHeight <= maxHeight) {
                return new TerritoryLabelLayout(lines, fontSize, lineHeight);
            }
        }
        return null;
    }

    private static List<String> wrapTerritoryName(UiCanvas canvas, String name, float maxWidth, float fontSize) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (textWidth(candidate, fontSize) <= maxWidth) {
                currentLine.setLength(0);
                currentLine.append(candidate);
                continue;
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine.setLength(0);
            }
            if (textWidth(word, fontSize) <= maxWidth) {
                currentLine.append(word);
                continue;
            }
            List<String> pieces = splitTerritoryWord(canvas, word, maxWidth, fontSize);
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

    private static List<String> splitTerritoryWord(UiCanvas canvas, String word, float maxWidth, float fontSize) {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (int index = 0; index < word.length(); index++) {
            char character = word.charAt(index);
            String candidate = piece.toString() + character;
            if (textWidth(candidate, fontSize) <= maxWidth) {
                piece.append(character);
                continue;
            }
            if (piece.isEmpty()) {
                return List.of();
            }
            pieces.add(piece.toString());
            piece.setLength(0);
            if (textWidth(String.valueOf(character), fontSize) > maxWidth) {
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

    private void renderMapBackground(UiCanvas canvas, MapViewport viewport) {
        UiImage image = mapImage();
        if (image != null) {
            renderFullMapImage(canvas, viewport, image);
        }
        renderMapTiles(canvas, viewport);
    }

    private void renderFullMapImage(UiCanvas canvas, MapViewport viewport, UiImage image) {
        float x = viewport.worldToScreenX(MapCalibration.MIN_WORLD_X);
        float y = viewport.worldToScreenZ(MapCalibration.MIN_WORLD_Z);
        float width = viewport.worldToScreenX(MapCalibration.MAX_WORLD_X) - x;
        float height = viewport.worldToScreenZ(MapCalibration.MAX_WORLD_Z) - y;
        if (width <= 0 || height <= 0) {
            return;
        }

        try {
            canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
            canvas.drawImage(image, x, y, width, height, 1f);
        } finally {
            canvas.resetScissor();
        }
    }

    private void renderMapTiles(UiCanvas canvas, MapViewport viewport) {
        var manifest = mapImageService.manifest().orElse(null);
        TileSet tileSet = manifest == null ? null : manifest.tiles();
        if (tileSet == null || !"tiles".equalsIgnoreCase(manifest.preferredMode())) {
            if (!tileImages.isEmpty()) {
                clearTileImages();
                loadedTileVersion = "";
            }
            resetTileRangeCache();
            return;
        }
        if (!manifest.version().equals(loadedTileVersion)) {
            clearTileImages();
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

        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        try {
            for (TileKey key : cachedVisibleTiles) {
                UiImage tileImage = tileImage(key, loadMissingTileHandles);
                if (tileImage != null) {
                    renderTile(canvas, viewport, tileSet, key, tileImage);
                }
            }
        } finally {
            canvas.resetScissor();
        }
        loadedTileContentVersion = tileContentVersion;
    }

    private UiImage tileImage(TileKey key, boolean loadMissing) {
        UiImage existing = tileImages.get(key);
        if (existing != null) {
            return existing;
        }
        if (!loadMissing) {
            return null;
        }
        byte[] imageBytes = mapImageService.cachedTileBytes(key);
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        try {
            UiImage image = UiRenderer.createImage(ByteBuffer.wrap(imageBytes), true);
            if (image != null) {
                tileImages.put(key, image);
            }
            return image;
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[GatheringMap] Could not load map tile {}.", key.id(), exception);
            return null;
        }
    }

    private void renderTile(UiCanvas canvas, MapViewport viewport, TileSet tileSet, TileKey key, UiImage image) {
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

        canvas.drawImage(image, x, y, width, height, 1f);
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

    private void clearTileImages() {
        for (UiImage image : tileImages.values()) {
            UiRenderer.deleteImage(image);
        }
        tileImages.clear();
    }

    private void resetTileRangeCache() {
        cachedVisibleTileRange = null;
        cachedPrefetchTileRange = null;
        cachedVisibleTiles = List.of();
        cachedPrefetchTiles = List.of();
        loadedTileContentVersion = -1;
        lastTileRequestAtMs = 0;
    }

    private UiImage mapImage() {
        long imageVersion = mapImageService.version();
        if (mapImage != null && loadedMapImageVersion == imageVersion) {
            return mapImage;
        }
        if (mapImage != null) {
            UiRenderer.deleteImage(mapImage);
            mapImage = null;
        }
        if (mapImageLoadAttempted && loadedMapImageVersion == imageVersion) {
            return null;
        }
        mapImageLoadAttempted = true;

        try {
            byte[] imageBytes = mapImageService.imageBytes();
            if (imageBytes.length == 0) {
                loadedMapImageVersion = imageVersion;
                return null;
            }
            mapImage = UiRenderer.createImage(ByteBuffer.wrap(imageBytes), true);
            loadedMapImageVersion = imageVersion;
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn(
                    "[GatheringMap] Could not load {} map image.",
                    mapImageService.imageSource().name().toLowerCase(Locale.ROOT),
                    exception);
            mapImage = null;
            loadedMapImageVersion = imageVersion;
        }
        return mapImage;
    }

    private void renderClusterHulls(UiCanvas canvas, MapViewport viewport, boolean allowHover) {
        hoveredCluster = null;
        float bestHoverDistance = 18f;
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
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
            renderClusterOutline(canvas, viewport, cluster, outline, x, y, selected, selected || hovered);
        }
        canvas.resetScissor();
    }

    private void renderClusterBadges(UiCanvas canvas, MapViewport viewport, boolean overviewMode) {
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
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
            drawClusterMarker(canvas, x, y, clusterRadius(cluster), cluster, false, false);
        }
        if (selectedBadge != null) {
            float x = viewport.worldToScreenX(selectedBadge.centerX());
            float y = viewport.worldToScreenZ(selectedBadge.centerZ());
            drawClusterMarker(canvas, x, y, clusterRadius(selectedBadge), selectedBadge, true, true);
        }
        if (hoveredBadge != null) {
            float x = viewport.worldToScreenX(hoveredBadge.centerX());
            float y = viewport.worldToScreenZ(hoveredBadge.centerZ());
            boolean selected = hoveredBadge == selectedCluster;
            drawClusterMarker(canvas, x, y, clusterRadius(hoveredBadge), hoveredBadge, selected, true);
        }
        canvas.resetScissor();
    }

    private void renderClusterOutline(
            UiCanvas canvas,
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
        Color color = selected ? color(MAP_SELECTED_CLUSTER) : cluster.profession().color();
        Color fill = withAlpha(color, highlighted ? 48 : 18);
        Color stroke = withAlpha(color, highlighted ? 220 : 105);

        List<UiCanvas.Point> points = outline.points().stream()
                .map(point -> new UiCanvas.Point(centerScreenX + point.x(), centerScreenY + point.y()))
                .toList();
        boolean closed = points.size() > 2;
        canvas.fillAndStrokePolygon(
                points,
                closed ? fill : null,
                stroke,
                hullStrokeWidthForZoom(viewport.pixelsPerBlock(), highlighted),
                closed);
    }

    private void drawClusterMarker(UiCanvas canvas, float x, float y, float radius, GatheringNodeCluster cluster, boolean selected, boolean highlighted) {
        Color color = selected ? color(MAP_SELECTED_CLUSTER) : cluster.profession().color();
        drawCircle(canvas, x, y, radius + 3, color(BACKGROUND_MODAL_OVERLAY, highlighted ? 205 : 150));
        drawCircle(canvas, x, y, radius, withAlpha(color, highlighted ? 250 : 220));
        drawText(canvas, x, y + 1, clusterCountTextSize(cluster), String.valueOf(cluster.nodeCount()), color(MAP_TEXT), TextAlignment.CENTER);
    }

    private void renderNodes(UiCanvas canvas, MapViewport viewport, List<GatheringNode> nodes) {
        hoveredNode = null;
        float bestHoverDistance = 10f;
        boolean allowHover = !draggingMap;
        MapBounds visibleBounds = viewport.visibleBounds();
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
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
            Color color = selected ? color(MAP_PLAYER) : node.profession().color();
            drawCircle(canvas, x, y, selected || hovered ? Math.min(radius + 1.8f, 5.6f) : radius,
                    color(BACKGROUND_MODAL_OVERLAY, 160));
            drawCircle(canvas, x, y, radius, color);
        }
        canvas.resetScissor();
        if (hoveredNode != null) {
            renderNodeTooltip(canvas, hoveredNode);
        }
    }

    private void renderGatheringTotemPlacements(UiCanvas canvas, MapViewport viewport) {
        if (!gatheringTotemSolverEnabled || gatheringTotemPlacements.isEmpty()) {
            hoveredGatheringTotemPlacement = null;
            return;
        }
        List<Placement> visiblePlacements = visibleGatheringTotemPlacements();
        hoveredGatheringTotemPlacement = !draggingMap && viewport.isInsideScreen(nvgMouseX, nvgMouseY)
                ? gatheringTotemPlacementAt(
                        visiblePlacements,
                        viewport,
                        nvgMouseX,
                        nvgMouseY)
                : null;
        for (Placement placement : visiblePlacements) {
            if (placement != gatheringTotemPlacement) {
                renderGatheringTotemPlacement(
                        canvas,
                        viewport,
                        placement,
                        false,
                        placement == hoveredGatheringTotemPlacement);
            }
        }
        if (gatheringTotemPlacement != null) {
            renderGatheringTotemPlacement(
                    canvas,
                    viewport,
                    gatheringTotemPlacement,
                    true,
                    gatheringTotemPlacement == hoveredGatheringTotemPlacement);
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
        if (selected && showGatheringTotemPlayerRadius) {
            drawCircleOutline(
                    canvas,
                    x,
                    z,
                    (float) (GatheringTotemSolver.TOTEM_RADIUS * viewport.pixelsPerBlock()),
                    hovered ? 2.4f : 1.8f,
                    color(MAP_TOTEM_RANGE));
        }
        if (selected && showGatheringTotemNodeReach) {
            drawDashedCircle(
                    canvas,
                    x,
                    z,
                    (float) (GatheringTotemSolver.EFFECTIVE_NODE_RADIUS * viewport.pixelsPerBlock()),
                    color(MAP_TOTEM_REACH));
        }

        if (showGatheringTotemHulls) {
            Color hullColor = selected ? color(MAP_TOTEM) : color(MAP_TOTEM_MUTED);
            if (screenHull.size() == 1) {
                drawCircle(canvas, screenHull.getFirst().x(), screenHull.getFirst().y(), hovered ? 7 : 5, hullColor);
            } else {
                boolean closed = screenHull.size() > 2;
                Color fill = selected && closed
                        ? withAlpha(color(MAP_TOTEM), hovered ? 76 : 44)
                        : null;
                canvas.fillAndStrokePolygon(
                        screenHull,
                        fill,
                        hullColor,
                        selected ? (hovered ? 2.8f : 2) : (hovered ? 2 : 1.2f),
                        closed);
            }
        }

        if (selected && showGatheringTotemCoveredNodes) {
            for (GatheringNode node : placement.coveredNodes()) {
                if (!viewport.visibleBounds().contains(node.x(), node.z())) {
                    continue;
                }
                drawCircle(
                        canvas,
                        viewport.worldToScreenX(node.x()),
                        viewport.worldToScreenZ(node.z()),
                        2.5f,
                        color(MAP_TOTEM));
            }
        }
        float markerRadius = selected ? 6 : 4.5f;
        drawCircle(canvas, x, z, markerRadius + 2, color(BACKGROUND_MODAL_OVERLAY, selected ? 220 : 165));
        drawCircle(canvas, x, z, markerRadius, selected ? color(MAP_PLAYER) : color(MAP_TOTEM_MUTED));
        drawCircle(canvas, x, z, selected ? 3 : 2.25f, color(MAP_TOTEM));
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
        if (showOtherOptimalGatheringTotems || gatheringTotemPlacement == null) {
            return gatheringTotemPlacements;
        }
        return List.of(gatheringTotemPlacement);
    }

    private void renderPlayer(UiCanvas canvas, MapViewport viewport) {
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
        drawCircle(canvas, sx, sy, 8, color(BACKGROUND_MODAL_OVERLAY, 180));
        drawCircle(canvas, sx, sy, 5, color(MAP_PLAYER));
    }

    private void renderSidebar(UiCanvas canvas) {
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            renderWorldEventSidebar(canvas);
            return;
        }
        if (displayMode == MapDisplayMode.INGREDIENTS) {
            renderIngredientSidebar(canvas);
            return;
        }
        float screenHeight = uiScreenHeight();
        sidebarScroll = clampSidebarScroll(sidebarScroll, screenHeight);
        SidebarLayout layout = sidebarLayout();
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, screenHeight, color(MAP_SIDEBAR));
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, SIDEBAR_HEADER_HEIGHT, color(MAP_HEADER));
        drawText(canvas, SIDEBAR_WIDTH / 2f, 22, 18, "Sequoia Map", color(MAP_TITLE), TextAlignment.CENTER);

        drawButton(canvas, PADDING, layout.backY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, "Back", false);
        drawButton(canvas, PADDING, layout.centerY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, centerPlayerButtonLabel(), false);
        drawMapModeControl(canvas, layout.modeY());
        canvas.scissor(0, SIDEBAR_PANEL_TOP, SIDEBAR_WIDTH, Math.max(0, screenHeight - SIDEBAR_PANEL_TOP));
        renderPanelHeader(
                canvas,
                sidebarY(layout.mapPanelY()),
                "Map & Territory",
                selectedTerritory == null ? gatheringAnalysisScope.label() : selectedTerritory.name(),
                WorldMapSidebarPanel.MAP_AND_TERRITORY);
        if (panelExpanded(WorldMapSidebarPanel.MAP_AND_TERRITORY)) {
            renderTerritoryToggles(canvas, sidebarY(layout.territoryToggleY()));
            drawText(canvas, PADDING, sidebarY(layout.scopeLabelY()), 12, "Gathering Scope", color(MAP_SUBTEXT), TextAlignment.LEFT);
            drawScopeControl(canvas, sidebarY(layout.scopeY()));
            drawText(canvas, PADDING, sidebarY(layout.territoryLabelY()), 12, "Territory", color(MAP_SUBTEXT), TextAlignment.LEFT);
            renderSearchInput(
                    canvas,
                    sidebarY(layout.territoryInputY()),
                    territoryDropdownOpen,
                    territoryInputFocused,
                    territorySearch,
                    selectedTerritory == null ? "Find territory" : selectedTerritory.name());
        }

        renderPanelHeader(
                canvas,
                sidebarY(layout.analysisPanelY()),
                "Gathering Analysis",
                showClusters ? clusterScoreMode.label() : "Nodes",
                WorldMapSidebarPanel.GATHERING_ANALYSIS);
        if (panelExpanded(WorldMapSidebarPanel.GATHERING_ANALYSIS)) {
            renderGatheringAnalysisToggles(canvas, sidebarY(layout.clustersY()));
        }

        renderPanelHeader(
                canvas,
                sidebarY(layout.filtersPanelY()),
                "Resource Filters",
                selectedResourceFilters.isEmpty() ? "All" : selectedResourceFilters.size() + " selected",
                WorldMapSidebarPanel.RESOURCE_FILTERS);
        if (panelExpanded(WorldMapSidebarPanel.RESOURCE_FILTERS)) {
            drawText(canvas, PADDING, sidebarY(layout.resourceLabelY()), 12, "Resource", color(MAP_SUBTEXT), TextAlignment.LEFT);
            renderSearchInput(
                    canvas,
                    sidebarY(layout.resourceInputY()),
                    resourceDropdownOpen,
                    resourceInputFocused,
                    resourceSearch,
                    selectedResourceLabel());
            drawText(canvas, PADDING, sidebarY(layout.professionLabelY()), 12, "Professions", color(MAP_SUBTEXT), TextAlignment.LEFT);
            float professionY = sidebarY(layout.professionStartY());
            for (GatheringProfession profession : gatheringProfessions()) {
                boolean active = professionToggles.getOrDefault(profession, true);
                drawToggle(canvas, PADDING, professionY, SIDEBAR_WIDTH - PADDING * 2, TOGGLE_HEIGHT, profession, active);
                professionY += TOGGLE_HEIGHT + 6;
            }
        }

        renderPanelHeader(
                canvas,
                sidebarY(layout.totemPanelY()),
                "Totem Solver",
                gatheringTotemPanelSummary(),
                WorldMapSidebarPanel.TOTEM_SOLVER);
        if (panelExpanded(WorldMapSidebarPanel.TOTEM_SOLVER)) {
            renderGatheringTotemControls(canvas, totemSolverLayout(layout.totemPanelY()));
        }

        if (resourceDropdownOpen) {
            renderResourceDropdown(canvas, sidebarY(layout.resourceInputY()) + INPUT_HEIGHT);
        }
        if (territoryDropdownOpen) {
            renderTerritoryDropdown(canvas, sidebarY(layout.territoryInputY()) + INPUT_HEIGHT);
        }
        sidebarContentHeight = layout.endY() + PADDING;
        sidebarScroll = clampSidebarScroll(sidebarScroll, screenHeight);
        canvas.resetScissor();
        renderSidebarScrollbar(canvas, screenHeight);
    }

    private void renderWorldEventSidebar(UiCanvas canvas) {
        float screenHeight = uiScreenHeight();
        sidebarScroll = clampSidebarScroll(sidebarScroll, screenHeight);
        WorldEventSidebarLayout layout = worldEventSidebarLayout();
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, screenHeight, color(MAP_SIDEBAR));
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, SIDEBAR_HEADER_HEIGHT, color(MAP_HEADER));
        drawText(canvas, SIDEBAR_WIDTH / 2f, 22, 18, "Sequoia Map", color(MAP_TITLE), TextAlignment.CENTER);

        drawButton(canvas, PADDING, layout.backY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, "Back", false);
        drawButton(canvas, PADDING, layout.centerY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, centerPlayerButtonLabel(), false);
        drawMapModeControl(canvas, layout.modeY());
        canvas.scissor(0, SIDEBAR_PANEL_TOP, SIDEBAR_WIDTH, Math.max(0, screenHeight - SIDEBAR_PANEL_TOP));

        renderPanelHeader(
                canvas,
                sidebarY(layout.displayPanelY()),
                "Event Display",
                worldEventDisplayFilter.label(),
                WorldMapSidebarPanel.EVENT_DISPLAY);
        if (panelExpanded(WorldMapSidebarPanel.EVENT_DISPLAY)) {
            drawText(canvas, PADDING, sidebarY(layout.filterLabelY()), 12, "Visible Events", color(MAP_SUBTEXT), TextAlignment.LEFT);
            drawWorldEventFilterControl(canvas, sidebarY(layout.filterY()));
        }

        renderPanelHeader(
                canvas,
                sidebarY(layout.trackingPanelY()),
                "Tracking",
                cachedTrackedWorldEventIds.size() + " tracked",
                WorldMapSidebarPanel.EVENT_TRACKING);
        if (panelExpanded(WorldMapSidebarPanel.EVENT_TRACKING)) {
            drawWorldEventTrackingListControl(canvas, sidebarY(layout.eventFilterY()));
            renderSearchInput(
                    canvas,
                    sidebarY(layout.eventInputY()),
                    worldEventDropdownOpen,
                    worldEventInputFocused,
                    worldEventSearch,
                    trackedWorldEventLabel());
        }

        if (worldEventDropdownOpen) {
            renderWorldEventDropdown(canvas, sidebarY(layout.eventInputY()) + INPUT_HEIGHT);
        }
        sidebarContentHeight = layout.endY() + PADDING;
        sidebarScroll = clampSidebarScroll(sidebarScroll, screenHeight);
        canvas.resetScissor();
        renderSidebarScrollbar(canvas, screenHeight);
    }

    private void renderIngredientSidebar(UiCanvas canvas) {
        float screenHeight = uiScreenHeight();
        IngredientSidebarLayout layout = ingredientSidebarLayout();
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, screenHeight, color(MAP_SIDEBAR));
        canvas.fillRect(0, 0, SIDEBAR_WIDTH, SIDEBAR_HEADER_HEIGHT, color(MAP_HEADER));
        drawText(canvas, SIDEBAR_WIDTH / 2f, 22, 18, "Sequoia Map", color(MAP_TITLE), TextAlignment.CENTER);
        drawButton(canvas, PADDING, layout.backY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, "Back", false);
        drawButton(canvas, PADDING, layout.centerY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, centerPlayerButtonLabel(), false);
        drawMapModeControl(canvas, layout.modeY());

        float contentWidth = SIDEBAR_WIDTH - PADDING * 2;
        drawIngredientMapCategoryControl(canvas, layout.titleY());
        renderIngredientWaypointActions(canvas, layout, contentWidth);
        if (ingredientMapCategory == IngredientMapCategory.TOTEM_SPOTS) {
            renderIngredientFarmSpotSidebar(canvas, layout, contentWidth);
            return;
        }

        drawButton(canvas, PADDING, layout.guideY(), contentWidth, BUTTON_HEIGHT, "Choose Ingredient", false);
        if (!hasMapFocus()) {
            drawFittedText(
                    canvas,
                    PADDING,
                    layout.ingredientY(),
                    11,
                    "Choose an ingredient to display all of its published spawn locations.",
                    color(MAP_SUBTEXT),
                    contentWidth,
                    TextAlignment.LEFT);
            return;
        }

        drawFittedText(
                canvas,
                PADDING,
                layout.ingredientY(),
                13,
                mapFocus.title(),
                color(MAP_TEXT),
                contentWidth,
                TextAlignment.LEFT);
        long sourceCount = mapFocus.markers().stream().map(MapFocus.Marker::source).distinct().count();
        drawText(
                canvas,
                PADDING,
                layout.summaryY(),
                10,
                mapFocus.markers().size() + " locations · " + sourceCount + " mobs",
                color(MAP_SUBTEXT),
                TextAlignment.LEFT);

        int selectedSpawnCount = ingredientMapSelection.spawnCount();
        drawText(
                canvas,
                PADDING,
                layout.selectedTitleY(),
                12,
                "Selected Spawns (" + selectedSpawnCount + ")",
                color(MAP_TITLE),
                TextAlignment.LEFT);
        if (selectedFocusMarker == null) {
            drawText(
                    canvas,
                    PADDING,
                    layout.selectedDetailY(),
                    10,
                    "Click markers to select multiple",
                    color(MAP_SUBTEXT),
                    TextAlignment.LEFT);
        } else {
            drawFittedText(
                    canvas,
                    PADDING,
                    layout.selectedDetailY(),
                    11,
                    selectedFocusMarker.source(),
                    color(MAP_TEXT),
                    contentWidth,
                    TextAlignment.LEFT);
            drawText(
                    canvas,
                    PADDING,
                    layout.selectedDetailY() + 18,
                    10,
                    selectedFocusMarker.coordinates(),
                    color(MAP_SUBTEXT),
                    TextAlignment.LEFT);
            drawButton(canvas, PADDING, layout.copyY(), contentWidth, BUTTON_HEIGHT, "Copy Selected Coordinates", false);
        }
    }

    private void renderIngredientWaypointActions(
            UiCanvas canvas, IngredientSidebarLayout layout, float contentWidth) {
        int selectedCount = ingredientMapSelection.size();
        int waypointCount = IngredientWaypointManager.getInstance().size();
        drawButton(
                canvas,
                PADDING,
                layout.renderWaypointsY(),
                contentWidth,
                BUTTON_HEIGHT,
                "Render Selected (" + selectedCount + ")",
                selectedCount > 0);
        drawButton(
                canvas,
                PADDING,
                layout.clearWaypointsY(),
                contentWidth,
                BUTTON_HEIGHT,
                "Clear Waypoints" + (waypointCount > 0 ? " (" + waypointCount + ")" : ""),
                waypointCount > 0);
    }

    private void renderIngredientFarmSpotSidebar(
            UiCanvas canvas, IngredientSidebarLayout layout, float contentWidth) {
        drawText(
                canvas,
                PADDING,
                layout.guideY(),
                13,
                "All Mob Totem Spots",
                color(MAP_TITLE),
                TextAlignment.LEFT);
        List<IngredientFarmSpot> spots = IngredientFarmSpotCatalog.all();
        float listY = ingredientFarmSpotListY(layout);
        float listHeight = ingredientFarmSpotListHeight(layout);
        float maxScroll = ingredientFarmSpotMaxScroll(layout);
        ingredientFarmSpotScroll = (float) clamp(ingredientFarmSpotScroll, 0, maxScroll);
        float rowY = listY - ingredientFarmSpotScroll;
        if (spots.isEmpty()) {
            drawFittedText(
                    canvas,
                    PADDING,
                    rowY,
                    10,
                    "No farming spots have been added yet.",
                    color(MAP_SUBTEXT),
                    contentWidth,
                    TextAlignment.LEFT);
            return;
        }

        canvas.scissor(0, listY, SIDEBAR_WIDTH, listHeight);
        try {
            for (IngredientFarmSpot spot : spots) {
                boolean selected = ingredientMapSelection.isTotemSelected(spot.id());
                boolean hovered = isHovered(nvgMouseX, nvgMouseY, 0, listY, SIDEBAR_WIDTH, listHeight)
                        && isHovered(
                                nvgMouseX,
                                nvgMouseY,
                                PADDING,
                                rowY,
                                contentWidth,
                                INGREDIENT_FARM_SPOT_CARD_HEIGHT);
                canvas.fillRect(
                        PADDING,
                        rowY,
                        contentWidth,
                        INGREDIENT_FARM_SPOT_CARD_HEIGHT,
                        selected
                                ? color(MAP_CONTROL_ACTIVE)
                                : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
                drawFittedText(
                        canvas,
                        PADDING + INGREDIENT_FARM_SPOT_CARD_PADDING,
                        rowY + 14,
                        11,
                        spot.name(),
                        color(MAP_TEXT),
                        contentWidth - INGREDIENT_FARM_SPOT_CARD_PADDING * 2,
                        TextAlignment.LEFT);
                drawFittedText(
                        canvas,
                        PADDING + INGREDIENT_FARM_SPOT_CARD_PADDING,
                        rowY + 31,
                        10,
                        spot.coordinates(),
                        color(MAP_SUBTEXT),
                        contentWidth - INGREDIENT_FARM_SPOT_CARD_PADDING * 2,
                        TextAlignment.LEFT);
                drawFittedText(
                        canvas,
                        PADDING + INGREDIENT_FARM_SPOT_CARD_PADDING,
                        rowY + 44,
                        9,
                        String.join(", ", spot.ingredients()),
                        color(MAP_SUBTEXT),
                        contentWidth - INGREDIENT_FARM_SPOT_CARD_PADDING * 2,
                        TextAlignment.LEFT);
                rowY += INGREDIENT_FARM_SPOT_ROW_HEIGHT;
            }
        } finally {
            canvas.resetScissor();
        }
        renderIngredientFarmSpotScrollbar(canvas, listY, listHeight, maxScroll);
    }

    private void renderIngredientFarmSpotScrollbar(
            UiCanvas canvas, float listY, float listHeight, float maxScroll) {
        if (maxScroll <= 0 || listHeight <= 0) {
            return;
        }
        float trackX = SIDEBAR_WIDTH - 5;
        float trackHeight = Math.max(0, listHeight - 4);
        float contentHeight = ingredientFarmSpotContentHeight();
        float thumbHeight = Math.min(trackHeight, Math.max(24, trackHeight * listHeight / contentHeight));
        float thumbY = listY + 2 + (trackHeight - thumbHeight) * (ingredientFarmSpotScroll / maxScroll);
        canvas.fillRect(trackX, listY + 2, 3, trackHeight, color(MAP_TEXT, 28));
        canvas.fillRect(trackX, thumbY, 3, thumbHeight, color(MAP_TEXT, 110));
    }

    private void drawIngredientMapCategoryControl(UiCanvas canvas, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / IngredientMapCategory.values().length;
        for (int index = 0; index < IngredientMapCategory.values().length; index++) {
            IngredientMapCategory category = IngredientMapCategory.values()[index];
            float x = PADDING + index * segmentWidth;
            boolean active = ingredientMapCategory == category;
            boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, segmentWidth, BUTTON_HEIGHT);
            canvas.fillRect(
                    x,
                    y,
                    segmentWidth,
                    BUTTON_HEIGHT,
                    active ? color(MAP_CONTROL_ACTIVE) : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
            canvas.strokeRect(x, y, segmentWidth, BUTTON_HEIGHT, 1, color(MAP_BORDER));
            drawText(
                    canvas,
                    x + segmentWidth / 2f,
                    y + BUTTON_HEIGHT / 2f,
                    10,
                    category.label(),
                    color(MAP_TEXT),
                    TextAlignment.CENTER);
        }
    }

    private void renderInsightsSidebar(UiCanvas canvas) {
        float screenWidth = uiScreenWidth();
        float screenHeight = uiScreenHeight();
        if (!insightsSidebarOpen) {
            drawText(
                    canvas,
                    screenWidth - INSIGHTS_RAIL_WIDTH / 2f,
                    10 + BUTTON_HEIGHT / 2f,
                    16,
                    "<",
                    color(MAP_TEXT),
                    TextAlignment.CENTER);
            return;
        }

        float x = screenWidth - INSIGHTS_SIDEBAR_WIDTH;
        InsightsLayout layout = insightsLayout();
        canvas.fillRect(x, 0, INSIGHTS_SIDEBAR_WIDTH, screenHeight, color(MAP_SIDEBAR));
        canvas.fillRect(x, 0, INSIGHTS_SIDEBAR_WIDTH, SIDEBAR_HEADER_HEIGHT, color(MAP_HEADER));
        drawText(canvas, x + PADDING, 22, 16, "Insights", color(MAP_TITLE), TextAlignment.LEFT);
        drawButton(canvas, x + INSIGHTS_SIDEBAR_WIDTH - PADDING - 24, 10, 24, BUTTON_HEIGHT, ">", false);
        canvas.scissor(x, SIDEBAR_HEADER_HEIGHT, INSIGHTS_SIDEBAR_WIDTH, Math.max(0, screenHeight - SIDEBAR_HEADER_HEIGHT));
        if (displayMode == MapDisplayMode.WORLD_EVENTS) {
            renderWorldEventInsights(canvas, x, layout);
        } else if (displayMode == MapDisplayMode.INGREDIENTS) {
            renderIngredientInsights(canvas, x);
        } else {
            renderGatheringInsights(canvas, x, screenHeight, layout);
        }
        canvas.resetScissor();
    }

    private void renderIngredientInsights(UiCanvas canvas, float x) {
        float contentX = x + PADDING;
        float contentWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        if (ingredientMapCategory == IngredientMapCategory.TOTEM_SPOTS) {
            drawInsightsSectionTitle(
                    canvas,
                    contentX,
                    60,
                    "Mob Totem Spots (" + ingredientMapSelection.totemCount() + " selected)");
            if (selectedIngredientFarmSpot != null) {
                drawInsightsSectionTitle(canvas, contentX, 92, "Selected Spot");
                drawFittedText(
                        canvas,
                        contentX,
                        116,
                        12,
                        selectedIngredientFarmSpot.name(),
                        color(MAP_TEXT),
                        contentWidth,
                        TextAlignment.LEFT);
                drawFittedText(
                        canvas,
                        contentX,
                        134,
                        11,
                        selectedIngredientFarmSpot.coordinates(),
                        color(MAP_SUBTEXT),
                        contentWidth,
                        TextAlignment.LEFT);
                drawFittedText(
                        canvas,
                        contentX,
                        152,
                        10,
                        String.join(", ", selectedIngredientFarmSpot.ingredients()),
                        color(MAP_SUBTEXT),
                        contentWidth,
                        TextAlignment.LEFT);
            }
            return;
        }
        drawInsightsSectionTitle(canvas, contentX, 60, "Ingredient");
        if (!hasMapFocus()) {
            drawFittedText(
                    canvas,
                    contentX,
                    84,
                    11,
                    "No ingredient selected. Open one from the Ingredient Guide.",
                    color(MAP_SUBTEXT),
                    contentWidth,
                    TextAlignment.LEFT);
            return;
        }
        drawFittedText(canvas, contentX, 84, 13, mapFocus.title(), color(MAP_TEXT), contentWidth, TextAlignment.LEFT);
        drawInsightRow(canvas, contentX, 106, contentWidth, "Spawn locations", String.valueOf(mapFocus.markers().size()));
        long sourceCount = mapFocus.markers().stream().map(MapFocus.Marker::source).distinct().count();
        drawInsightRow(canvas, contentX, 122, contentWidth, "Mob sources", String.valueOf(sourceCount));
        if (selectedFocusMarker != null) {
            drawInsightsSectionTitle(
                    canvas,
                    contentX,
                    154,
                    "Selected Spawns (" + ingredientMapSelection.spawnCount() + ")");
            drawFittedText(
                    canvas,
                    contentX,
                    178,
                    12,
                    selectedFocusMarker.source(),
                    color(MAP_TEXT),
                    contentWidth,
                    TextAlignment.LEFT);
            drawFittedText(
                    canvas,
                    contentX,
                    196,
                    11,
                    selectedFocusMarker.coordinates(),
                    color(MAP_SUBTEXT),
                    contentWidth,
                    TextAlignment.LEFT);
        }
    }

    private void renderGatheringInsights(UiCanvas canvas, float x, float screenHeight, InsightsLayout layout) {
        float contentX = x + PADDING;
        float contentWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        drawInsightsSectionTitle(canvas, contentX, layout.overviewY(), "Overview");
        drawInsightRow(canvas, contentX, layout.overviewY() + 18, contentWidth, "Scope", gatheringAnalysisScope.label());
        drawInsightRow(canvas, contentX, layout.overviewY() + 34, contentWidth, "Matching nodes", String.valueOf(cachedFilteredNodes.size()));
        drawInsightRow(canvas, contentX, layout.overviewY() + 50, contentWidth, "Clusters", String.valueOf(cachedClusters.size()));
        float overviewRowY = layout.overviewY() + 66;
        if (showDebugInfo) {
            drawInsightRow(canvas, contentX, overviewRowY, contentWidth, "Map source", displayMapImageSource());
            drawInsightRow(canvas, contentX, overviewRowY + 16, contentWidth, "HQ status", mapImageService.hqStatus());
            overviewRowY += 32;
        }
        if (gatheringTotemSolverEnabled) {
            String coverage = pendingGatheringTotemSolve != null
                    ? "Optimizing..."
                    : gatheringTotemPlacement == null
                            ? "No eligible nodes"
                            : gatheringTotemPlacement.nodeCount()
                                    + (gatheringTotemPlacement.clusterFocused() ? " nodes (cluster)" : " nodes")
                                    + (gatheringTotemPlacements.size() > 1
                                            ? " · " + gatheringTotemPlacements.size() + " spots"
                                            : "");
            String expectedYield = gatheringTotemPlacement == null
                    ? "-"
                    : String.format(Locale.ROOT, "%.1f items", gatheringTotemPlacement.expectedItemsPerGather());
            String placementCoordinates = gatheringTotemPlacement == null
                    ? "-"
                    : Math.round(gatheringTotemPlacement.x()) + " " + Math.round(gatheringTotemPlacement.z());
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

    private void renderWorldEventInsights(UiCanvas canvas, float x, InsightsLayout layout) {
        float contentX = x + PADDING;
        float contentWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        long visibleCount = allWorldEvents.stream().filter(WorldEventDefinition::isVisible).count();
        drawInsightsSectionTitle(canvas, contentX, layout.overviewY(), "Overview");
        drawInsightRow(canvas, contentX, layout.overviewY() + 18, contentWidth, "Visible", visibleWorldEvents.size() + " shown / " + visibleCount + " active");
        drawInsightRow(canvas, contentX, layout.overviewY() + 34, contentWidth, "Tracked", String.valueOf(cachedTrackedWorldEventIds.size()));
        drawInsightRow(canvas, contentX, layout.overviewY() + 50, contentWidth, "API", worldEventService.status());

        WorldEventDefinition detail = selectedWorldEvent != null ? selectedWorldEvent : hoveredWorldEvent;
        drawInsightsSectionTitle(canvas, contentX, layout.eventDetailY() - 8, "Selection");
        if (detail != null) {
            renderWorldEventDetail(canvas, contentX, layout.eventDetailY() + 4, contentWidth, detail, selectedWorldEvent != null);
        } else {
            drawFittedText(canvas, contentX, layout.eventDetailY() + 18, 11, "Hover or select a world event", color(MAP_SUBTEXT), contentWidth, TextAlignment.LEFT);
        }
    }

    private void drawInsightsSectionTitle(UiCanvas canvas, float x, float y, String label) {
        drawText(canvas, x, y, 12, label, color(MAP_SUBTEXT), TextAlignment.LEFT);
    }

    private void drawInsightRow(UiCanvas canvas, float x, float y, float width, String label, String value) {
        drawFittedText(canvas, x, y, 10, label, color(MAP_SUBTEXT), width * 0.42f, TextAlignment.LEFT);
        drawFittedText(canvas, x + width, y, 10, value, color(MAP_TEXT), width * 0.58f, TextAlignment.RIGHT);
    }

    private void renderClusterDetail(UiCanvas canvas, float x, float y, float width, GatheringNodeCluster cluster) {
        canvas.fillRect(x, y, width, CLUSTER_DETAIL_HEIGHT, color(MAP_HEADER, 210));
        canvas.strokeRect(x, y, width, CLUSTER_DETAIL_HEIGHT, 1, color(MAP_BORDER));
        float textWidth = width - 16;
        drawFittedText(canvas, x + 8, y + 17, 14, cluster.resource(), color(MAP_TEXT), textWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 36, 12, cluster.nodeCount() + " nodes | score " + cluster.score() + "%", color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 55, 12, Math.round(cluster.averageSpacing()) + "m spacing", color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 74, 12, cluster.profession().name(), cluster.profession().color(), textWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 93, 12, clusterCoords(cluster), color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
    }

    private void renderNodeDetail(UiCanvas canvas, float x, float y, float width, GatheringNode node) {
        canvas.fillRect(x, y, width, NODE_DETAIL_HEIGHT, color(MAP_HEADER, 210));
        canvas.strokeRect(x, y, width, NODE_DETAIL_HEIGHT, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 17, 14, node.resource(), color(MAP_TEXT), width - 16, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 38, 12, nodeCoords(node), color(MAP_SUBTEXT), width - 16, TextAlignment.LEFT);
    }

    private void renderWorldEventDetail(
            UiCanvas canvas,
            float x,
            float y,
            float width,
            WorldEventDefinition event,
            boolean allowTrackingButton) {
        float textWidth = width - 16;
        canvas.fillRect(x, y, width, WORLD_EVENT_DETAIL_HEIGHT, color(MAP_HEADER, 220));
        canvas.strokeRect(x, y, width, WORLD_EVENT_DETAIL_HEIGHT, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 16, 14, event.name(), color(MAP_TEXT), textWidth, TextAlignment.LEFT);
        String metadata = worldEventMetadata(event);
        drawFittedText(canvas, x + 8, y + 35, 11, metadata, color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 53, 11, worldEventScheduleLabel(event.schedule()), color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
        String locationLabel = event.locations().size() == 1
                ? worldEventCoordinates(event.locations().getFirst())
                : event.locations().size() + " possible locations";
        drawFittedText(canvas, x + 8, y + 71, 11, locationLabel, color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
        if (allowTrackingButton) {
            boolean tracked = cachedTrackedWorldEventIds.contains(event.internalName());
            drawButton(
                    canvas,
                    x + 8,
                    y + 88,
                    width - 16,
                    24,
                    tracked ? "Untrack Event" : "Track Event",
                    tracked);
        }
    }

    private void drawMapModeControl(UiCanvas canvas, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / MapDisplayMode.values().length;
        for (int index = 0; index < MapDisplayMode.values().length; index++) {
            MapDisplayMode mode = MapDisplayMode.values()[index];
            float x = PADDING + index * segmentWidth;
            boolean active = displayMode == mode;
            boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, segmentWidth, BUTTON_HEIGHT);
            canvas.fillRect(x, y, segmentWidth, BUTTON_HEIGHT, active ? color(MAP_CONTROL_ACTIVE) : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
            canvas.strokeRect(x, y, segmentWidth, BUTTON_HEIGHT, 1, color(MAP_BORDER));
            drawText(canvas, x + segmentWidth / 2f, y + BUTTON_HEIGHT / 2f, 11, mode.label(), color(MAP_TEXT), TextAlignment.CENTER);
        }
    }

    private void drawWorldEventFilterControl(UiCanvas canvas, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / WorldEventDisplayFilter.values().length;
        for (int index = 0; index < WorldEventDisplayFilter.values().length; index++) {
            WorldEventDisplayFilter filter = WorldEventDisplayFilter.values()[index];
            float x = PADDING + index * segmentWidth;
            boolean active = worldEventDisplayFilter == filter;
            boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, segmentWidth, BUTTON_HEIGHT);
            canvas.fillRect(x, y, segmentWidth, BUTTON_HEIGHT, active ? color(MAP_CONTROL_ACTIVE) : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
            canvas.strokeRect(x, y, segmentWidth, BUTTON_HEIGHT, 1, color(MAP_BORDER));
            drawText(canvas, x + segmentWidth / 2f, y + BUTTON_HEIGHT / 2f, 11, filter.label(), color(MAP_TEXT), TextAlignment.CENTER);
        }
    }

    private void drawWorldEventTrackingListControl(UiCanvas canvas, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / 2f;
        drawTrackingListSegment(canvas, PADDING, y, segmentWidth, "All Events", !worldEventDropdownTrackedOnly);
        drawTrackingListSegment(
                canvas,
                PADDING + segmentWidth,
                y,
                segmentWidth,
                "Tracked Only",
                worldEventDropdownTrackedOnly);
    }

    private void drawTrackingListSegment(UiCanvas canvas, float x, float y, float width, String label, boolean active) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, width, BUTTON_HEIGHT);
        canvas.fillRect(x, y, width, BUTTON_HEIGHT, active ? color(MAP_CONTROL_ACTIVE) : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
        canvas.strokeRect(x, y, width, BUTTON_HEIGHT, 1, color(MAP_BORDER));
        drawFittedText(
                canvas,
                x + width / 2f,
                y + BUTTON_HEIGHT / 2f,
                11,
                label,
                color(MAP_TEXT),
                width - 10,
                TextAlignment.CENTER);
    }

    private void renderPanelHeader(
            UiCanvas canvas,
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
        canvas.fillRect(PADDING,
                y,
                SIDEBAR_WIDTH - PADDING * 2,
                PANEL_HEADER_HEIGHT,
                hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL_INACTIVE));
        canvas.strokeRect(PADDING,
                y,
                SIDEBAR_WIDTH - PADDING * 2,
                PANEL_HEADER_HEIGHT,
                1,
                color(MAP_BORDER));
        drawText(
                canvas,
                PADDING + 10,
                y + PANEL_HEADER_HEIGHT / 2f,
                12,
                expanded ? "v" : ">",
                color(MAP_SUBTEXT),
                TextAlignment.CENTER);
        drawFittedText(
                canvas,
                PADDING + 22,
                y + PANEL_HEADER_HEIGHT / 2f,
                12,
                label,
                color(MAP_TEXT),
                PANEL_LABEL_WIDTH,
                TextAlignment.LEFT);
        drawFittedText(
                canvas,
                SIDEBAR_WIDTH - PADDING - 8,
                y + PANEL_HEADER_HEIGHT / 2f,
                10,
                summary,
                color(MAP_SUBTEXT),
                PANEL_SUMMARY_WIDTH,
                TextAlignment.RIGHT);
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

    private void renderGatheringTotemControls(UiCanvas canvas, TotemSolverLayout layout) {
        drawButton(
                canvas,
                PADDING,
                sidebarY(layout.enabledY()),
                SIDEBAR_WIDTH - PADDING * 2,
                BUTTON_HEIGHT,
                gatheringTotemSolverEnabled ? "Totem Solver On" : "Totem Solver Off",
                gatheringTotemSolverEnabled);
        drawGatheringTotemTargetControl(canvas, sidebarY(layout.targetY()));
        drawFittedText(
                canvas,
                PADDING,
                sidebarY(layout.filterSummaryY()),
                10,
                "Scope: " + gatheringTotemScopeSummary(),
                color(MAP_SUBTEXT),
                SIDEBAR_WIDTH - PADDING * 2,
                TextAlignment.LEFT);
        drawFittedText(
                canvas,
                PADDING,
                sidebarY(layout.filterSummaryY() + 14),
                10,
                "Resources: " + selectedResourceLabel(),
                color(MAP_SUBTEXT),
                SIDEBAR_WIDTH - PADDING * 2,
                TextAlignment.LEFT);
        drawButton(
                canvas,
                PADDING,
                sidebarY(layout.refreshY()),
                SIDEBAR_WIDTH - PADDING * 2,
                BUTTON_HEIGHT,
                pendingGatheringTotemSolve == null ? "Refresh" : "Optimizing...",
                pendingGatheringTotemSolve != null);

        drawText(
                canvas,
                PADDING,
                sidebarY(layout.layerLabelY()),
                11,
                "Display Layers",
                color(MAP_SUBTEXT),
                TextAlignment.LEFT);
        renderGatheringTotemLayerButtons(canvas, sidebarY(layout.layerStartY()));
        renderGatheringTotemLegend(canvas, sidebarY(layout.legendY()));

        drawText(
                canvas,
                PADDING,
                sidebarY(layout.resultsLabelY()),
                11,
                gatheringTotemStatus(),
                color(MAP_SUBTEXT),
                TextAlignment.LEFT);
        renderGatheringTotemResults(canvas, sidebarY(layout.resultsStartY()));

        float actionWidth = (SIDEBAR_WIDTH - PADDING * 2 - SPLIT_CONTROL_GAP) / 2f;
        drawButton(
                canvas,
                PADDING,
                sidebarY(layout.actionsY()),
                actionWidth,
                BUTTON_HEIGHT,
                "Center",
                false);
        drawButton(
                canvas,
                PADDING + actionWidth + SPLIT_CONTROL_GAP,
                sidebarY(layout.actionsY()),
                actionWidth,
                BUTTON_HEIGHT,
                "Copy X Z",
                false);
    }

    private void drawGatheringTotemTargetControl(UiCanvas canvas, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / GatheringTotemSearchTarget.values().length;
        for (int index = 0; index < GatheringTotemSearchTarget.values().length; index++) {
            GatheringTotemSearchTarget target = GatheringTotemSearchTarget.values()[index];
            float x = PADDING + index * segmentWidth;
            boolean active = gatheringTotemSearchTarget == target;
            boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, segmentWidth, BUTTON_HEIGHT);
            Color background = active
                    ? color(MAP_CONTROL_ACTIVE)
                    : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL);
            canvas.fillRect(x, y, segmentWidth, BUTTON_HEIGHT, background);
            canvas.strokeRect(x, y, segmentWidth, BUTTON_HEIGHT, 1, color(MAP_BORDER));
            drawFittedText(
                    canvas,
                    x + segmentWidth / 2f,
                    y + BUTTON_HEIGHT / 2f,
                    10,
                    target == GatheringTotemSearchTarget.ALL_FILTERED ? "All Filtered" : "Cluster",
                    color(MAP_TEXT),
                    segmentWidth - 8,
                    TextAlignment.CENTER);
        }
    }

    private void renderGatheringTotemLayerButtons(UiCanvas canvas, float startY) {
        String[] labels = {"Hulls", "50 Range", "+2 Reach", "Nodes", "Other Spots"};
        float gap = 4;
        float width = (SIDEBAR_WIDTH - PADDING * 2 - gap) / 2f;
        for (int index = 0; index < labels.length; index++) {
            int column = index % 2;
            int row = index / 2;
            drawButton(
                    canvas,
                    PADDING + column * (width + gap),
                    startY + row * (TOGGLE_HEIGHT + gap),
                    width,
                    TOGGLE_HEIGHT,
                    labels[index],
                    gatheringTotemLayerEnabled(index));
        }
    }

    private void renderGatheringTotemLegend(UiCanvas canvas, float y) {
        drawCircle(canvas, PADDING + 5, y, 3.5f, color(MAP_TOTEM));
        drawText(canvas, PADDING + 14, y, 9, "amber hull = valid totem positions", color(MAP_SUBTEXT), TextAlignment.LEFT);
        drawCircleOutline(canvas, PADDING + 5, y + 13, 4, 1.5f, color(MAP_TOTEM_RANGE));
        drawText(canvas, PADDING + 14, y + 13, 9, "solid cyan = 50 player range", color(MAP_SUBTEXT), TextAlignment.LEFT);
        drawText(canvas, PADDING + 5, y + 26, 10, "--", color(MAP_TOTEM_REACH), TextAlignment.CENTER);
        drawText(canvas, PADDING + 14, y + 26, 9, "dashed cyan = 52 node reach", color(MAP_SUBTEXT), TextAlignment.LEFT);
        drawCircle(canvas, PADDING + 5, y + 39, 3.5f, color(MAP_PLAYER));
        drawText(canvas, PADDING + 14, y + 39, 9, "bright marker = best integer spot", color(MAP_SUBTEXT), TextAlignment.LEFT);
    }

    private void renderGatheringTotemResults(UiCanvas canvas, float startY) {
        gatheringTotemResultScroll = clampDropdownScroll(
                gatheringTotemResultScroll,
                gatheringTotemPlacements.size(),
                TOTEM_RESULT_VISIBLE_ROWS);
        if (gatheringTotemPlacements.isEmpty()) {
            drawFittedText(
                    canvas,
                    PADDING + 8,
                    startY + 14,
                    10,
                    gatheringTotemStatus(),
                    color(MAP_SUBTEXT),
                    SIDEBAR_WIDTH - PADDING * 2 - 16,
                    TextAlignment.LEFT);
            return;
        }
        int visibleRows = Math.min(
                TOTEM_RESULT_VISIBLE_ROWS,
                gatheringTotemPlacements.size() - gatheringTotemResultScroll);
        for (int row = 0; row < visibleRows; row++) {
            int resultIndex = gatheringTotemResultScroll + row;
            Placement placement = gatheringTotemPlacements.get(resultIndex);
            float y = startY + row * TOTEM_RESULT_ROW_HEIGHT;
            boolean active = placement == gatheringTotemPlacement;
            canvas.fillRect(
                    PADDING,
                    y,
                    SIDEBAR_WIDTH - PADDING * 2,
                    TOTEM_RESULT_ROW_HEIGHT - 3,
                    active ? color(MAP_CONTROL_ACTIVE) : color(MAP_CONTROL));
            canvas.strokeRect(
                    PADDING,
                    y,
                    SIDEBAR_WIDTH - PADDING * 2,
                    TOTEM_RESULT_ROW_HEIGHT - 3,
                    1,
                    color(MAP_BORDER));
            drawFittedText(
                    canvas,
                    PADDING + 7,
                    y + 9,
                    10,
                    "#" + (resultIndex + 1) + " · " + placement.nodeCount() + " nodes",
                    color(MAP_TEXT),
                    SIDEBAR_WIDTH - PADDING * 2 - 14,
                    TextAlignment.LEFT);
            drawFittedText(
                    canvas,
                    PADDING + 7,
                    y + 20,
                    9,
                    totemCoords(placement),
                    color(MAP_SUBTEXT),
                    SIDEBAR_WIDTH - PADDING * 2 - 14,
                    TextAlignment.LEFT);
        }
    }

    private String gatheringTotemPanelSummary() {
        if (!gatheringTotemSolverEnabled) {
            return "Off";
        }
        if (gatheringTotemSearchTarget == GatheringTotemSearchTarget.SELECTED_CLUSTER && selectedCluster == null) {
            return "No cluster";
        }
        if (gatheringTotemOptimizing()) {
            return "Working";
        }
        if (gatheringTotemPlacement == null) {
            return gatheringTotemSolveError == null ? "No results" : "Failed";
        }
        return gatheringTotemPlacement.nodeCount() + " x" + gatheringTotemPlacements.size();
    }

    private String gatheringTotemStatus() {
        if (!gatheringTotemSolverEnabled) {
            return "Disabled";
        }
        if (gatheringTotemSearchTarget == GatheringTotemSearchTarget.SELECTED_CLUSTER && selectedCluster == null) {
            return "Select a cluster";
        }
        if (gatheringTotemOptimizing()) {
            return "Optimizing...";
        }
        if (gatheringTotemSolveError != null) {
            return "Optimization failed";
        }
        if (gatheringTotemPlacement == null) {
            return "No results";
        }
        return gatheringTotemPlacement.nodeCount()
                + " nodes · "
                + gatheringTotemPlacements.size()
                + (gatheringTotemPlacements.size() == 1 ? " optimal spot" : " optimal spots");
    }

    private boolean gatheringTotemOptimizing() {
        if (!gatheringTotemSolverEnabled
                || gatheringTotemSearchTarget == GatheringTotemSearchTarget.SELECTED_CLUSTER
                        && selectedCluster == null) {
            return false;
        }
        return pendingGatheringTotemSolve != null
                || !gatheringTotemKey().equals(solvedGatheringTotemKey);
    }

    private String gatheringTotemScopeSummary() {
        if (gatheringAnalysisScope == GatheringAnalysisScope.SELECTED_TERRITORY && selectedTerritory != null) {
            return selectedTerritory.name();
        }
        return gatheringAnalysisScope.label();
    }

    private boolean gatheringTotemLayerEnabled(int index) {
        return switch (index) {
            case 0 -> showGatheringTotemHulls;
            case 1 -> showGatheringTotemPlayerRadius;
            case 2 -> showGatheringTotemNodeReach;
            case 3 -> showGatheringTotemCoveredNodes;
            case 4 -> showOtherOptimalGatheringTotems;
            default -> false;
        };
    }

    private void toggleGatheringTotemLayer(int index) {
        switch (index) {
            case 0 -> {
                showGatheringTotemHulls = !showGatheringTotemHulls;
                mapSettings.setShowGatheringTotemHulls(showGatheringTotemHulls);
            }
            case 1 -> {
                showGatheringTotemPlayerRadius = !showGatheringTotemPlayerRadius;
                mapSettings.setShowGatheringTotemPlayerRadius(showGatheringTotemPlayerRadius);
            }
            case 2 -> {
                showGatheringTotemNodeReach = !showGatheringTotemNodeReach;
                mapSettings.setShowGatheringTotemNodeReach(showGatheringTotemNodeReach);
            }
            case 3 -> {
                showGatheringTotemCoveredNodes = !showGatheringTotemCoveredNodes;
                mapSettings.setShowGatheringTotemCoveredNodes(showGatheringTotemCoveredNodes);
            }
            case 4 -> {
                showOtherOptimalGatheringTotems = !showOtherOptimalGatheringTotems;
                mapSettings.setShowOtherOptimalGatheringTotems(showOtherOptimalGatheringTotems);
            }
            default -> {
                return;
            }
        }
    }

    private GatheringTotemSearchTarget gatheringTotemTargetAt(float mouseX) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / GatheringTotemSearchTarget.values().length;
        int index = Math.min(
                GatheringTotemSearchTarget.values().length - 1,
                Math.max(0, (int) ((mouseX - PADDING) / segmentWidth)));
        return GatheringTotemSearchTarget.values()[index];
    }

    private void selectGatheringTotemPlacement(Placement placement) {
        if (placement == null) {
            return;
        }
        gatheringTotemPlacement = placement;
        selectedGatheringTotemPlacementKey = placement.key();
        gatheringTotemResultScroll = resultScrollForSelection();
    }

    private int resultScrollForSelection() {
        if (gatheringTotemPlacement == null || gatheringTotemPlacements.isEmpty()) {
            return 0;
        }
        int index = gatheringTotemPlacements.indexOf(gatheringTotemPlacement);
        if (index < 0) {
            return clampDropdownScroll(
                    gatheringTotemResultScroll,
                    gatheringTotemPlacements.size(),
                    TOTEM_RESULT_VISIBLE_ROWS);
        }
        if (index < gatheringTotemResultScroll) {
            return index;
        }
        if (index >= gatheringTotemResultScroll + TOTEM_RESULT_VISIBLE_ROWS) {
            return Math.max(0, index - TOTEM_RESULT_VISIBLE_ROWS + 1);
        }
        return clampDropdownScroll(
                gatheringTotemResultScroll,
                gatheringTotemPlacements.size(),
                TOTEM_RESULT_VISIBLE_ROWS);
    }

    private void centerOnGatheringTotemPlacement() {
        if (gatheringTotemPlacement == null) {
            return;
        }
        double minX = gatheringTotemPlacement.x() - GatheringTotemSolver.EFFECTIVE_NODE_RADIUS;
        double maxX = gatheringTotemPlacement.x() + GatheringTotemSolver.EFFECTIVE_NODE_RADIUS;
        double minZ = gatheringTotemPlacement.z() - GatheringTotemSolver.EFFECTIVE_NODE_RADIUS;
        double maxZ = gatheringTotemPlacement.z() + GatheringTotemSolver.EFFECTIVE_NODE_RADIUS;
        for (Position position : gatheringTotemPlacement.validCenterHull()) {
            minX = Math.min(minX, position.x());
            maxX = Math.max(maxX, position.x());
            minZ = Math.min(minZ, position.z());
            maxZ = Math.max(maxZ, position.z());
        }
        centerX = (minX + maxX) / 2.0;
        centerZ = (minZ + maxZ) / 2.0;
        double availableWidth = Math.max(1, uiScreenWidth() - SIDEBAR_WIDTH - insightsSidebarInset() - 48);
        double availableHeight = Math.max(1, uiScreenHeight() - 48);
        pixelsPerBlock = clamp(
                Math.min(availableWidth / Math.max(1, maxX - minX), availableHeight / Math.max(1, maxZ - minZ)),
                MIN_PIXELS_PER_BLOCK,
                MAX_PIXELS_PER_BLOCK);
    }

    private void renderSearchInput(
            UiCanvas canvas,
            float y,
            boolean dropdownOpen,
            boolean inputFocused,
            String search,
            String unfocusedValue) {
        canvas.fillRect(PADDING, y, SIDEBAR_WIDTH - PADDING * 2, INPUT_HEIGHT, dropdownOpen ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
        canvas.strokeRect(PADDING, y, SIDEBAR_WIDTH - PADDING * 2, INPUT_HEIGHT, 1, color(MAP_BORDER));
        String value = inputFocused ? search : unfocusedValue;
        Color valueColor = value == null || value.isBlank() ? color(MAP_SUBTEXT) : color(MAP_TEXT);
        String displayValue = value == null || value.isBlank() ? "Search" : value;
        float inputTextWidth = SIDEBAR_WIDTH - PADDING * 2 - 30;
        drawFittedText(canvas, PADDING + 8, y + INPUT_HEIGHT / 2f, 12, displayValue, valueColor, inputTextWidth, TextAlignment.LEFT);
        if (inputFocused) {
            float cursorX = PADDING + 10 + Math.min(textWidth(value, 12), inputTextWidth);
            drawText(canvas, cursorX, y + INPUT_HEIGHT / 2f, 12, "|", color(MAP_TEXT), TextAlignment.LEFT);
        }
        drawText(canvas, SIDEBAR_WIDTH - PADDING - 10, y + INPUT_HEIGHT / 2f, 12, dropdownOpen ? "^" : "v", color(MAP_SUBTEXT), TextAlignment.CENTER);
    }

    private void drawScopeControl(UiCanvas canvas, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / GatheringAnalysisScope.values().length;
        for (int index = 0; index < GatheringAnalysisScope.values().length; index++) {
            GatheringAnalysisScope scope = GatheringAnalysisScope.values()[index];
            float x = PADDING + index * segmentWidth;
            boolean enabled = scope != GatheringAnalysisScope.SELECTED_TERRITORY || selectedTerritory != null;
            boolean active = gatheringAnalysisScope == scope;
            boolean hovered = enabled && isHovered(nvgMouseX, nvgMouseY, x, y, segmentWidth, BUTTON_HEIGHT);
            Color background = active ? color(MAP_CONTROL_ACTIVE) : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL);
            if (!enabled) {
                background = withAlpha(background, 105);
            }
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
        canvas.fillRect(x, y, width, TERRITORY_DETAIL_HEIGHT, color(MAP_HEADER, 210));
        canvas.strokeRect(x, y, width, TERRITORY_DETAIL_HEIGHT, 1, color(MAP_SELECTED_TERRITORY));
        float detailWidth = width - 16;
        int totalNodes = cachedTerritoryNodeCounts.getOrDefault(territory.name(), 0);
        int matchingNodes = selectedTerritoryMatchingNodeCount;
        drawFittedText(canvas, x + 8, y + 17, 14, territory.name(), color(MAP_TEXT), detailWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 36, 11, totalNodes + " total nodes | " + matchingNodes + " matching", color(MAP_SUBTEXT), detailWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 56, 10, territoryBoundsLabel(territory), color(MAP_SUBTEXT), detailWidth, TextAlignment.LEFT);
    }

    private void drawButton(UiCanvas canvas, float x, float y, float w, float h, String label, boolean active) {
        boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y, w, h);
        canvas.fillRect(x, y, w, h, active ? color(MAP_CONTROL_ACTIVE) : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
        canvas.strokeRect(x, y, w, h, 1, color(MAP_BORDER));
        drawText(canvas, x + w / 2f, y + h / 2f, 12, label, color(MAP_TEXT), TextAlignment.CENTER);
    }

    private void drawToggle(UiCanvas canvas, float x, float y, float w, float h, GatheringProfession profession, boolean active) {
        drawButton(canvas, x, y, w, h, displayProfession(profession), active);
        drawCircle(canvas, x + 13, y + h / 2f, 4, profession.color());
    }

    private void renderResourceDropdown(UiCanvas canvas, float y) {
        List<String> resources = resourceDropdownOptions();
        int visibleRows = Math.min(RESOURCE_DROPDOWN_VISIBLE_ROWS, resources.size());
        resourceDropdownScroll = clampResourceDropdownScroll(resourceDropdownScroll, resources.size());
        float x = PADDING;
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float height = Math.max(1, visibleRows) * RESOURCE_DROPDOWN_ROW_HEIGHT;
        canvas.fillRect(x, y, width, height, color(BACKGROUND_BODY, 248));
        canvas.strokeRect(x, y, width, height, 1, color(MAP_BORDER));
        if (resources.isEmpty()) {
            drawText(canvas, x + 8, y + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f, 11, "No matches", color(MAP_SUBTEXT), TextAlignment.LEFT);
            return;
        }
        for (int index = 0; index < visibleRows; index++) {
            String resource = resources.get(resourceDropdownScroll + index);
            boolean selected = resource.isBlank()
                    ? selectedResourceFilters.isEmpty()
                    : selectedResourceFilters.contains(resource);
            boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT, width, RESOURCE_DROPDOWN_ROW_HEIGHT);
            if (selected || hovered) {
                canvas.fillRect(x + 1, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + 1, width - 2, RESOURCE_DROPDOWN_ROW_HEIGHT - 2, selected ? color(MAP_CONTROL_ACTIVE) : color(MAP_CONTROL_HOVER));
            }
            String label = resource.isBlank() ? "All resources" : resource;
            drawFittedText(canvas, x + 8, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f, 11, label, resource.isBlank() ? color(MAP_SUBTEXT) : color(MAP_TEXT), width - 16, TextAlignment.LEFT);
        }
        if (resources.size() > visibleRows) {
            String range = (resourceDropdownScroll + 1) + "-" + (resourceDropdownScroll + visibleRows) + "/" + resources.size();
            drawText(canvas, x + width - 8, y + height - 7, 9, range, color(MAP_SUBTEXT), TextAlignment.RIGHT);
        }
    }

    private void renderTerritoryDropdown(UiCanvas canvas, float y) {
        List<GuildTerritory> territories = territoryDropdownOptions();
        int visibleRows = Math.min(TERRITORY_DROPDOWN_VISIBLE_ROWS, territories.size());
        territoryDropdownScroll = clampDropdownScroll(territoryDropdownScroll, territories.size(), TERRITORY_DROPDOWN_VISIBLE_ROWS);
        float x = PADDING;
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float height = Math.max(1, visibleRows) * RESOURCE_DROPDOWN_ROW_HEIGHT;
        canvas.fillRect(x, y, width, height, color(BACKGROUND_BODY, 248));
        canvas.strokeRect(x, y, width, height, 1, color(MAP_BORDER));
        if (territories.isEmpty()) {
            drawText(canvas, x + 8, y + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f, 11, "No matches", color(MAP_SUBTEXT), TextAlignment.LEFT);
            return;
        }
        for (int index = 0; index < visibleRows; index++) {
            GuildTerritory territory = territories.get(territoryDropdownScroll + index);
            boolean selected = territory.equals(selectedTerritory);
            boolean hovered = isHovered(nvgMouseX, nvgMouseY, x, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT, width, RESOURCE_DROPDOWN_ROW_HEIGHT);
            if (selected || hovered) {
                canvas.fillRect(x + 1, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + 1, width - 2, RESOURCE_DROPDOWN_ROW_HEIGHT - 2, selected ? color(MAP_CONTROL_ACTIVE) : color(MAP_CONTROL_HOVER));
            }
            drawFittedText(canvas, x + 8, y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f, 11, territory.name(), color(MAP_TEXT), width - 16, TextAlignment.LEFT);
        }
        if (territories.size() > visibleRows) {
            String range = (territoryDropdownScroll + 1) + "-" + (territoryDropdownScroll + visibleRows) + "/" + territories.size();
            drawText(canvas, x + width - 8, y + height - 7, 9, range, color(MAP_SUBTEXT), TextAlignment.RIGHT);
        }
    }

    private void renderWorldEventDropdown(UiCanvas canvas, float y) {
        List<WorldEventDefinition> events = worldEventDropdownOptions();
        int visibleRows = Math.min(WORLD_EVENT_DROPDOWN_VISIBLE_ROWS, events.size());
        worldEventDropdownScroll = clampDropdownScroll(
                worldEventDropdownScroll,
                events.size(),
                WORLD_EVENT_DROPDOWN_VISIBLE_ROWS);
        float x = PADDING;
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float height = Math.max(1, visibleRows) * RESOURCE_DROPDOWN_ROW_HEIGHT;
        canvas.fillRect(x, y, width, height, color(BACKGROUND_BODY, 248));
        canvas.strokeRect(x, y, width, height, 1, color(MAP_BORDER));
        if (events.isEmpty()) {
            drawText(canvas, x + 8, y + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f, 11, "No matches", color(MAP_SUBTEXT), TextAlignment.LEFT);
            return;
        }
        for (int index = 0; index < visibleRows; index++) {
            WorldEventDefinition event = events.get(worldEventDropdownScroll + index);
            boolean selected = cachedTrackedWorldEventIds.contains(event.internalName());
            boolean hovered = isHovered(
                    nvgMouseX,
                    nvgMouseY,
                    x,
                    y + index * RESOURCE_DROPDOWN_ROW_HEIGHT,
                    width,
                    RESOURCE_DROPDOWN_ROW_HEIGHT);
            if (selected || hovered) {
                canvas.fillRect(x + 1,
                        y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + 1,
                        width - 2,
                        RESOURCE_DROPDOWN_ROW_HEIGHT - 2,
                        selected ? color(MAP_CONTROL_ACTIVE) : color(MAP_CONTROL_HOVER));
            }
            String label = (selected ? "[x] " : "[ ] ") + event.name();
            drawFittedText(
                    canvas,
                    x + 8,
                    y + index * RESOURCE_DROPDOWN_ROW_HEIGHT + RESOURCE_DROPDOWN_ROW_HEIGHT / 2f,
                    11,
                    label,
                    event.isVisible() ? color(MAP_TEXT) : color(MAP_SUBTEXT),
                    width - 16,
                    TextAlignment.LEFT);
        }
        if (events.size() > visibleRows) {
            String range = (worldEventDropdownScroll + 1) + "-" + (worldEventDropdownScroll + visibleRows) + "/" + events.size();
            drawText(canvas, x + width - 8, y + height - 7, 9, range, color(MAP_SUBTEXT), TextAlignment.RIGHT);
        }
    }

    private void renderSidebarScrollbar(UiCanvas canvas, float screenHeight) {
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
        canvas.fillRect(trackX, trackY, 3, trackHeight, color(MAP_TEXT, 28));
        canvas.fillRect(trackX, thumbY, 3, thumbHeight, color(MAP_TEXT, 110));
    }

    private String centerPlayerButtonLabel() {
        return System.currentTimeMillis() < centerPlayerWarningUntilMs ? "Leave housing bum !" : "Center Player";
    }

    private boolean copyHoveredCoordinates(float mx, float my, float sidebarMy, float screenWidth, float screenHeight) {
        MapViewport viewport = mapViewport(screenWidth, screenHeight);
        if (viewport.isInsideScreen(mx, my) && hoveredIngredientFarmSpot != null) {
            copyToClipboard(hoveredIngredientFarmSpot.coordinates());
            return true;
        }
        if (viewport.isInsideScreen(mx, my) && hoveredFocusMarker != null) {
            copyToClipboard(hoveredFocusMarker.coordinates());
            return true;
        }
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
            if (viewport.isInsideScreen(mx, my)
                    && hoveredWorldEvent != null
                    && hoveredWorldEventLocationIndex >= 0) {
                copyToClipboard(worldEventCoordinates(
                        hoveredWorldEvent.locations().get(hoveredWorldEventLocationIndex)));
                return true;
            }
            return false;
        }
        if (gatheringTotemSolverEnabled && gatheringTotemPlacement != null) {
            float totemRowsY = insights.overviewY() + 58 + (showDebugInfo ? 32 : 0);
            if (insightsSidebarOpen
                    && isHovered(
                            mx,
                            my,
                            insightsX + PADDING,
                            totemRowsY,
                            INSIGHTS_SIDEBAR_WIDTH - PADDING * 2,
                            48)) {
                copyToClipboard(totemCoords(gatheringTotemPlacement));
                return true;
            }
            Placement clickedPlacement = viewport.isInsideScreen(mx, my)
                    ? gatheringTotemPlacementAt(visibleGatheringTotemPlacements(), viewport, mx, my)
                    : null;
            if (clickedPlacement != null) {
                copyToClipboard(totemCoords(clickedPlacement));
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

    private static String totemCoords(Placement placement) {
        return Math.round(placement.x()) + " " + Math.round(placement.z());
    }

    private Placement gatheringTotemPlacementAt(
            List<Placement> placements,
            MapViewport viewport,
            float mouseX,
            float mouseY) {
        Placement closestMarker = null;
        double closestMarkerDistance = 9;
        for (Placement placement : placements) {
            float bestX = viewport.worldToScreenX(placement.x());
            float bestZ = viewport.worldToScreenZ(placement.z());
            double distance = Math.hypot(mouseX - bestX, mouseY - bestZ);
            if (distance <= closestMarkerDistance) {
                closestMarker = placement;
                closestMarkerDistance = distance;
            }
        }
        if (closestMarker != null) {
            return closestMarker;
        }
        if (gatheringTotemPlacement != null
                && placements.contains(gatheringTotemPlacement)
                && isGatheringTotemPlacementHovered(
                        gatheringTotemPlacement,
                        viewport,
                        mouseX,
                        mouseY)) {
            return gatheringTotemPlacement;
        }
        for (Placement placement : placements) {
            if (placement == gatheringTotemPlacement) {
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
                gatheringTotemPlacement,
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
        float y = Math.max(8, nvgMouseY + 12);
        canvas.fillRect(x, y, 180, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 180, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, title, color(MAP_TEXT), 164, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 11, subtitle, color(MAP_SUBTEXT), 164, TextAlignment.LEFT);
    }

    private void renderGatheringTotemTooltip(UiCanvas canvas, Placement placement) {
        int index = gatheringTotemPlacements.indexOf(placement);
        String title = "#"
                + (index < 0 ? "?" : index + 1)
                + " of "
                + gatheringTotemPlacements.size()
                + " · "
                + placement.nodeCount()
                + " nodes";
        String subtitle = totemCoords(placement) + " · Right-click to copy";
        float x = tooltipX(210);
        float y = Math.max(8, nvgMouseY + 12);
        canvas.fillRect(x, y, 210, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 210, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, title, color(MAP_TEXT), 194, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 11, subtitle, color(MAP_SUBTEXT), 194, TextAlignment.LEFT);
    }

    private void renderClusterTooltip(UiCanvas canvas, GatheringNodeCluster cluster) {
        String title = cluster.resource() + " | score " + cluster.score() + "%";
        String subtitle = cluster.nodeCount() + " nodes | " + Math.round(cluster.averageSpacing()) + "m";
        float x = tooltipX(200);
        float y = Math.max(8, nvgMouseY + 12);
        canvas.fillRect(x, y, 200, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 200, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, title, color(MAP_TEXT), 184, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 11, subtitle, color(MAP_SUBTEXT), 184, TextAlignment.LEFT);
    }

    private void renderTerritoryTooltip(UiCanvas canvas, GuildTerritory territory) {
        String subtitle = cachedTerritoryNodeCounts.getOrDefault(territory.name(), 0) + " gathering nodes";
        float x = tooltipX(200);
        float y = Math.max(8, nvgMouseY + 12);
        canvas.fillRect(x, y, 200, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 200, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, territory.name(), color(MAP_TEXT), 184, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 11, subtitle, color(MAP_SUBTEXT), 184, TextAlignment.LEFT);
    }

    private void renderWorldEventTooltip(UiCanvas canvas, WorldEventDefinition event, int locationIndex) {
        WorldEventLocation location = event.locations().get(Math.max(0, locationIndex));
        String locationLabel = event.locations().size() > 1
                ? "Possible " + (locationIndex + 1) + "/" + event.locations().size()
                        + ": " + worldEventCoordinates(location)
                : worldEventCoordinates(location);
        String subtitle = worldEventScheduleLabel(event.schedule()) + " | " + locationLabel;
        float x = tooltipX(210);
        float y = Math.max(8, nvgMouseY + 12);
        canvas.fillRect(x, y, 210, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 210, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, event.name(), color(MAP_TEXT), 194, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 11, subtitle, color(MAP_SUBTEXT), 194, TextAlignment.LEFT);
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
        gatheringAnalysisVersion++;
        refreshSelectedTerritoryMatchingCount();
        clusterOutlineShapes.clear();
        clusterOutlineScale = Double.NaN;
        hoveredNode = null;
        hoveredCluster = null;
        clearInvalidSelections();
    }

    private void refreshGatheringTotemPlacement() {
        if (!gatheringTotemSolverEnabled) {
            if (pendingGatheringTotemSolve != null
                    || gatheringTotemPlacement != null
                    || !gatheringTotemPlacements.isEmpty()) {
                resetGatheringTotemSolve();
            }
            return;
        }

        String requestKey = gatheringTotemKey();
        long now = System.currentTimeMillis();
        if (!requestKey.equals(observedGatheringTotemKey)) {
            invalidateGatheringTotemSolve(requestKey, now + TOTEM_SOLVE_DEBOUNCE_MS);
        }
        if (gatheringTotemSearchTarget == GatheringTotemSearchTarget.SELECTED_CLUSTER
                && selectedCluster == null) {
            solvedGatheringTotemKey = requestKey;
            return;
        }

        if (pendingGatheringTotemSolve != null) {
            if (!pendingGatheringTotemSolve.isDone()) {
                return;
            }
            String completedKey = pendingGatheringTotemKey;
            long completedGeneration = pendingGatheringTotemGeneration;
            try {
                List<Placement> completed = pendingGatheringTotemSolve.join();
                if (requestKey.equals(completedKey)
                        && completedGeneration == gatheringTotemRequestGeneration) {
                    gatheringTotemPlacements = GatheringTotemResults.ordered(
                            completed,
                            currentPlayerTotemPosition());
                    gatheringTotemPlacement = GatheringTotemResults.select(
                            gatheringTotemPlacements,
                            selectedGatheringTotemPlacementKey);
                    selectedGatheringTotemPlacementKey = gatheringTotemPlacement == null
                            ? null
                            : gatheringTotemPlacement.key();
                    gatheringTotemResultScroll = resultScrollForSelection();
                    gatheringTotemSolveError = null;
                    solvedGatheringTotemKey = requestKey;
                }
            } catch (RuntimeException exception) {
                if (requestKey.equals(completedKey)
                        && completedGeneration == gatheringTotemRequestGeneration) {
                    gatheringTotemPlacements = List.of();
                    gatheringTotemPlacement = null;
                    gatheringTotemSolveError = exception.getMessage() == null
                            ? exception.getClass().getSimpleName()
                            : exception.getMessage();
                    solvedGatheringTotemKey = requestKey;
                    SeqClient.LOGGER.warn("[GatheringMap] Gathering totem optimization failed.", exception);
                }
            } finally {
                pendingGatheringTotemSolve = null;
                pendingGatheringTotemKey = "";
                pendingGatheringTotemGeneration = 0;
            }
        }
        if (requestKey.equals(solvedGatheringTotemKey)
                || now < gatheringTotemSolveNotBeforeMs) {
            return;
        }

        List<GatheringNode> eligibleNodes = List.copyOf(cachedFilteredNodes);
        Set<String> resources = Set.copyOf(selectedResourceFilters);
        GuildTerritory territory = gatheringAnalysisScope == GatheringAnalysisScope.SELECTED_TERRITORY
                ? selectedTerritory
                : null;
        List<GatheringNode> clusterNodes =
                gatheringTotemSearchTarget == GatheringTotemSearchTarget.SELECTED_CLUSTER
                                && selectedCluster != null
                        ? List.copyOf(selectedCluster.nodes())
                        : List.of();
        gatheringTotemPlacements = List.of();
        gatheringTotemPlacement = null;
        gatheringTotemSolveError = null;
        pendingGatheringTotemKey = requestKey;
        pendingGatheringTotemGeneration = gatheringTotemRequestGeneration;
        pendingGatheringTotemSolve = CompletableFuture.supplyAsync(
                () -> GatheringTotemSolver.solveAll(eligibleNodes, resources, territory, clusterNodes));
    }

    private String gatheringTotemKey() {
        String clusterSelection = gatheringTotemSearchTarget == GatheringTotemSearchTarget.SELECTED_CLUSTER
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
                + gatheringTotemSearchTarget.name()
                + "|"
                + clusterSelection;
    }

    private void invalidateGatheringTotemSolve(String requestKey, long notBeforeMs) {
        if (pendingGatheringTotemSolve != null) {
            pendingGatheringTotemSolve.cancel(true);
        }
        gatheringTotemRequestGeneration++;
        pendingGatheringTotemSolve = null;
        pendingGatheringTotemKey = "";
        pendingGatheringTotemGeneration = 0;
        solvedGatheringTotemKey = "";
        observedGatheringTotemKey = requestKey;
        gatheringTotemSolveNotBeforeMs = notBeforeMs;
        gatheringTotemSolveError = null;
        gatheringTotemPlacements = List.of();
        gatheringTotemPlacement = null;
        hoveredGatheringTotemPlacement = null;
        gatheringTotemResultScroll = 0;
    }

    private void refreshGatheringTotemPlacementNow() {
        invalidateGatheringTotemSolve(gatheringTotemKey(), 0);
        refreshGatheringTotemPlacement();
    }

    private Position currentPlayerTotemPosition() {
        if (SeqClient.mc.player == null) {
            return null;
        }
        return new Position(SeqClient.mc.player.getX(), SeqClient.mc.player.getZ());
    }

    private void resetGatheringTotemSolve() {
        if (pendingGatheringTotemSolve != null) {
            pendingGatheringTotemSolve.cancel(true);
        }
        gatheringTotemRequestGeneration++;
        pendingGatheringTotemSolve = null;
        pendingGatheringTotemKey = "";
        pendingGatheringTotemGeneration = 0;
        solvedGatheringTotemKey = "";
        observedGatheringTotemKey = "";
        gatheringTotemSolveNotBeforeMs = 0;
        gatheringTotemSolveError = null;
        gatheringTotemPlacements = List.of();
        gatheringTotemPlacement = null;
        hoveredGatheringTotemPlacement = null;
        gatheringTotemResultScroll = 0;
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
                cachedTrackedWorldEventIds,
                worldEventDropdownTrackedOnly,
                query);
    }

    private String trackedWorldEventLabel() {
        int tracked = cachedTrackedWorldEventIds.size();
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

    private boolean hasMapFocus() {
        return mapFocus != null && !mapFocus.markers().isEmpty();
    }

    private void fitMapFocus(float mapW, float mapH) {
        MapBounds bounds = mapFocus.bounds();
        centerX = (bounds.minX() + bounds.maxX()) / 2.0;
        centerZ = (bounds.minZ() + bounds.maxZ()) / 2.0;
        double spanX = Math.max(80, bounds.maxX() - bounds.minX());
        double spanZ = Math.max(80, bounds.maxZ() - bounds.minZ());
        double xScale = Math.max(1, mapW - 80) / spanX;
        double zScale = Math.max(1, mapH - 100) / spanZ;
        pixelsPerBlock = clamp(
                Math.min(Math.min(xScale, zScale) * 0.9, CONTEXT_FOCUS_MAX_PIXELS_PER_BLOCK),
                MIN_PIXELS_PER_BLOCK,
                MAX_PIXELS_PER_BLOCK);
    }

    private void selectIngredientFarmSpot(IngredientFarmSpot spot) {
        if (spot == null) {
            return;
        }
        ingredientMapCategory = IngredientMapCategory.TOTEM_SPOTS;
        clearIngredientMapSelections();
        ingredientMapSelection.toggleTotem(spot.id());
        selectedIngredientFarmSpot = spot;
        centerOnIngredientFarmSpot(spot);
    }

    private void toggleIngredientFarmSpotSelection(IngredientFarmSpot spot) {
        boolean selected = ingredientMapSelection.toggleTotem(spot.id());
        selectedIngredientFarmSpot = selected
                ? spot
                : IngredientFarmSpotCatalog.all().stream()
                        .filter(candidate -> ingredientMapSelection.isTotemSelected(candidate.id()))
                        .reduce((first, second) -> second)
                        .orElse(null);
    }

    private void toggleFocusMarkerSelection(MapFocus.Marker marker) {
        boolean selected = ingredientMapSelection.toggleSpawn(marker.id());
        selectedFocusMarker = selected
                ? marker
                : mapFocus.markers().stream()
                        .filter(candidate -> ingredientMapSelection.isSpawnSelected(candidate.id()))
                        .reduce((first, second) -> second)
                        .orElse(null);
    }

    private void clearIngredientMapSelections() {
        ingredientMapSelection.clear();
        selectedFocusMarker = null;
        selectedIngredientFarmSpot = null;
    }

    private void renderSelectedIngredientWaypoints() {
        if (ingredientMapSelection.isEmpty()) {
            return;
        }
        List<Waypoint> waypoints = new ArrayList<>();
        if (mapFocus != null) {
            for (MapFocus.Marker marker : mapFocus.markers()) {
                if (!ingredientMapSelection.isSpawnSelected(marker.id())) {
                    continue;
                }
                waypoints.add(new Waypoint(
                        "ingredient-spawn:" + mapFocus.title() + ":" + marker.id(),
                        Kind.INGREDIENT_SPAWN,
                        marker.label(),
                        marker.source(),
                        marker.x(),
                        marker.y(),
                        marker.z(),
                        WaypointIcon.of(mapFocusIcon, mapFocusSkinLookup)));
            }
        }
        for (IngredientFarmSpot spot : IngredientFarmSpotCatalog.all()) {
            if (!ingredientMapSelection.isTotemSelected(spot.id())) {
                continue;
            }
            waypoints.add(new Waypoint(
                    "ingredient-totem:" + spot.id(),
                    Kind.TOTEM_SPOT,
                    spot.name(),
                    String.join(", ", spot.ingredients()),
                    spot.x(),
                    spot.y(),
                    spot.z(),
                    WaypointIcon.of(new ItemStack(Items.TOTEM_OF_UNDYING), null)));
        }
        IngredientWaypointManager.getInstance().replaceAll(waypoints);
    }

    private void centerOnIngredientFarmSpot(IngredientFarmSpot spot) {
        centerX = spot.x();
        centerZ = spot.z();
        pixelsPerBlock = Math.max(pixelsPerBlock, 0.35);
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
        MapViewport focusedViewport = mapViewport(screenWidth, screenHeight);
        if (displayMode == MapDisplayMode.INGREDIENTS
                && focusedViewport.isInsideScreen(mx, my)
                && hoveredIngredientFarmSpot != null) {
            toggleIngredientFarmSpotSelection(hoveredIngredientFarmSpot);
            draggingMap = true;
            hoveredIngredientFarmSpot = null;
            closeSearchDropdowns();
            return true;
        }
        if (displayMode == MapDisplayMode.INGREDIENTS
                && focusedViewport.isInsideScreen(mx, my)
                && hoveredFocusMarker != null) {
            toggleFocusMarkerSelection(hoveredFocusMarker);
            draggingMap = true;
            hoveredFocusMarker = null;
            closeSearchDropdowns();
            return true;
        }
        if (displayMode == MapDisplayMode.INGREDIENTS) {
            if (mouseClickedIngredients(mx, my, screenWidth, screenHeight)) {
                return true;
            }
            return super.mouseClicked(click, outsideScreen);
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
        if (isHovered(mx, sidebarMy, PADDING, layout.totemPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.TOTEM_SOLVER);
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
        TotemSolverLayout totemLayout = totemSolverLayout(layout.totemPanelY());
        if (totemLayout.enabledY() >= 0) {
            if (isHovered(
                    mx,
                    sidebarMy,
                    PADDING,
                    totemLayout.enabledY(),
                    SIDEBAR_WIDTH - PADDING * 2,
                    BUTTON_HEIGHT)) {
                gatheringTotemSolverEnabled = !gatheringTotemSolverEnabled;
                mapSettings.setGatheringTotemSolverEnabled(gatheringTotemSolverEnabled);
                resetGatheringTotemSolve();
                return true;
            }
            if (isHovered(
                    mx,
                    sidebarMy,
                    PADDING,
                    totemLayout.targetY(),
                    SIDEBAR_WIDTH - PADDING * 2,
                    BUTTON_HEIGHT)) {
                GatheringTotemSearchTarget target = gatheringTotemTargetAt(mx);
                gatheringTotemSearchTarget = target;
                mapSettings.setGatheringTotemSearchTarget(target);
                invalidateGatheringTotemSolve(
                        gatheringTotemKey(),
                        System.currentTimeMillis() + TOTEM_SOLVE_DEBOUNCE_MS);
                return true;
            }
            if (isHovered(
                    mx,
                    sidebarMy,
                    PADDING,
                    totemLayout.refreshY(),
                    SIDEBAR_WIDTH - PADDING * 2,
                    BUTTON_HEIGHT)) {
                if (gatheringTotemSolverEnabled) {
                    refreshGatheringTotemPlacementNow();
                }
                return true;
            }

            float layerGap = 4;
            float layerWidth = (SIDEBAR_WIDTH - PADDING * 2 - layerGap) / 2f;
            for (int index = 0; index < 5; index++) {
                int column = index % 2;
                int row = index / 2;
                if (isHovered(
                        mx,
                        sidebarMy,
                        PADDING + column * (layerWidth + layerGap),
                        totemLayout.layerStartY() + row * (TOGGLE_HEIGHT + layerGap),
                        layerWidth,
                        TOGGLE_HEIGHT)) {
                    toggleGatheringTotemLayer(index);
                    return true;
                }
            }

            if (isHovered(
                    mx,
                    sidebarMy,
                    PADDING,
                    totemLayout.resultsStartY(),
                    SIDEBAR_WIDTH - PADDING * 2,
                    TOTEM_RESULT_VISIBLE_ROWS * TOTEM_RESULT_ROW_HEIGHT)) {
                int row = Math.max(
                        0,
                        (int) ((sidebarMy - totemLayout.resultsStartY()) / TOTEM_RESULT_ROW_HEIGHT));
                int index = gatheringTotemResultScroll + row;
                if (index < gatheringTotemPlacements.size()) {
                    selectGatheringTotemPlacement(gatheringTotemPlacements.get(index));
                }
                return true;
            }

            float actionWidth = (SIDEBAR_WIDTH - PADDING * 2 - SPLIT_CONTROL_GAP) / 2f;
            if (isHovered(
                    mx,
                    sidebarMy,
                    PADDING,
                    totemLayout.actionsY(),
                    actionWidth,
                    BUTTON_HEIGHT)) {
                centerOnGatheringTotemPlacement();
                return true;
            }
            if (isHovered(
                    mx,
                    sidebarMy,
                    PADDING + actionWidth + SPLIT_CONTROL_GAP,
                    totemLayout.actionsY(),
                    actionWidth,
                    BUTTON_HEIGHT)) {
                if (gatheringTotemPlacement != null) {
                    copyToClipboard(totemCoords(gatheringTotemPlacement));
                }
                return true;
            }
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
            Placement clickedPlacement = gatheringTotemSolverEnabled
                    ? gatheringTotemPlacementAt(visibleGatheringTotemPlacements(), viewport, mx, my)
                    : null;
            if (clickedPlacement != null) {
                selectGatheringTotemPlacement(clickedPlacement);
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
            draggingMap = true;
            hoveredNode = null;
            hoveredCluster = null;
            hoveredTerritory = null;
            closeSearchDropdowns();
            return true;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    private boolean mouseClickedIngredients(float mx, float my, float screenWidth, float screenHeight) {
        IngredientSidebarLayout layout = ingredientSidebarLayout();
        float buttonWidth = SIDEBAR_WIDTH - PADDING * 2;
        if (isHovered(mx, my, PADDING, layout.backY(), buttonWidth, BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(parent);
            return true;
        }
        if (isHovered(mx, my, PADDING, layout.centerY(), buttonWidth, BUTTON_HEIGHT)) {
            if (!centerOnPlayer()) {
                centerPlayerWarningUntilMs = System.currentTimeMillis() + CENTER_PLAYER_WARNING_DURATION_MS;
            }
            return true;
        }
        if (isHovered(mx, my, PADDING, layout.modeY(), buttonWidth, BUTTON_HEIGHT)) {
            setDisplayMode(mapModeAt(mx));
            return true;
        }
        if (isHovered(mx, my, PADDING, layout.titleY(), buttonWidth, BUTTON_HEIGHT)) {
            IngredientMapCategory nextCategory = ingredientMapCategoryAt(mx);
            if (nextCategory != ingredientMapCategory) {
                ingredientMapCategory = nextCategory;
            }
            hoveredFocusMarker = null;
            hoveredIngredientFarmSpot = null;
            return true;
        }
        if (isHovered(mx, my, PADDING, layout.renderWaypointsY(), buttonWidth, BUTTON_HEIGHT)) {
            renderSelectedIngredientWaypoints();
            return true;
        }
        if (isHovered(mx, my, PADDING, layout.clearWaypointsY(), buttonWidth, BUTTON_HEIGHT)) {
            IngredientWaypointManager.getInstance().clear();
            return true;
        }
        if (ingredientMapCategory == IngredientMapCategory.TOTEM_SPOTS) {
            float listY = ingredientFarmSpotListY(layout);
            float listHeight = ingredientFarmSpotListHeight(layout);
            if (isHovered(mx, my, 0, listY, SIDEBAR_WIDTH, listHeight)) {
                float rowY = listY - ingredientFarmSpotScroll;
                for (IngredientFarmSpot spot : IngredientFarmSpotCatalog.all()) {
                    if (isHovered(
                            mx,
                            my,
                            PADDING,
                            rowY,
                            buttonWidth,
                            INGREDIENT_FARM_SPOT_CARD_HEIGHT)) {
                        selectIngredientFarmSpot(spot);
                        return true;
                    }
                    rowY += INGREDIENT_FARM_SPOT_ROW_HEIGHT;
                }
            }
        } else if (isHovered(mx, my, PADDING, layout.guideY(), buttonWidth, BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(new IngredientGuideScreen(this));
            return true;
        }
        if (ingredientMapCategory == IngredientMapCategory.SPAWNS
                && selectedFocusMarker != null
                && isHovered(mx, my, PADDING, layout.copyY(), buttonWidth, BUTTON_HEIGHT)) {
            copyToClipboard(selectedFocusMarker.coordinates());
            return true;
        }

        MapViewport viewport = mapViewport(screenWidth, screenHeight);
        if (viewport.isInsideScreen(mx, my)) {
            clearIngredientMapSelections();
            draggingMap = true;
            hoveredFocusMarker = null;
            hoveredIngredientFarmSpot = null;
            return true;
        }
        return mx >= 0 && mx <= SIDEBAR_WIDTH;
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
        if (displayMode == MapDisplayMode.INGREDIENTS) {
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
            centerX -= MinecraftUiRenderer.mouseDelta(deltaX) / pixelsPerBlock;
            centerZ -= MinecraftUiRenderer.mouseDelta(deltaY) / pixelsPerBlock;
            hoveredNode = null;
            hoveredCluster = null;
            hoveredTerritory = null;
            hoveredWorldEvent = null;
            hoveredWorldEventLocationIndex = -1;
            hoveredFocusMarker = null;
            hoveredIngredientFarmSpot = null;
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
        if (displayMode == MapDisplayMode.INGREDIENTS
                && mx >= 0
                && mx <= SIDEBAR_WIDTH) {
            if (ingredientMapCategory == IngredientMapCategory.TOTEM_SPOTS) {
                IngredientSidebarLayout ingredientLayout = ingredientSidebarLayout();
                float listY = ingredientFarmSpotListY(ingredientLayout);
                float listHeight = ingredientFarmSpotListHeight(ingredientLayout);
                if (isHovered(mx, my, 0, listY, SIDEBAR_WIDTH, listHeight)) {
                    ingredientFarmSpotScroll = (float) clamp(
                            ingredientFarmSpotScroll - scrollY * SIDEBAR_SCROLL_STEP,
                            0,
                            ingredientFarmSpotMaxScroll(ingredientLayout));
                }
            }
            return true;
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
        TotemSolverLayout totemLayout = totemSolverLayout(layout.totemPanelY());
        if (totemLayout.resultsStartY() >= 0) {
            float resultsY = totemLayout.resultsStartY() - sidebarScroll;
            if (isHovered(
                    mx,
                    my,
                    PADDING,
                    resultsY,
                    SIDEBAR_WIDTH - PADDING * 2,
                    TOTEM_RESULT_VISIBLE_ROWS * TOTEM_RESULT_ROW_HEIGHT)) {
                gatheringTotemResultScroll = clampDropdownScroll(
                        gatheringTotemResultScroll + (scrollY > 0 ? -1 : 1),
                        gatheringTotemPlacements.size(),
                        TOTEM_RESULT_VISIBLE_ROWS);
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

    private static void drawCircle(UiCanvas canvas, float x, float y, float radius, Color color) {
        canvas.fillCircle(x, y, radius, color);
    }

    private static void drawCircleOutline(UiCanvas canvas, float x, float y, float radius, float width, Color color) {
        canvas.strokeCircle(x, y, radius, width, color);
    }

    private static void drawText(
            UiCanvas canvas, float x, float y, float size, String text, Color color, TextAlignment align) {
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(
                SeqClient.getFontManager().getSelectedFont(),
                size,
                color,
                align.horizontalAlign(),
                UiCanvas.VerticalAlign.MIDDLE));
    }

    private static void drawSidebarText(UiCanvas canvas, float x, float y, float size, String text, Color color) {
        drawFittedText(canvas, x, y, size, text, color, SIDEBAR_WIDTH - x - PADDING, TextAlignment.LEFT);
    }

    private static void drawFittedText(
            UiCanvas canvas,
            float x,
            float y,
            float size,
            String text,
            Color color,
            float maxWidth,
            TextAlignment align) {
        String fitted = fitText(canvas, text, maxWidth, size);
        drawText(canvas, x, y, size, fitted, color, align);
    }

    private static String fitText(UiCanvas canvas, String text, float maxWidth, float size) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (textWidth(text, size) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        if (textWidth(ellipsis, size) > maxWidth) {
            return "";
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = text.substring(0, mid).stripTrailing() + ellipsis;
            if (textWidth(candidate, size) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low).stripTrailing() + ellipsis;
    }

    private static float textWidth(String text, float size) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return UiRenderer.measureText(text, SeqClient.getFontManager().getSelectedFont(), size).width();
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
        return MinecraftUiRenderer.mouseX(rawX);
    }

    private float scaledMouseY(double rawY) {
        return MinecraftUiRenderer.mouseY(rawY);
    }

    private static float uiScreenWidth() {
        return MinecraftUiRenderer.screenWidth();
    }

    private static float uiScreenHeight() {
        return MinecraftUiRenderer.screenHeight();
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

    private enum TextAlignment {
        LEFT(UiCanvas.HorizontalAlign.LEFT),
        CENTER(UiCanvas.HorizontalAlign.CENTER),
        RIGHT(UiCanvas.HorizontalAlign.RIGHT);

        private final UiCanvas.HorizontalAlign horizontalAlign;

        TextAlignment(UiCanvas.HorizontalAlign horizontalAlign) {
            this.horizontalAlign = horizontalAlign;
        }

        private UiCanvas.HorizontalAlign horizontalAlign() {
            return horizontalAlign;
        }
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

    private IngredientMapCategory ingredientMapCategoryAt(float mouseX) {
        float segmentWidth = (SIDEBAR_WIDTH - PADDING * 2) / IngredientMapCategory.values().length;
        int index = (int) ((mouseX - PADDING) / segmentWidth);
        return index >= 0 && index < IngredientMapCategory.values().length
                ? IngredientMapCategory.values()[index]
                : ingredientMapCategory;
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
        boolean tracked = cachedTrackedWorldEventIds.contains(event.internalName());
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
        if (displayMode == MapDisplayMode.WORLD_EVENTS || displayMode == MapDisplayMode.INGREDIENTS) {
            return new InsightsLayout(overviewY, -1, -1, -1, overviewY + 82);
        }
        float y = overviewY + (showDebugInfo ? 106 : 74);
        if (gatheringTotemSolverEnabled) {
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

    private static float ingredientFarmSpotListY(IngredientSidebarLayout layout) {
        return layout.guideY() + 22;
    }

    private float ingredientFarmSpotListHeight(IngredientSidebarLayout layout) {
        return Math.max(0, uiScreenHeight() - ingredientFarmSpotListY(layout) - PADDING);
    }

    private static float ingredientFarmSpotContentHeight() {
        int spotCount = IngredientFarmSpotCatalog.all().size();
        return spotCount == 0
                ? 0
                : (spotCount - 1) * INGREDIENT_FARM_SPOT_ROW_HEIGHT + INGREDIENT_FARM_SPOT_CARD_HEIGHT;
    }

    private float ingredientFarmSpotMaxScroll(IngredientSidebarLayout layout) {
        return Math.max(0, ingredientFarmSpotContentHeight() - ingredientFarmSpotListHeight(layout));
    }

    private IngredientSidebarLayout ingredientSidebarLayout() {
        float backY = 58;
        float centerY = backY + BUTTON_HEIGHT + 8;
        float modeY = centerY + BUTTON_HEIGHT + 18;
        float titleY = modeY + BUTTON_HEIGHT + 18;
        float renderWaypointsY = titleY + BUTTON_HEIGHT + 10;
        float clearWaypointsY = renderWaypointsY + BUTTON_HEIGHT + 6;
        float guideY = clearWaypointsY + BUTTON_HEIGHT + 18;
        float ingredientY = guideY + BUTTON_HEIGHT + 20;
        float summaryY = ingredientY + 20;
        float selectedTitleY = summaryY + 30;
        float selectedDetailY = selectedTitleY + 24;
        float copyY = selectedDetailY + 44;
        return new IngredientSidebarLayout(
                backY,
                centerY,
                modeY,
                titleY,
                renderWaypointsY,
                clearWaypointsY,
                guideY,
                ingredientY,
                summaryY,
                selectedTitleY,
                selectedDetailY,
                copyY);
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
        y += PANEL_GAP;
        float totemPanelY = y;
        y = totemSolverLayout(totemPanelY).endY();
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
                totemPanelY,
                filtersPanelY,
                resourceLabelY,
                resourceInputY,
                professionLabelY,
                professionStartY,
                y);
    }

    private TotemSolverLayout totemSolverLayout(float panelY) {
        float y = panelY + PANEL_HEADER_HEIGHT;
        if (!panelExpanded(WorldMapSidebarPanel.TOTEM_SOLVER)) {
            return new TotemSolverLayout(-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, y);
        }
        y += 8;
        float enabledY = y;
        y += BUTTON_HEIGHT + 6;
        float targetY = y;
        y += BUTTON_HEIGHT + 8;
        float filterSummaryY = y + 5;
        y += 34;
        float refreshY = y;
        y += BUTTON_HEIGHT + 8;
        float layerLabelY = y + 5;
        y += 14;
        float layerStartY = y;
        y += 3 * (TOGGLE_HEIGHT + 4);
        float legendY = y + 2;
        y += 52;
        float resultsLabelY = y + 5;
        y += 14;
        float resultsStartY = y;
        y += TOTEM_RESULT_VISIBLE_ROWS * TOTEM_RESULT_ROW_HEIGHT;
        float actionsY = y;
        y += BUTTON_HEIGHT + 8;
        return new TotemSolverLayout(
                enabledY,
                targetY,
                filterSummaryY,
                refreshY,
                layerLabelY,
                layerStartY,
                legendY,
                resultsLabelY,
                resultsStartY,
                actionsY,
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

    private record FocusIconOverlay(
            float x,
            float y,
            float size,
            ItemStack stack,
            Supplier<PlayerSkin> skinLookup) {}

    private record ScreenPoint(float x, float y) {}

    private record TerritoryLabelLayout(List<String> lines, float fontSize, float lineHeight) {}

    private record IngredientSidebarLayout(
            float backY,
            float centerY,
            float modeY,
            float titleY,
            float renderWaypointsY,
            float clearWaypointsY,
            float guideY,
            float ingredientY,
            float summaryY,
            float selectedTitleY,
            float selectedDetailY,
            float copyY) {}

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
            float totemPanelY,
            float filtersPanelY,
            float resourceLabelY,
            float resourceInputY,
            float professionLabelY,
            float professionStartY,
            float endY) {}

    private record TotemSolverLayout(
            float enabledY,
            float targetY,
            float filterSummaryY,
            float refreshY,
            float layerLabelY,
            float layerStartY,
            float legendY,
            float resultsLabelY,
            float resultsStartY,
            float actionsY,
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
