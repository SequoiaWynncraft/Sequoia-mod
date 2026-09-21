package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.Test;

class WarTerritoryResourceStyleTest {
    @Test
    void twoResourcesRenderAsTwoEqualSlices() {
        assertEquals(
                List.of(new Color(52, 211, 99), new Color(176, 190, 197)),
                WarTerritoryPickerScreen.resourceDisplayColors(List.of("EMERALD", "ORE")));
    }

    @Test
    void allFourMaterialResourcesCollapseToPurple() {
        assertEquals(
                List.of(new Color(156, 85, 210)),
                WarTerritoryPickerScreen.resourceDisplayColors(List.of("ORE", "WOOD", "FISH", "CROP")));
    }

    @Test
    void teamAssignmentListAdaptsToShortAndTallGuiScales() {
        assertEquals(1, WarTerritoryPickerScreen.visibleTeamRows(342));
        assertEquals(4, WarTerritoryPickerScreen.visibleTeamRows(480));
    }
}
