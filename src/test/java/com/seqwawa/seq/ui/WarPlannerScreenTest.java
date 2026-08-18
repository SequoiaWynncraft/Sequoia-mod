package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.MapCalibration;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarCompositionTargets;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarTeamType;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarPlannerScreenTest {
    @Test
    void wideScreensUseACenteredCappedPlannerViewport() {
        assertEquals(new WarPlannerScreen.PlannerViewport(510, 900), WarPlannerScreen.plannerViewport(1920));
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
    void zonesUseTwoColumnPreviewsWhenSpaceAllows() {
        assertEquals(1, WarPlannerScreen.zoneGridColumns(640));
        assertEquals(2, WarPlannerScreen.zoneGridColumns(900));
        assertEquals(3, WarPlannerScreen.zoneGridRows(5, 900));
    }

    @Test
    void teamTypePreviewUsesIndependentAutomaticSequencesAndUniqueHq() {
        assertEquals(3, WarTeamType.values().length);
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
    }

    @Test
    void teamMembersUseTheDenseVerticalStack() {
        assertEquals(88, WarPlannerScreen.teamCardHeight());
        assertEquals(11, WarPlannerScreen.teamMemberRowStep());
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
    void lockedManagerZoneViewOffersASeparateOverviewBeforeTheGrid() {
        WarPlannerSnapshot base = snapshot(List.of(), List.of());
        WarPlannerSnapshot withZone = new WarPlannerSnapshot(
                3,
                base.serverTime(),
                base.self(),
                base.discordRolesAvailable(),
                base.roster(),
                base.teams(),
                base.support(),
                List.of(new WarPlannerSnapshot.Zone(1, "North", "#55B8C5", List.of(), 1L, List.of("Zoned"))),
                List.of("Zoned"),
                List.of());

        assertTrue(WarPlannerScreen.zoneOverviewAvailable(withZone, true, true));
        assertFalse(WarPlannerScreen.zoneOverviewAvailable(withZone, false, true));
        assertEquals(134, WarPlannerScreen.zoneGridTop(100, true));
        assertEquals(100, WarPlannerScreen.zoneGridTop(100, false));
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
