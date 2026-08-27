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
    void availabilityCountdownCarriesRoundedMinutesIntoWholeHours() {
        assertEquals("30m", WarPlannerScreen.formatDuration(Duration.ofMinutes(30)));
        assertEquals("1h", WarPlannerScreen.formatDuration(Duration.ofSeconds(3_599)));
        assertEquals("2h", WarPlannerScreen.formatDuration(Duration.ofSeconds(7_199)));
        assertEquals("1h 1m", WarPlannerScreen.formatDuration(Duration.ofMinutes(61)));
    }

    @Test
    void wideScreensUseACenteredCappedPlannerViewport() {
        assertEquals(new WarPlannerScreen.PlannerViewport(360, 1200), WarPlannerScreen.plannerViewport(1920));
        assertEquals(new WarPlannerScreen.PlannerViewport(620, 680), WarPlannerScreen.plannerViewport(1920, 680));
        assertEquals(new WarPlannerScreen.PlannerViewport(570, 780), WarPlannerScreen.plannerViewport(1920, 780));
        assertEquals(new WarPlannerScreen.PlannerViewport(0, 640), WarPlannerScreen.plannerViewport(640));
    }

    @Test
    void narrowManagerTabsReserveSpaceForActionButton() {
        float screenWidth = 320;
        float tabsRight = 12 + WarPlannerScreen.tabWidth(screenWidth, true) * 3;
        float managerActionLeft = screenWidth - 92;

        assertTrue(tabsRight <= managerActionLeft);
    }

    @Test
    void displayControlsReserveManagerLockAndMapOpacityAcrossTheFullSlider() {
        WarPlannerScreen.DisplayControls member = WarPlannerScreen.displayControls(640, false);
        WarPlannerScreen.DisplayControls manager = WarPlannerScreen.displayControls(640, true);
        WarPlannerScreen.DisplayControls narrowManager = WarPlannerScreen.displayControls(320, true);

        assertTrue(manager.opacityX() < member.opacityX());
        assertTrue(manager.opacityX() < manager.resourceX());
        assertTrue(manager.resourceX() < manager.lockX());
        assertEquals(0, WarPlannerScreen.opacityPercentForMouse(manager.opacityX() + 65, manager));
        assertEquals(100, WarPlannerScreen.opacityPercentForMouse(manager.opacityX() + 125, manager));
        assertEquals(100, WarPlannerScreen.opacityAlpha(200, 50));
        assertFalse(WarPlannerScreen.shouldBlurBackground(95));
        assertTrue(WarPlannerScreen.shouldBlurBackground(100));
        assertTrue(narrowManager.opacityX() >= 12);
        assertTrue(narrowManager.lockX() + narrowManager.lockWidth() <= 308);
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
    void warMapKeepsOneCanvasAndACompactSidebar() {
        assertEquals(220, WarPlannerScreen.warMapSidebarWidth(1200));
        assertEquals(220, WarPlannerScreen.warMapSidebarWidth(900));
        assertEquals(160, WarPlannerScreen.warMapSidebarWidth(640));
        assertEquals(150, WarPlannerScreen.warMapSidebarWidth(320));
        assertEquals(4, WarPlannerScreen.warMapVisibleZoneRows(320));
    }

    @Test
    void warMapLayoutKeepsMapAndSidebarSeparateOnNarrowScreens() {
        WarPlannerScreen.WarMapLayout layout = WarPlannerScreen.warMapLayout(320, 110, 430);
        WarPlannerScreen.WarQueueFilterBounds queueFilter = WarPlannerScreen.warQueueFilterBounds(layout);

        assertEquals(12, layout.mapX());
        assertEquals(138, layout.mapWidth());
        assertEquals(158, layout.sidebarX());
        assertTrue(layout.mapX() + layout.mapWidth() < layout.sidebarX());
        assertTrue(queueFilter.visible());
        assertTrue(queueFilter.x() >= layout.mapX());
        assertTrue(queueFilter.x() + queueFilter.width() <= layout.mapX() + layout.mapWidth());
    }

    @Test
    void wideWarMapLayoutGivesTheInteractiveCanvasMostOfThePlannerWidth() {
        WarPlannerScreen.WarMapLayout layout = WarPlannerScreen.warMapLayout(1200, 110, 610);

        assertEquals(12, layout.mapX());
        assertEquals(948, layout.mapWidth());
        assertEquals(500, layout.mapHeight());
        assertEquals(968, layout.sidebarX());
        assertEquals(220, layout.sidebarWidth());
        assertTrue(layout.mapWidth() > layout.sidebarWidth() * 2);
        assertTrue(layout.containsMap(layout.mapX() + layout.mapWidth() / 2, layout.mapY() + 1));
        assertFalse(layout.containsMap(layout.sidebarX() + 1, layout.mapY() + 1));
        assertTrue(layout.containsSidebar(layout.sidebarX() + 1, layout.mapY() + 1));
        assertEquals(1200 - 12, layout.sidebarX() + layout.sidebarWidth());
    }

    @Test
    void hiddenZoneLayersAreExcludedWithoutChangingTheSnapshot() {
        WarPlannerSnapshot.Zone north = new WarPlannerSnapshot.Zone(
                1, "North", "#55B8C5", List.of(), 1L, List.of("A"));
        WarPlannerSnapshot.Zone south = new WarPlannerSnapshot.Zone(
                2, "South", "#E05A65", List.of(), 1L, List.of("B"));

        assertEquals(List.of(north), WarPlannerScreen.visibleZones(List.of(north, south), java.util.Set.of(2L)));
        assertEquals(List.of(north, south), WarPlannerScreen.visibleZones(List.of(north, south), java.util.Set.of()));
    }

    @Test
    void queueLabelsIncludeOnlyTerritoriesBelongingToCurrentlyShownZones() {
        WarPlannerSnapshot.Zone shown = new WarPlannerSnapshot.Zone(
                1, "Shown", "#55B8C5", List.of(), 1L, List.of("Alekin", "Shared Territory"));
        WarPlannerSnapshot.Zone hidden = new WarPlannerSnapshot.Zone(
                2, "Hidden", "#E05A65", List.of(), 1L, List.of("Context Only", "Shared Territory"));
        WarPlannerSnapshot.Zone hiddenCategory = new WarPlannerSnapshot.Zone(
                3, "Hidden category", "#E0A65A", List.of(), 1L, List.of("Unlocked Only"), 9L, 0);

        List<WarPlannerSnapshot.Zone> displayed = WarPlannerScreen.visibleZones(
                List.of(shown, hidden, hiddenCategory), Set.of(2L), Set.of(9L));
        GuildTerritory alekin = GuildTerritory.fromCorners("Alekin", 0, 0, 10, 10);
        GuildTerritory context = GuildTerritory.fromCorners("Context Only", 20, 0, 30, 10);
        GuildTerritory unlocked = GuildTerritory.fromCorners("Unlocked Only", 40, 0, 50, 10);
        List<GuildTerritory> allTerritories = List.of(alekin, context, unlocked);
        Map<String, WarPlannerSnapshot.TerritoryDetails> details = Map.of(
                "Alekin", new WarPlannerSnapshot.TerritoryDetails("Alekin", List.of("Context Only"), List.of()));
        Set<String> labelTerritories = WarPlannerScreen.shownZoneTerritoryNames(displayed);

        assertEquals(Set.of("alekin", "shared territory"), labelTerritories);
        assertEquals(allTerritories, WarPlannerScreen.visibleMapTerritories(allTerritories, displayed, false));
        assertEquals(
                List.of(context),
                WarPlannerScreen.oneHopContextTerritories(allTerritories, List.of(alekin), details));
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

        assertEquals("xiaolongbao", WarPlannerScreen.warQueueMapUsername(queue));
        assertEquals("xiao…", WarPlannerScreen.fitWarQueueText("xiaolongbao", 5, String::length));
        assertEquals(
                List.of("Party 5/5 ·", "One, Two,", "Three"),
                WarPlannerScreen.wrapWarQueueText("Party 5/5 · One, Two, Three", 12, String::length));
        assertEquals(
                List.of(
                        "xiaolongbao/Soup Person",
                        "Alekin · Defense Very Low/Very High",
                        "Queued 12m 19s ago · 02:40 remaining",
                        "Party 3/5 · One, Two, Three"),
                WarPlannerScreen.warQueueTooltipLines(queue, now));
        assertEquals(
                "You own this queue · owner remains joined",
                WarPlannerScreen.warQueueActionHint(queue, "queuer-uuid"));
        assertEquals("Double-click to leave", WarPlannerScreen.warQueueActionHint(queue, "two"));
        assertEquals("Double-click to join", WarPlannerScreen.warQueueActionHint(queue, "other"));
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

        assertEquals("Unknown", WarPlannerScreen.warQueueMapUsername(provisional));
        assertEquals(
                List.of(
                        "Unknown",
                        "Alekin · Defense Unknown",
                        "Queued 19s ago · 01:41 remaining",
                        "Party 1/5"),
                WarPlannerScreen.warQueueTooltipLines(provisional, now));
        assertEquals("Double-click to join", WarPlannerScreen.warQueueActionHint(provisional, "self"));
        assertEquals(
                provisional,
                WarPlannerScreen.warQueueForTerritory(
                        WarPlannerScreen.warQueueMapMarkers(
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
        assertEquals("Party 5/5 · One, Two, Three, Four", WarPlannerScreen.warQueueTooltipLines(full, now).get(3));
        assertEquals("Queue full", WarPlannerScreen.warQueueActionHint(full, "self"));
        assertEquals("Double-click to leave", WarPlannerScreen.warQueueActionHint(full, "four"));
    }

    @Test
    void queuedMapLabelBoundsRemainInsideTheProjectedTerritoryBox() {
        GuildTerritory territory = GuildTerritory.fromCorners("Alekin", 0, 0, 20, 10);

        WarPlannerScreen.WarQueueLabelBounds label = WarPlannerScreen.warQueueLabelBounds(
                territory, new MapBounds(0, 0, 100, 100), 10, 20, 2, 25, 12);

        assertEquals(new WarPlannerScreen.WarQueueLabelBounds(17.5f, 24, 25, 12), label);
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

        List<WarPlannerScreen.WarQueueMapMarker> markers = WarPlannerScreen.warQueueMapMarkers(
                List.of(shown, duplicate, hidden, missing),
                Set.of(" ALEKIN "),
                Map.of("Alekin", alekin, "Context Only", context));

        assertEquals(List.of(new WarPlannerScreen.WarQueueMapMarker(shown, alekin)), markers);
        assertEquals(shown, WarPlannerScreen.warQueueForTerritory(markers, "ALEKIN"));
        assertEquals(null, WarPlannerScreen.warQueueForTerritory(markers, "Context Only"));
        assertEquals(List.of(), WarPlannerScreen.warQueueMapMarkers(null, Set.of("alekin"), Map.of()));
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
                WarPlannerScreen.warQueuesForMap(List.of(unrelated, owned, joined), "self", false));
        assertEquals(
                List.of(owned, joined),
                WarPlannerScreen.warQueuesForMap(List.of(unrelated, owned, joined), "self", true));
        assertEquals(
                List.of(),
                WarPlannerScreen.warQueuesForMap(List.of(owned, joined), null, true));
    }

    @Test
    void queuedTerritoryPulseUsesCapturedTierAndAStablePeriodicAlpha() {
        TerritoryQueue exactQueue = territoryQueue(1, "Alekin", "Very Low", "Very High");
        TerritoryQueue observedQueue = territoryQueue(2, "Detlas", null, "Very High");

        assertEquals("Very Low", WarPlannerScreen.warQueuePulseDefense(exactQueue));
        assertEquals("Very High", WarPlannerScreen.warQueuePulseDefense(observedQueue));
        Color trough = WarPlannerScreen.warQueuePulseColor(exactQueue, 0);
        Color peak = WarPlannerScreen.warQueuePulseColor(exactQueue, 800);
        Color observed = WarPlannerScreen.warQueuePulseColor(observedQueue, 800);
        assertEquals(new Color(0x00AA00), new Color(trough.getRed(), trough.getGreen(), trough.getBlue()));
        assertEquals(new Color(0x00AA00), new Color(peak.getRed(), peak.getGreen(), peak.getBlue()));
        assertEquals(
                new Color(0xAA0000),
                new Color(observed.getRed(), observed.getGreen(), observed.getBlue()));
        assertEquals(36, trough.getAlpha());
        assertEquals(96, peak.getAlpha());

        assertEquals(36, WarPlannerScreen.warQueuePulseAlpha(0));
        assertEquals(66, WarPlannerScreen.warQueuePulseAlpha(400));
        assertEquals(96, WarPlannerScreen.warQueuePulseAlpha(800));
        assertEquals(66, WarPlannerScreen.warQueuePulseAlpha(1_200));
        assertEquals(36, WarPlannerScreen.warQueuePulseAlpha(1_600));
        assertEquals(96, WarPlannerScreen.warQueuePulseAlpha(-800));
        assertEquals(
                WarPlannerScreen.warQueuePulseAlpha(137),
                WarPlannerScreen.warQueuePulseAlpha(1_737));
        for (long elapsed = -3_200; elapsed <= 3_200; elapsed += 37) {
            int alpha = WarPlannerScreen.warQueuePulseAlpha(elapsed);
            assertTrue(alpha >= 36 && alpha <= 96);
        }
    }

    @Test
    void queuedTerritoryDoubleClickRequiresSameQueueTimeWindowAndPointerLocation() {
        WarPlannerScreen.PendingWarQueueClick first =
                new WarPlannerScreen.PendingWarQueueClick(42, "Alekin", 100, 80, 1_000);

        assertTrue(WarPlannerScreen.isWarQueueDoubleClick(first, 42, "alekin", 102, 81, 1_350));
        assertFalse(WarPlannerScreen.isWarQueueDoubleClick(first, 42, "Alekin", 102, 81, 1_351));
        assertFalse(WarPlannerScreen.isWarQueueDoubleClick(first, 43, "Alekin", 102, 81, 1_200));
        assertFalse(WarPlannerScreen.isWarQueueDoubleClick(first, 42, "Lutho", 102, 81, 1_200));
        assertFalse(WarPlannerScreen.isWarQueueDoubleClick(first, 42, "Alekin", 104, 80, 1_200));
        assertTrue(WarPlannerScreen.warQueueClickMoved(first, 104, 80));
    }

    @Test
    void fittedWarMapViewportCentersAndFitsRequestedBounds() {
        WarPlannerScreen.WarMapLayout layout = new WarPlannerScreen.WarMapLayout(12, 100, 400, 300, 420, 180);
        MapViewport viewport = WarPlannerScreen.fittedWarMapViewport(
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
        WarPlannerScreen.WarMapLayout narrowLayout = WarPlannerScreen.warMapLayout(320, 110, 430);
        WarPlannerScreen.WarMapLayout wideLayout = WarPlannerScreen.warMapLayout(1200, 110, 610);

        MapViewport narrow = WarPlannerScreen.fittedWarMapViewport(requested, narrowLayout);
        MapViewport wide = WarPlannerScreen.fittedWarMapViewport(requested, wideLayout);

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
    void warMapPanAndZoomPreserveScreenGeometryAndPointerAnchor() {
        MapViewport before = new MapViewport(100, -200, 1, 12, 110, 948, 500);

        MapViewport panned = WarPlannerScreen.panWarMapViewport(before, 20, -10);

        assertEquals(80, panned.centerX());
        assertEquals(-190, panned.centerZ());
        assertEquals(before.pixelsPerBlock(), panned.pixelsPerBlock());
        assertEquals(before.screenWidth(), panned.screenWidth());
        assertEquals(before.screenHeight(), panned.screenHeight());

        double pointerX = 300;
        double pointerY = 240;
        double anchorX = panned.screenToWorldX(pointerX);
        double anchorZ = panned.screenToWorldZ(pointerY);
        MapViewport zoomed = WarPlannerScreen.zoomWarMapViewport(panned, pointerX, pointerY, 1.15);

        assertEquals(1.15, zoomed.pixelsPerBlock(), .0001);
        assertEquals(anchorX, zoomed.screenToWorldX(pointerX), .0001);
        assertEquals(anchorZ, zoomed.screenToWorldZ(pointerY), .0001);
        assertEquals(1.8, WarPlannerScreen.zoomWarMapViewport(before, pointerX, pointerY, 100).pixelsPerBlock());
        MapViewport nearMinimum = new MapViewport(0, 0, .016, 0, 0, 100, 100);
        assertEquals(
                .015,
                WarPlannerScreen.zoomWarMapViewport(nearMinimum, 50, 50, .01).pixelsPerBlock());
    }

    @Test
    void lockedWarMapKeepsManualCameraUntilModeOrViewportChanges() {
        WarPlannerScreen.WarMapLayout layout = WarPlannerScreen.warMapLayout(1200, 110, 610);

        assertFalse(WarPlannerScreen.shouldRefitWarMap(
                true, layout.mapWidth(), layout.mapHeight(), true, layout, true));
        assertTrue(WarPlannerScreen.shouldRefitWarMap(
                false, layout.mapWidth(), layout.mapHeight(), true, layout, true));
        assertTrue(WarPlannerScreen.shouldRefitWarMap(
                true, layout.mapWidth(), layout.mapHeight(), false, layout, true));
        assertTrue(WarPlannerScreen.shouldRefitWarMap(
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
                WarPlannerScreen.oneHopContextTerritories(List.of(a, b, c), List.of(a), details));
    }

    @Test
    void mapHoverHitTestingOnlyReturnsDisplayedTerritories() {
        GuildTerritory territory = GuildTerritory.fromCorners("Detlas", 0, 0, 10, 10);
        GuildTerritoryIndex index = new GuildTerritoryIndex(List.of(territory));
        MapViewport viewport = new MapViewport(5, 5, 10, 0, 0, 100, 100);

        assertEquals(territory, WarPlannerScreen.territoryAt(index, viewport, java.util.Set.of("detlas"), 50, 50));
        assertEquals(null, WarPlannerScreen.territoryAt(index, viewport, java.util.Set.of(), 50, 50));
        assertEquals(null, WarPlannerScreen.territoryAt(index, viewport, java.util.Set.of("detlas"), 150, 50));
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

        assertEquals(WarTeamType.VLOW_MUNCH, WarPlannerScreen.defaultTeamType(snapshot));
        assertEquals("VLow Munch 1", WarPlannerScreen.automaticTeamName(snapshot, WarTeamType.VLOW_MUNCH, null));
        assertEquals("FFA 1", WarPlannerScreen.automaticTeamName(snapshot, WarTeamType.FFA, null));
        assertFalse(WarPlannerScreen.teamTypeSelectable(snapshot, WarTeamType.HQ, null));
        assertTrue(WarPlannerScreen.teamTypeSelectable(snapshot, WarTeamType.HQ, 1L));
        assertEquals("HQ Team", WarPlannerScreen.automaticTeamName(snapshot, WarTeamType.HQ, 1L));
        assertFalse(WarPlannerScreen.teamTypeSelectable(snapshot, WarTeamType.UNKNOWN, null));
    }

    @Test
    void explicitTeamTypeDoesNotDependOnItsDisplayName() {
        WarPlannerSnapshot.Team team = new WarPlannerSnapshot.Team(
                7, "Alpha", WarTeamType.FFA, 3L, WarCompositionTargets.NONE, List.of());

        assertEquals(WarTeamType.FFA, team.teamType());
        assertEquals("Alpha", WarPlannerScreen.automaticTeamName(snapshot(List.of(team), List.of()), WarTeamType.FFA, 7L));
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
        WarPlannerScreen.TeamEditorBase base = WarPlannerScreen.TeamEditorBase.from(original);

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

        TeamMemberMoveDraft betweenTeams = WarPlannerScreen.teamMemberMoveDraft(7L, 3L, target);
        TeamMemberMoveDraft toRoster = WarPlannerScreen.teamMemberMoveDraft(7L, 3L, null);

        assertEquals(7L, betweenTeams.sourceTeamId());
        assertEquals(3L, betweenTeams.sourceVersion());
        assertEquals(9L, betweenTeams.targetTeamId());
        assertEquals(5L, betweenTeams.targetVersion());
        assertEquals(null, toRoster.targetTeamId());
        assertEquals(null, toRoster.targetVersion());
    }

    @Test
    void teamCardsGrowOnlyWithTheirDenseVerticalMemberStack() {
        assertEquals(48, WarPlannerScreen.teamCardHeight(0));
        assertEquals(48, WarPlannerScreen.teamCardHeight(1));
        assertEquals(56, WarPlannerScreen.teamCardHeight(2));
        assertEquals(89, WarPlannerScreen.teamCardHeight(5));
        assertEquals(11, WarPlannerScreen.teamMemberRowStep());
    }

    @Test
    void narrowManagerTeamActionsStackInsideTheCard() {
        float cardsRight = 120;
        WarPlannerScreen.TeamActionLayout actions =
                WarPlannerScreen.teamActionLayout(cardsRight, true, true);

        assertTrue(actions.editX() >= 12);
        assertTrue(actions.deleteX() + actions.deleteWidth() <= cardsRight);
        assertTrue(actions.selfX() >= 12);
        assertTrue(actions.selfX() + actions.selfWidth() <= cardsRight);
        assertTrue(actions.selfY() > actions.managerY());
        assertTrue(actions.memberTop() > actions.selfY() + 22);
        assertEquals(101, WarPlannerScreen.teamCardHeight(1, actions));
    }

    @Test
    void teamSidebarAndEditorStayCompactOnWideScreens() {
        assertEquals(214, WarPlannerScreen.teamSidebarWidth(780));
        assertEquals(179.2f, WarPlannerScreen.teamSidebarWidth(640), .01f);
        assertEquals(560, WarPlannerScreen.teamEditorWidth(780));
        assertEquals(496, WarPlannerScreen.teamEditorWidth(520));
    }

    @Test
    void adaptiveTeamsLayoutUsesCompactRailAndGridBreakpoints() {
        float top = 118;
        float bottom = 600;

        WarPlannerScreen.TeamsLayout compact = WarPlannerScreen.teamsLayout(519, top, bottom, 4);
        WarPlannerScreen.TeamsLayout oneColumnAtBoundary =
                WarPlannerScreen.teamsLayout(520, top, bottom, 4);
        WarPlannerScreen.TeamsLayout oneColumnBelowGrid =
                WarPlannerScreen.teamsLayout(899, top, bottom, 4);
        WarPlannerScreen.TeamsLayout twoColumnsAtBoundary =
                WarPlannerScreen.teamsLayout(900, top, bottom, 4);

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
        WarPlannerScreen.TeamsLayout layout = WarPlannerScreen.teamsLayout(1_000, 118, 600, teams.size());

        List<WarPlannerScreen.TeamPlacement> placements =
                WarPlannerScreen.teamPlacements(teams, 0, layout, true, true);

        assertEquals(4, placements.size());
        assertTrue(placements.get(0).x() < placements.get(1).x());
        assertEquals(placements.get(0).y(), placements.get(1).y(), .01f);
        assertEquals(2, placements.stream().map(WarPlannerScreen.TeamPlacement::x).distinct().count());
        for (WarPlannerScreen.TeamPlacement placement : placements) {
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
        WarPlannerScreen.TeamsLayout layout = WarPlannerScreen.teamsLayout(1_000, 118, 600, teams.size());

        assertEquals(3, WarPlannerScreen.teamScrollStart(4, teams.size(), 2));
        assertEquals(
                List.of(3, 4),
                WarPlannerScreen.teamPlacements(teams, 4, layout, false, false).stream()
                        .map(WarPlannerScreen.TeamPlacement::index)
                        .toList());
    }

    @Test
    void compactAndRailSupportPlacementsRemainInsideTheirPanels() {
        WarPlannerScreen.TeamsLayout compact = WarPlannerScreen.teamsLayout(519, 118, 600, 4);
        WarPlannerScreen.TeamsLayout rail = WarPlannerScreen.teamsLayout(640, 118, 600, 4);

        assertTrue(compact.compactAuxiliary());
        assertFalse(rail.compactAuxiliary());
        assertSupportPlacementsInside(compact);
        assertSupportPlacementsInside(rail);
    }

    @Test
    void clippedTeamCardsDoNotExposeInvisibleActionsOrHitArea() {
        List<WarPlannerSnapshot.Team> teams = teams(1);
        WarPlannerScreen.TeamsLayout shortLayout =
                WarPlannerScreen.teamsLayout(640, 118, 138, teams.size());

        WarPlannerScreen.TeamPlacement placement =
                WarPlannerScreen.teamPlacements(teams, 0, shortLayout, true, true).getFirst();

        assertEquals(20, placement.visibleHeight());
        assertFalse(placement.fullyShows(placement.actions().managerY(), 22));
        assertFalse(placement.fullyShows(placement.actions().selfY(), 22));
        assertTrue(placement.contains(placement.x() + 1, placement.y() + 19));
        assertFalse(placement.contains(placement.x() + 1, placement.y() + 21));
    }

    @Test
    void compactTeamRolesFollowTheNameWithoutOverflowingTheMemberChip() {
        assertEquals(44, WarPlannerScreen.compactRoleX(10, 30, 150, 12));
        assertEquals(138, WarPlannerScreen.compactRoleX(10, 200, 150, 12));
    }

    @Test
    void teamMemberRolesComeFromTheRosterAndDefaultToEmpty() {
        WarPlannerSnapshot.RosterMember member = new WarPlannerSnapshot.RosterMember(
                "member", "Member", null, null, List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS),
                true, false, null, 1L);
        WarPlannerSnapshot snapshot = snapshot(List.of(), List.of(member));

        assertEquals(
                List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS),
                WarPlannerScreen.teamMemberRoles(snapshot, "member"));
        assertEquals(List.of(), WarPlannerScreen.teamMemberRoles(snapshot, "missing"));
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

        assertEquals(1, WarPlannerScreen.teamCompositionCount(snapshot, team, WarCompositionRole.DPS));
        assertEquals("Need D1", WarPlannerScreen.compositionTargetStatus(snapshot, team));
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
                WarPlannerScreen.unassignedOnlineRoster(snapshot(List.of(), List.of(offline, assigned, free))).stream()
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

        assertTrue(WarPlannerScreen.canChangeOwnTeam(soloSnapshot, hq));
        assertEquals("Join", WarPlannerScreen.teamMembershipActionLabel(soloSnapshot, hq));
        assertTrue(WarPlannerScreen.canChangeOwnTeam(soloSnapshot, ffa));
        assertEquals("Join", WarPlannerScreen.teamMembershipActionLabel(soloSnapshot, ffa));

        WarPlannerSnapshot.RosterMember tank = new WarPlannerSnapshot.RosterMember(
                "self", "Self", null, null, List.of(WarCompositionRole.SOLO, WarCompositionRole.TANK),
                true, true, null, 1L);
        WarPlannerSnapshot tankSnapshot = snapshot(List.of(hq, ffa), List.of(tank));
        assertTrue(WarPlannerScreen.canChangeOwnTeam(tankSnapshot, hq));
        assertEquals("Leave", WarPlannerScreen.teamMembershipActionLabel(tankSnapshot, hq));
        assertEquals("Switch", WarPlannerScreen.teamMembershipActionLabel(tankSnapshot, ffa));
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

        List<String> ordered = WarPlannerScreen.teamEditorRoster(
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
                WarPlannerScreen.teamEditorRoster(snapshot, "  SaGe ").stream()
                        .map(WarPlannerSnapshot.RosterMember::playerUuid)
                        .toList());
        assertEquals(List.of(offline), WarPlannerScreen.teamEditorRoster(snapshot, "discordalias"));
        assertEquals(List.of(offline), WarPlannerScreen.teamEditorRoster(snapshot, "ROYAL-ID"));
        assertEquals(3, WarPlannerScreen.teamEditorRoster(snapshot, "   ").size());
        assertTrue(WarPlannerScreen.teamEditorRoster(snapshot, "missing").isEmpty());
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

        assertTrue(WarPlannerScreen.canPingPlayer(snapshot, linked));
        assertFalse(WarPlannerScreen.canPingPlayer(snapshot, offline));
        assertFalse(WarPlannerScreen.canPingPlayer(snapshot, unlinked));
        assertFalse(WarPlannerScreen.canPingPlayer(snapshot, self));
        assertFalse(WarPlannerScreen.canPingPlayer(snapshot.withCanManage(false), linked));
        assertEquals(
                List.of("linked-first", "linked"),
                WarPlannerScreen.pingCandidates(snapshot, "").stream()
                        .map(WarPlannerSnapshot.RosterMember::playerUuid)
                        .toList());
        assertEquals(List.of(linked), WarPlannerScreen.pingCandidates(snapshot, "discordalias"));
        assertTrue(WarPlannerScreen.pingCandidates(snapshot, "missing").isEmpty());
        assertTrue(WarPlannerScreen.pingCandidates(snapshot.withCanManage(false), "").isEmpty());
    }

    @Test
    void warPingCannotClickAPartiallyClippedPickerRow() {
        assertTrue(WarPlannerScreen.warPingRowFullyVisible(100, 130));
        assertFalse(WarPlannerScreen.warPingRowFullyVisible(100, 129));
        assertEquals(1, WarPlannerScreen.warPingScrollStart(19, 2));
        assertEquals(0, WarPlannerScreen.warPingScrollStart(19, 0));
    }

    @Test
    void zonePreviewUsesAContextCropOverTheCalibratedMapImage() {
        GuildTerritory selected = GuildTerritory.fromCorners("Selected", -1000, -3000, -800, -2800);
        MapBounds fitted = WarPlannerScreen.zonePreviewBounds(List.of(selected));

        assertEquals(new MapBounds(-1180, -3180, -620, -2620), fitted);
        assertEquals(fitted, WarPlannerScreen.warMapFitBounds(List.of(selected), true));
        assertEquals(MapCalibration.fullBounds(), WarPlannerScreen.warMapFitBounds(List.of(selected), false));
        assertEquals(MapCalibration.fullBounds(), WarPlannerScreen.warMapFitBounds(List.of(), true));
        assertEquals(MapCalibration.fullBounds(), WarPlannerScreen.mapImageBounds());
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
                WarPlannerScreen.visibleMapTerritories(List.of(zoned, free), withZone, false));
        assertEquals(
                List.of(zoned),
                WarPlannerScreen.visibleMapTerritories(List.of(zoned, free), withZone, true));
        assertEquals(
                java.util.Set.of("Zoned"),
                WarPlannerScreen.visibleTerritoryNames(
                        withZone, java.util.Set.of("Zoned", "Free"), true));
    }

    @Test
    void warMapSidebarRowsScaleWithAvailableHeight() {
        assertEquals(1, WarPlannerScreen.warMapVisibleZoneRows(100));
        assertEquals(4, WarPlannerScreen.warMapVisibleZoneRows(320));
        assertEquals(6, WarPlannerScreen.warMapVisibleZoneRows(480));
        assertEquals(6, WarPlannerScreen.warMapScrollStart(99, 10, 4));
        assertEquals(0, WarPlannerScreen.warMapScrollStart(2, 3, 4));
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

        List<WarPlannerScreen.ZoneSidebarEntry> entries = WarPlannerScreen.zoneSidebarEntries(snapshot);

        assertEquals(List.of("Front", "Center", "North", "Back", "Uncategorized", "Loose"),
                entries.stream().map(WarPlannerScreen.ZoneSidebarEntry::label).toList());
        assertEquals(List.of(loose), WarPlannerScreen.visibleZones(snapshot.zones(), java.util.Set.of(10L), java.util.Set.of(5L)));
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
                WarPlannerScreen.zoneSidebarEntries(snapshot, folded).stream()
                        .map(WarPlannerScreen.ZoneSidebarEntry::label)
                        .toList());
    }

    @Test
    void zoneSidebarCanScrollFarEnoughToRenderTheLastRow() {
        WarPlannerSnapshot.ZoneCategory front = new WarPlannerSnapshot.ZoneCategory(5, "Front", 0, 1L);
        WarPlannerSnapshot.Zone first = new WarPlannerSnapshot.Zone(
                10, "First", "#112233", List.of(), 2L, List.of("A"), 5L, 0);
        WarPlannerSnapshot.Zone last = new WarPlannerSnapshot.Zone(
                11, "Last", "#223344", List.of(), 3L, List.of("B"), 5L, 1);
        List<WarPlannerScreen.ZoneSidebarEntry> entries = List.of(
                WarPlannerScreen.ZoneSidebarEntry.category(front),
                WarPlannerScreen.ZoneSidebarEntry.zone(5L, first),
                WarPlannerScreen.ZoneSidebarEntry.zone(5L, last));

        assertEquals(2, WarPlannerScreen.zoneSidebarScrollStart(99, entries, 96));
        assertEquals(1, WarPlannerScreen.zoneSidebarScrollStart(99, entries, 128));
    }

    @Test
    void zoneDropTargetsSupportCategoryHeadersAndBeforeOrAfterZoneRows() {
        WarPlannerSnapshot.ZoneCategory front = new WarPlannerSnapshot.ZoneCategory(5, "Front", 0, 1L);
        WarPlannerSnapshot.Zone first = new WarPlannerSnapshot.Zone(
                10, "First", "#112233", List.of(), 2L, List.of("A"), 5L, 0);
        WarPlannerSnapshot.Zone second = new WarPlannerSnapshot.Zone(
                11, "Second", "#223344", List.of(), 3L, List.of("B"), 5L, 1);
        WarPlannerSnapshot snapshot = categorizedSnapshot(List.of(first, second), List.of(front));
        WarPlannerScreen.ZoneSidebarPlacement header = new WarPlannerScreen.ZoneSidebarPlacement(
                WarPlannerScreen.ZoneSidebarEntry.category(front), 50, 0);
        WarPlannerScreen.ZoneSidebarPlacement secondRow = new WarPlannerScreen.ZoneSidebarPlacement(
                WarPlannerScreen.ZoneSidebarEntry.zone(5L, second), 100, 2);

        assertEquals(new WarPlannerScreen.ZoneDropTarget(5L, 0),
                WarPlannerScreen.zoneDropTarget(snapshot, header, 55, 10));
        assertEquals(new WarPlannerScreen.ZoneDropTarget(5L, 0),
                WarPlannerScreen.zoneDropTarget(snapshot, secondRow, 110, 10));
        assertEquals(new WarPlannerScreen.ZoneDropTarget(5L, 1),
                WarPlannerScreen.zoneDropTarget(snapshot, secondRow, 150, 10));
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

    private static void assertSupportPlacementsInside(WarPlannerScreen.TeamsLayout layout) {
        List<WarPlannerScreen.SupportPlacement> placements = WarPlannerScreen.supportPlacements(layout);
        assertEquals(4, placements.size());
        for (WarPlannerScreen.SupportPlacement placement : placements) {
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
