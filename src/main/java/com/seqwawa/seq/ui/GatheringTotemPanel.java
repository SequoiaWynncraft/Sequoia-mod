package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import static com.seqwawa.seq.ui.WorldMapUi.*;
import com.seqwawa.seq.map.GatheringTotemSearchTarget;
import com.seqwawa.seq.map.GatheringTotemSolver;
import com.seqwawa.seq.map.GatheringTotemSolver.Placement;
import com.seqwawa.seq.map.WorldMapSettings;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;
import java.util.List;

/** Gathering optimization controls and their per-screen state. */
final class GatheringTotemPanel {
    private static final float BUTTON_HEIGHT = 24;
    private static final float TOGGLE_HEIGHT = 22;
    private static final float TOTEM_LAYER_GAP = 4;
    private static final float SPLIT_CONTROL_GAP = 4;
    private static final float PANEL_HEADER_HEIGHT = 28;
    private static final int TOTEM_RESULT_VISIBLE_ROWS = GatheringTotemSession.VISIBLE_ROWS;
    private static final float TOTEM_RESULT_ROW_HEIGHT = 28;
    private static final List<String> TOTEM_LAYER_LABELS = List.of(
            "Placement areas", "Player range (50)", "Node reach (52)", "Covered nodes", "Other placements");
    private final WorldMapSettings mapSettings;
    private final GatheringTotemSession session;
    private boolean gatheringTotemSolverEnabled;
    private GatheringTotemSearchTarget gatheringTotemSearchTarget;
    private boolean showGatheringTotemHulls;
    private boolean showGatheringTotemPlayerRadius;
    private boolean showGatheringTotemNodeReach;
    private boolean showGatheringTotemCoveredNodes;
    private boolean showOtherOptimalGatheringTotems;

    GatheringTotemPanel(WorldMapSettings settings, GatheringTotemSession session) {
        this.mapSettings = settings;
        this.session = session;
        gatheringTotemSolverEnabled = settings.gatheringTotemSolverEnabled();
        gatheringTotemSearchTarget = settings.gatheringTotemSearchTarget();
        showGatheringTotemHulls = settings.showGatheringTotemHulls();
        showGatheringTotemPlayerRadius = settings.showGatheringTotemPlayerRadius();
        showGatheringTotemNodeReach = settings.showGatheringTotemNodeReach();
        showGatheringTotemCoveredNodes = settings.showGatheringTotemCoveredNodes();
        showOtherOptimalGatheringTotems = settings.showOtherOptimalGatheringTotems();
    }

    boolean enabled() { return gatheringTotemSolverEnabled; }
    GatheringTotemSearchTarget target() { return gatheringTotemSearchTarget; }
    boolean layerEnabled(int index) { return gatheringTotemLayerEnabled(index); }
    List<Placement> placements() { return session.placements(); }
    Placement selected() { return session.selected(); }
    boolean pending() { return session.pending(); }
    void select(Placement placement) { session.select(placement); }
    void reset() { session.reset(); }
    void setEnabled(boolean enabled) {
        gatheringTotemSolverEnabled = enabled;
        mapSettings.setGatheringTotemSolverEnabled(enabled);
        if (!enabled) reset();
    }
    void refresh(GatheringTotemSession.Request request, GatheringTotemSolver.Position player) {
        if (enabled()) session.refresh(request, player);
        else reset();
    }
    void refreshNow(GatheringTotemSession.Request request, GatheringTotemSolver.Position player) {
        if (enabled()) session.refreshNow(request, player);
    }

