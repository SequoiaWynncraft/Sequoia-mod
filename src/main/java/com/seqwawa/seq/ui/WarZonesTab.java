package com.seqwawa.seq.ui;

import static com.seqwawa.seq.ui.WarQueueMapOverlay.*;

import static com.seqwawa.seq.ui.WarMapSettings.*;

import static com.seqwawa.seq.ui.WarMapGeometry.*;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import static com.seqwawa.seq.ui.WarPlannerDialogUi.*;
import static com.seqwawa.seq.ui.WarPingPicker.pingCandidates;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.WarPlannerManager;
import com.seqwawa.seq.managers.WarTerritoryQueueManager;
import com.seqwawa.seq.map.GatheringMapImageService;
import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.map.GuildTerritoryService;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapViewport;
import com.seqwawa.seq.map.TelemetryPlayerMapOverlay;
import com.seqwawa.seq.map.WorldMapBackgroundRenderer;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.Zone;
import com.seqwawa.seq.model.war.WarPlannerSnapshot.ZoneCategory;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** War-map tab owns map and zone interactions. */
final class WarZonesTab {
    private static final float PADDING = 12;
    private static final float HEADER_HEIGHT = SequoiaUiStyle.HEADER_HEIGHT;

    private static final float ROW_HEIGHT = 38;

    static final int RESOURCE_FILL_ALPHA = 96;

    private final WarTerritoryQueueManager queueManager;
    private final GuildTerritoryIndex territoryIndex;
    private final GatheringMapImageService mapImageService = GatheringMapImageService.getInstance();
    private final WorldMapBackgroundRenderer mapBackground = new WorldMapBackgroundRenderer(mapImageService);
    private final TelemetryPlayerMapOverlay telemetryPlayerOverlay = new TelemetryPlayerMapOverlay();
    private boolean coloringDropdownOpen;
    private String selectedWarTerritory;
    private boolean warMapFitted;
    private double warMapCenterX;
    private double warMapCenterZ;
    private double warMapPixelsPerBlock;
    private float fittedWarMapWidth = -1;
    private float fittedWarMapHeight = -1;
    private Boolean fittedWarMapLocked;
    private GuildTerritory hoveredWarMapTerritory;

    interface Actions {
        void openZone(Zone zone, boolean inspect);
        void openCategory(ZoneCategory category);
        void result(java.util.concurrent.CompletableFuture<WarPlannerManager.ActionResult> result);
        void queueResult(java.util.concurrent.CompletableFuture<WarTerritoryQueueManager.ActionResult> result);
        boolean busy();
        String stateLabel();
        Color stateColor();
        void feedback(String message);
    }
    private final WarMapGesture gesture = new WarMapGesture();
    private final WarZoneSidebar sidebar;
    private final WarPlannerManager manager;
    private final Actions actions;
    private float nvgMouseX, nvgMouseY;
    private boolean sectionDropdownOpen;
    WarZonesTab(WarPlannerManager manager, Actions actions) {
        this.manager = manager;
        this.actions = actions;
        this.queueManager = SeqClient.getWarTerritoryQueueManager();
        sidebar = new WarZoneSidebar(manager, actions, this::clearQueueClick);
        GuildTerritoryService.getInstance().loadBundledTerritories();
        this.territoryIndex = GuildTerritoryService.getInstance().index();
        mapImageService.requestLoad();
    }
    void tick() { if (warMapPlayersEnabled()) telemetryPlayerOverlay.tick(); }
    boolean dropdownOpen() { return coloringDropdownOpen; }
    void closeDropdown() { coloringDropdownOpen = false; }
    boolean actionsOpen() { return sidebar.actionsOpen(); }
    boolean closeActions() { return sidebar.closeActions(); }
    void cancelMapDrag() { gesture.cancelDrag(); }
    void clearQueueClick() { gesture.clearQueueClick(); }

