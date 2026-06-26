package com.seqwawa.seq.map;

public final class MapCalibration {
    public static final double MIN_WORLD_X = -2559.992015616445;
    public static final double MAX_WORLD_X = 2048.0018901611634;
    public static final double MIN_WORLD_Z = -6634.997935164797;
    public static final double MAX_WORLD_Z = 8.989267839958302;
    public static final int MAP_IMAGE_WIDTH = 1536;
    public static final int MAP_IMAGE_HEIGHT = 2215;
    public static final int ORIGINAL_IMAGE_WIDTH = 4608;
    public static final int ORIGINAL_IMAGE_HEIGHT = 6644;

    private MapCalibration() {}

    public static MapBounds fullBounds() {
        return new MapBounds(MIN_WORLD_X, MIN_WORLD_Z, MAX_WORLD_X, MAX_WORLD_Z);
    }

    public static double worldToImageX(double worldX, int imageWidth) {
        double ratio = (worldX - MIN_WORLD_X) / (MAX_WORLD_X - MIN_WORLD_X);
        return ratio * imageWidth;
    }

    public static double worldToImageZ(double worldZ, int imageHeight) {
        double ratio = (worldZ - MIN_WORLD_Z) / (MAX_WORLD_Z - MIN_WORLD_Z);
        return ratio * imageHeight;
    }
}
