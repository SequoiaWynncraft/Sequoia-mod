package com.seqwawa.seq.map;

import java.util.List;

/**
 * Temporary markers supplied by the screen that opened the world map.
 * They are deliberately not persisted as part of the user's map settings.
 */
public record MapFocus(String title, List<Marker> markers, String selectedMarkerId) {
    public MapFocus {
        title = title == null ? "" : title;
        markers = markers == null ? List.of() : List.copyOf(markers);
    }

    public Marker selectedMarker() {
        if (selectedMarkerId == null) {
            return null;
        }
        return markers.stream()
                .filter(marker -> selectedMarkerId.equals(marker.id()))
                .findFirst()
                .orElse(null);
    }

    public MapBounds bounds() {
        if (markers.isEmpty()) {
            return MapCalibration.fullBounds();
        }
        double minX = Double.POSITIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;
        for (Marker marker : markers) {
            double radius = Math.max(0, marker.radius());
            minX = Math.min(minX, marker.x() - radius);
            minZ = Math.min(minZ, marker.z() - radius);
            maxX = Math.max(maxX, marker.x() + radius);
            maxZ = Math.max(maxZ, marker.z() + radius);
        }
        return new MapBounds(minX, minZ, maxX, maxZ);
    }

    public record Marker(
            String id,
            String label,
            String source,
            double x,
            double y,
            double z,
            double radius) {
        public Marker {
            id = id == null ? "" : id;
            label = label == null ? "" : label;
            source = source == null ? "" : source;
            radius = Math.max(0, radius);
        }

        public String coordinates() {
            return formatCoordinate(x) + " " + formatCoordinate(y) + " " + formatCoordinate(z);
        }

        private static String formatCoordinate(double value) {
            if (value == Math.rint(value)) {
                return Long.toString(Math.round(value));
            }
            return Double.toString(value);
        }
    }
}