    void clearActionMenu() { sidebar.clearActionMenu(); }
    void cancelZoneDrag() { sidebar.cancelDrag(); }
    void resetInteractions() { gesture.cancelDrag(); gesture.clearQueueClick(); sidebar.resetInteractions(); }
    void reset() { resetInteractions(); sidebar.resetScroll(); }
    void closeResources() {
        resetInteractions();
        UiRenderer.renderResource(canvas -> { mapBackground.close(); telemetryPlayerOverlay.close(); });
    }
    void renderActions(UiCanvas canvas, float mx, float my) { sidebar.renderActions(canvas, mx, my); }
    boolean clickActions(float mx, float my) { return sidebar.clickActions(mx, my); }
    void renderDropdown(UiCanvas canvas, float width, float height, float mx, float my) {
        nvgMouseX = mx; nvgMouseY = my;
        if (coloringDropdownOpen) renderDropdownOptions(canvas,
            warMapControls(warMapLayout(width, contentTop(width), height - PADDING), manager.canManage()).coloring(),
            List.of("War queues", "Resources"), resourceColorsEnabled() ? 1 : 0);
    }
    boolean clickDropdown(float mx, float my, float width, float height) {
        var coloring = warMapControls(warMapLayout(width, contentTop(width), height - PADDING), manager.canManage()).coloring();
        if (coloringDropdownOpen) {
            coloringDropdownOpen = false;
            int index = DropdownMenu.optionAt(mx, my, coloring.x(), coloring.y() + coloring.height(), coloring.width(), DropdownMenu.ROW_HEIGHT, 2, 0);
            if (index >= 0 && SeqClient.getWarPlannerResourceColorsSetting() != null) {
                SeqClient.getWarPlannerResourceColorsSetting().setValue(index == 1);
                SeqClient.getConfigManager().save();
            }
            return true;
        }
        if (!coloring.contains(mx, my)) return false;
        coloringDropdownOpen = true; cancelMapDrag(); clearQueueClick(); clearActionMenu(); return true;
    }
    boolean scroll(WarPlannerSnapshot snapshot, float mx, float my, float width, float height, double scrollY) {
        int delta = scrollY > 0 ? -1 : 1;

        WarMapLayout layout = warMapLayout(
                width, contentTop(width), height - PADDING);
        if (warMapControls(layout, manager.canManage()).panel().contains(mx, my)) return true;
        if (layout.containsMap(mx, my)) {
            gesture.clearQueueClick();
            boolean locked = manager.canManage() && territoriesLocked();
            MapViewport before = warMapViewport(
                    layout, warMapDisplayedTerritories(snapshot, locked), locked);
            double zoomFactor = scrollY > 0
                    ? MapViewport.SCROLL_ZOOM_FACTOR
                    : 1 / MapViewport.SCROLL_ZOOM_FACTOR;
            applyWarMapViewport(before.zoomAt(mx, my, zoomFactor));
            return true;
        }
        if (layout.containsSidebar(mx, my)) sidebar.scroll(snapshot, layout, delta);
        return true;

    }
    void refreshQueueViewer() {
        if (queueManager == null) {
            return;
        }
        queueManager.refreshForViewer().whenComplete((result, error) -> {
            if (error == null && result != null && result.success()) {
                return;
            }
            SeqClient.mc.execute(() -> actions.feedback(error != null
                    ? "War queue request failed."
                    : result == null ? "War queue request failed." : result.message()));
        });
    }
    boolean drag(float mx, float my, float width, float height, double deltaX, double deltaY) {
        if (gesture.drag(mx, my)) {
            WarMapLayout layout = warMapLayout(width, contentTop(width), height - PADDING);
            applyWarMapViewport(currentWarMapViewport(layout).panByScreenDelta(
                    deltaX,
                    deltaY));
            return true;
        }
        if (sidebar.drag(mx, my)) return true;
        return false;
    }
    boolean release(float mx, float my, float width, float height) {
        if (gesture.dragging()) {
            if (gesture.release()) {

                var layout = warMapLayout(width, contentTop(width), height - PADDING);
                var snapshot = manager.snapshot();
                if (snapshot != null) {
                    boolean locked = manager.canManage() && territoriesLocked();
                    var territories = warMapDisplayedTerritories(snapshot, locked);
                    var names = territories.stream().map(territory -> territory.name().toLowerCase(Locale.ROOT))
                            .collect(java.util.stream.Collectors.toSet());
                    var selected = territoryAt(territoryIndex, warMapViewport(layout, territories, locked), names,
                            mx, my);
                    selectedWarTerritory = selected == null ? null : selected.name();
                }
            }
            return true;
        }
        if (sidebar.release(mx, my, width, height)) return true;
        return false;
    }
    void render(UiCanvas canvas, WarPlannerSnapshot snapshot, float width, float top, float bottom, float mouseX, float mouseY, boolean sectionOpen) {
        nvgMouseX = mouseX; nvgMouseY = mouseY; sectionDropdownOpen = sectionOpen;
        WarMapLayout layout = warMapLayout(width, top, bottom);
        TerritoryQueue hoveredQueue = renderWarMap(canvas, snapshot, layout);
        sidebar.render(canvas, snapshot, layout, nvgMouseX, nvgMouseY);
        renderWarMapControls(canvas, layout);
        var controls = warMapControls(layout, manager.canManage());
        if (hoveredQueue != null && !sectionDropdownOpen && !coloringDropdownOpen
                && !controls.panel().contains(nvgMouseX, nvgMouseY) && !controls.fit().contains(nvgMouseX, nvgMouseY)) {
            drawWarQueueTooltip(canvas, hoveredQueue, queueManager.serverNow(), layout, queueManager.localPlayerUuid(), nvgMouseX, nvgMouseY);
        }
    }

