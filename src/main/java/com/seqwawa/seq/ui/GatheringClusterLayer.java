package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WorldMapUi.*;

import java.awt.Color;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.GatheringNodeCluster;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapViewport;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;

/** Cluster hull geometry cache, marker drawing and matching hover geometry. */
final class GatheringClusterLayer {

    private static final float MIN_HULL_PADDING_PX = 4f;
    private static final float MAX_HULL_PADDING_PX = 12f;
    private static final int HULL_SMOOTHING_PASSES = 2;
    private static final double MIN_PIXELS_PER_BLOCK = MapViewport.MIN_PIXELS_PER_BLOCK;
    private static final double MAX_PIXELS_PER_BLOCK = MapViewport.MAX_PIXELS_PER_BLOCK;

    private static final double NODE_DETAIL_PIXELS_PER_BLOCK = 0.42;

    private final Map<GatheringNodeCluster, ClusterOutlineShape> clusterOutlineShapes = new IdentityHashMap<>();
    private double clusterOutlineScale = Double.NaN;

    void clear() { clusterOutlineShapes.clear(); clusterOutlineScale = Double.NaN; }
    GatheringNodeCluster renderHulls(UiCanvas canvas, WorldMapFrame frame, List<GatheringNodeCluster> cachedClusters, GatheringNodeCluster selectedCluster, boolean allowHover) {
        MapViewport viewport = frame.viewport();
        GatheringNodeCluster hoveredCluster = null;
        float bestHoverDistance = 18f;
        List<ClusterOutlineRender> highlightedOutlines = new ArrayList<>();
        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (int index = cachedClusters.size() - 1; index >= 0; index--) {
            GatheringNodeCluster cluster = cachedClusters.get(index);
            float x = viewport.worldToScreenX(cluster.centerX());
            float y = viewport.worldToScreenZ(cluster.centerZ());
            ClusterOutlineShape outline = clusterOutlineShape(cluster, viewport.pixelsPerBlock());
            if (outline.points().isEmpty() || !outline.isVisible(viewport, x, y)) {
                continue;
            }
            float radius = clusterRadius(cluster);
            float distance = allowHover ? (float) markerDistance(frame.mouseX() - x, frame.mouseY() - y) : Float.MAX_VALUE;
            boolean hovered = allowHover
                    && (distance <= Math.max(12, radius + 3)
                            || isPointInsideCluster(outline, x, y, frame.mouseX(), frame.mouseY()));
            if (hovered && distance < bestHoverDistance) {
                bestHoverDistance = distance;
                hoveredCluster = cluster;
            }
            boolean selected = cluster == selectedCluster;
            if (selected || hovered) {
                highlightedOutlines.add(new ClusterOutlineRender(cluster, outline, x, y, selected));
            } else {
                renderClusterOutline(canvas, viewport, cluster, outline, x, y, false, false);
            }
        }
        for (ClusterOutlineRender render : highlightedOutlines) {
            renderClusterOutline(
                    canvas,
                    viewport,
                    render.cluster(),
                    render.outline(),
                    render.centerScreenX(),
                    render.centerScreenY(),
                    render.selected(),
                    true);
        }
        canvas.resetScissor();
            return hoveredCluster;
    }

