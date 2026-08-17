package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
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
}