    private TerritoryQueue renderWarMap(UiCanvas canvas, WarPlannerSnapshot snapshot, WarMapLayout layout) {
        float x = layout.mapX();
        float y = layout.mapY();
        float width = layout.mapWidth();
        float height = layout.mapHeight();
        canvas.fillRect(x, y, width, height, plannerBackground(color(BACKGROUND_BODY_OPAQUE)));
        hoveredWarMapTerritory = null;
        List<GuildTerritory> allMapTerritories = territoryIndex.territories();
        boolean locked = manager.canManage() && territoriesLocked();
        List<Zone> displayedZones = sidebar.visibleZones(snapshot);
        Set<String> shownZoneTerritories = shownZoneTerritoryNames(displayedZones);
        List<GuildTerritory> coreTerritories = visibleMapTerritories(
                allMapTerritories, displayedZones, locked);
        if (coreTerritories.isEmpty()) {
            text(canvas, "Map unavailable", x + width / 2, y + height / 2, 9, color(TEXT_MUTED), true);
            return null;
        }
        Map<String, GuildTerritory> byName = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        allMapTerritories.forEach(territory -> byName.put(territory.name(), territory));
        List<WarQueueMapMarker> queueMarkers = warQueueMapMarkers(
                displayedWarQueues(), shownZoneTerritories, byName);
        Map<String, WarPlannerSnapshot.TerritoryDetails> details = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        snapshot.territoryDetails().forEach(detail -> details.put(detail.name(), detail));
        List<GuildTerritory> contextTerritories = locked
                ? oneHopContextTerritories(allMapTerritories, coreTerritories, details)
                : List.of();
        ArrayList<GuildTerritory> displayedTerritories = new ArrayList<>(coreTerritories);
        displayedTerritories.addAll(contextTerritories);
        MapViewport viewport = warMapViewport(layout, displayedTerritories, locked);
        Set<String> displayedNames = displayedTerritories.stream()
                .map(GuildTerritory::name)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        hoveredWarMapTerritory = territoryAt(
                territoryIndex, viewport, displayedNames, nvgMouseX, nvgMouseY);
        var mapControls = warMapControls(layout, manager.canManage());
        if (sectionDropdownOpen || coloringDropdownOpen || mapControls.panel().contains(nvgMouseX, nvgMouseY)
                || mapControls.fit().contains(nvgMouseX, nvgMouseY)) hoveredWarMapTerritory = null;
        MapBounds coordinateBounds = mapImageBounds();
        float scale = (float) viewport.pixelsPerBlock();
        float offsetX = viewport.worldToScreenX(coordinateBounds.minX());
        float offsetY = viewport.worldToScreenZ(coordinateBounds.minZ());
        mapBackground.render(canvas, viewport);
        canvas.scissor(x, y, width, height);
        boolean resourceColors = resourceColorsEnabled();
        long overlayTime = monotonicMillis();
        if (resourceColors) {
            drawPreviewResources(canvas, coreTerritories, details, coordinateBounds, offsetX, offsetY, scale,
                    RESOURCE_FILL_ALPHA);
        } else {
            drawWarQueuePulses(canvas, queueMarkers, coordinateBounds, offsetX, offsetY, scale, overlayTime);
        }
        Set<String> emphasizedTerritories = new java.util.HashSet<>();
        if (selectedWarTerritory != null) emphasizedTerritories.add(selectedWarTerritory.toLowerCase(Locale.ROOT));
        if (hoveredWarMapTerritory != null) emphasizedTerritories.add(hoveredWarMapTerritory.name().toLowerCase(Locale.ROOT));
        drawPreviewConnections(
                canvas,
                coreTerritories,
                byName,
                details,
                displayedNames,
                coordinateBounds,
                offsetX,
                offsetY,
                scale,
                emphasizedTerritories,
                resourceColors);
        if (hoveredWarMapTerritory != null) {
            drawPreviewFill(
                    canvas,
                    hoveredWarMapTerritory,
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale,
                    color(MAP_TERRITORY));
        }
        Color mapColor = color(TEXT_MUTED);
        drawPreviewOutlines(
                canvas,
                coreTerritories,
                coordinateBounds,
                offsetX,
                offsetY,
                scale,
                mapColor,
                .55f,
                0,
                resourceColors);
        if (!contextTerritories.isEmpty()) {
            drawPreviewOutlines(
                    canvas,
                    contextTerritories,
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale,
                    mapColor,
                    .75f,
                    0,
                    resourceColors);
        }
        for (Zone zone : displayedZones) {
            Color zoneColor = parseColor(zone.color(), color(ACCENT_PRIMARY));
            drawPreviewOutlines(
                    canvas,
                    resolveTerritories(zone.territories(), byName),
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale,
                    zoneColor,
                    resourceColors ? 1.8f : 1.2f,
                    resourceColors ? 1 : 0,
                    resourceColors);
        }
        for (String name : emphasizedTerritories) {
            GuildTerritory territory = byName.get(name);
            if (territory != null && displayedNames.contains(name)) {
                drawPreviewOutlines(canvas, List.of(territory), coordinateBounds, offsetX, offsetY, scale,
                        color(MAP_SELECTED_TERRITORY), 2, 0);
            }
        }
        GuildTerritory hqTerritory = snapshot.hqTerritory() == null ? null : byName.get(snapshot.hqTerritory());
        if (hqTerritory != null && displayedNames.contains(hqTerritory.name().toLowerCase(Locale.ROOT))) {
            Color hqColor = color(MAP_SELECTED_TERRITORY);
            drawPreviewOutlines(
                    canvas,
                    List.of(hqTerritory),
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale,
                    hqColor,
                    2.6f,
                    2);
        }
        TerritoryQueue hoveredQueue = null;
        if (!queueMarkers.isEmpty()) {
            drawWarQueueLabels(
                    canvas,
                    queueMarkers,
                    coordinateBounds,
                    offsetX,
                    offsetY,
                    scale);
            if (hoveredWarMapTerritory != null) {
                hoveredQueue = warQueueForTerritory(queueMarkers, hoveredWarMapTerritory.name());
            }
        }
        if (hoveredWarMapTerritory != null && hoveredQueue == null) {
            drawTerritoryName(canvas, hoveredWarMapTerritory, coordinateBounds, offsetX, offsetY, scale, layout);
        }
        if (hqTerritory != null && displayedNames.contains(hqTerritory.name().toLowerCase(Locale.ROOT))) {
            drawHqIcon(canvas, hqTerritory, coordinateBounds, offsetX, offsetY, scale);
        }
        canvas.resetScissor();
        if (warMapPlayersEnabled()) telemetryPlayerOverlay.render(canvas, viewport, territoryIndex);
        return hoveredQueue;
    }

