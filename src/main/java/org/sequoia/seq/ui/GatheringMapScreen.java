package org.sequoia.seq.ui;

import java.awt.Color;
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
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.map.ClusterScoreMode;
import org.sequoia.seq.map.GatheringClusterCache;
import org.sequoia.seq.map.GatheringMapImageService;
import org.sequoia.seq.map.GatheringMapSettings;
import org.sequoia.seq.map.GatheringNode;
import org.sequoia.seq.map.GatheringNodeCluster;
import org.sequoia.seq.map.GatheringNodeService;
import org.sequoia.seq.map.GatheringProfession;
import org.sequoia.seq.map.MapCalibration;
import org.sequoia.seq.map.MapBounds;
import org.sequoia.seq.map.MapViewport;
import org.sequoia.seq.map.GatheringMapImageService.TileKey;
import org.sequoia.seq.map.GatheringMapImageService.TileSet;
import org.sequoia.seq.utils.TextInputHelper;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

import static org.lwjgl.nanovg.NanoVG.*;

public class GatheringMapScreen extends Screen {
    private static final float SIDEBAR_WIDTH = 230;
    private static final float PADDING = 12;
    private static final float BUTTON_HEIGHT = 24;
    private static final float TOGGLE_HEIGHT = 22;
    private static final float INPUT_HEIGHT = 24;
    private static final float SIDEBAR_HEADER_HEIGHT = 44;
    private static final float SIDEBAR_SCROLL_STEP = 28;
    private static final long CENTER_PLAYER_WARNING_DURATION_MS = 6_767;
    private static final float CLUSTER_DETAIL_HEIGHT = 110;
    private static final float NODE_DETAIL_HEIGHT = 58;
    private static final float RESOURCE_DROPDOWN_ROW_HEIGHT = 20;
    private static final int RESOURCE_DROPDOWN_VISIBLE_ROWS = 8;
    private static final float MIN_HULL_PADDING_PX = 4f;
    private static final float MAX_HULL_PADDING_PX = 12f;
    private static final int HULL_SMOOTHING_PASSES = 2;
    private static final double MIN_PIXELS_PER_BLOCK = 0.035;
    private static final double MAX_PIXELS_PER_BLOCK = 2.5;
    private static final double NODE_DETAIL_PIXELS_PER_BLOCK = 0.42;
    private static final double CLUSTER_BADGE_PIXELS_PER_BLOCK = 0.65;
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

    private final Screen parent;
    private final GatheringNodeService nodeService = GatheringNodeService.getInstance();
    private final GatheringMapImageService mapImageService = GatheringMapImageService.getInstance();
    private final GatheringMapSettings mapSettings = GatheringMapSettings.getInstance();
    private final GatheringClusterCache clusterCache = GatheringClusterCache.getInstance();
    private final EnumMap<GatheringProfession, Boolean> professionToggles = new EnumMap<>(GatheringProfession.class);

    private double centerX = (MapCalibration.MIN_WORLD_X + MapCalibration.MAX_WORLD_X) / 2.0;
    private double centerZ = (MapCalibration.MIN_WORLD_Z + MapCalibration.MAX_WORLD_Z) / 2.0;
    private double pixelsPerBlock = 0.08;
    private boolean initializedViewport;
    private boolean draggingMap;
    private boolean resourceDropdownOpen;
    private boolean resourceInputFocused;
    private int resourceDropdownScroll;
    private float sidebarScroll;
    private float sidebarContentHeight;
    private long centerPlayerWarningUntilMs;
    private String resourceSearch = "";
    private final Set<String> selectedResourceFilters = new TreeSet<>();
    private GatheringNode hoveredNode;
    private GatheringNode selectedNode;
    private GatheringNodeCluster hoveredCluster;
    private GatheringNodeCluster selectedCluster;
    private boolean showClusters = true;
    private boolean showDebugInfo;
    private ClusterScoreMode clusterScoreMode = ClusterScoreMode.FOUR_TICK;
    private List<GatheringNode> cachedSourceNodes = List.of();
    private List<GatheringNode> cachedFilteredNodes = List.of();
    private List<GatheringNodeCluster> cachedClusters = List.of();
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