    void renderBadges(UiCanvas canvas, MapViewport viewport, List<GatheringNodeCluster> cachedClusters, GatheringNodeCluster selectedCluster, GatheringNodeCluster hoveredCluster, boolean overviewMode) {
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

    private static Color clusterHullColor(Color source) {
        return new Color(source.getRed(), source.getGreen(), source.getBlue(), Math.round(255 * 0.35f));
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
        Color color = clusterHullColor(selected ? color(MAP_SELECTED_CLUSTER) : cluster.profession().color());
        List<UiCanvas.Point> points = outline.points().stream()
                .map(point -> new UiCanvas.Point(centerScreenX + point.x(), centerScreenY + point.y()))
                .toList();
        boolean closed = points.size() > 2;
        canvas.fillAndStrokePolygon(
                points,
                closed ? color : null,
                color,
                hullStrokeWidthForZoom(viewport.pixelsPerBlock(), highlighted),
                closed);
    }

    private void drawClusterMarker(UiCanvas canvas, float x, float y, float radius, GatheringNodeCluster cluster, boolean selected, boolean highlighted) {
        Color color = selected ? color(MAP_SELECTED_CLUSTER) : cluster.profession().color();
        drawSquareMarker(canvas, x, y, radius, color);
        Color border = color(BACKGROUND_MODAL_OVERLAY);
        // A thin, softer border; the badge fill keeps its normal theme opacity.
        border = new Color(border.getRed(), border.getGreen(), border.getBlue(), Math.round(255 * 0.35f));
        drawSquareMarkerOutline(canvas, x, y, radius + 0.5f, 1, border);

        String count = String.valueOf(cluster.nodeCount());
        String font = SeqClient.getFontManager().getSelectedFont();
        float size = clusterCountTextSize(cluster);
        var bounds = UiRenderer.measureText(count, font, size);
        float availableSize = radius * 2 - 2;
        float textSize = Math.max(bounds.width(), bounds.height());
        if (textSize > availableSize) {
            size *= availableSize / textSize;
            bounds = UiRenderer.measureText(count, font, size);
        }
        canvas.drawText(count, x - (bounds.minX() + bounds.maxX()) / 2f,
                y - (bounds.minY() + bounds.maxY()) / 2f, new UiCanvas.TextStyle(
                        font, size, color(MAP_TEXT), UiCanvas.HorizontalAlign.LEFT, UiCanvas.VerticalAlign.BASELINE));
    }

    private static float clusterRadius(GatheringNodeCluster cluster) {
        return (float) (Math.max(6, Math.min(13, 5 + Math.sqrt(cluster.nodeCount()) * 1.5)) * 0.8);
    }

    private static float clusterCountTextSize(GatheringNodeCluster cluster) {
        return (float) Math.max(8.0, Math.min(10.5, 7.4 + Math.sqrt(cluster.nodeCount()) * 0.42));
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
        List<UiCanvas.Point> points = cluster.outline().stream()
                .map(point -> new UiCanvas.Point(
                        (float) ((point.x() - cluster.centerX()) * scale),
                        (float) ((point.z() - cluster.centerZ()) * scale)))
                .toList();
        if (points.size() < 3) {
            return ClusterOutlineShape.from(points);
        }

        List<UiCanvas.Point> displayPoints = expandFromCentroid(points, hullPaddingForZoom(scale));
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

    private static List<UiCanvas.Point> expandFromCentroid(List<UiCanvas.Point> points, float padding) {
        float centerX = 0;
        float centerY = 0;
        for (UiCanvas.Point point : points) {
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
                    return new UiCanvas.Point(finalCenterX + dx * scale, finalCenterY + dy * scale);
                })
                .toList();
    }

    private static List<UiCanvas.Point> chaikinClosedPass(List<UiCanvas.Point> points) {
        java.util.ArrayList<UiCanvas.Point> smoothed = new java.util.ArrayList<>(points.size() * 2);
        for (int index = 0; index < points.size(); index++) {
            UiCanvas.Point point = points.get(index);
            UiCanvas.Point next = points.get((index + 1) % points.size());
            smoothed.add(new UiCanvas.Point(
                    point.x() * 0.75f + next.x() * 0.25f,
                    point.y() * 0.75f + next.y() * 0.25f));
            smoothed.add(new UiCanvas.Point(
                    point.x() * 0.25f + next.x() * 0.75f,
                    point.y() * 0.25f + next.y() * 0.75f));
        }
        return smoothed;
    }

    private record ClusterOutlineRender(
            GatheringNodeCluster cluster,
            ClusterOutlineShape outline,
            float centerScreenX,
            float centerScreenY,
            boolean selected) {}

    private record ClusterOutlineShape(
            List<UiCanvas.Point> points,
            float minX,
            float maxX,
            float minY,
            float maxY) {
        private static ClusterOutlineShape from(List<UiCanvas.Point> points) {
            if (points.isEmpty()) {
                return new ClusterOutlineShape(List.of(), 0, 0, 0, 0);
            }
            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            for (UiCanvas.Point point : points) {
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
    private static double markerDistance(double x, double y) { return Math.max(Math.abs(x), Math.abs(y)); }

}