    private void renderWarMapControls(UiCanvas canvas, WarMapLayout layout) {
        WarMapControls controls = warMapControls(layout, manager.canManage());
        var fit = controls.fit();
        primaryButton(canvas, fit.x(), fit.y(), fit.width(), fit.height(), "Fit", false);
        if (layout.mapWidth() > 180) {
            text(canvas, actions.stateLabel(), layout.mapX() + layout.mapWidth() - 8, fit.y() + 11,
                    10, actions.stateColor(), UiCanvas.HorizontalAlign.RIGHT);
        }
        var panel = controls.panel();
        canvas.fillRect(panel.x(), panel.y(), panel.width(), panel.height(), color(BACKGROUND_CONTENT));
        if (manager.canManage()) renderMapSwitch(canvas, controls.lock(), "Lock territories", "Lock", territoriesLocked());
        renderMapSwitch(canvas, controls.queues(), "Only show personal queues", "Mine", onlyMyWarQueuesEnabled());
        renderMapSwitch(canvas, controls.players(), "Display players", "Players", warMapPlayersEnabled());
        renderDropdown(canvas, controls.coloring(), resourceColorsEnabled() ? "Resources" : "War queues", coloringDropdownOpen);
    }

    private void renderMapSwitch(UiCanvas canvas, WarMapButtonBounds row, String label, String shortLabel, boolean on) {
        float switchWidth = row.width() < 150 ? 26 : 36;
        float switchX = row.x() + row.width() - switchWidth - 6;
        text(canvas, row.width() < 210 ? shortLabel : label, switchX - 6, row.y() + row.height() / 2,
                row.width() < 150 ? 8 : 10, color(TEXT_PRIMARY), UiCanvas.HorizontalAlign.RIGHT);
        canvas.fillRect(switchX, row.y() + 3, switchWidth, 18, color(on ? ACCENT_PRIMARY : ACCENT_SECONDARY));
        canvas.fillRect(switchX + (on ? switchWidth - 16 : 2), row.y() + 5, 14, 14, color(TEXT_PRIMARY));
    }