    public GatheringMapScreen(Screen parent) {
        super(Component.literal("Sequoia Gathering Map"));
        this.parent = parent;
        professionToggles.putAll(mapSettings.professionToggles());
        selectedResourceFilters.addAll(mapSettings.resourceFilters());
        showClusters = mapSettings.showClusters();
        showDebugInfo = mapSettings.showDebugInfo();
        clusterScoreMode = mapSettings.clusterScoreMode();
        nodeService.loadBundledNodes();
        mapImageService.requestLoad();
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

        double guiScale = SeqClient.mc.getWindow().getGuiScale();
        nvgMouseX = (float) (mouseX * guiScale / 2.0);
        nvgMouseY = (float) (mouseY * guiScale / 2.0);

        float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
        float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;
        showDebugInfo = mapSettings.showDebugInfo();
        float mapX = SIDEBAR_WIDTH;
        float mapY = 0;
        float mapW = Math.max(1, screenWidth - SIDEBAR_WIDTH);
        float mapH = Math.max(1, screenHeight);

        if (!initializedViewport) {
            initializedViewport = true;
            fitFullMap(mapW, mapH);
        }

        refreshClusterAnalysisIfNeeded();
        MapViewport viewport = new MapViewport(centerX, centerZ, pixelsPerBlock, mapX, mapY, mapW, mapH);
        NVGContext.renderDeferred(nvg -> renderNvg(nvg, viewport));
    }

