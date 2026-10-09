package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;
import static com.seqwawa.seq.ui.WorldMapUi.*;
import com.seqwawa.seq.managers.AssetManager;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.map.*;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Supplier;
import org.lwjgl.glfw.GLFW;

/** World-event map mode: cached events, tracking, selection, filtering and input. */
final class WorldEventMapMode {
    interface Tracking {
        Set<String> ids();
        void setTracked(String id, boolean tracked);
    }
    private static final float BUTTON_HEIGHT = 24, INPUT_HEIGHT = 24, PANEL_HEADER_HEIGHT = 28;
    private static final float SIDEBAR_PANEL_TOP = 92, INSIGHTS_SIDEBAR_WIDTH = 250, PANEL_GAP = 10;
    private static final int WORLD_EVENT_DROPDOWN_VISIBLE_ROWS = 8;
    private static final float RESOURCE_DROPDOWN_ROW_HEIGHT = 20, WORLD_EVENT_DETAIL_HEIGHT = 122;
    private static final String WORLD_EVENT_MARKER_ASSET = "world_event_icon";
    private final Supplier<WorldEventService.Snapshot> snapshots;
    private final Supplier<String> status;
    private final Tracking tracking;
    private final WorldMapSettings mapSettings;
    private final MapSidebar sidebar = new MapSidebar();
    private WorldMapFrame frame;
    private boolean worldEventDropdownOpen;
    private boolean worldEventInputFocused;
    private boolean worldEventDropdownTrackedOnly;
    private int worldEventDropdownScroll;
    private String worldEventSearch = "";
    private WorldEventDisplayFilter worldEventDisplayFilter = WorldEventDisplayFilter.ALL;
    private List<WorldEventDefinition> allWorldEvents = List.of();
    private List<WorldEventDefinition> visibleWorldEvents = List.of();
    private Set<String> cachedTrackedWorldEventIds = Set.of();
    private long cachedWorldEventSnapshotVersion = -1;
    private WorldEventDisplayFilter cachedWorldEventDisplayFilter;
    private WorldEventDefinition hoveredWorldEvent;
    private int hoveredWorldEventLocationIndex = -1;
    private WorldEventDefinition selectedWorldEvent;

    WorldEventMapMode(Supplier<WorldEventService.Snapshot> snapshots, Supplier<String> status,
            WorldMapSettings settings, Tracking tracking) {
        this.snapshots = snapshots;
        this.status = status;
        this.mapSettings = settings;
        this.tracking = tracking;
        worldEventDisplayFilter = settings.worldEventDisplayFilter();
    }

    void deactivate() {
        sidebar.reset();
        selectedWorldEvent = null;
        hoveredWorldEvent = null;
        hoveredWorldEventLocationIndex = -1;
        closeWorldEventSearch();
    }
    boolean dropdownOpen() { return worldEventDropdownOpen; }
    void closeSearch() { closeWorldEventSearch(); }
    WorldEventDefinition selected() { return selectedWorldEvent; }
    WorldEventDefinition hovered() { return hoveredWorldEvent; }
    int hoveredLocationIndex() { return hoveredWorldEventLocationIndex; }
    void selectHovered() {
        selectedWorldEvent = hoveredWorldEvent;
        hoveredWorldEvent = null;
        hoveredWorldEventLocationIndex = -1;
        closeWorldEventSearch();
    }
    void clearHover() { hoveredWorldEvent = null; hoveredWorldEventLocationIndex = -1; }
    private boolean panelExpanded(WorldMapSidebarPanel panel) { return mapSettings.sidebarPanelExpanded(panel); }
    private void togglePanel(WorldMapSidebarPanel panel) {
        boolean expanded = !panelExpanded(panel);
        mapSettings.setSidebarPanelExpanded(panel, expanded);
        sidebar.reset();
        if (!expanded && panel == WorldMapSidebarPanel.EVENT_TRACKING) closeWorldEventSearch();
    }
    private void renderPanelHeader(UiCanvas canvas, float y, String label, String summary, WorldMapSidebarPanel panel) {
        sidebar.panelHeader(canvas, y, label, summary, panel, frame, mapSettings);
    }
    private void drawButton(UiCanvas canvas, float x, float y, float w, float h, String label, boolean active) {
        MapSidebar.button(canvas, x, y, w, h, label, active, frame);
    }
    private static double markerDistance(double dx, double dy) { return Math.max(Math.abs(dx), Math.abs(dy)); }
    private static int clampDropdownScroll(int scroll, int count, int rows) { return Math.max(0, Math.min(scroll, count - rows)); }

