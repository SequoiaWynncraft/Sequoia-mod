package com.seqwawa.seq.ui;

import com.seqwawa.seq.map.MapCalibration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

final class WorldMapGeometry {
    private WorldMapGeometry() {}

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    static int clampIndex(int value, int count) {
        return Math.max(0, Math.min(count - 1, value));
    }

    static int clampScroll(int scroll, int optionCount, int visibleRows) {
        return Math.max(0, Math.min(scroll, Math.max(0, optionCount - visibleRows)));
    }

    static boolean contains(float pointX, float pointY, float x, float y, float width, float height) {
        return pointX >= x && pointX <= x + width && pointY >= y && pointY <= y + height;
    }

    static double distanceToSegment(
            double pointX, double pointY, double startX, double startY, double endX, double endY) {
        double dx = endX - startX;
        double dy = endY - startY;
        if (dx == 0 && dy == 0) {
            return Math.hypot(pointX - startX, pointY - startY);
        }
        double t = clamp(((pointX - startX) * dx + (pointY - startY) * dy) / (dx * dx + dy * dy), 0, 1);
        return Math.hypot(pointX - (startX + t * dx), pointY - (startY + t * dy));
    }

    static double imageToWorldX(double imageX, int imageWidth) {
        return MapCalibration.MIN_WORLD_X
                + (imageX / imageWidth) * (MapCalibration.MAX_WORLD_X - MapCalibration.MIN_WORLD_X);
    }

    static double imageToWorldZ(double imageY, int imageHeight) {
        return MapCalibration.MIN_WORLD_Z
                + (imageY / imageHeight) * (MapCalibration.MAX_WORLD_Z - MapCalibration.MIN_WORLD_Z);
    }

    static List<String> wrapText(String text, double maxWidth, ToDoubleFunction<String> widthMeasurer) {
        if (text == null || text.isBlank() || maxWidth <= 0) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.strip().split("\\R")) {
            String currentLine = "";
            for (String word : paragraph.trim().split("\\s+")) {
                String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
                if (currentLine.isEmpty() || widthMeasurer.applyAsDouble(candidate) <= maxWidth) {
                    currentLine = candidate;
                } else {
                    lines.add(currentLine);
                    currentLine = word;
                }
            }
            if (!currentLine.isEmpty()) {
                lines.add(currentLine);
            }
        }
        return List.copyOf(lines);
    }

    static String fitText(String text, double maxWidth, ToDoubleFunction<String> widthMeasurer) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (widthMeasurer.applyAsDouble(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        if (widthMeasurer.applyAsDouble(ellipsis) > maxWidth) {
            return "";
        }
        int low = 0;
        int high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = text.substring(0, mid).stripTrailing() + ellipsis;
            if (widthMeasurer.applyAsDouble(candidate) <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low).stripTrailing() + ellipsis;
    }
}
