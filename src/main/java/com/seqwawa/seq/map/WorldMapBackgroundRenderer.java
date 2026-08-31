package com.seqwawa.seq.map;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.GatheringMapImageService.TileKey;
import com.seqwawa.seq.map.GatheringMapImageService.TileSet;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiImage;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Shared high-resolution image and tile layer used by Sequoia map screens. */
public final class WorldMapBackgroundRenderer {
    public static final double MIN_PIXELS_PER_BLOCK = 0.035;
    public static final double MAX_PIXELS_PER_BLOCK = 2.5;
    public static final double FULL_MAP_FIT_SCALE = 0.92;

    private final GatheringMapImageService service;
    private final Map<TileKey, UiImage> tileImages = new HashMap<>();
    private UiImage mapImage;
    private boolean mapImageLoadAttempted;
    private long loadedMapImageVersion = -1;
    private String loadedTileVersion = "";
    private long loadedTileContentVersion = -1;
    private TileRange cachedVisibleTileRange;
    private TileRange cachedPrefetchTileRange;
    private List<TileKey> cachedVisibleTiles = List.of();
    private List<TileKey> cachedPrefetchTiles = List.of();
    private long lastTileRequestAtMs;

    public WorldMapBackgroundRenderer(GatheringMapImageService service) {
        this.service = service;
    }

    public void render(UiCanvas canvas, MapViewport viewport) {
        render(canvas, viewport, 1);
    }

    public void render(UiCanvas canvas, MapViewport viewport, float alpha) {
        alpha = Math.max(0, Math.min(1, alpha));
        UiImage image = mapImage();
        if (image != null) renderFullMapImage(canvas, viewport, image, alpha);
        renderMapTiles(canvas, viewport, alpha);
    }

    public void close() {
        if (mapImage != null) {
            UiRenderer.deleteImage(mapImage);
            mapImage = null;
        }
        clearTileImages();
    }

    private void renderFullMapImage(UiCanvas canvas, MapViewport viewport, UiImage image, float alpha) {
        float x = viewport.worldToScreenX(MapCalibration.MIN_WORLD_X);
        float y = viewport.worldToScreenZ(MapCalibration.MIN_WORLD_Z);
        float width = viewport.worldToScreenX(MapCalibration.MAX_WORLD_X) - x;
        float height = viewport.worldToScreenZ(MapCalibration.MAX_WORLD_Z) - y;
        if (width <= 0 || height <= 0) return;

        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        try {
            canvas.drawImage(image, x, y, width, height, alpha);
        } finally {
            canvas.resetScissor();
        }
    }

    private void renderMapTiles(UiCanvas canvas, MapViewport viewport, float alpha) {
        var manifest = service.manifest().orElse(null);
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
            service.requestTiles(cachedVisibleTiles, cachedPrefetchTiles);
            lastTileRequestAtMs = now;
        }

        long tileContentVersion = service.tileVersion();
        boolean loadMissingTileHandles = visibleRangeChanged || tileContentVersion != loadedTileContentVersion;
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        try {
            for (TileKey key : cachedVisibleTiles) {
                UiImage tileImage = tileImage(key, loadMissingTileHandles);
                if (tileImage != null) renderTile(canvas, viewport, tileSet, key, tileImage, alpha);
            }
        } finally {
            canvas.resetScissor();
        }
        loadedTileContentVersion = tileContentVersion;
    }

    private UiImage tileImage(TileKey key, boolean loadMissing) {
        UiImage existing = tileImages.get(key);
        if (existing != null || !loadMissing) return existing;
        byte[] bytes = service.cachedTileBytes(key);
        if (bytes == null || bytes.length == 0) return null;
        try {
            UiImage image = UiRenderer.createImage(ByteBuffer.wrap(bytes), true);
            if (image != null) tileImages.put(key, image);
            return image;
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn("[WorldMap] Could not load map tile {}.", key.id(), exception);
            return null;
        }
    }

    private static void renderTile(
            UiCanvas canvas, MapViewport viewport, TileSet tileSet, TileKey key, UiImage image, float alpha) {
        int pixelX0 = key.x() * tileSet.tileSize();
        int pixelY0 = key.y() * tileSet.tileSize();
        int pixelX1 = Math.min(tileSet.width(), pixelX0 + tileSet.tileSize());
        int pixelY1 = Math.min(tileSet.height(), pixelY0 + tileSet.tileSize());
        float x = viewport.worldToScreenX(imageToWorldX(pixelX0, tileSet.width()));
        float y = viewport.worldToScreenZ(imageToWorldZ(pixelY0, tileSet.height()));
        float width = viewport.worldToScreenX(imageToWorldX(pixelX1, tileSet.width())) - x;
        float height = viewport.worldToScreenZ(imageToWorldZ(pixelY1, tileSet.height())) - y;
        if (width > 0 && height > 0) canvas.drawImage(image, x, y, width, height, alpha);
    }

    private static TileRange visibleTileRange(MapViewport viewport, TileSet tileSet, int margin) {
        double minImageX = clampImageX(MapCalibration.worldToImageX(viewport.minWorldX(), tileSet.width()), tileSet);
        double maxImageX = clampImageX(MapCalibration.worldToImageX(viewport.maxWorldX(), tileSet.width()), tileSet);
        double minImageY = clampImageY(MapCalibration.worldToImageZ(viewport.minWorldZ(), tileSet.height()), tileSet);
        double maxImageY = clampImageY(MapCalibration.worldToImageZ(viewport.maxWorldZ(), tileSet.height()), tileSet);
        return new TileRange(
                clampTile((int) Math.floor(minImageX / tileSet.tileSize()) - margin, tileSet.columns()),
                clampTile((int) Math.floor(maxImageX / tileSet.tileSize()) + margin, tileSet.columns()),
                clampTile((int) Math.floor(minImageY / tileSet.tileSize()) - margin, tileSet.rows()),
                clampTile((int) Math.floor(maxImageY / tileSet.tileSize()) + margin, tileSet.rows()));
    }

    private static List<TileKey> tilesInRange(TileRange range) {
        List<TileKey> tiles = new ArrayList<>();
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) tiles.add(new TileKey(x, y));
        }
        return tiles;
    }

    private static double imageToWorldX(double imageX, int imageWidth) {
        return MapCalibration.MIN_WORLD_X
                + imageX / imageWidth * (MapCalibration.MAX_WORLD_X - MapCalibration.MIN_WORLD_X);
    }

    private static double imageToWorldZ(double imageY, int imageHeight) {
        return MapCalibration.MIN_WORLD_Z
                + imageY / imageHeight * (MapCalibration.MAX_WORLD_Z - MapCalibration.MIN_WORLD_Z);
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
        tileImages.values().forEach(UiRenderer::deleteImage);
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
        long version = service.version();
        if (mapImage != null && loadedMapImageVersion == version) return mapImage;
        if (mapImage != null) {
            UiRenderer.deleteImage(mapImage);
            mapImage = null;
        }
        if (mapImageLoadAttempted && loadedMapImageVersion == version) return null;
        mapImageLoadAttempted = true;
        loadedMapImageVersion = version;

        try {
            byte[] bytes = service.imageBytes();
            if (bytes.length > 0) mapImage = UiRenderer.createImage(ByteBuffer.wrap(bytes), true);
        } catch (RuntimeException exception) {
            SeqClient.LOGGER.warn(
                    "[WorldMap] Could not load {} map image.",
                    service.imageSource().name().toLowerCase(Locale.ROOT),
                    exception);
        }
        return mapImage;
    }

    private record TileRange(int minX, int maxX, int minY, int maxY) {}
}
