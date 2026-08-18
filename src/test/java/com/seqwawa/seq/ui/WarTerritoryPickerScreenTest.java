package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WarTerritoryPickerScreenTest {
    @Test
    void readOnlyInspectionCentersAndFitsTheSelectedTerritories() {
        GuildTerritory selected = GuildTerritory.fromCorners("Selected", -1000, -3000, -800, -2800);
        GuildTerritory distant = GuildTerritory.fromCorners("Distant", 1200, -500, 1400, -300);
        GuildTerritoryIndex index = new GuildTerritoryIndex(List.of(selected, distant));

        WarTerritoryPickerScreen.InitialViewport viewport = WarTerritoryPickerScreen.initialViewport(
                index, Set.of("Selected"), true, 560, 560);

        assertEquals(-900, viewport.centerX());
        assertEquals(-2900, viewport.centerZ());
        assertEquals(1, viewport.pixelsPerBlock());
    }

    @Test
    void expandedPaletteReducesTeamRowsOnShortScreens() {
        assertEquals(4, WarTerritoryPickerScreen.visibleTeamRows(480, 42));
        assertEquals(2, WarTerritoryPickerScreen.visibleTeamRows(480, 146));
        assertEquals(1, WarTerritoryPickerScreen.visibleTeamRows(342, 146));
    }

    @Test
    void mapHeaderControlsRemainClickableOnNarrowManagerScreens() {
        WarTerritoryPickerScreen.HeaderControls controls =
                WarTerritoryPickerScreen.headerControls(320, true);

        assertEquals(148, controls.resourceX());
        assertEquals(224, controls.lockX());
        assertEquals(310, controls.lockX() + controls.lockWidth());
    }

    @Test
    void overviewMapsEveryTerritoryToItsZoneAndSupportsScrollingLegends() {
        WarPlannerSnapshot.Zone north =
                new WarPlannerSnapshot.Zone(1, "North", "#55B8C5", List.of(), 1L, List.of("Ragni", "Detlas"));
        WarPlannerSnapshot.Zone south =
                new WarPlannerSnapshot.Zone(2, "South", "#AA7744", List.of(), 1L, List.of("Almuj"));
        WarPlannerSnapshot snapshot = new WarPlannerSnapshot(
                3,
                null,
                new WarPlannerSnapshot.Self("self", true),
                true,
                List.of(),
                List.of(),
                new WarPlannerSnapshot.SupportBoard(1L, List.of()),
                List.of(north, south),
                List.of("Ragni", "Detlas", "Almuj"),
                List.of());

        assertEquals(List.of("Ragni", "Detlas", "Almuj"),
                WarTerritoryPickerScreen.overviewTerritoryNames(snapshot));
        assertEquals(north, WarTerritoryPickerScreen.zonesByTerritory(snapshot).get("ragni"));
        assertEquals(south, WarTerritoryPickerScreen.zonesByTerritory(snapshot).get("almuj"));
        assertEquals(3, WarTerritoryPickerScreen.overviewVisibleRows(220));
    }
}
