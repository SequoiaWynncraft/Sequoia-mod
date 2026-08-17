package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarPlannerScreenTest {
    @Test
    void narrowManagerTabsReserveSpaceForActionButton() {
        float screenWidth = 320;
        float tabsRight = 12 + WarPlannerScreen.tabWidth(screenWidth, true) * 3;
        float managerActionLeft = screenWidth - 92;

        assertTrue(tabsRight <= managerActionLeft);
    }

    @Test
    void zonesUseTwoColumnPreviewsWhenSpaceAllows() {
        assertEquals(1, WarPlannerScreen.zoneGridColumns(640));
        assertEquals(2, WarPlannerScreen.zoneGridColumns(900));
        assertEquals(3, WarPlannerScreen.zoneGridRows(5, 900));
    }

    @Test
    void standardTeamPreviewFillsTheFirstAvailableName() {
        WarPlannerSnapshot snapshot = snapshot(
                List.of(new WarPlannerSnapshot.Team(1, "HQ Team", 1L, List.of()),
                        new WarPlannerSnapshot.Team(2, "VLow Munch 2", 1L, List.of())),
                List.of());

        assertEquals("VLow Munch 1", WarPlannerScreen.nextStandardTeamName(snapshot));
    }

    @Test
    void teamMemberRolesComeFromTheRosterAndDefaultToSolo() {
        WarPlannerSnapshot.RosterMember member = new WarPlannerSnapshot.RosterMember(
                "member", "Member", null, null, List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS),
                true, false, null, 1L);
        WarPlannerSnapshot snapshot = snapshot(List.of(), List.of(member));

        assertEquals(
                List.of(WarCompositionRole.SOLO, WarCompositionRole.DPS),
                WarPlannerScreen.teamMemberRoles(snapshot, "member"));
        assertEquals(List.of(WarCompositionRole.SOLO), WarPlannerScreen.teamMemberRoles(snapshot, "missing"));
    }

    @Test
    void zonePreviewFitsEveryTerritoryBoundary() {
        MapBounds fitted = WarPlannerScreen.fittedBounds(List.of(
                GuildTerritory.fromCorners("West", -30, 10, -5, 40),
                GuildTerritory.fromCorners("East", 8, -20, 50, 25)));

        assertEquals(new MapBounds(-30, -20, 50, 40), fitted);
    }

    private static WarPlannerSnapshot snapshot(
            List<WarPlannerSnapshot.Team> teams, List<WarPlannerSnapshot.RosterMember> roster) {
        return new WarPlannerSnapshot(
                2,
                null,
                null,
                true,
                roster,
                teams,
                new WarPlannerSnapshot.SupportBoard(1L, List.of()),
                List.of(),
                List.of(),
                List.of());
    }
}
