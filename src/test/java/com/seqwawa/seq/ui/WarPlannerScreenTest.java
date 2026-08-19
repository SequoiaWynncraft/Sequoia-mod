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
import java.time.Duration;
import java.util.List;
import java.util.Map;
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
        assertEquals(new WarPlannerScreen.PlannerViewport(510, 900), WarPlannerScreen.plannerViewport(1920));
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
        assertEquals(220, WarPlannerScreen.warMapSidebarWidth(900));
        assertEquals(160, WarPlannerScreen.warMapSidebarWidth(640));
        assertEquals(150, WarPlannerScreen.warMapSidebarWidth(320));
        assertEquals(4, WarPlannerScreen.warMapVisibleZoneRows(320));
    }

    @Test
    void warMapLayoutKeepsMapAndSidebarSeparateOnNarrowScreens() {
        WarPlannerScreen.WarMapLayout layout = WarPlannerScreen.warMapLayout(320, 110, 430);

        assertEquals(12, layout.mapX());
        assertEquals(138, layout.mapWidth());
        assertEquals(158, layout.sidebarX());
        assertTrue(layout.mapX() + layout.mapWidth() < layout.sidebarX());
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
    void teamSidebarAndEditorStayCompactOnWideScreens() {
        assertEquals(214, WarPlannerScreen.teamSidebarWidth(780));
        assertEquals(179.2f, WarPlannerScreen.teamSidebarWidth(640), .01f);
        assertEquals(560, WarPlannerScreen.teamEditorWidth(780));
        assertEquals(496, WarPlannerScreen.teamEditorWidth(520));
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
    void pingRequiresAConnectedDiscordAccountAndCannotTargetSelf() {
        WarPlannerSnapshot.RosterMember linked = new WarPlannerSnapshot.RosterMember(
                "linked", "Linked", "123", null, List.of(WarCompositionRole.SOLO), true, true, null, null);
        WarPlannerSnapshot.RosterMember unlinked = rosterMember(
                "unlinked", "Unlinked", List.of(WarCompositionRole.SOLO), true);
        WarPlannerSnapshot.RosterMember self = new WarPlannerSnapshot.RosterMember(
                "self", "Self", "456", null, List.of(WarCompositionRole.SOLO), true, true, null, null);
        WarPlannerSnapshot snapshot = snapshot(List.of(), List.of(linked, unlinked, self));

        assertTrue(WarPlannerScreen.canPingPlayer(snapshot, linked));
        assertFalse(WarPlannerScreen.canPingPlayer(snapshot, unlinked));
        assertFalse(WarPlannerScreen.canPingPlayer(snapshot, self));
        assertEquals(308, WarPlannerScreen.rosterPingButtonX(420));
    }

    @Test
    void zonePreviewUsesAContextCropOverTheCalibratedMapImage() {
        GuildTerritory selected = GuildTerritory.fromCorners("Selected", -1000, -3000, -800, -2800);
        MapBounds fitted = WarPlannerScreen.zonePreviewBounds(List.of(selected));

        assertEquals(new MapBounds(-1180, -3180, -620, -2620), fitted);
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

    private static WarPlannerSnapshot.RosterMember rosterMember(
            String uuid, String username, List<WarCompositionRole> roles, boolean available) {
        return new WarPlannerSnapshot.RosterMember(
                uuid, username, null, null, roles, true, available, null, null);
    }
}
