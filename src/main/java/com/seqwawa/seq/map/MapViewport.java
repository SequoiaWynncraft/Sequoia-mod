package com.seqwawa.seq.map;

public record MapViewport(
        double centerX,
        double centerZ,
        double pixelsPerBlock,
        float screenX,
        float screenY,
        float screenWidth,
        float screenHeight) {
    public static final double MIN_PIXELS_PER_BLOCK = 0.035;
    public static final double MAX_PIXELS_PER_BLOCK = 2.5;
    public static final double FULL_MAP_FIT_SCALE = 0.92;
    public static final double SCROLL_ZOOM_FACTOR = 1.15;

    public double minWorldX() {
        return centerX - screenWidth / (2.0 * pixelsPerBlock);
    }

    public double maxWorldX() {
        return centerX + screenWidth / (2.0 * pixelsPerBlock);
    }

    public double minWorldZ() {
        return centerZ - screenHeight / (2.0 * pixelsPerBlock);
    }

    public double maxWorldZ() {
        return centerZ + screenHeight / (2.0 * pixelsPerBlock);
    }

    public MapBounds visibleBounds() {
        return new MapBounds(minWorldX(), minWorldZ(), maxWorldX(), maxWorldZ());
    }

    public float worldToScreenX(double worldX) {
        return (float) (screenX + screenWidth / 2.0 + (worldX - centerX) * pixelsPerBlock);
    }

    public float worldToScreenZ(double worldZ) {
        return (float) (screenY + screenHeight / 2.0 + (worldZ - centerZ) * pixelsPerBlock);
    }

    public double screenToWorldX(double x) {
        return centerX + (x - (screenX + screenWidth / 2.0)) / pixelsPerBlock;
    }

    public double screenToWorldZ(double y) {
        return centerZ + (y - (screenY + screenHeight / 2.0)) / pixelsPerBlock;
    }

    public boolean isInsideScreen(float x, float y) {
        return x >= screenX && x <= screenX + screenWidth && y >= screenY && y <= screenY + screenHeight;
    }

    public MapViewport panByScreenDelta(double deltaX, double deltaY) {
        if (pixelsPerBlock <= 0) return this;
        return new MapViewport(
                centerX - deltaX / pixelsPerBlock,
                centerZ - deltaY / pixelsPerBlock,
                pixelsPerBlock,
                screenX,
                screenY,
                screenWidth,
                screenHeight);
    }

    public MapViewport zoomAt(double pointerX, double pointerY, double factor) {
        if (pixelsPerBlock <= 0 || factor <= 0) return this;
        double anchorX = screenToWorldX(pointerX);
        double anchorZ = screenToWorldZ(pointerY);
        double zoom = clampPixelsPerBlock(pixelsPerBlock * factor);
        return new MapViewport(
                anchorX - (pointerX - (screenX + screenWidth / 2.0)) / zoom,
                anchorZ - (pointerY - (screenY + screenHeight / 2.0)) / zoom,
                zoom,
                screenX,
                screenY,
                screenWidth,
                screenHeight);
    }

    public static double fitPixelsPerBlock(
            MapBounds bounds, float availableWidth, float availableHeight, double fitScale) {
        double fitX = Math.max(1, availableWidth) / Math.max(1, bounds.maxX() - bounds.minX());
        double fitZ = Math.max(1, availableHeight) / Math.max(1, bounds.maxZ() - bounds.minZ());
        return clampPixelsPerBlock(Math.min(fitX, fitZ) * fitScale);
    }

    public static double clampPixelsPerBlock(double value) {
        return Math.max(MIN_PIXELS_PER_BLOCK, Math.min(MAX_PIXELS_PER_BLOCK, value));
    }
}
