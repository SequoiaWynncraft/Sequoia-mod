package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.MapCalibration;
import com.seqwawa.seq.map.MapBounds;
import com.seqwawa.seq.model.war.WarCompositionRole;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.model.war.WarTeamType;
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
    void compactTeamGridKeepsActionsAboveMemberRoles() {
        assertEquals(3, WarPlannerScreen.teamMemberGridColumns(420));
        assertEquals(66, WarPlannerScreen.teamCardHeight(420));
        assertTrue(WarPlannerScreen.teamActionBottomOffset() < WarPlannerScreen.teamMemberContentTopOffset());
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
    void zonePreviewUsesAContextCropOverTheCalibratedMapImage() {
        GuildTerritory selected = GuildTerritory.fromCorners("Selected", -1000, -3000, -800, -2800);
        MapBounds fitted = WarPlannerScreen.zonePreviewBounds(List.of(selected));

        assertEquals(new MapBounds(-1180, -3180, -620, -2620), fitted);
        assertEquals(MapCalibration.fullBounds(), WarPlannerScreen.mapImageBounds());
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
