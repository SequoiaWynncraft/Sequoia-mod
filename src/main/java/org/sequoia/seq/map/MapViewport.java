package org.sequoia.seq.map;

public record MapViewport(
        double centerX,
        double centerZ,
        double pixelsPerBlock,
        float screenX,
        float screenY,
        float screenWidth,
        float screenHeight) {
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
}
