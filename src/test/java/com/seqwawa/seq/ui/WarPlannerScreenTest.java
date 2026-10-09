package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.map.MapCalibration;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.map.MapViewport;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarCompositionTargets;
import com.seqwawa.seq.model.war.WarPlannerDrafts.TeamMemberMoveDraft;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarTeamType;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.Participant;
import com.seqwawa.seq.model.war.WarTerritoryQueueFeed.TerritoryQueue;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WarPlannerScreenTest {
    @Test
    void zoomedOutMapKeepsFocusButSuppressesTinyOutlinesAndUnrelatedConnections() {
        assertEquals(0, WarMapGeometry.territoryOutlineWeight(3, 1.2f));
        assertTrue(WarMapGeometry.territoryOutlineWeight(10, 1.2f)
                < WarMapGeometry.territoryOutlineWeight(50, 1.2f));
        assertEquals(2, WarMapGeometry.territoryOutlineWeight(3, 2));
        assertFalse(WarMapGeometry.warConnectionVisible(.08f, false));
        assertTrue(WarMapGeometry.warConnectionVisible(.08f, true));
        assertTrue(WarMapGeometry.warConnectionVisible(.3f, false));
    }

    @Test
    void resourceModePreservesBordersAndConnectionsAtEveryZoom() {
        for (float size : new float[] {2, 4, 10, 50}) {
            for (float weight : new float[] {.55f, .75f, 1.8f}) {
                assertEquals(weight, WarMapGeometry.territoryOutlineWeight(size, weight, true));
            }
        }
        assertTrue(WarMapGeometry.warConnectionVisible(.08f, false, true));
        assertFalse(WarMapGeometry.warConnectionVisible(.08f, false, false));
    }

    @Test
    void availabilityCountdownCarriesRoundedMinutesIntoWholeHours() {
        assertEquals("30m", WarPlannerScreen.formatDuration(Duration.ofMinutes(30)));
        assertEquals("1h", WarPlannerScreen.formatDuration(Duration.ofSeconds(3_599)));
        assertEquals("2h", WarPlannerScreen.formatDuration(Duration.ofSeconds(7_199)));
        assertEquals("1h 1m", WarPlannerScreen.formatDuration(Duration.ofMinutes(61)));
    }

    @Test
    void plannerUsesTheSharedSidebarAndRemainingScreenWidth() {
        for (float width : new float[] {420, 640, 960, 1920}) {
            var viewport = WarPlannerScreen.plannerViewport(width);
            assertEquals(SequoiaSidebarNavigation.WIDTH, viewport.x());
            assertEquals(width, viewport.x() + viewport.width());
        }
    }

    @Test
    void headerDropdownAndActionsFitBesideTheTitleAtAllSupportedWidths() {
        for (float width : new float[] {280, 320, 420, 659, 660, 820, 1780}) {
            var header = WarPlannerScreen.headerControls(width);
            assertTrue(header.section().x() + header.section().width() < header.roles().x());
            assertTrue(header.roles().x() + header.roles().width() < header.refresh().x());
            assertTrue(header.refresh().x() + header.refresh().width() < width - (width < 420 ? 70 : 125));
            var opacity = header.opacity();
            assertTrue(opacity.x() >= 0 && opacity.x() + opacity.width() <= width);
            assertTrue(opacity.y() + opacity.height() <= WarMapGeometry.headerHeight(width));
            if (width >= 660) {
                assertTrue(opacity.x() > header.refresh().x() + header.refresh().width());
                assertTrue(opacity.x() + opacity.width() < width - 125);
            } else {
                assertTrue(opacity.y() > header.refresh().y() + header.refresh().height());
            }

        }
    }

    @Test
    void panelOpacityScalesTheConfiguredThemeAlpha() {
        assertEquals(100, WarPlannerDialogUi.opacityAlpha(200, 50));
        for (int alpha : new int[] {0, 1, 42, 127, 254, 255}) {
            assertEquals(alpha, WarPlannerDialogUi.opacityAlpha(alpha, 100));
            assertEquals(0, WarPlannerDialogUi.opacityAlpha(alpha, 0));
        }
        assertFalse(WarPlannerDialogUi.shouldBlurBackground(95));
        assertTrue(WarPlannerDialogUi.shouldBlurBackground(100));
    }

    @Test
    void availabilityControlsStayInsideCompactScreens() {
        WarPlannerScreen.AvailabilityLayout compact = WarPlannerScreen.availabilityLayout(320);
        WarPlannerScreen.AvailabilityLayout wide = WarPlannerScreen.availabilityLayout(680);

        assertTrue(compact.compact());
        assertTrue(compact.buttonX(4) + compact.buttonWidth(4) <= 320 - 12);
        assertFalse(wide.compact());
        assertEquals(320, wide.x());
        assertEquals(76, wide.buttonWidth(3));
        assertTrue(wide.buttonX(4) + wide.buttonWidth(4) <= 680 - 10);
    }

    @Test
    void mapSwitchesStayInTheBottomRightAndColorDropdownStaysInTheSidebar() {
        for (float width : new float[] {280, 320, 500, 820, 1780}) {
            var layout = WarMapGeometry.warMapLayout(width, 86, 610);
            for (boolean manager : new boolean[] {false, true}) {
                var controls = WarMapGeometry.warMapControls(layout, manager);
                for (var control : List.of(controls.fit(), controls.panel(), controls.queues(), controls.players())) {
                    assertTrue(control.x() >= layout.mapX());
                    assertTrue(control.x() + control.width() <= layout.mapX() + layout.mapWidth());
                    assertTrue(control.y() >= layout.mapY());
                    assertTrue(control.y() + control.height() <= layout.mapY() + layout.mapHeight());
                }
                assertTrue(controls.coloring().x() >= layout.sidebarX());
                assertTrue(controls.coloring().x() + controls.coloring().width() <= layout.sidebarX() + layout.sidebarWidth());
                assertEquals(manager, controls.lock().visible());
                assertEquals(controls.queues().y() + controls.queues().height(), controls.players().y());
                assertTrue(controls.players().y() + controls.players().height() <= controls.panel().y() + controls.panel().height());
                assertTrue(controls.fit().y() + controls.fit().height() < controls.panel().y());
            }
        }
    }

    @Test
    void warMapKeepsOneCanvasAndACompactSidebar() {
        assertEquals(220, WarMapGeometry.warMapSidebarWidth(1200));
        assertEquals(220, WarMapGeometry.warMapSidebarWidth(900));
        assertEquals(160, WarMapGeometry.warMapSidebarWidth(640));
        assertEquals(150, WarMapGeometry.warMapSidebarWidth(320));
    }

    @Test
    void warMapLayoutKeepsMapAndSidebarSeparateOnNarrowScreens() {
        var layout = WarMapGeometry.warMapLayout(320, 86, 430);
        assertEquals(12, layout.mapX());
        assertEquals(138, layout.mapWidth());
        assertEquals(158, layout.sidebarX());
        assertTrue(layout.mapX() + layout.mapWidth() < layout.sidebarX());
    }

    @Test
    void wideWarMapUsesTheFullContentHeight() {
        var layout = WarMapGeometry.warMapLayout(1200, 86, 610);
        assertEquals(948, layout.mapWidth());
        assertEquals(524, layout.mapHeight());
        assertEquals(968, layout.sidebarX());
        assertEquals(220, layout.sidebarWidth());
        assertTrue(layout.mapWidth() > layout.sidebarWidth() * 2);
        assertFalse(layout.containsMap(layout.sidebarX() + 1, layout.mapY() + 1));
    }

    @Test
    void hiddenZoneLayersAreExcludedWithoutChangingTheSnapshot() {
        WarPlannerSnapshot.Zone north = new WarPlannerSnapshot.Zone(
                1, "North", "#55B8C5", List.of(), 1L, List.of("A"));
        WarPlannerSnapshot.Zone south = new WarPlannerSnapshot.Zone(
                2, "South", "#E05A65", List.of(), 1L, List.of("B"));

        assertEquals(List.of(north), WarZoneSidebar.visibleZones(List.of(north, south), java.util.Set.of(2L)));
        assertEquals(List.of(north, south), WarZoneSidebar.visibleZones(List.of(north, south), java.util.Set.of()));
    }

    @Test
    void queueLabelsIncludeOnlyTerritoriesBelongingToCurrentlyShownZones() {
        WarPlannerSnapshot.Zone shown = new WarPlannerSnapshot.Zone(
                1, "Shown", "#55B8C5", List.of(), 1L, List.of("Alekin", "Shared Territory"));
        WarPlannerSnapshot.Zone hidden = new WarPlannerSnapshot.Zone(
                2, "Hidden", "#E05A65", List.of(), 1L, List.of("Context Only", "Shared Territory"));
        WarPlannerSnapshot.Zone hiddenCategory = new WarPlannerSnapshot.Zone(
                3, "Hidden category", "#E0A65A", List.of(), 1L, List.of("Unlocked Only"), 9L, 0);

        List<WarPlannerSnapshot.Zone> displayed = WarZoneSidebar.visibleZones(
                List.of(shown, hidden, hiddenCategory), Set.of(2L), Set.of(9L));
        GuildTerritory alekin = GuildTerritory.fromCorners("Alekin", 0, 0, 10, 10);
        GuildTerritory context = GuildTerritory.fromCorners("Context Only", 20, 0, 30, 10);
        GuildTerritory unlocked = GuildTerritory.fromCorners("Unlocked Only", 40, 0, 50, 10);
        List<GuildTerritory> allTerritories = List.of(alekin, context, unlocked);
        Map<String, WarPlannerSnapshot.TerritoryDetails> details = Map.of(
                "Alekin", new WarPlannerSnapshot.TerritoryDetails("Alekin", List.of("Context Only"), List.of()));
        Set<String> labelTerritories = WarMapGeometry.shownZoneTerritoryNames(displayed);

        assertEquals(Set.of("alekin", "shared territory"), labelTerritories);
        assertEquals(allTerritories, WarMapGeometry.visibleMapTerritories(allTerritories, displayed, false));
        assertEquals(
                List.of(context),
                WarMapGeometry.oneHopContextTerritories(allTerritories, List.of(alekin), details));
        assertFalse(labelTerritories.contains("context only"));
        assertFalse(labelTerritories.contains("unlocked only"));
    }

    @Test
    void queuedMapTerritoriesShowOnlyFittedMinecraftUsernamesAndExposeFullHoverDetails() {
        Instant now = Instant.parse("2026-08-24T12:12:19Z");
        TerritoryQueue queue = new TerritoryQueue(
                7,
                "Alekin",
                "queuer-uuid",
                "xiaolongbao",
                "Soup Person",
                "Very Low",
                "Very High",
                now.minusSeconds(12 * 60 + 19),
                now.plusSeconds(160),
                List.of(
                        new Participant("one", "One", 0),
                        new Participant("two", "Two", 1),
                        new Participant("three", "Three", 2)));

        assertEquals("xiaolongbao", WarQueueMapOverlay.warQueueMapUsername(queue));
        assertEquals("xiao…", WarQueueMapOverlay.fitWarQueueText("xiaolongbao", 5, String::length));
        assertEquals(
                List.of("Party 5/5 ·", "One, Two,", "Three"),
                WarQueueMapOverlay.wrapWarQueueText("Party 5/5 · One, Two, Three", 12, String::length));
        assertEquals(
                List.of(
                        "xiaolongbao/Soup Person",
                        "Alekin · Defense Very Low/Very High",
                        "Queued 12m 19s ago · 02:40 remaining",
                        "Party 3/5 · One, Two, Three"),
                WarQueueMapOverlay.warQueueTooltipLines(queue, now));
        assertEquals(
                "You own this queue · owner remains joined",
                WarQueueMapOverlay.warQueueActionHint(queue, "queuer-uuid"));
        assertEquals("Double-click to leave", WarQueueMapOverlay.warQueueActionHint(queue, "two"));
        assertEquals("Double-click to join", WarQueueMapOverlay.warQueueActionHint(queue, "other"));
    }

    @Test
    void timerOnlyQueueUsesUnknownMapLabelTooltipAndReservedOwnerJoinCapacity() {
        Instant now = Instant.parse("2026-08-24T12:12:19Z");
        TerritoryQueue provisional = new TerritoryQueue(
                8,
                "Alekin",
                null,
                null,
                null,
                null,
                null,
                now.minusSeconds(19),
                now.plusSeconds(101),
                List.of());
        GuildTerritory alekin = GuildTerritory.fromCorners("Alekin", 0, 0, 20, 10);

        assertEquals("Unknown", WarQueueMapOverlay.warQueueMapUsername(provisional));
        assertEquals(
                List.of(
                        "Unknown",
                        "Alekin · Defense Unknown",
                        "Queued 19s ago · 01:41 remaining",
                        "Party 1/5"),
                WarQueueMapOverlay.warQueueTooltipLines(provisional, now));
        assertEquals("Double-click to join", WarQueueMapOverlay.warQueueActionHint(provisional, "self"));
        assertEquals(
                provisional,
                WarQueueMapOverlay.warQueueForTerritory(
                        WarQueueMapOverlay.warQueueMapMarkers(
                                List.of(provisional), Set.of("alekin"), Map.of("Alekin", alekin)),
                        "Alekin"));

        TerritoryQueue full = new TerritoryQueue(
                8,
                "Alekin",
                null,
                null,
                null,
                null,
                null,
                provisional.queuedAt(),
                provisional.expiresAt(),
                List.of(
                        new Participant("one", "One", 1),
                        new Participant("two", "Two", 2),
                        new Participant("three", "Three", 3),
                        new Participant("four", "Four", 4)));
        assertEquals("Party 5/5 · One, Two, Three, Four", WarQueueMapOverlay.warQueueTooltipLines(full, now).get(3));
        assertEquals("Queue full", WarQueueMapOverlay.warQueueActionHint(full, "self"));
        assertEquals("Double-click to leave", WarQueueMapOverlay.warQueueActionHint(full, "four"));
    }

    @Test
    void queuedMapLabelBoundsRemainInsideTheProjectedTerritoryBox() {
        GuildTerritory territory = GuildTerritory.fromCorners("Alekin", 0, 0, 20, 10);

        WarQueueMapOverlay.WarQueueLabelBounds label = WarQueueMapOverlay.warQueueLabelBounds(
                territory, new MapBounds(0, 0, 100, 100), 10, 20, 2, 25, 12);

        assertEquals(new WarQueueMapOverlay.WarQueueLabelBounds(17.5f, 24, 25, 12), label);
        assertTrue(label.x() >= 10 && label.x() + label.width() <= 50);
        assertTrue(label.y() >= 20 && label.y() + label.height() <= 40);
    }

    @Test
    void queuedMapMarkersIncludeOnlyShownTerritoriesAndDeduplicateCaseInsensitively() {
        GuildTerritory alekin = GuildTerritory.fromCorners("Alekin", 0, 0, 20, 10);
        GuildTerritory context = GuildTerritory.fromCorners("Context Only", 20, 0, 40, 10);
        TerritoryQueue shown = territoryQueue(1, "alekin", "Very Low", null);
        TerritoryQueue duplicate = territoryQueue(2, "ALEKIN", "High", null);
        TerritoryQueue hidden = territoryQueue(3, "Context Only", "Medium", null);
        TerritoryQueue missing = territoryQueue(4, "Unknown", "Low", null);

        List<WarQueueMapOverlay.WarQueueMapMarker> markers = WarQueueMapOverlay.warQueueMapMarkers(
                List.of(shown, duplicate, hidden, missing),
                Set.of(" ALEKIN "),
                Map.of("Alekin", alekin, "Context Only", context));

        assertEquals(List.of(new WarQueueMapOverlay.WarQueueMapMarker(shown, alekin)), markers);
        assertEquals(shown, WarQueueMapOverlay.warQueueForTerritory(markers, "ALEKIN"));
        assertEquals(null, WarQueueMapOverlay.warQueueForTerritory(markers, "Context Only"));
        assertEquals(List.of(), WarQueueMapOverlay.warQueueMapMarkers(null, Set.of("alekin"), Map.of()));
    }

    @Test
    void warMapQueueFilterUsesTheHudOwnedOrJoinedRulesWithoutItsRowLimit() {
        TerritoryQueue unrelated = territoryQueue(1, "Unrelated", "Very Low", null);
        TerritoryQueue owned = new TerritoryQueue(
                2,
                "Owned",
                "self",
                "Self",
                "Self",
                "Low",
                null,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(60),
                List.of(new Participant("other", "Other", 0)));
        TerritoryQueue joined = new TerritoryQueue(
                3,
                "Joined",
                "other",
                "Other",
                "Other",
                "Medium",
                null,
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(60),
                List.of(new Participant("SELF", "Self", 0)));

        assertEquals(
                List.of(unrelated, owned, joined),
                WarQueueMapOverlay.warQueuesForMap(List.of(unrelated, owned, joined), "self", false));
        assertEquals(
                List.of(owned, joined),
                WarQueueMapOverlay.warQueuesForMap(List.of(unrelated, owned, joined), "self", true));
        assertEquals(
                List.of(),
                WarQueueMapOverlay.warQueuesForMap(List.of(owned, joined), null, true));
    }

    @Test
    void queuedTerritoryPulseUsesCapturedTierAndAStablePeriodicAlpha() {
        TerritoryQueue exactQueue = territoryQueue(1, "Alekin", "Very Low", "Very High");
        TerritoryQueue observedQueue = territoryQueue(2, "Detlas", null, "Very High");

        assertEquals("Very Low", WarQueueMapOverlay.warQueuePulseDefense(exactQueue));
        assertEquals("Very High", WarQueueMapOverlay.warQueuePulseDefense(observedQueue));
        Color trough = WarQueueMapOverlay.warQueuePulseColor(exactQueue, 0);
        Color peak = WarQueueMapOverlay.warQueuePulseColor(exactQueue, 800);
        Color observed = WarQueueMapOverlay.warQueuePulseColor(observedQueue, 800);
        assertEquals(new Color(0x00AA00), new Color(trough.getRed(), trough.getGreen(), trough.getBlue()));
        assertEquals(new Color(0x00AA00), new Color(peak.getRed(), peak.getGreen(), peak.getBlue()));
        assertEquals(
                new Color(0xAA0000),
                new Color(observed.getRed(), observed.getGreen(), observed.getBlue()));
        assertEquals(36, trough.getAlpha());
        assertEquals(96, peak.getAlpha());

        assertEquals(36, WarQueueMapOverlay.warQueuePulseAlpha(0));
        assertEquals(66, WarQueueMapOverlay.warQueuePulseAlpha(400));
        assertEquals(96, WarQueueMapOverlay.warQueuePulseAlpha(800));
        assertEquals(66, WarQueueMapOverlay.warQueuePulseAlpha(1_200));
        assertEquals(36, WarQueueMapOverlay.warQueuePulseAlpha(1_600));
        assertEquals(96, WarQueueMapOverlay.warQueuePulseAlpha(-800));
        assertEquals(
                WarQueueMapOverlay.warQueuePulseAlpha(137),
                WarQueueMapOverlay.warQueuePulseAlpha(1_737));
        for (long elapsed = -3_200; elapsed <= 3_200; elapsed += 37) {
            int alpha = WarQueueMapOverlay.warQueuePulseAlpha(elapsed);
            assertTrue(alpha >= 36 && alpha <= 96);
        }
    }

    @Test
    void resourceFillsUseFixedTransparencyWithoutChangingThePalette() {
        for (String resource : List.of("EMERALD", "ORE", "WOOD", "FISH", "CROP")) {
            Color palette = WarTerritoryPickerScreen.resourceColor(resource);
            Color fill = WarTerritoryPickerScreen.resourceFillColor(palette, WarPlannerScreen.RESOURCE_FILL_ALPHA);
            assertEquals(palette.getRGB() & 0xffffff, fill.getRGB() & 0xffffff);
            assertEquals(96, fill.getAlpha());
        }
    }

    @Test
    void queuedTerritoryDoubleClickRequiresSameQueueTimeWindowAndPointerLocation() {
        WarQueueMapOverlay.PendingWarQueueClick first =
                new WarQueueMapOverlay.PendingWarQueueClick(42, "Alekin", 100, 80, 1_000);

        assertTrue(WarQueueMapOverlay.isWarQueueDoubleClick(first, 42, "alekin", 102, 81, 1_350));
        assertFalse(WarQueueMapOverlay.isWarQueueDoubleClick(first, 42, "Alekin", 102, 81, 1_351));
        assertFalse(WarQueueMapOverlay.isWarQueueDoubleClick(first, 43, "Alekin", 102, 81, 1_200));
        assertFalse(WarQueueMapOverlay.isWarQueueDoubleClick(first, 42, "Lutho", 102, 81, 1_200));
        assertFalse(WarQueueMapOverlay.isWarQueueDoubleClick(first, 42, "Alekin", 104, 80, 1_200));
        assertTrue(WarQueueMapOverlay.warQueueClickMoved(first, 104, 80));
    }

    @Test
    void fittedWarMapViewportCentersAndFitsRequestedBounds() {
        WarMapGeometry.WarMapLayout layout = new WarMapGeometry.WarMapLayout(12, 100, 400, 300, 420, 180);
        MapViewport viewport = WarMapGeometry.fittedWarMapViewport(
                new MapBounds(-1000, -3000, 1000, -2000), layout);

        assertEquals(0, viewport.centerX());
        assertEquals(-2500, viewport.centerZ());
        assertEquals(.195, viewport.pixelsPerBlock(), .0001);
        assertTrue(viewport.visibleBounds().minX() <= -1000);
        assertTrue(viewport.visibleBounds().maxX() >= 1000);
    }

    @Test
    void fittedViewportUsesWiderCanvasAndKeepsRequestedWorldBoundsInteractive() {
        MapBounds requested = new MapBounds(-1500, -500, 1500, 500);
        WarMapGeometry.WarMapLayout narrowLayout = WarMapGeometry.warMapLayout(320, 110, 430);
        WarMapGeometry.WarMapLayout wideLayout = WarMapGeometry.warMapLayout(1200, 110, 610);

        MapViewport narrow = WarMapGeometry.fittedWarMapViewport(requested, narrowLayout);
        MapViewport wide = WarMapGeometry.fittedWarMapViewport(requested, wideLayout);

        assertTrue(wide.pixelsPerBlock() > narrow.pixelsPerBlock());
        assertEquals(wideLayout.mapX(), wide.screenX());
        assertEquals(wideLayout.mapY(), wide.screenY());
        assertEquals(wideLayout.mapWidth(), wide.screenWidth());
        assertEquals(wideLayout.mapHeight(), wide.screenHeight());
        for (double worldX : List.of(requested.minX(), requested.maxX())) {
            float screenX = wide.worldToScreenX(worldX);
            assertTrue(screenX >= wideLayout.mapX() && screenX <= wideLayout.mapX() + wideLayout.mapWidth());
            assertEquals(worldX, wide.screenToWorldX(screenX), .001);
        }
        for (double worldZ : List.of(requested.minZ(), requested.maxZ())) {
            float screenY = wide.worldToScreenZ(worldZ);
            assertTrue(screenY >= wideLayout.mapY() && screenY <= wideLayout.mapY() + wideLayout.mapHeight());
            assertEquals(worldZ, wide.screenToWorldZ(screenY), .001);
        }
        assertTrue(wide.isInsideScreen(wide.worldToScreenX(requested.minX()), wide.worldToScreenZ(requested.minZ())));
        assertTrue(wide.isInsideScreen(wide.worldToScreenX(requested.maxX()), wide.worldToScreenZ(requested.maxZ())));
    }

    @Test
    void lockedWarMapKeepsManualCameraUntilModeOrViewportChanges() {
        WarMapGeometry.WarMapLayout layout = WarMapGeometry.warMapLayout(1200, 110, 610);

        assertFalse(WarMapGeometry.shouldRefitWarMap(
                true, layout.mapWidth(), layout.mapHeight(), true, layout, true));
        assertTrue(WarMapGeometry.shouldRefitWarMap(
                false, layout.mapWidth(), layout.mapHeight(), true, layout, true));
        assertTrue(WarMapGeometry.shouldRefitWarMap(
                true, layout.mapWidth(), layout.mapHeight(), false, layout, true));
        assertTrue(WarMapGeometry.shouldRefitWarMap(
                true, layout.mapWidth() - 1, layout.mapHeight(), true, layout, true));
    }

    @Test
    void lockedMapAddsOnlyDirectConnectionNeighborsAsGreyContext() {
        GuildTerritory a = GuildTerritory.fromCorners("A", 0, 0, 10, 10);
        GuildTerritory b = GuildTerritory.fromCorners("B", 20, 0, 30, 10);
        GuildTerritory c = GuildTerritory.fromCorners("C", 40, 0, 50, 10);
        Map<String, WarPlannerSnapshot.TerritoryDetails> details = Map.of(
                "A", new WarPlannerSnapshot.TerritoryDetails("A", List.of("B"), List.of()),
                "B", new WarPlannerSnapshot.TerritoryDetails("B", List.of("A", "C"), List.of()));

        assertEquals(
                List.of(b),
                WarMapGeometry.oneHopContextTerritories(List.of(a, b, c), List.of(a), details));
    }

    @Test
    void mapHoverHitTestingOnlyReturnsDisplayedTerritories() {
        GuildTerritory territory = GuildTerritory.fromCorners("Detlas", 0, 0, 10, 10);
        GuildTerritoryIndex index = new GuildTerritoryIndex(List.of(territory));
        MapViewport viewport = new MapViewport(5, 5, 10, 0, 0, 100, 100);

        assertEquals(territory, WarMapGeometry.territoryAt(index, viewport, java.util.Set.of("detlas"), 50, 50));
        assertEquals(null, WarMapGeometry.territoryAt(index, viewport, java.util.Set.of(), 50, 50));
        assertEquals(null, WarMapGeometry.territoryAt(index, viewport, java.util.Set.of("detlas"), 150, 50));
    }

    @Test
    void teamTypePreviewUsesIndependentAutomaticSequencesAndUniqueHq() {
        assertEquals(4, WarTeamType.values().length);
        assertEquals(3, WarTeamType.editableValues().size());
        WarPlannerSnapshot snapshot = snapshot(
                List.of(new WarPlannerSnapshot.Team(1, "HQ Team", 1L, List.of()),
                        new WarPlannerSnapshot.Team(2, "VLow Munch 2", 1L, List.of()),
                        new WarPlannerSnapshot.Team(3, "FFA 2", 1L, List.of())),
                List.of());

        assertEquals(WarTeamType.VLOW_MUNCH, WarTeamEditor.defaultTeamType(snapshot));
        assertEquals("VLow Munch 1", WarTeamEditor.automaticTeamName(snapshot, WarTeamType.VLOW_MUNCH, null));
        assertEquals("FFA 1", WarTeamEditor.automaticTeamName(snapshot, WarTeamType.FFA, null));
        assertFalse(WarTeamEditor.teamTypeSelectable(snapshot, WarTeamType.HQ, null));
        assertTrue(WarTeamEditor.teamTypeSelectable(snapshot, WarTeamType.HQ, 1L));
        assertEquals("HQ Team", WarTeamEditor.automaticTeamName(snapshot, WarTeamType.HQ, 1L));
        assertFalse(WarTeamEditor.teamTypeSelectable(snapshot, WarTeamType.UNKNOWN, null));
    }

    @Test
    void explicitTeamTypeDoesNotDependOnItsDisplayName() {
        WarPlannerSnapshot.Team team = new WarPlannerSnapshot.Team(
                7, "Alpha", WarTeamType.FFA, 3L, WarCompositionTargets.NONE, List.of());

        assertEquals(WarTeamType.FFA, team.teamType());
        assertEquals("Alpha", WarTeamEditor.automaticTeamName(snapshot(List.of(team), List.of()), WarTeamType.FFA, 7L));
    }

    @Test
    void teamEditorBaseRejectsARefreshThatChangesVersionTypeOrMembers() {
        WarPlannerSnapshot.Team original = new WarPlannerSnapshot.Team(
                7,
                "FFA 1",
                WarTeamType.FFA,
                3L,
                WarCompositionTargets.NONE,
                List.of(new WarPlannerSnapshot.TeamMember("a", "A", 0)));
        WarTeamEditor.TeamEditorBase base = WarTeamEditor.TeamEditorBase.from(original);

        assertTrue(base.matches(original));
        assertFalse(base.matches(new WarPlannerSnapshot.Team(
                7, "FFA 1", WarTeamType.FFA, 4L, WarCompositionTargets.NONE, original.members())));
        assertFalse(base.matches(new WarPlannerSnapshot.Team(
                7, "VLow Munch 1", WarTeamType.VLOW_MUNCH, 3L, WarCompositionTargets.NONE, original.members())));
        assertFalse(base.matches(new WarPlannerSnapshot.Team(
                7,
                "FFA 1",
                WarTeamType.FFA,
                3L,
                WarCompositionTargets.NONE,
                List.of(new WarPlannerSnapshot.TeamMember("b", "B", 0)))));
    }

    @Test
    void dragMoveHelperCarriesCapturedSourceAndCurrentTargetVersions() {
        WarPlannerSnapshot.Team target = new WarPlannerSnapshot.Team(
                9, "FFA 2", WarTeamType.FFA, 5L, WarCompositionTargets.NONE, List.of());

        TeamMemberMoveDraft betweenTeams = WarTeamsTab.teamMemberMoveDraft(7L, 3L, target);
        TeamMemberMoveDraft toRoster = WarTeamsTab.teamMemberMoveDraft(7L, 3L, null);

        assertEquals(7L, betweenTeams.sourceTeamId());
        assertEquals(3L, betweenTeams.sourceVersion());
        assertEquals(9L, betweenTeams.targetTeamId());
        assertEquals(5L, betweenTeams.targetVersion());
        assertEquals(null, toRoster.targetTeamId());
        assertEquals(null, toRoster.targetVersion());
    }

    @Test
    void teamCardsGiveMembersReadableSpacing() {
        assertEquals(80, WarTeamsTab.teamCardHeight(0));
        assertEquals(80, WarTeamsTab.teamCardHeight(1));
        assertEquals(80, WarTeamsTab.teamCardHeight(2));
        assertEquals(116, WarTeamsTab.teamCardHeight(5));
    }

    @Test
    void narrowManagerTeamActionsStackInsideTheCard() {
        float cardsRight = 120;
        WarTeamsTab.TeamActionLayout actions =
                WarTeamsTab.teamActionLayout(cardsRight, true, true);

        assertTrue(actions.editX() >= 12);
        assertTrue(actions.deleteX() + actions.deleteWidth() <= cardsRight);
        assertTrue(actions.selfX() >= 12);
        assertTrue(actions.selfX() + actions.selfWidth() <= cardsRight);
        assertTrue(actions.selfY() > actions.managerY());
        assertTrue(actions.memberTop() > actions.selfY() + 22);
        assertTrue(WarTeamsTab.teamCardHeight(1, actions) >= actions.memberTop() + 16);
    }

    @Test
    void teamSidebarAndEditorStayCompactOnWideScreens() {
        assertEquals(234, WarTeamsTab.teamSidebarWidth(780), .01f);
        assertEquals(220, WarTeamsTab.teamSidebarWidth(640), .01f);
        assertEquals(560, WarTeamEditor.teamEditorWidth(780));
        assertEquals(496, WarTeamEditor.teamEditorWidth(520));
    }

    @Test
    void adaptiveTeamsLayoutUsesCompactRailAndGridBreakpoints() {
        float top = 118;
        float bottom = 600;

        WarTeamsTab.TeamsLayout compact = WarTeamsTab.teamsLayout(519, top, bottom, 4);
        WarTeamsTab.TeamsLayout oneColumnAtBoundary =
                WarTeamsTab.teamsLayout(520, top, bottom, 4);
        WarTeamsTab.TeamsLayout oneColumnBelowGrid =
                WarTeamsTab.teamsLayout(899, top, bottom, 4);
        WarTeamsTab.TeamsLayout twoColumnsAtBoundary =
                WarTeamsTab.teamsLayout(900, top, bottom, 4);

        assertTrue(compact.compactAuxiliary());
        assertEquals(1, compact.columns());
        assertFalse(oneColumnAtBoundary.compactAuxiliary());
        assertEquals(1, oneColumnAtBoundary.columns());
        assertFalse(oneColumnBelowGrid.compactAuxiliary());
        assertEquals(1, oneColumnBelowGrid.columns());
        assertFalse(twoColumnsAtBoundary.compactAuxiliary());
        assertEquals(2, twoColumnsAtBoundary.columns());
    }

    @Test
    void adaptiveTeamPlacementsStayVisibleAndUseSeparateGridColumns() {
        List<WarPlannerSnapshot.Team> teams = teams(4);
        WarTeamsTab.TeamsLayout layout = WarTeamsTab.teamsLayout(1_000, 118, 600, teams.size());

        List<WarTeamsTab.TeamPlacement> placements =
                WarTeamsTab.teamPlacements(teams, 0, layout, true, true);

        assertEquals(4, placements.size());
        assertTrue(placements.get(0).x() < placements.get(1).x());
        assertEquals(placements.get(0).y(), placements.get(1).y(), .01f);
        assertEquals(2, placements.stream().map(WarTeamsTab.TeamPlacement::x).distinct().count());
        for (WarTeamsTab.TeamPlacement placement : placements) {
            assertTrue(placement.x() >= layout.cardsX());
            assertTrue(placement.x() + placement.width() <= layout.cardsX() + layout.cardsWidth() + .01f);
            assertTrue(placement.y() >= layout.cardsTop());
            assertTrue(placement.visibleHeight() > 0);
            assertTrue(placement.visibleHeight() <= placement.height());
            assertTrue(placement.y() + placement.visibleHeight() <= layout.cardsBottom() + .01f);
        }
    }

    @Test
    void teamGridScrollStartBackfillsTheLastFullRow() {
        List<WarPlannerSnapshot.Team> teams = teams(5);
        WarTeamsTab.TeamsLayout layout = WarTeamsTab.teamsLayout(1_000, 118, 600, teams.size());

        assertEquals(3, WarTeamsTab.teamScrollStart(4, teams.size(), 2));
        assertEquals(
                List.of(3, 4),
                WarTeamsTab.teamPlacements(teams, 4, layout, false, false).stream()
                        .map(WarTeamsTab.TeamPlacement::index)
                        .toList());
    }

    @Test
    void compactAndRailSupportPlacementsRemainInsideTheirPanels() {
        WarTeamsTab.TeamsLayout compact = WarTeamsTab.teamsLayout(519, 118, 600, 4);
        WarTeamsTab.TeamsLayout rail = WarTeamsTab.teamsLayout(640, 118, 600, 4);

        assertTrue(compact.compactAuxiliary());
        assertFalse(rail.compactAuxiliary());
        assertSupportPlacementsInside(compact);
        assertSupportPlacementsInside(rail);
    }

    @Test
    void clippedTeamCardsDoNotExposeInvisibleActionsOrHitArea() {
        List<WarPlannerSnapshot.Team> teams = teams(1);
        WarTeamsTab.TeamsLayout shortLayout =
                WarTeamsTab.teamsLayout(640, 118, 138, teams.size());

        WarTeamsTab.TeamPlacement placement =
                WarTeamsTab.teamPlacements(teams, 0, shortLayout, true, true).getFirst();

        assertEquals(20, placement.visibleHeight());
        assertFalse(placement.fullyShows(placement.actions().managerY(), 22));
        assertFalse(placement.fullyShows(placement.actions().selfY(), 22));
        assertTrue(placement.contains(placement.x() + 1, placement.y() + 19));
        assertFalse(placement.contains(placement.x() + 1, placement.y() + 21));
    }

    @Test
    void compactTeamRolesFollowTheNameWithoutOverflowingTheMemberChip() {
        assertEquals(44, WarTeamsTab.compactRoleX(10, 30, 150, 12));
        assertEquals(138, WarTeamsTab.compactRoleX(10, 200, 150, 12));
    }

    @Test
    void teamMemberRolesComeFromTheRosterAndDefaultToEmpty() {
        WarPlannerSnapshot.RosterMember member = new WarPlannerSnapshot.RosterMember(
                "member", "Member", null, null, List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS),
                true, false, null, 1L);
        WarPlannerSnapshot snapshot = snapshot(List.of(), List.of(member));

        assertEquals(
                List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS),
                WarTeamsTab.teamMemberRoles(snapshot, "member"));
        assertEquals(List.of(), WarTeamsTab.teamMemberRoles(snapshot, "missing"));
    }

    @Test
    void compositionTargetsReportOnlyMissingCapabilities() {
        WarPlannerSnapshot.RosterMember dps = new WarPlannerSnapshot.RosterMember(
                "dps", "Dps", null, null, List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS),
                true, false, null, 1L);
        WarPlannerSnapshot.RosterMember tank = new WarPlannerSnapshot.RosterMember(
                "tank", "Tank", null, null, List.of(WarCompositionRole.TANK),
                true, false, null, 1L);
        WarPlannerSnapshot.Team team = new WarPlannerSnapshot.Team(
                1,
                "HQ Team",
                1L,
                new WarCompositionTargets(1, 2, 1),
                List.of(
                        new WarPlannerSnapshot.TeamMember("dps", "Dps", 0),
                        new WarPlannerSnapshot.TeamMember("tank", "Tank", 1)));
        WarPlannerSnapshot snapshot = snapshot(List.of(team), List.of(dps, tank));

        assertEquals(1, WarTeamsTab.teamCompositionCount(snapshot, team, WarCompositionRole.DPS));
        assertEquals("Need D1", WarTeamsTab.compositionTargetStatus(snapshot, team));
    }

    @Test
    void unassignedDragPoolContainsOnlyOnlineUnassignedMembers() {
        WarPlannerSnapshot.RosterMember free = rosterMember(
                "free", "Free", List.of(WarCompositionRole.DPS), true);
        WarPlannerSnapshot.RosterMember assigned = new WarPlannerSnapshot.RosterMember(
                "assigned", "Assigned", null, null, List.of(WarCompositionRole.TANK),
                true, true, null, 1L);
        WarPlannerSnapshot.RosterMember offline = new WarPlannerSnapshot.RosterMember(
                "offline", "Offline", null, null, List.of(WarCompositionRole.SOLO),
                false, true, null, null);

        assertEquals(
                List.of("free"),
                WarTeamsTab.unassignedOnlineRoster(snapshot(List.of(), List.of(offline, assigned, free))).stream()
                        .map(WarPlannerSnapshot.RosterMember::playerUuid)
                        .toList());
    }

    @Test
    void ownTeamControlsJoinSwitchAndLeaveWithoutAnHqRoleGate() {
        WarPlannerSnapshot.Team hq = new WarPlannerSnapshot.Team(1, "HQ Team", 1L, List.of());
        WarPlannerSnapshot.Team ffa = new WarPlannerSnapshot.Team(2, "FFA 1", 1L, List.of());
        WarPlannerSnapshot.RosterMember solo = new WarPlannerSnapshot.RosterMember(
                "self", "Self", null, null, List.of(WarCompositionRole.SOLO), true, true, null, null);
        WarPlannerSnapshot soloSnapshot = snapshot(List.of(hq, ffa), List.of(solo));

        assertTrue(WarTeamsTab.canChangeOwnTeam(soloSnapshot, hq));
        assertEquals("Join", WarTeamsTab.teamMembershipActionLabel(soloSnapshot, hq));
        assertTrue(WarTeamsTab.canChangeOwnTeam(soloSnapshot, ffa));
        assertEquals("Join", WarTeamsTab.teamMembershipActionLabel(soloSnapshot, ffa));

        WarPlannerSnapshot.RosterMember tank = new WarPlannerSnapshot.RosterMember(
                "self", "Self", null, null, List.of(WarCompositionRole.SOLO, WarCompositionRole.TANK),
                true, true, null, 1L);
        WarPlannerSnapshot tankSnapshot = snapshot(List.of(hq, ffa), List.of(tank));
        assertTrue(WarTeamsTab.canChangeOwnTeam(tankSnapshot, hq));
        assertEquals("Leave", WarTeamsTab.teamMembershipActionLabel(tankSnapshot, hq));
        assertEquals("Switch", WarTeamsTab.teamMembershipActionLabel(tankSnapshot, ffa));
    }

    @Test
    void onlineWarRosterSortsByAvailabilityThenRoleCountThenName() {
        WarPlannerSnapshot.RosterMember unavailable = rosterMember(
                "unavailable", "Able", List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS, WarCompositionRole.TANK), false);
        WarPlannerSnapshot.RosterMember flexible = rosterMember(
                "flexible", "Zulu", List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS), true);
        WarPlannerSnapshot.RosterMember alpha = rosterMember(
                "alpha", "Alpha", List.of(WarCompositionRole.SOLO), true);
        WarPlannerSnapshot.RosterMember bravo = rosterMember(
                "bravo", "Bravo", List.of(WarCompositionRole.SOLO), true);

        List<String> ordered = WarPlannerScreen.sortedWarRoster(
                        snapshot(List.of(), List.of(unavailable, bravo, flexible, alpha)))
                .stream()
                .map(WarPlannerSnapshot.RosterMember::playerUuid)
                .toList();

        assertEquals(List.of("flexible", "alpha", "bravo", "unavailable"), ordered);
    }

    @Test
    void teamEditorListsEveryPlayerOnlineFirstThenRoleCountThenName() {
        WarPlannerSnapshot.RosterMember flexible = new WarPlannerSnapshot.RosterMember(
                "flexible", "Zulu", null, null, List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS),
                true, false, null, null);
        WarPlannerSnapshot.RosterMember alpha = new WarPlannerSnapshot.RosterMember(
                "alpha", "Alpha", null, null, List.of(WarCompositionRole.TANK),
                true, false, null, null);
        WarPlannerSnapshot.RosterMember bravo = new WarPlannerSnapshot.RosterMember(
                "bravo", "bravo", null, null, List.of(WarCompositionRole.SOLO),
                true, true, null, 1L);
        WarPlannerSnapshot.RosterMember onlineWithoutRoles = new WarPlannerSnapshot.RosterMember(
                "online-empty", "Cedar", null, null, List.of(),
                true, false, null, null);
        WarPlannerSnapshot.RosterMember offlineFlexible = new WarPlannerSnapshot.RosterMember(
                "offline-flexible", "Delta", null, null,
                List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS, WarCompositionRole.TANK),
                false, true, null, 2L);
        WarPlannerSnapshot.RosterMember offlineWithoutRoles = new WarPlannerSnapshot.RosterMember(
                "offline-empty", "Echo", null, null, List.of(),
                false, false, null, null);

        List<String> ordered = WarTeamEditor.teamEditorRoster(
                        snapshot(List.of(), List.of(
                                offlineWithoutRoles,
                                bravo,
                                offlineFlexible,
                                onlineWithoutRoles,
                                flexible,
                                alpha)),
                        "")
                .stream()
                .map(WarPlannerSnapshot.RosterMember::playerUuid)
                .toList();

        assertEquals(
                List.of("flexible", "alpha", "bravo", "online-empty", "offline-flexible", "offline-empty"),
                ordered);
    }

    @Test
    void teamEditorSearchIsTrimmedCaseInsensitiveAndKeepsRosterOrdering() {
        WarPlannerSnapshot.RosterMember online = new WarPlannerSnapshot.RosterMember(
                "online-sage", "sagebrush", null, null, List.of(WarCompositionRole.DPS),
                true, false, null, null);
        WarPlannerSnapshot.RosterMember offline = new WarPlannerSnapshot.RosterMember(
                "royal-id", "RoyalSage", null, "DiscordAlias", List.of(WarCompositionRole.SOLO),
                false, false, null, null);
        WarPlannerSnapshot.RosterMember other = new WarPlannerSnapshot.RosterMember(
                "other", "Other", null, null, List.of(),
                true, false, null, null);
        WarPlannerSnapshot snapshot = snapshot(List.of(), List.of(offline, other, online));

        assertEquals(
                List.of("online-sage", "royal-id"),
                WarTeamEditor.teamEditorRoster(snapshot, "  SaGe ").stream()
                        .map(WarPlannerSnapshot.RosterMember::playerUuid)
                        .toList());
        assertEquals(List.of(offline), WarTeamEditor.teamEditorRoster(snapshot, "discordalias"));
        assertEquals(List.of(offline), WarTeamEditor.teamEditorRoster(snapshot, "ROYAL-ID"));
        assertEquals(3, WarTeamEditor.teamEditorRoster(snapshot, "   ").size());
        assertTrue(WarTeamEditor.teamEditorRoster(snapshot, "missing").isEmpty());
    }

    @Test
    void warPingCandidatesRequireManagerAccessOnlinePresenceDiscordAndANonSelfTarget() {
        WarPlannerSnapshot.RosterMember linked = new WarPlannerSnapshot.RosterMember(
                "linked", "Zulu", "123", "DiscordAlias", List.of(WarCompositionRole.SOLO),
                true, true, null, null);
        WarPlannerSnapshot.RosterMember linkedFirst = new WarPlannerSnapshot.RosterMember(
                "linked-first", "Alpha", "234", null, List.of(WarCompositionRole.DPS),
                true, true, null, null);
        WarPlannerSnapshot.RosterMember offline = new WarPlannerSnapshot.RosterMember(
                "offline", "Offline", "345", null, List.of(WarCompositionRole.SOLO),
                false, true, null, null);
        WarPlannerSnapshot.RosterMember unlinked = rosterMember(
                "unlinked", "Unlinked", List.of(WarCompositionRole.SOLO), true);
        WarPlannerSnapshot.RosterMember self = new WarPlannerSnapshot.RosterMember(
                "self", "Self", "456", null, List.of(WarCompositionRole.SOLO), true, true, null, null);
        WarPlannerSnapshot snapshot = snapshot(
                List.of(), List.of(linked, offline, unlinked, self, linkedFirst));

        assertTrue(WarPingPicker.canPingPlayer(snapshot, linked));
        assertFalse(WarPingPicker.canPingPlayer(snapshot, offline));
        assertFalse(WarPingPicker.canPingPlayer(snapshot, unlinked));
        assertFalse(WarPingPicker.canPingPlayer(snapshot, self));
        assertFalse(WarPingPicker.canPingPlayer(snapshot.withCanManage(false), linked));
        assertEquals(
                List.of("linked-first", "linked"),
                WarPingPicker.pingCandidates(snapshot, "").stream()
                        .map(WarPlannerSnapshot.RosterMember::playerUuid)
                        .toList());
        assertEquals(List.of(linked), WarPingPicker.pingCandidates(snapshot, "discordalias"));
        assertTrue(WarPingPicker.pingCandidates(snapshot, "missing").isEmpty());
        assertTrue(WarPingPicker.pingCandidates(snapshot.withCanManage(false), "").isEmpty());
    }

    @Test
    void warPingCannotClickAPartiallyClippedPickerRow() {
        assertTrue(WarPingPicker.warPingRowFullyVisible(100, 130));
        assertFalse(WarPingPicker.warPingRowFullyVisible(100, 129));
        assertEquals(1, WarPingPicker.warPingScrollStart(19, 2));
        assertEquals(0, WarPingPicker.warPingScrollStart(19, 0));
    }

    @Test
    void zonePreviewUsesAContextCropOverTheCalibratedMapImage() {
        GuildTerritory selected = GuildTerritory.fromCorners("Selected", -1000, -3000, -800, -2800);
        MapBounds fitted = WarMapGeometry.zonePreviewBounds(List.of(selected));

        assertEquals(new MapBounds(-1180, -3180, -620, -2620), fitted);
        assertEquals(fitted, WarMapGeometry.warMapFitBounds(List.of(selected), true));
        assertEquals(MapCalibration.fullBounds(), WarMapGeometry.warMapFitBounds(List.of(selected), false));
        assertEquals(MapCalibration.fullBounds(), WarMapGeometry.warMapFitBounds(List.of(), true));
        assertEquals(MapCalibration.fullBounds(), WarMapGeometry.mapImageBounds());
    }

    @Test
    void lockedTerritoryViewContainsOnlyTerritoriesAssignedToAnyZone() {
        GuildTerritory zoned = GuildTerritory.fromCorners("Zoned", -1000, -3000, -800, -2800);
        GuildTerritory free = GuildTerritory.fromCorners("Free", -700, -2700, -500, -2500);
        WarPlannerSnapshot base = snapshot(List.of(), List.of());
        WarPlannerSnapshot withZone = new WarPlannerSnapshot(
                base.schemaVersion(),
                base.serverTime(),
                base.self(),
                base.discordRolesAvailable(),
                base.roster(),
                base.teams(),
                base.support(),
                List.of(new WarPlannerSnapshot.Zone(1, "North", "#55B8C5", List.of(), 1L, List.of("Zoned"))),
                List.of("Zoned", "Free"),
                List.of());

        assertEquals(
                List.of(zoned, free),
                WarMapGeometry.visibleMapTerritories(List.of(zoned, free), withZone, false));
        assertEquals(
                List.of(zoned),
                WarMapGeometry.visibleMapTerritories(List.of(zoned, free), withZone, true));
        assertEquals(
                java.util.Set.of("Zoned"),
                WarMapGeometry.visibleTerritoryNames(
                        withZone, java.util.Set.of("Zoned", "Free"), true));
    }



    @Test
    void zoneSidebarGroupsOrderedZonesAndKeepsIndividualAndCategoryVisibilityIndependent() {
        WarPlannerSnapshot.ZoneCategory front = new WarPlannerSnapshot.ZoneCategory(5, "Front", 0, 1L);
        WarPlannerSnapshot.ZoneCategory back = new WarPlannerSnapshot.ZoneCategory(6, "Back", 1, 1L);
        WarPlannerSnapshot.Zone north = new WarPlannerSnapshot.Zone(
                11, "North", "#112233", List.of(), 1L, List.of("A"), 5L, 1);
        WarPlannerSnapshot.Zone center = new WarPlannerSnapshot.Zone(
                10, "Center", "#223344", List.of(), 1L, List.of("B"), 5L, 0);
        WarPlannerSnapshot.Zone loose = new WarPlannerSnapshot.Zone(
                12, "Loose", "#334455", List.of(), 1L, List.of("C"), null, 0);
        WarPlannerSnapshot snapshot = categorizedSnapshot(List.of(north, loose, center), List.of(back, front));

        List<WarZoneSidebar.ZoneSidebarEntry> entries = WarZoneSidebar.zoneSidebarEntries(snapshot);

        assertEquals(List.of("Front", "Center", "North", "Back", "Uncategorized", "Loose"),
                entries.stream().map(WarZoneSidebar.ZoneSidebarEntry::label).toList());
        assertEquals(List.of(loose), WarZoneSidebar.visibleZones(snapshot.zones(), java.util.Set.of(10L), java.util.Set.of(5L)));
    }

    @Test
    void foldedZoneCategoriesKeepHeadersAndHideOnlyTheirSidebarRows() {
        WarPlannerSnapshot.ZoneCategory front = new WarPlannerSnapshot.ZoneCategory(5, "Front", 0, 1L);
        WarPlannerSnapshot.ZoneCategory back = new WarPlannerSnapshot.ZoneCategory(6, "Back", 1, 1L);
        WarPlannerSnapshot.Zone north = new WarPlannerSnapshot.Zone(
                11, "North", "#112233", List.of(), 1L, List.of("A"), 5L, 0);
        WarPlannerSnapshot.Zone south = new WarPlannerSnapshot.Zone(
                12, "South", "#223344", List.of(), 1L, List.of("B"), 6L, 0);
        WarPlannerSnapshot.Zone loose = new WarPlannerSnapshot.Zone(
                13, "Loose", "#334455", List.of(), 1L, List.of("C"), null, 0);
        WarPlannerSnapshot snapshot = categorizedSnapshot(List.of(north, south, loose), List.of(front, back));
        java.util.HashSet<Long> folded = new java.util.HashSet<>();
        folded.add(5L);
        folded.add(null);

        assertEquals(
                List.of("Front", "Back", "South", "Uncategorized"),
                WarZoneSidebar.zoneSidebarEntries(snapshot, folded).stream()
                        .map(WarZoneSidebar.ZoneSidebarEntry::label)
                        .toList());
    }

    @Test
    void zoneSidebarCanScrollFarEnoughToRenderTheLastRow() {
        WarPlannerSnapshot.ZoneCategory front = new WarPlannerSnapshot.ZoneCategory(5, "Front", 0, 1L);
        WarPlannerSnapshot.Zone first = new WarPlannerSnapshot.Zone(
                10, "First", "#112233", List.of(), 2L, List.of("A"), 5L, 0);
        WarPlannerSnapshot.Zone last = new WarPlannerSnapshot.Zone(
                11, "Last", "#223344", List.of(), 3L, List.of("B"), 5L, 1);
        List<WarZoneSidebar.ZoneSidebarEntry> entries = List.of(
                WarZoneSidebar.ZoneSidebarEntry.category(front),
                WarZoneSidebar.ZoneSidebarEntry.zone(5L, first),
                WarZoneSidebar.ZoneSidebarEntry.zone(5L, last));

        assertEquals(1, WarZoneSidebar.zoneSidebarScrollStart(99, entries, 96));
        assertEquals(0, WarZoneSidebar.zoneSidebarScrollStart(99, entries, 128));
    }

    @Test
    void zoneDropTargetsSupportCategoryHeadersAndBeforeOrAfterZoneRows() {
        WarPlannerSnapshot.ZoneCategory front = new WarPlannerSnapshot.ZoneCategory(5, "Front", 0, 1L);
        WarPlannerSnapshot.Zone first = new WarPlannerSnapshot.Zone(
                10, "First", "#112233", List.of(), 2L, List.of("A"), 5L, 0);
        WarPlannerSnapshot.Zone second = new WarPlannerSnapshot.Zone(
                11, "Second", "#223344", List.of(), 3L, List.of("B"), 5L, 1);
        WarPlannerSnapshot snapshot = categorizedSnapshot(List.of(first, second), List.of(front));
        WarZoneSidebar.ZoneSidebarPlacement header = new WarZoneSidebar.ZoneSidebarPlacement(
                WarZoneSidebar.ZoneSidebarEntry.category(front), 50, 0);
        WarZoneSidebar.ZoneSidebarPlacement secondRow = new WarZoneSidebar.ZoneSidebarPlacement(
                WarZoneSidebar.ZoneSidebarEntry.zone(5L, second), 100, 2);

        assertEquals(new WarZoneSidebar.ZoneDropTarget(5L, 0),
                WarZoneSidebar.zoneDropTarget(snapshot, header, 55, 10));
        assertEquals(new WarZoneSidebar.ZoneDropTarget(5L, 0),
                WarZoneSidebar.zoneDropTarget(snapshot, secondRow, 110, 10));
        assertEquals(new WarZoneSidebar.ZoneDropTarget(5L, 1),
                WarZoneSidebar.zoneDropTarget(snapshot, secondRow, 150, 10));
    }

    private static WarPlannerSnapshot categorizedSnapshot(
            List<WarPlannerSnapshot.Zone> zones, List<WarPlannerSnapshot.ZoneCategory> categories) {
        return new WarPlannerSnapshot(
                3,
                1L,
                null,
                new WarPlannerSnapshot.Self("self", true),
                true,
                List.of(),
                List.of(),
                new WarPlannerSnapshot.SupportBoard(1L, List.of()),
                zones,
                List.of("A", "B", "C"),
                List.of(),
                null,
                1L,
                categories);
    }

    private static WarPlannerSnapshot snapshot(
            List<WarPlannerSnapshot.Team> teams, List<WarPlannerSnapshot.RosterMember> roster) {
        return new WarPlannerSnapshot(
                3,
                null,
                new WarPlannerSnapshot.Self("self", true),
                true,
                roster,
                teams,
                new WarPlannerSnapshot.SupportBoard(1L, List.of()),
                List.of(),
                List.of(),
                List.of());
    }

    private static TerritoryQueue territoryQueue(
            long id, String territory, String queuedDefense, String reportedDefense) {
        return new TerritoryQueue(
                id,
                territory,
                "player-" + id,
                "Player" + id,
                null,
                queuedDefense,
                reportedDefense,
                Instant.parse("2026-08-24T12:00:00Z"),
                Instant.parse("2026-08-24T12:15:00Z"),
                List.of());
    }

    private static List<WarPlannerSnapshot.Team> teams(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(index -> new WarPlannerSnapshot.Team(
                        index + 1L, "Team " + (index + 1), 1L, List.of()))
                .toList();
    }

    private static void assertSupportPlacementsInside(WarTeamsTab.TeamsLayout layout) {
        List<WarTeamsTab.SupportPlacement> placements = WarTeamsTab.supportPlacements(layout);
        assertEquals(4, placements.size());
        for (WarTeamsTab.SupportPlacement placement : placements) {
            assertTrue(placement.x() >= layout.supportX());
            assertTrue(placement.x() + placement.width() <= layout.supportX() + layout.supportWidth() + .01f);
            assertTrue(placement.y() >= layout.supportY());
            assertTrue(placement.y() + placement.height() <= layout.supportY() + layout.supportHeight() + .01f);
        }
    }

    private static WarPlannerSnapshot.RosterMember rosterMember(
            String uuid, String username, List<WarCompositionRole> roles, boolean available) {
        return new WarPlannerSnapshot.RosterMember(
                uuid, username, null, null, roles, true, available, null, null);
    }
}