    boolean clickSidebar(WorldMapFrame frame) {
        this.frame = frame;
        float mx = frame.mouseX(), my = frame.mouseY(), sidebarMy = my + sidebar.scroll();
        SidebarLayout layout = worldEventSidebarLayout();
        if (worldEventDropdownOpen) {
            List<WorldEventDefinition> events = worldEventDropdownOptions();
            int visible = Math.min(WORLD_EVENT_DROPDOWN_VISIBLE_ROWS, events.size());
            int index = DropdownMenu.optionAt(mx, my, PADDING, sidebar.y(layout.eventInputY()) + INPUT_HEIGHT,
                    SIDEBAR_WIDTH - PADDING * 2, RESOURCE_DROPDOWN_ROW_HEIGHT, visible, worldEventDropdownScroll);
            if (index >= 0 && index < events.size()) { toggleTrackedWorldEvent(events.get(index), true); return true; }
        }
        if (mx < 0 || mx > SIDEBAR_WIDTH || my < SIDEBAR_PANEL_TOP || my > frame.height()) {
            if (worldEventDropdownOpen) { closeWorldEventSearch(); return true; }
            return false;
        }
        if (MapSidebar.hit(mx, sidebarMy, PADDING, layout.displayPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.EVENT_DISPLAY); return true;
        }
        if (MapSidebar.hit(mx, sidebarMy, PADDING, layout.trackingPanelY(), SIDEBAR_WIDTH - PADDING * 2, PANEL_HEADER_HEIGHT)) {
            togglePanel(WorldMapSidebarPanel.EVENT_TRACKING); return true;
        }
        if (layout.filterY() >= 0 && MapSidebar.hit(mx, sidebarMy, PADDING, layout.filterY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            WorldEventDisplayFilter filter = worldEventFilterAt(mx);
            if (filter != null) { worldEventDisplayFilter = filter; mapSettings.setWorldEventDisplayFilter(filter); refresh(); }
            return true;
        }
        if (layout.eventFilterY() >= 0 && MapSidebar.hit(mx, sidebarMy, PADDING, layout.eventFilterY(), SIDEBAR_WIDTH - PADDING * 2, BUTTON_HEIGHT)) {
            worldEventDropdownTrackedOnly = mx >= PADDING + (SIDEBAR_WIDTH - PADDING * 2) / 2;
            worldEventDropdownOpen = true; worldEventInputFocused = true; worldEventDropdownScroll = 0; return true;
        }
        if (layout.eventInputY() >= 0 && MapSidebar.hit(mx, sidebarMy, PADDING, layout.eventInputY(), SequoiaUiStyle.searchWidth(SIDEBAR_WIDTH - PADDING * 2), INPUT_HEIGHT)) {
            boolean open = !worldEventDropdownOpen;
            closeWorldEventSearch(); worldEventInputFocused = open; worldEventDropdownOpen = open; return true;
        }
        if (worldEventDropdownOpen) { closeWorldEventSearch(); return true; }
        return true;
    }

    boolean scroll(WorldMapFrame frame, double delta) {
        this.frame = frame;
        if (worldEventDropdownOpen) {
            var layout = worldEventSidebarLayout();
            var events = worldEventDropdownOptions();
            int visible = Math.min(WORLD_EVENT_DROPDOWN_VISIBLE_ROWS, events.size());
            if (MapSidebar.hit(frame.mouseX(), frame.mouseY(), PADDING, sidebar.y(layout.eventInputY()) + INPUT_HEIGHT,
                    SIDEBAR_WIDTH - PADDING * 2, visible * RESOURCE_DROPDOWN_ROW_HEIGHT)) {
                worldEventDropdownScroll = clampDropdownScroll(worldEventDropdownScroll + (delta > 0 ? -1 : 1), events.size(), WORLD_EVENT_DROPDOWN_VISIBLE_ROWS);
                return true;
            }
        }
        if (frame.mouseX() >= 0 && frame.mouseX() <= SIDEBAR_WIDTH) {
            if (frame.mouseY() >= SIDEBAR_PANEL_TOP && frame.mouseY() <= frame.height()) sidebar.scroll(delta, frame.height());
            return true;
        }
        return false;
    }

    boolean keyPressed(int key) {
        if (worldEventInputFocused) {
            if (key == GLFW.GLFW_KEY_ESCAPE) closeWorldEventSearch();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) applyWorldEventAutocompleteSelection();
            else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
                worldEventSearch = key == GLFW.GLFW_KEY_DELETE || worldEventSearch.isEmpty() ? "" : worldEventSearch.substring(0, worldEventSearch.length() - 1);
                worldEventDropdownOpen = true; worldEventDropdownScroll = 0;
            }
            return true;
        }
        if (worldEventDropdownOpen && key == GLFW.GLFW_KEY_ESCAPE) { closeWorldEventSearch(); return true; }
        return false;
    }
    boolean charTyped(String text) {
        if (!worldEventInputFocused) return false;
        if (text != null) { worldEventSearch += text; worldEventDropdownOpen = true; worldEventDropdownScroll = 0; }
        return true;
    }
    void clickTrackingButton(WorldMapFrame frame, float x, float eventDetailY) {
        if (selectedWorldEvent != null && new UiBounds(x + PADDING + 8, eventDetailY + 92, INSIGHTS_SIDEBAR_WIDTH - PADDING * 2 - 16, 24).contains(frame.mouseX(), frame.mouseY())) {
            toggleTrackedWorldEvent(selectedWorldEvent, false);
        }
    }

    void refresh() {
        WorldEventService.Snapshot snapshot = snapshots.get();
        Set<String> trackedWorldEventIds = tracking.ids();
        if (snapshot.version() == cachedWorldEventSnapshotVersion
                && worldEventDisplayFilter == cachedWorldEventDisplayFilter
                && trackedWorldEventIds.equals(cachedTrackedWorldEventIds)) {
            return;
        }
        cachedWorldEventSnapshotVersion = snapshot.version();
        cachedWorldEventDisplayFilter = worldEventDisplayFilter;
        cachedTrackedWorldEventIds = Set.copyOf(trackedWorldEventIds);
        allWorldEvents = snapshot.events();
        visibleWorldEvents = WorldEventFilters.visibleEvents(
                allWorldEvents,
                worldEventDisplayFilter,
                cachedTrackedWorldEventIds);
        selectedWorldEvent = WorldEventFilters.retainVisibleSelection(selectedWorldEvent, visibleWorldEvents);
    }

    void renderMap(UiCanvas canvas, WorldMapFrame frame) {
        this.frame = frame;
        MapViewport viewport = frame.viewport();
        hoveredWorldEvent = null;
        hoveredWorldEventLocationIndex = -1;
        boolean allowHover = !frame.dragging() && viewport.isInsideScreen(frame.mouseX(), frame.mouseY());
        MapBounds visibleBounds = viewport.visibleBounds();
        AssetManager.Asset markerAsset = worldEventMarkerAsset();

        if (allowHover) {
            List<WorldEventMarkerHitTester.Candidate> candidates = new ArrayList<>();
            for (WorldEventDefinition event : visibleWorldEvents) {
                for (int locationIndex = 0; locationIndex < event.locations().size(); locationIndex++) {
                    WorldEventLocation location = event.locations().get(locationIndex);
                    if (!visibleBounds.contains(location.x(), location.z())) {
                        continue;
                    }
                    float x = viewport.worldToScreenX(location.x());
                    float y = viewport.worldToScreenZ(location.z());
                    candidates.add(new WorldEventMarkerHitTester.Candidate(
                            event,
                            locationIndex,
                            markerDistance(frame.mouseX() - x, frame.mouseY() - y)));
                }
            }
            WorldEventMarkerHitTester.Candidate closest = WorldEventMarkerHitTester.closest(candidates, 9);
            if (closest != null) {
                hoveredWorldEvent = closest.event();
                hoveredWorldEventLocationIndex = closest.locationIndex();
            }
        }

        canvas.scissor(viewport.screenX(), viewport.screenY(), viewport.screenWidth(), viewport.screenHeight());
        for (WorldEventDefinition event : visibleWorldEvents) {
            boolean eventSelected = selectedWorldEvent != null && selectedWorldEvent.runId().equals(event.runId());
            boolean eventTracked = cachedTrackedWorldEventIds.contains(event.internalName());
            for (int locationIndex = 0; locationIndex < event.locations().size(); locationIndex++) {
                WorldEventLocation location = event.locations().get(locationIndex);
                if (!visibleBounds.contains(location.x(), location.z())) {
                    continue;
                }
                float x = viewport.worldToScreenX(location.x());
                float y = viewport.worldToScreenZ(location.z());
                float areaRadius = (float) (location.radius() * viewport.pixelsPerBlock());
                if (areaRadius >= 5) {
                    Color areaColor = eventTracked ? color(MAP_TRACKED_WORLD_EVENT) : color(MAP_WORLD_EVENT);
                    drawCircleOutline(canvas, x, y, areaRadius, 1, areaColor);
                }

                Color markerColor = eventTracked ? color(MAP_TRACKED_WORLD_EVENT) : color(MAP_WORLD_EVENT);
                boolean highlighted = eventSelected || (event.equals(hoveredWorldEvent) && locationIndex == hoveredWorldEventLocationIndex);
                if (markerAsset == null) {
                    drawSquareMarker(canvas, x, y, highlighted ? 8 : 7, color(BACKGROUND_MODAL_OVERLAY));
                    drawSquareMarker(canvas, x, y, highlighted ? 5.5f : 4.5f, eventSelected ? color(MAP_PLAYER) : markerColor);
                } else {
                    float outerRadius = highlighted ? 9 : 8;
                    float assetSize = highlighted ? 12 : 11;
                    drawSquareMarker(canvas, x, y, outerRadius, color(BACKGROUND_MODAL_OVERLAY));
                    drawSquareMarker(canvas, x, y, outerRadius - 1.5f, markerColor);
                    canvas.drawImage(
                            markerAsset.getImage(),
                            x - assetSize / 2,
                            y - assetSize / 2,
                            assetSize,
                            assetSize,
                            1f);
                    if (eventSelected) {
                        drawSquareMarkerOutline(canvas, x, y, outerRadius + 1, 1.5f, color(MAP_PLAYER));
                    }
                }
            }
        }
        canvas.resetScissor();
        if (hoveredWorldEvent != null) {
            renderWorldEventTooltip(canvas, hoveredWorldEvent, hoveredWorldEventLocationIndex);
        }
    }

    void renderSidebar(UiCanvas canvas, WorldMapFrame frame, String centerLabel) {
        this.frame = frame;
        float screenHeight = frame.height();
        SidebarLayout layout = worldEventSidebarLayout();
        sidebar.contentHeight(layout.endY() + PADDING, screenHeight);
        sidebar.renderBase(canvas, frame, MapDisplayMode.WORLD_EVENTS, centerLabel);
        canvas.scissor(0, SIDEBAR_PANEL_TOP, SIDEBAR_WIDTH, Math.max(0, screenHeight - SIDEBAR_PANEL_TOP));

        renderPanelHeader(
                canvas,
                sidebar.y(layout.displayPanelY()),
                "Event Display",
                worldEventDisplayFilter.label(),
                WorldMapSidebarPanel.EVENT_DISPLAY);
        if (panelExpanded(WorldMapSidebarPanel.EVENT_DISPLAY)) {
            drawText(canvas, PADDING, sidebar.y(layout.filterLabelY()), 12, "Visible Events", color(MAP_SUBTEXT), TextAlignment.LEFT);
            drawWorldEventFilterControl(canvas, sidebar.y(layout.filterY()));
        }

        renderPanelHeader(
                canvas,
                sidebar.y(layout.trackingPanelY()),
                "Tracking",
                cachedTrackedWorldEventIds.size() + " tracked",
                WorldMapSidebarPanel.EVENT_TRACKING);
        if (panelExpanded(WorldMapSidebarPanel.EVENT_TRACKING)) {
            drawWorldEventTrackingListControl(canvas, sidebar.y(layout.eventFilterY()));
            MapSidebar.searchInput(
                    canvas,
                    sidebar.y(layout.eventInputY()),
                    worldEventDropdownOpen,
                    worldEventInputFocused,
                    worldEventSearch,
                    trackedWorldEventLabel(), frame);
        }

        if (worldEventDropdownOpen) {
            renderWorldEventDropdown(canvas, sidebar.y(layout.eventInputY()) + INPUT_HEIGHT);
        }
        canvas.resetScissor();
        sidebar.renderScrollbar(canvas, screenHeight);
    }

    void renderInsights(UiCanvas canvas, WorldMapFrame frame, float x, float overviewY, float eventDetailY) {
        this.frame = frame;
        float contentX = x + PADDING;
        float contentWidth = INSIGHTS_SIDEBAR_WIDTH - PADDING * 2;
        long visibleCount = allWorldEvents.stream().filter(WorldEventDefinition::isVisible).count();
        drawInsightsSectionTitle(canvas, contentX, overviewY, "Overview");
        drawInsightRow(canvas, contentX, overviewY + 18, contentWidth, "Visible", visibleWorldEvents.size() + " shown / " + visibleCount + " active");
        drawInsightRow(canvas, contentX, overviewY + 34, contentWidth, "Tracked", String.valueOf(cachedTrackedWorldEventIds.size()));
        drawInsightRow(canvas, contentX, overviewY + 50, contentWidth, "API", status.get());

        WorldEventDefinition detail = selectedWorldEvent != null ? selectedWorldEvent : hoveredWorldEvent;
        drawInsightsSectionTitle(canvas, contentX, eventDetailY - 8, "Selection");
        if (detail != null) {
            renderWorldEventDetail(canvas, contentX, eventDetailY + 4, contentWidth, detail, selectedWorldEvent != null);
        } else {
            drawFittedText(canvas, contentX, eventDetailY + 18, 11, "Hover or select a world event", color(MAP_SUBTEXT), contentWidth, TextAlignment.LEFT);
        }
    }

    private void renderWorldEventDetail(
            UiCanvas canvas,
            float x,
            float y,
            float width,
            WorldEventDefinition event,
            boolean allowTrackingButton) {
        float textWidth = width - 16;
        canvas.fillRect(x, y, width, WORLD_EVENT_DETAIL_HEIGHT, color(MAP_HEADER));
        canvas.strokeRect(x, y, width, WORLD_EVENT_DETAIL_HEIGHT, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 16, 14, event.name(), color(MAP_TEXT), textWidth, TextAlignment.LEFT);
        String metadata = worldEventMetadata(event);
        drawFittedText(canvas, x + 8, y + 35, 11, metadata, color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 53, 11, worldEventScheduleLabel(event.schedule()), color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
        String locationLabel = event.locations().size() == 1
                ? worldEventCoordinates(event.locations().getFirst())
                : event.locations().size() + " possible locations";
        drawFittedText(canvas, x + 8, y + 71, 11, locationLabel, color(MAP_SUBTEXT), textWidth, TextAlignment.LEFT);
        if (allowTrackingButton) {
            boolean tracked = cachedTrackedWorldEventIds.contains(event.internalName());
            drawButton(
                    canvas,
                    x + 8,
                    y + 88,
                    width - 16,
                    24,
                    tracked ? "Untrack Event" : "Track Event",
                    tracked);
        }
    }

    private void drawWorldEventFilterControl(UiCanvas canvas, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / WorldEventDisplayFilter.values().length;
        for (int index = 0; index < WorldEventDisplayFilter.values().length; index++) {
            WorldEventDisplayFilter filter = WorldEventDisplayFilter.values()[index];
            float x = PADDING + index * segmentWidth;
            boolean active = worldEventDisplayFilter == filter;
            boolean hovered = MapSidebar.hit(frame.mouseX(), frame.mouseY(), x, y, segmentWidth, BUTTON_HEIGHT);
            canvas.fillRect(x, y, segmentWidth, BUTTON_HEIGHT, active ? color(MAP_CONTROL_ACTIVE) : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
            canvas.strokeRect(x, y, segmentWidth, BUTTON_HEIGHT, 1, color(MAP_BORDER));
            drawText(canvas, x + segmentWidth / 2f, y + BUTTON_HEIGHT / 2f, 11, filter.label(), color(MAP_TEXT), TextAlignment.CENTER);
        }
    }

    private void drawWorldEventTrackingListControl(UiCanvas canvas, float y) {
        float width = SIDEBAR_WIDTH - PADDING * 2;
        float segmentWidth = width / 2f;
        drawTrackingListSegment(canvas, PADDING, y, segmentWidth, "All Events", !worldEventDropdownTrackedOnly);
        drawTrackingListSegment(
                canvas,
                PADDING + segmentWidth,
                y,
                segmentWidth,
                "Tracked Only",
                worldEventDropdownTrackedOnly);
    }

    private void drawTrackingListSegment(UiCanvas canvas, float x, float y, float width, String label, boolean active) {
        boolean hovered = MapSidebar.hit(frame.mouseX(), frame.mouseY(), x, y, width, BUTTON_HEIGHT);
        canvas.fillRect(x, y, width, BUTTON_HEIGHT, active ? color(MAP_CONTROL_ACTIVE) : hovered ? color(MAP_CONTROL_HOVER) : color(MAP_CONTROL));
        canvas.strokeRect(x, y, width, BUTTON_HEIGHT, 1, color(MAP_BORDER));
        drawFittedText(
                canvas,
                x + width / 2f,
                y + BUTTON_HEIGHT / 2f,
                11,
                label,
                color(MAP_TEXT),
                width - 10,
                TextAlignment.CENTER);
    }

    private void renderWorldEventDropdown(UiCanvas canvas, float y) {
        List<WorldEventDefinition> events = worldEventDropdownOptions();
        worldEventDropdownScroll = clampDropdownScroll(worldEventDropdownScroll, events.size(), WORLD_EVENT_DROPDOWN_VISIBLE_ROWS);
        DropdownMenu.list(canvas, PADDING, y, SIDEBAR_WIDTH - PADDING * 2, RESOURCE_DROPDOWN_ROW_HEIGHT,
                events.stream().map(event -> (cachedTrackedWorldEventIds.contains(event.internalName()) ? "[x] " : "[ ] ") + event.name()).toList(),
                i -> cachedTrackedWorldEventIds.contains(events.get(i).internalName()),
                worldEventDropdownScroll, WORLD_EVENT_DROPDOWN_VISIBLE_ROWS, frame.mouseX(), frame.mouseY());
    }

    private void renderWorldEventTooltip(UiCanvas canvas, WorldEventDefinition event, int locationIndex) {
        WorldEventLocation location = event.locations().get(Math.max(0, locationIndex));
        String locationLabel = event.locations().size() > 1
                ? "Possible " + (locationIndex + 1) + "/" + event.locations().size()
                        + ": " + worldEventCoordinates(location)
                : worldEventCoordinates(location);
        String subtitle = worldEventScheduleLabel(event.schedule()) + " | " + locationLabel;
        float x = frame.tooltipX(210);
        float y = Math.max(8, frame.mouseY() + 12);
        canvas.fillRect(x, y, 210, 42, color(MAP_SIDEBAR));
        canvas.strokeRect(x, y, 210, 42, 1, color(MAP_BORDER));
        drawFittedText(canvas, x + 8, y + 15, 12, event.name(), color(MAP_TEXT), 194, TextAlignment.LEFT);
        drawFittedText(canvas, x + 8, y + 31, 11, subtitle, color(MAP_SUBTEXT), 194, TextAlignment.LEFT);
    }

    private List<WorldEventDefinition> worldEventDropdownOptions() {
        String query = worldEventInputFocused ? worldEventSearch : "";
        return WorldEventFilters.trackingOptions(
                allWorldEvents,
                cachedTrackedWorldEventIds,
                worldEventDropdownTrackedOnly,
                query);
    }

    private String trackedWorldEventLabel() {
        int tracked = cachedTrackedWorldEventIds.size();
        return tracked == 0 ? "Manage tracked events" : tracked + " tracked events";
    }

    private static String worldEventMetadata(WorldEventDefinition event) {
        List<String> metadata = new ArrayList<>();
        if (event.level() != null) {
            metadata.add("Lv. " + event.level());
        }
        if (event.difficulty() != null) {
            metadata.add(displayEnumValue(event.difficulty()));
        }
        if (event.length() != null) {
            metadata.add(displayEnumValue(event.length()));
        }
        return metadata.isEmpty() ? "World event" : String.join(" | ", metadata);
    }

    private static String worldEventScheduleLabel(Instant schedule) {
        if (schedule == null) {
            return "Not scheduled";
        }
        Instant now = Instant.now();
        if (!schedule.isAfter(now)) {
            long minutes = Math.max(0, Duration.between(schedule, now).toMinutes());
            return minutes == 0 ? "Started" : "Started " + minutes + "m ago";
        }
        long seconds = Duration.between(now, schedule).getSeconds();
        return "Starts in " + Math.max(1, (seconds + 59) / 60) + "m";
    }

    static String worldEventCoordinates(WorldEventLocation location) {
        return Math.round(location.x()) + " " + Math.round(location.y()) + " " + Math.round(location.z());
    }

    private static AssetManager.Asset worldEventMarkerAsset() {
        return SeqClient.assetManager == null ? null : SeqClient.assetManager.getAsset(WORLD_EVENT_MARKER_ASSET);
    }

    private void applyWorldEventAutocompleteSelection() {
        String search = worldEventSearch.trim();
        WorldEventDefinition match = allWorldEvents.stream()
                .filter(event -> event.name().equalsIgnoreCase(search))
                .findFirst()
                .orElse(null);
        if (match == null) {
            List<WorldEventDefinition> options = worldEventDropdownOptions();
            match = options.isEmpty() ? null : options.getFirst();
        }
        if (match != null) {
            toggleTrackedWorldEvent(match, true);
        }
    }

    private WorldEventDisplayFilter worldEventFilterAt(float mouseX) {
        float segmentWidth = (SIDEBAR_WIDTH - PADDING * 2) / WorldEventDisplayFilter.values().length;
        int index = (int) ((mouseX - PADDING) / segmentWidth);
        return index >= 0 && index < WorldEventDisplayFilter.values().length
                ? WorldEventDisplayFilter.values()[index]
                : null;
    }

    private void closeWorldEventSearch() {
        worldEventDropdownOpen = false;
        worldEventInputFocused = false;
        worldEventSearch = "";
        worldEventDropdownScroll = 0;
    }

    private void toggleTrackedWorldEvent(WorldEventDefinition event, boolean keepOpen) {
        boolean tracked = cachedTrackedWorldEventIds.contains(event.internalName());
        tracking.setTracked(event.internalName(), !tracked);
        worldEventDropdownOpen = keepOpen;
        worldEventInputFocused = keepOpen;
        if (!keepOpen) {
            worldEventSearch = "";
            worldEventDropdownScroll = 0;
        }
        refresh();
    }

    SidebarLayout worldEventSidebarLayout() {
        float y = 58;
        float centerY = y;
        y += BUTTON_HEIGHT + 18;
        float displayPanelY = y;
        y += PANEL_HEADER_HEIGHT;
        float filterLabelY = -1;
        float filterY = -1;
        if (panelExpanded(WorldMapSidebarPanel.EVENT_DISPLAY)) {
            y += 8;
            filterLabelY = y;
            y += 12;
            filterY = y;
            y += BUTTON_HEIGHT + 8;
        }
        y += PANEL_GAP;
        float trackingPanelY = y;
        y += PANEL_HEADER_HEIGHT;
        float eventFilterY = -1;
        float eventInputY = -1;
        if (panelExpanded(WorldMapSidebarPanel.EVENT_TRACKING)) {
            y += 8;
            eventFilterY = y;
            y += BUTTON_HEIGHT + 8;
            eventInputY = y;
            y += INPUT_HEIGHT + 8;
        }
        return new SidebarLayout(
                centerY,
                displayPanelY,
                filterLabelY,
                filterY,
                trackingPanelY,
                eventFilterY,
                eventInputY,
                y);
    }

    record SidebarLayout(
            float centerY,
            float displayPanelY,
            float filterLabelY,
            float filterY,
            float trackingPanelY,
            float eventFilterY,
            float eventInputY,
            float endY) {}
}