    private List<GuildTerritory> warMapDisplayedTerritories(
            WarPlannerSnapshot snapshot, boolean locked) {
        List<GuildTerritory> allTerritories = territoryIndex.territories();
        List<GuildTerritory> coreTerritories = visibleMapTerritories(
                allTerritories,
                sidebar.visibleZones(snapshot),
                locked);
        if (!locked) return coreTerritories;

        Map<String, WarPlannerSnapshot.TerritoryDetails> details =
                new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        snapshot.territoryDetails().forEach(detail -> details.put(detail.name(), detail));
        ArrayList<GuildTerritory> displayedTerritories = new ArrayList<>(coreTerritories);
        displayedTerritories.addAll(oneHopContextTerritories(allTerritories, coreTerritories, details));
        return List.copyOf(displayedTerritories);
    }

    private MapViewport warMapViewport(
            WarMapLayout layout, List<GuildTerritory> displayedTerritories, boolean locked) {
        MapBounds fitBounds = warMapFitBounds(displayedTerritories, locked);
        if (shouldRefitWarMap(
                warMapFitted,
                fittedWarMapWidth,
                fittedWarMapHeight,
                fittedWarMapLocked,
                layout,
                locked)) {
            resetWarMapViewport(layout, fitBounds, locked);
        }
        return currentWarMapViewport(layout);
    }

    private MapViewport currentWarMapViewport(WarMapLayout layout) {
        return new MapViewport(
                warMapCenterX,
                warMapCenterZ,
                warMapPixelsPerBlock,
                layout.mapX(),
                layout.mapY(),
                layout.mapWidth(),
                layout.mapHeight());
    }

    private void resetWarMapViewport(WarMapLayout layout, MapBounds bounds, boolean locked) {
        MapViewport fitted = fittedWarMapViewport(bounds, layout);
        applyWarMapViewport(fitted);
        gesture.clearQueueClick();
        gesture.cancelDrag();
        fittedWarMapWidth = layout.mapWidth();
        fittedWarMapHeight = layout.mapHeight();
        fittedWarMapLocked = locked;
        warMapFitted = true;
    }

    private void applyWarMapViewport(MapViewport viewport) {
        warMapCenterX = viewport.centerX();
        warMapCenterZ = viewport.centerZ();
        warMapPixelsPerBlock = viewport.pixelsPerBlock();
    }

    boolean rightClickZoneName(WarPlannerSnapshot snapshot, float mx, float my, float width, float height) {
        return sidebar.rightClickZoneName(snapshot, mx, my, width, height);
    }
    boolean rightClickWarMapTerritory(
            WarPlannerSnapshot snapshot, float mx, float my, float width, float height) {
        WarMapLayout layout = warMapLayout(width, contentTop(width), height - PADDING);
        if (!layout.containsMap(mx, my)) return false;
        boolean locked = manager.canManage() && territoriesLocked();
        List<GuildTerritory> displayedTerritories = warMapDisplayedTerritories(snapshot, locked);
        Set<String> displayedNames = displayedTerritories.stream()
                .map(GuildTerritory::name)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        GuildTerritory territory = territoryAt(
                territoryIndex, warMapViewport(layout, displayedTerritories, locked), displayedNames, mx, my);
        if (territory == null) return false;
        if (!manager.isMutating()) {
            String nextHq = territory.name().equalsIgnoreCase(snapshot.hqTerritory()) ? null : territory.name();
            actions.result(manager.setHqTerritory(nextHq, snapshot.mapVersion()));
        }
        return true;
    }