    private void renderNvg(long nvg, MapViewport viewport) {
        String fontName = SeqClient.getFontManager().getSelectedFont();
        nvgFontFace(nvg, fontName);

        renderMapBackground(nvg, viewport);
        NVGWrapper.drawRect(nvg, viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight(), MAP_TINT);
        boolean clusterMode = shouldRenderClusters();
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
        if (!draggingMap && hoveredCluster != null && (clusterMode || hoveredNode == null)) {
            renderClusterTooltip(nvg, hoveredCluster);
        }
        renderSidebar(nvg);
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
        float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;
        sidebarScroll = clampSidebarScroll(sidebarScroll, screenHeight);
        NVGWrapper.drawRect(nvg, 0, 0, SIDEBAR_WIDTH, screenHeight, SIDEBAR_COLOR);
        NVGWrapper.drawRect(nvg, 0, 0, SIDEBAR_WIDTH, SIDEBAR_HEADER_HEIGHT, HEADER_COLOR);
        drawText(nvg, SIDEBAR_WIDTH / 2f, 22, 18, "Gathering Map", TITLE_COLOR, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);

        nvgScissor(nvg, 0, SIDEBAR_HEADER_HEIGHT, SIDEBAR_WIDTH, Math.max(0, screenHeight - SIDEBAR_HEADER_HEIGHT));
        float y = 58 - sidebarScroll;
        drawButton(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, "Back", false);
        y += BUTTON_HEIGHT + 8;
        drawButton(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, centerPlayerButtonLabel(), false);
        y += BUTTON_HEIGHT + 18;
        drawButton(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, showClusters ? "Clusters On" : "Clusters Off", showClusters);
        y += BUTTON_HEIGHT + 8;
        drawButton(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT, "Score " + clusterScoreMode.label(), true);
        y += BUTTON_HEIGHT + 18;

        if (showDebugInfo) {
            drawSidebarText(nvg, PADDING, y, 11, "Map source: " + displayMapImageSource(), SUBTEXT_COLOR);
            y += 18;
            drawSidebarText(nvg, PADDING, y, 11, "HQ status: " + mapImageService.hqStatus(), SUBTEXT_COLOR);
            y += 18;
        }

        drawText(nvg, PADDING, y, 12, "Resource", SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        y += 12;
        float resourceInputY = y;
        Color inputColor = resourceDropdownOpen ? CONTROL_HOVER : CONTROL_COLOR;
        NVGWrapper.drawRect(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, INPUT_HEIGHT, inputColor);
        NVGWrapper.drawRectOutline(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, INPUT_HEIGHT, 1, BORDER_COLOR);
        String value = resourceInputFocused ? resourceSearch : selectedResourceLabel();
        Color valueColor = value.isBlank() || (!resourceInputFocused && selectedResourceFilters.isEmpty())
                ? SUBTEXT_COLOR
                : TEXT_COLOR;
        String displayValue = value.isBlank() && !resourceInputFocused ? "All resources" : value;
        float inputTextWidth = SIDEBAR_WIDTH - PADDING * 2 - 30;
        drawFittedText(nvg, PADDING + 8, y + INPUT_HEIGHT / 2f, 12, displayValue, valueColor, inputTextWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        if (resourceInputFocused) {
            nvgFontSize(nvg, 12);
            float cursorX = PADDING + 10 + Math.min(textWidth(nvg, value), inputTextWidth);
            drawText(nvg, cursorX, y + INPUT_HEIGHT / 2f, 12, "|", TEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        }
        drawText(nvg, SIDEBAR_WIDTH - PADDING - 10, y + INPUT_HEIGHT / 2f, 12, resourceDropdownOpen ? "^" : "v", SUBTEXT_COLOR, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        y += INPUT_HEIGHT + 18;

        drawText(nvg, PADDING, y, 12, "Professions", SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        y += 12;
        for (GatheringProfession profession : List.of(
                GatheringProfession.WOODCUTTING,
                GatheringProfession.MINING,
                GatheringProfession.FARMING,
                GatheringProfession.FISHING)) {
            boolean active = professionToggles.getOrDefault(profession, true);
            drawToggle(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, TOGGLE_HEIGHT, profession, active);
            y += TOGGLE_HEIGHT + 6;
        }
        y += 12;

        GatheringNodeCluster clusterDetail = selectedCluster != null ? selectedCluster : hoveredCluster;
        GatheringNode detail = selectedNode != null ? selectedNode : hoveredNode;
        if (clusterDetail != null) {
            NVGWrapper.drawRect(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, CLUSTER_DETAIL_HEIGHT, new Color(28, 28, 38, 210));
            NVGWrapper.drawRectOutline(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, CLUSTER_DETAIL_HEIGHT, 1, BORDER_COLOR);
            float detailWidth = SIDEBAR_WIDTH - PADDING * 2 - 16;
            drawFittedText(nvg, PADDING + 8, y + 17, 14, clusterDetail.resource(), TEXT_COLOR, detailWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            drawFittedText(nvg, PADDING + 8, y + 36, 12, clusterDetail.nodeCount() + " nodes | score " + clusterDetail.score() + "%", SUBTEXT_COLOR, detailWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            drawFittedText(nvg, PADDING + 8, y + 55, 12, Math.round(clusterDetail.averageSpacing()) + "m spacing", SUBTEXT_COLOR, detailWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            drawFittedText(nvg, PADDING + 8, y + 74, 12, clusterDetail.profession().name(), clusterDetail.profession().color(), detailWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            drawFittedText(nvg, PADDING + 8, y + 93, 12, clusterCoords(clusterDetail), SUBTEXT_COLOR, detailWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            y += CLUSTER_DETAIL_HEIGHT + 14;
        } else if (detail != null) {
            NVGWrapper.drawRect(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, NODE_DETAIL_HEIGHT, new Color(28, 28, 38, 210));
            NVGWrapper.drawRectOutline(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, NODE_DETAIL_HEIGHT, 1, BORDER_COLOR);
            float detailWidth = SIDEBAR_WIDTH - PADDING * 2 - 16;
            drawFittedText(nvg, PADDING + 8, y + 17, 14, detail.resource(), TEXT_COLOR, detailWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            drawFittedText(nvg, PADDING + 8, y + 38, 12, nodeCoords(detail), SUBTEXT_COLOR, detailWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            y += NODE_DETAIL_HEIGHT + 14;
        }

        if (showClusters && !cachedClusters.isEmpty()) {
            drawText(nvg, PADDING, y, 12, "Top Clusters", SUBTEXT_COLOR, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            y += 12;
            for (int index = 0; index < Math.min(SIDEBAR_CLUSTER_LIMIT, cachedClusters.size()); index++) {
                GatheringNodeCluster cluster = cachedClusters.get(index);
                boolean active = cluster == selectedCluster;
                NVGWrapper.drawRect(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, 34, active ? CONTROL_ACTIVE : CONTROL_COLOR);
                NVGWrapper.drawRectOutline(nvg, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, 34, 1, BORDER_COLOR);
                float rowTextWidth = SIDEBAR_WIDTH - PADDING * 2 - 16;
                drawFittedText(nvg, PADDING + 8, y + 11, 11, "#" + (index + 1) + " " + cluster.resource() + " | score " + cluster.score() + "%", TEXT_COLOR, rowTextWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
                drawFittedText(nvg, PADDING + 8, y + 26, 10, cluster.nodeCount() + " nodes | " + Math.round(cluster.averageSpacing()) + "m spacing", SUBTEXT_COLOR, rowTextWidth, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
                y += 40;
            }
        }
        if (resourceDropdownOpen) {
            renderResourceDropdown(nvg, resourceInputY + INPUT_HEIGHT);
        }
        sidebarContentHeight = y + sidebarScroll + PADDING;
        sidebarScroll = clampSidebarScroll(sidebarScroll, screenHeight);
        nvgResetScissor(nvg);
        renderSidebarScrollbar(nvg, screenHeight);
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

    private void renderSidebarScrollbar(long nvg, float screenHeight) {
        float viewportHeight = Math.max(0, screenHeight - SIDEBAR_HEADER_HEIGHT);
        float maxScroll = sidebarMaxScroll(screenHeight);
        if (maxScroll <= 0 || viewportHeight <= 0) {
            return;
        }
        float trackX = SIDEBAR_WIDTH - 5;
        float trackY = SIDEBAR_HEADER_HEIGHT + 4;
        float trackHeight = viewportHeight - 8;
        float thumbHeight = Math.max(24, trackHeight * (viewportHeight / sidebarContentHeight));
        float thumbY = trackY + (trackHeight - thumbHeight) * (sidebarScroll / maxScroll);
        NVGWrapper.drawRect(nvg, trackX, trackY, 3, trackHeight, new Color(255, 255, 255, 28));
        NVGWrapper.drawRect(nvg, trackX, thumbY, 3, thumbHeight, new Color(255, 255, 255, 110));
    }

    private String centerPlayerButtonLabel() {
        return System.currentTimeMillis() < centerPlayerWarningUntilMs ? "Leave housing bum !" : "Center Player";
    }

    private boolean copyHoveredCoordinates(float mx, float my, float sidebarMy, float screenWidth, float screenHeight) {
        GatheringNodeCluster clusterDetail = selectedCluster != null ? selectedCluster : hoveredCluster;
        GatheringNode detail = selectedNode != null ? selectedNode : hoveredNode;
        float detailY = sidebarDetailY();
        if (clusterDetail != null && isHovered(mx, sidebarMy, PADDING, detailY, SIDEBAR_WIDTH - PADDING * 2, CLUSTER_DETAIL_HEIGHT)) {
            copyToClipboard(clusterCoords(clusterDetail));
            return true;
        }
        if (clusterDetail == null && detail != null && isHovered(mx, sidebarMy, PADDING, detailY, SIDEBAR_WIDTH - PADDING * 2, NODE_DETAIL_HEIGHT)) {
            copyToClipboard(nodeCoords(detail));
            return true;
        }

        MapViewport viewport = new MapViewport(centerX, centerZ, pixelsPerBlock, SIDEBAR_WIDTH, 0, screenWidth - SIDEBAR_WIDTH, screenHeight);
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
        float x = Math.min(nvgMouseX + 12, SeqClient.mc.getWindow().getWidth() / 2f - 190);
        float y = Math.max(8, nvgMouseY + 12);
        NVGWrapper.drawRect(nvg, x, y, 180, 42, new Color(18, 18, 24, 235));
        NVGWrapper.drawRectOutline(nvg, x, y, 180, 42, 1, BORDER_COLOR);
        drawFittedText(nvg, x + 8, y + 15, 12, title, TEXT_COLOR, 164, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 31, 11, subtitle, SUBTEXT_COLOR, 164, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    private void renderClusterTooltip(long nvg, GatheringNodeCluster cluster) {
        String title = cluster.resource() + " | score " + cluster.score() + "%";
        String subtitle = cluster.nodeCount() + " nodes | " + Math.round(cluster.averageSpacing()) + "m";
        float x = Math.min(nvgMouseX + 12, SeqClient.mc.getWindow().getWidth() / 2f - 210);
        float y = Math.max(8, nvgMouseY + 12);
        NVGWrapper.drawRect(nvg, x, y, 200, 42, new Color(18, 18, 24, 235));
        NVGWrapper.drawRectOutline(nvg, x, y, 200, 42, 1, BORDER_COLOR);
        drawFittedText(nvg, x + 8, y + 15, 12, title, TEXT_COLOR, 184, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        drawFittedText(nvg, x + 8, y + 31, 11, subtitle, SUBTEXT_COLOR, 184, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
    }

    private void refreshClusterAnalysisIfNeeded() {
        List<GatheringNode> sourceNodes = nodeService.nodes();
        long settingsVersion = mapSettings.version();
        String key = clusterKey();
        if (sourceNodes == cachedSourceNodes && settingsVersion == cachedSettingsVersion && key.equals(cachedClusterKey)) {
            return;
        }
        cachedSourceNodes = sourceNodes;
        cachedSettingsVersion = settingsVersion;
        cachedClusterKey = key;
        GatheringClusterCache.Result result = clusterCache.getOrCompute(
                sourceNodes,
                selectedResourceFilters,
                professionToggles,
                clusterScoreMode,
                mapSettings.clusterEps(),
                mapSettings.clusterMinSamples());
        cachedFilteredNodes = result.filteredNodes();
        cachedResourceOptions = result.resourceOptions();
        cachedClusters = result.clusters();
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
        float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
        float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;

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
        if (isHovered(mx, sidebarMy, PADDING, 58, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            SeqClient.mc.setScreen(parent);
            return true;
        }
        if (isHovered(mx, sidebarMy, PADDING, 58 + BUTTON_HEIGHT + 8, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            if (!centerOnPlayer()) {
                centerPlayerWarningUntilMs = System.currentTimeMillis() + CENTER_PLAYER_WARNING_DURATION_MS;
            }
            return true;
        }
        float clustersButtonY = 58 + BUTTON_HEIGHT + 8 + BUTTON_HEIGHT + 18;
        if (isHovered(mx, sidebarMy, PADDING, clustersButtonY, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            showClusters = !showClusters;
            mapSettings.setShowClusters(showClusters);
            selectedCluster = null;
            selectedNode = null;
            return true;
        }
        float scoreButtonY = clustersButtonY + BUTTON_HEIGHT + 8;
        if (isHovered(mx, sidebarMy, PADDING, scoreButtonY, SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            clusterScoreMode = clusterScoreMode.next();
            mapSettings.setClusterScoreMode(clusterScoreMode);
            selectedCluster = null;
            cachedClusterKey = "";
            return true;
        }

        float inputY = scoreButtonY + BUTTON_HEIGHT + 18 + (showDebugInfo ? 36 : 0) + 12;
        if (resourceDropdownOpen) {
            List<String> resources = resourceDropdownOptions();
            int visibleRows = Math.min(RESOURCE_DROPDOWN_VISIBLE_ROWS, resources.size());
            float dropdownY = inputY - sidebarScroll + INPUT_HEIGHT;
            if (isHovered(mx, my, PADDING, dropdownY, SIDEBAR_WIDTH - PADDING * 2, visibleRows * RESOURCE_DROPDOWN_ROW_HEIGHT)) {
                int optionIndex = Math.min(visibleRows - 1, Math.max(0, (int) ((my - dropdownY) / RESOURCE_DROPDOWN_ROW_HEIGHT)));
                toggleResourceFilter(resources.get(resourceDropdownScroll + optionIndex), true);
                return true;
            }
        }
        if (isHovered(mx, sidebarMy, PADDING, inputY, SIDEBAR_WIDTH - PADDING * 2, INPUT_HEIGHT)) {
            boolean shouldOpen = !resourceDropdownOpen;
            resourceInputFocused = shouldOpen;
            resourceDropdownOpen = shouldOpen;
            resourceSearch = "";
            resourceDropdownScroll = 0;
            return true;
        }
        if (resourceDropdownOpen) {
            resourceDropdownOpen = false;
            resourceInputFocused = false;
            resourceSearch = "";
            return true;
        }

        float toggleY = inputY + INPUT_HEIGHT + 18 + 12;
        for (GatheringProfession profession : List.of(
                GatheringProfession.WOODCUTTING,
                GatheringProfession.MINING,
                GatheringProfession.FARMING,
                GatheringProfession.FISHING)) {
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

        float topClusterY = sidebarTopClusterY();
        for (int index = 0; index < Math.min(SIDEBAR_CLUSTER_LIMIT, cachedClusters.size()); index++) {
            if (isHovered(mx, sidebarMy, PADDING, topClusterY, SIDEBAR_WIDTH - PADDING * 2, 34)) {
                selectedCluster = cachedClusters.get(index);
                selectedNode = null;
                centerX = selectedCluster.centerX();
                centerZ = selectedCluster.centerZ();
                pixelsPerBlock = Math.max(pixelsPerBlock, 0.20);
                return true;
            }
            topClusterY += 40;
        }

        MapViewport viewport = new MapViewport(centerX, centerZ, pixelsPerBlock, SIDEBAR_WIDTH, 0, screenWidth - SIDEBAR_WIDTH, screenHeight);
        if (viewport.isInsideScreen(mx, my)) {
            selectedCluster = shouldRenderClusters() || hoveredNode == null ? hoveredCluster : selectedCluster;
            selectedNode = shouldRenderClusters() ? null : hoveredNode;
            if (selectedNode != null) {
                selectedCluster = null;
            }
            draggingMap = true;
            hoveredNode = null;
            hoveredCluster = null;
            resourceDropdownOpen = false;
            resourceInputFocused = false;
            resourceSearch = "";
            return true;
        }
        return super.mouseClicked(click, outsideScreen);
    }

    @Override
    public boolean mouseReleased(@NotNull MouseButtonEvent click) {
        draggingMap = false;
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (draggingMap) {
            double guiScale = SeqClient.mc.getWindow().getGuiScale();
            centerX -= (deltaX * guiScale / 2.0) / pixelsPerBlock;
            centerZ -= (deltaY * guiScale / 2.0) / pixelsPerBlock;
            hoveredNode = null;
            hoveredCluster = null;
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        float mx = scaledMouseX(mouseX);
        float my = scaledMouseY(mouseY);
        if (resourceDropdownOpen) {
            float inputY = 58 + BUTTON_HEIGHT + 8 + BUTTON_HEIGHT + 18 + BUTTON_HEIGHT + 8 + BUTTON_HEIGHT + 18 + 12;
            List<String> resources = resourceDropdownOptions();
            int visibleRows = Math.min(RESOURCE_DROPDOWN_VISIBLE_ROWS, resources.size());
            float dropdownY = inputY - sidebarScroll + INPUT_HEIGHT;
            if (isHovered(mx, my, PADDING, dropdownY, SIDEBAR_WIDTH - PADDING * 2, visibleRows * RESOURCE_DROPDOWN_ROW_HEIGHT)) {
                resourceDropdownScroll = clampResourceDropdownScroll(resourceDropdownScroll + (scrollY > 0 ? -1 : 1), resources.size());
                return true;
            }
        }
        float screenWidth = SeqClient.mc.getWindow().getWidth() / 2f;
        float screenHeight = SeqClient.mc.getWindow().getHeight() / 2f;
        if (mx >= 0 && mx <= SIDEBAR_WIDTH && my >= SIDEBAR_HEADER_HEIGHT && my <= screenHeight) {
            sidebarScroll = clampSidebarScroll(sidebarScroll - (float) scrollY * SIDEBAR_SCROLL_STEP, screenHeight);
            return true;
        }
        MapViewport viewport = new MapViewport(centerX, centerZ, pixelsPerBlock, SIDEBAR_WIDTH, 0, screenWidth - SIDEBAR_WIDTH, screenHeight);
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
            Character typedCharacter = resourceSearchCharacter(keyEvent);
            if (typedCharacter != null) {
                resourceSearch += typedCharacter;
                resourceDropdownOpen = true;
                resourceDropdownScroll = 0;
                return true;
            }
            return true;
        }
        if (resourceDropdownOpen && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            resourceDropdownOpen = false;
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

    private static float scaledMouseX(double rawX) {
        return (float) (rawX * SeqClient.mc.getWindow().getGuiScale() / 2.0);
    }

    private static float scaledMouseY(double rawY) {
        return (float) (rawY * SeqClient.mc.getWindow().getGuiScale() / 2.0);
    }

    private static boolean isHovered(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static Character resourceSearchCharacter(KeyEvent keyEvent) {
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
        return Math.max(0, Math.min(scroll, Math.max(0, optionCount - RESOURCE_DROPDOWN_VISIBLE_ROWS)));
    }

    private float clampSidebarScroll(float scroll, float screenHeight) {
        return (float) clamp(scroll, 0, sidebarMaxScroll(screenHeight));
    }

    private float sidebarMaxScroll(float screenHeight) {
        float viewportHeight = Math.max(0, screenHeight - SIDEBAR_HEADER_HEIGHT);
        return Math.max(0, sidebarContentHeight - SIDEBAR_HEADER_HEIGHT - viewportHeight);
    }

    private float sidebarTopClusterY() {
        float y = sidebarDetailY();
        if (selectedCluster != null || hoveredCluster != null) {
            y += CLUSTER_DETAIL_HEIGHT + 14;
        } else if (selectedNode != null || hoveredNode != null) {
            y += NODE_DETAIL_HEIGHT + 14;
        }
        return y + 12;
    }

    private float sidebarDetailY() {
        float y = 58;
        y += BUTTON_HEIGHT + 8;
        y += BUTTON_HEIGHT + 18;
        y += BUTTON_HEIGHT + 8;
        y += BUTTON_HEIGHT + 18;
        if (showDebugInfo) {
            y += 36;
        }
        y += 12;
        y += INPUT_HEIGHT + 18;
        y += 12;
        y += (TOGGLE_HEIGHT + 6) * 4;
        y += 12;
        return y;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ScreenPoint(float x, float y) {}

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
