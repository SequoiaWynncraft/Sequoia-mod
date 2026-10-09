package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;
import com.seqwawa.seq.map.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class WorldEventMapModeTest {
    @Test
    void trackingSearchHandlesTypingAutocompleteAndDeactivation() {
        var settings = WorldMapSettings.getInstance();
        boolean previous = settings.sidebarPanelExpanded(WorldMapSidebarPanel.EVENT_TRACKING);
        Set<String> tracked = new HashSet<>();
        try {
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.EVENT_TRACKING, true);
            var event = new WorldEventDefinition("Alpha", "alpha", null, null, null, null,
                    List.of(new WorldEventLocation(1, 2, 3, 4, 5)), Instant.now());
            var mode = new WorldEventMapMode(() -> new WorldEventService.Snapshot(List.of(event), 1, Instant.now()),
                    () -> "Ready", settings, new WorldEventMapMode.Tracking() {
                        public Set<String> ids() { return tracked; }
                        public void setTracked(String id, boolean value) { if (value) tracked.add(id); else tracked.remove(id); }
                    });
            mode.refresh();
            float inputY = mode.worldEventSidebarLayout().eventInputY();
            assertTrue(mode.clickSidebar(frame(20, inputY + 5)));
            assertTrue(mode.dropdownOpen());
            assertTrue(mode.charTyped("Alpha"));
            assertTrue(mode.keyPressed(GLFW.GLFW_KEY_ENTER));
            assertEquals(Set.of("alpha"), tracked);
            assertTrue(mode.keyPressed(GLFW.GLFW_KEY_ENTER));
            assertTrue(tracked.isEmpty(), "second autocomplete toggles against the refreshed tracking cache");
            mode.deactivate();
            assertFalse(mode.dropdownOpen());
            assertFalse(mode.charTyped("Alpha"));
            assertFalse(mode.keyPressed(GLFW.GLFW_KEY_ENTER));
            assertNull(mode.selected());
            assertNull(mode.hovered());
        } finally {
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.EVENT_TRACKING, previous);
        }
    }

    @Test
    void inputOutsideSidebarDoesNotToggleControlsAndOpenDropdownConsumesOutsideClick() {
        var settings = WorldMapSettings.getInstance();
        boolean previous = settings.sidebarPanelExpanded(WorldMapSidebarPanel.EVENT_TRACKING);
        try {
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.EVENT_TRACKING, true);
            var mode = new WorldEventMapMode(() -> new WorldEventService.Snapshot(List.of(), 0, null),
                    () -> "Ready", settings, new WorldEventMapMode.Tracking() {
                        public Set<String> ids() { return Set.of(); }
                        public void setTracked(String id, boolean value) { fail(); }
                    });
            assertFalse(mode.clickSidebar(frame(400, 100)));
            assertFalse(mode.scroll(frame(400, 100), 1));
            mode.clickSidebar(frame(20, mode.worldEventSidebarLayout().eventInputY() + 5));
            assertTrue(mode.clickSidebar(frame(400, 100)));
            assertFalse(mode.dropdownOpen());
        } finally {
            settings.setSidebarPanelExpanded(WorldMapSidebarPanel.EVENT_TRACKING, previous);
        }
    }

    private WorldMapFrame frame(float x, float y) {
        return new WorldMapFrame(1000, 700, x, y, new MapViewport(0, 0, 1, 230, 0, 520, 700), false);
    }
}