    boolean click(WarPlannerSnapshot snapshot, float mx, float my, float width, float height) {
        float top = contentTop(width);
        WarMapLayout layout = warMapLayout(width, top, height - PADDING);
        top = layout.mapY();
        boolean locked = manager.canManage() && territoriesLocked();
        WarMapControls controls = warMapControls(layout, manager.canManage());
        if (controls.fit().contains(mx, my)) {
            gesture.clearQueueClick();
            resetWarMapViewport(
                    layout,
                    warMapFitBounds(warMapDisplayedTerritories(snapshot, locked), locked),
                    locked);
            return true;
        }
        if (manager.canManage() && controls.lock().contains(mx, my)) {
            var setting = SeqClient.getWarPlannerLockTerritoriesSetting();
            if (setting != null) {
                setting.setValue(!territoriesLocked());
                SeqClient.getConfigManager().save();
            }
            warMapFitted = false;
            gesture.clearQueueClick();
            gesture.cancelDrag();
            return true;
        }
        if (controls.queues().contains(mx, my)) {
            var setting = SeqClient.getWarQueueHudOnlyOwnedOrJoinedSetting();
            if (setting != null) {
                setting.setValue(!onlyMyWarQueuesEnabled());
                SeqClient.getConfigManager().save();
            }
            gesture.clearQueueClick();
            gesture.cancelDrag();
            return true;
        }
        if (controls.players().contains(mx, my)) {
            var setting = SeqClient.getWarPlannerShowPlayersSetting();
            if (setting != null) {
                setting.setValue(!warMapPlayersEnabled());
                SeqClient.getConfigManager().save();
                if (!setting.getValue()) {
                    UiRenderer.renderResource(canvas -> telemetryPlayerOverlay.close());
                }
            }
            gesture.clearQueueClick();
            gesture.cancelDrag();
            return true;
        }
        if (controls.panel().contains(mx, my)) return true;
        if (layout.containsMap(mx, my)) {
            TerritoryQueue queue = warQueueAtMapPoint(snapshot, layout, locked, mx, my);
            if (gesture.press(queue == null ? null : queue.id(), queue == null ? null : queue.territory(), mx, my, monotonicMillis())) {
                actions.queueResult(queueManager.toggleQueueMembership(queue.id()));
            }
            return true;
        }
        return sidebar.click(snapshot, mx, my, width, height);
    }

    private TerritoryQueue warQueueAtMapPoint(
            WarPlannerSnapshot snapshot, WarMapLayout layout, boolean locked, float mouseX, float mouseY) {
        if (queueManager == null) return null;
        List<Zone> displayedZones = sidebar.visibleZones(snapshot);
        Set<String> shownZoneTerritories = shownZoneTerritoryNames(displayedZones);
        if (shownZoneTerritories.isEmpty()) return null;

        List<GuildTerritory> displayedTerritories = warMapDisplayedTerritories(snapshot, locked);
        GuildTerritory territory = territoryAt(
                territoryIndex,
                warMapViewport(layout, displayedTerritories, locked),
                shownZoneTerritories,
                mouseX,
                mouseY);
        if (territory == null) return null;
        return displayedWarQueues().stream()
                .filter(queue -> territory.name().equalsIgnoreCase(queue.territory()))
                .findFirst()
                .orElse(null);
    }

    private List<TerritoryQueue> displayedWarQueues() {
        return queueManager == null
                ? List.of()
                : warQueuesForMap(
                        queueManager.activeQueues(),
                        queueManager.localPlayerUuid(),
                        onlyMyWarQueuesEnabled());
    }

    private void renderDropdown(UiCanvas canvas, WarMapButtonBounds bounds, String label, boolean open) {
        DropdownMenu.trigger(canvas, bounds.x(), bounds.y(), bounds.width(), bounds.height(), label, open, true,
                nvgMouseX, nvgMouseY);
    }

    private void renderDropdownOptions(UiCanvas canvas, WarMapButtonBounds trigger, List<String> labels, int selected) {
        DropdownMenu.list(canvas, trigger.x(), trigger.y() + trigger.height(), trigger.width(), DropdownMenu.ROW_HEIGHT,
                labels, i -> i == selected, 0, labels.size(), nvgMouseX, nvgMouseY);
    }

    private void primaryButton(UiCanvas canvas, float x, float y, float width, float height, String label, boolean disabled) {
        tonedButton(canvas, nvgMouseX, nvgMouseY, x, y, width, height, label, ButtonTone.PRIMARY, disabled);
    }

}