    boolean click(Layout layout, float mouseX, float contentMouseY, Runnable refresh,
            Runnable center, java.util.function.Consumer<Placement> copy) {
        if (layout.targetY() < 0) return false;
        if (layout.target().contains(mouseX, contentMouseY)) {
            for (int index = 0; index < GatheringTotemSearchTarget.values().length; index++) {
                if (layout.targetSegment(index).contains(mouseX, contentMouseY)) {
                    gatheringTotemSearchTarget = GatheringTotemSearchTarget.values()[index];
                    break;
                }
            }
            mapSettings.setGatheringTotemSearchTarget(gatheringTotemSearchTarget);
            reset();
            return true;
        }
        if (layout.refresh().contains(mouseX, contentMouseY)) { refresh.run(); return true; }
        for (int index = 0; index < TOTEM_LAYER_LABELS.size(); index++) {
            if (layout.layer(index).contains(mouseX, contentMouseY)) {
                toggleGatheringTotemLayer(index);
                return true;
            }
        }
        for (int row = 0; row < TOTEM_RESULT_VISIBLE_ROWS; row++) {
            if (layout.result(row).contains(mouseX, contentMouseY)) {
                int index = session.scroll() + row;
                if (index < session.placements().size()) session.select(session.placements().get(index));
                return true;
            }
        }
        if (layout.center().contains(mouseX, contentMouseY)) { center.run(); return true; }
        if (layout.copy().contains(mouseX, contentMouseY)) {
            if (session.selected() != null) copy.accept(session.selected());
            return true;
        }
        return false;
    }

    boolean scroll(Layout layout, float mouseX, float contentMouseY, int delta) {
        if (layout.resultsStartY() >= 0 && layout.results().contains(mouseX, contentMouseY)) {
            session.scroll(delta);
            return true;
        }
        return false;
    }

    private static String totemCoords(Placement placement) {
        return Math.round(placement.x()) + " " + Math.round(placement.z());
    }

    private static void drawButton(UiCanvas canvas, float mouseX, float mouseY,
            UiBounds bounds, String label, boolean active) {
        float x = bounds.x(), y = bounds.y(), width = bounds.width(), height = bounds.height();
        canvas.fillRect(x, y, width, height, color(active ? MAP_CONTROL_ACTIVE
                : bounds.contains(mouseX, mouseY) ? MAP_CONTROL_HOVER : MAP_CONTROL));
        canvas.strokeRect(x, y, width, height, 1, color(MAP_BORDER));
        drawText(canvas, x + width / 2, y + height / 2, 12, label, color(MAP_TEXT), TextAlignment.CENTER);
    }

    void render(UiCanvas canvas, Layout layout, float nvgMouseX, float nvgMouseY,
            String scopeSummary, String resourceSummary, boolean hasSelectedCluster) {
        drawGatheringTotemTargetControl(canvas, nvgMouseX, nvgMouseY, layout);
        drawFittedText(
                canvas,
                PADDING,
                layout.filterSummaryY(),
                10,
                "Scope: " + scopeSummary,
                color(MAP_SUBTEXT),
                SIDEBAR_WIDTH - PADDING * 2,
                TextAlignment.LEFT);
        drawFittedText(
                canvas,
                PADDING,
                layout.filterSummaryY() + 14,
                10,
                "Resources: " + resourceSummary,
                color(MAP_SUBTEXT),
                SIDEBAR_WIDTH - PADDING * 2,
                TextAlignment.LEFT);
        drawButton(
                canvas, nvgMouseX, nvgMouseY,
                layout.refresh(),
                !session.pending() ? "Refresh" : "Optimizing...",
                session.pending());

        drawText(
                canvas,
                PADDING,
                layout.layerLabelY(),
                11,
                "Display Layers",
                color(MAP_SUBTEXT),
                TextAlignment.LEFT);
        renderGatheringTotemLayerButtons(canvas, nvgMouseX, nvgMouseY, layout);
        renderGatheringTotemLegend(canvas, layout.legendY());

        drawText(
                canvas,
                PADDING,
                layout.resultsLabelY(),
                11,
                status(hasSelectedCluster),
                color(MAP_SUBTEXT),
                TextAlignment.LEFT);
        renderGatheringTotemResults(canvas, layout, hasSelectedCluster);

        drawButton(
                canvas, nvgMouseX, nvgMouseY,
                layout.center(),
                "Center",
                false);
        drawButton(
                canvas, nvgMouseX, nvgMouseY,
                layout.copy(),
                "Copy X Z",
                false);
    }

    private void drawGatheringTotemTargetControl(UiCanvas canvas, float nvgMouseX, float nvgMouseY, Layout layout) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / GatheringTotemSearchTarget.values().length;
        for (int index = 0; index < GatheringTotemSearchTarget.values().length; index++) {
            GatheringTotemSearchTarget target = GatheringTotemSearchTarget.values()[index];
            UiBounds segment = layout.targetSegment(index);
            float x = segment.x(), y = segment.y();
            boolean active = gatheringTotemSearchTarget == target;
            boolean hovered = segment.contains(nvgMouseX, nvgMouseY);
            Color background = active
                    ? color(MAP_CONTROL_ACTIVE)
                    : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL);
            segment.fill(canvas, background);
            segment.stroke(canvas, 1, color(MAP_BORDER));
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

    private void renderGatheringTotemLayerButtons(UiCanvas canvas, float nvgMouseX, float nvgMouseY, Layout layout) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        for (int index = 0; index < TOTEM_LAYER_LABELS.size(); index++) {
            UiBounds layer = layout.layer(index);
            float y = layer.y();
            boolean enabled = gatheringTotemLayerEnabled(index);
            boolean hovered = layer.contains(nvgMouseX, nvgMouseY);
            layer.fill(canvas,
                    hovered ? color(MAP_CONTROL_HOVER) : enabled ? color(MAP_CONTROL_ACTIVE) : color(MAP_CONTROL));
            layer.stroke(canvas, 1, color(MAP_BORDER));
            drawFittedText(canvas, PADDING + 8, y + TOGGLE_HEIGHT / 2, 11, TOTEM_LAYER_LABELS.get(index),
                    color(MAP_TEXT), width - 42, TextAlignment.LEFT);
            drawText(canvas, PADDING + width - 8, y + TOGGLE_HEIGHT / 2, 10,
                    enabled ? "On" : "Off", color(MAP_SUBTEXT), TextAlignment.RIGHT);
        }
    }

    private void renderGatheringTotemLegend(UiCanvas canvas, float y) {
        drawSquareMarker(canvas, PADDING + 5, y, 3.5f, color(MAP_TOTEM));
        drawText(canvas, PADDING + 14, y, 9, "filled area = valid positions", color(MAP_SUBTEXT), TextAlignment.LEFT);
        drawCircleOutline(canvas, PADDING + 5, y + 13, 4, 1.5f, color(MAP_TOTEM_RANGE));
        drawText(canvas, PADDING + 14, y + 13, 9, "solid ring = player range (50)", color(MAP_SUBTEXT), TextAlignment.LEFT);
        drawText(canvas, PADDING + 5, y + 26, 10, "--", color(MAP_TOTEM_REACH), TextAlignment.CENTER);
        drawText(canvas, PADDING + 14, y + 26, 9, "dashed ring = node reach (52)", color(MAP_SUBTEXT), TextAlignment.LEFT);
        drawTotemMarker(canvas, PADDING + 5, y + 39, 8, color(MAP_PLAYER), true);
        drawText(canvas, PADDING + 14, y + 39, 9, "bright marker = best spot", color(MAP_SUBTEXT), TextAlignment.LEFT);
    }

    private void renderGatheringTotemResults(UiCanvas canvas, Layout layout, boolean hasSelectedCluster) {
        float startY = layout.resultsStartY();
        if (session.placements().isEmpty()) {
            drawFittedText(
                    canvas,
                    PADDING + 8,
                    startY + 14,
                    10,
                    status(hasSelectedCluster),
                    color(MAP_SUBTEXT),
                    SIDEBAR_WIDTH - PADDING * 2 - 16,
                    TextAlignment.LEFT);
            return;
        }
        int visibleRows = Math.min(
                TOTEM_RESULT_VISIBLE_ROWS,
                session.placements().size() - session.scroll());
        for (int row = 0; row < visibleRows; row++) {
            int resultIndex = session.scroll() + row;
            Placement placement = session.placements().get(resultIndex);
            UiBounds result = layout.result(row);
            float y = result.y();
            boolean active = placement == session.selected();
            result.fill(canvas,
                    active ? color(MAP_CONTROL_ACTIVE) : color(MAP_CONTROL));
            result.stroke(canvas, 1,
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

    String status(boolean hasSelectedCluster) {
        if (!gatheringTotemSolverEnabled) {
            return "Disabled";
        }
        if (gatheringTotemSearchTarget == GatheringTotemSearchTarget.SELECTED_CLUSTER && !hasSelectedCluster) {
            return "Select a cluster";
        }
        if (session.optimizing()) {
            return "Optimizing...";
        }
        if (session.error() != null) {
            return "Optimization failed";
        }
        if (session.selected() == null) {
            return "No results";
        }
        return session.selected().nodeCount()
                + " nodes · "
                + session.placements().size()
                + (session.placements().size() == 1 ? " optimal spot" : " optimal spots");
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

    static Layout layout(float panelY, boolean expanded) {
        float y = panelY + PANEL_HEADER_HEIGHT;
        if (!expanded) {
            return new Layout(-1, -1, -1, -1, -1, -1, -1, -1, -1, y);
        }
        y += 8;
        float targetY = y;
        y += BUTTON_HEIGHT + 8;
        float filterSummaryY = y + 5;
        y += 34;
        float refreshY = y;
        y += BUTTON_HEIGHT + 8;
        float layerLabelY = y + 5;
        y += 14;
        float layerStartY = y;
        y += TOTEM_LAYER_LABELS.size() * (TOGGLE_HEIGHT + TOTEM_LAYER_GAP);
        float legendY = y + 2;
        y += 52;
        float resultsLabelY = y + 5;
        y += 14;
        float resultsStartY = y;
        y += TOTEM_RESULT_VISIBLE_ROWS * TOTEM_RESULT_ROW_HEIGHT;
        float actionsY = y;
        y += BUTTON_HEIGHT + 8;
        return new Layout(
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

    record Layout(
            float targetY,
            float filterSummaryY,
            float refreshY,
            float layerLabelY,
            float layerStartY,
            float legendY,
            float resultsLabelY,
            float resultsStartY,
            float actionsY,
            float endY) {
        Layout shifted(float delta) {
            return new Layout(targetY + delta, filterSummaryY + delta, refreshY + delta,
                    layerLabelY + delta, layerStartY + delta, legendY + delta,
                    resultsLabelY + delta, resultsStartY + delta, actionsY + delta, endY + delta);
        }
        UiBounds target() { return bounds(targetY, BUTTON_HEIGHT); }
        UiBounds targetSegment(int index) {
            float width = target().width() / GatheringTotemSearchTarget.values().length;
            return new UiBounds(PADDING + index * width, targetY, width, BUTTON_HEIGHT);
        }
        UiBounds refresh() { return bounds(refreshY, BUTTON_HEIGHT); }
        UiBounds layer(int index) { return bounds(layerStartY + index * (TOGGLE_HEIGHT + TOTEM_LAYER_GAP), TOGGLE_HEIGHT); }
        UiBounds results() { return bounds(resultsStartY, TOTEM_RESULT_VISIBLE_ROWS * TOTEM_RESULT_ROW_HEIGHT); }
        UiBounds result(int row) { return bounds(resultsStartY + row * TOTEM_RESULT_ROW_HEIGHT, TOTEM_RESULT_ROW_HEIGHT - 3); }
        UiBounds center() { return new UiBounds(PADDING, actionsY, (SIDEBAR_WIDTH - PADDING * 2 - SPLIT_CONTROL_GAP) / 2, BUTTON_HEIGHT); }
        UiBounds copy() { UiBounds center = center(); return new UiBounds(center.x() + center.width() + SPLIT_CONTROL_GAP, actionsY, center.width(), BUTTON_HEIGHT); }
        private static UiBounds bounds(float y, float height) { return new UiBounds(PADDING, y, SIDEBAR_WIDTH - PADDING * 2, height); }
    }
}
