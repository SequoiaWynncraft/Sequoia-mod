package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.map.GuildTerritory;
import com.seqwawa.seq.map.GuildTerritoryIndex;
import com.seqwawa.seq.model.war.WarPlannerSnapshot;
import com.seqwawa.seq.ui.PickerControlLayout.Bounds;
import com.seqwawa.seq.ui.WarTerritoryPickerPolicy.ControlKind;
import com.seqwawa.seq.ui.WarTerritoryPickerPolicy.ControlTarget;
import com.seqwawa.seq.ui.WarTerritoryPickerPolicy.TerritoryAccess;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class WarTerritoryPickerScreenTest {
    @Test
    void focusedViewportCentersAndFitsSelectedTerritories() {
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
        assertEquals(4, PickerControlLayout.visibleTeamRows(480, 42));
        assertEquals(2, PickerControlLayout.visibleTeamRows(480, 146));
        assertEquals(1, PickerControlLayout.visibleTeamRows(342, 146));
    }

    @Test
    void shortScreenDetectsExpandedPaletteOverlapBeforeRenderingControls() {
        assertTrue(PickerControlLayout.create(800, 342, 146, true).controlsOverlapFooter());
        assertFalse(PickerControlLayout.create(800, 342, 42, true).controlsOverlapFooter());
        assertFalse(PickerControlLayout.create(800, 480, 146, true).controlsOverlapFooter());
    }

    @Test
    void veryShortScreenPlacesClearCancelAndSaveInOneNonOverlappingFooter() {
        PickerControlLayout layout = PickerControlLayout.create(800, 280, 42, true);

        assertEquals(layout.clear().y(), layout.cancel().y());
        assertEquals(layout.cancel().y(), layout.save().y());
        assertTrue(layout.clear().x() + layout.clear().width() < layout.cancel().x());
        assertTrue(layout.cancel().x() + layout.cancel().width() < layout.save().x());
        assertTrue(layout.save().x() + layout.save().width() <= 226);
    }

    @Test
    void partyAssignmentLabelsMakeTheToggleStateExplicit() {
        assertEquals("Assigned · HQ Team", WarTerritoryPickerScreen.teamAssignmentLabel("HQ Team", true));
        assertEquals("Unassigned · FFA 1", WarTerritoryPickerScreen.teamAssignmentLabel("FFA 1", false));
    }

    @Test
    void mapHeaderControlsRemainClickableOnNarrowManagerScreens() {
        PickerControlLayout layout = PickerControlLayout.create(320, 480, 42, true);

        assertEquals(new Bounds(148, 5, 70, 23), layout.resourceToggle());
        assertEquals(new Bounds(224, 5, 86, 23), layout.lockToggle());
        assertEquals(310, layout.lockToggle().x() + layout.lockToggle().width());
        assertFalse(layout.resourceToggle().contains(layout.lockToggle().x(), layout.lockToggle().y()));
    }

    @Test
    void pureLayoutSharesSidebarBoundsAcrossRenderingAndInput() {
        PickerControlLayout layout = PickerControlLayout.create(800, 480, 146, true);

        assertEquals(new Bounds(10, 60, 216, 24), layout.nameField());
        assertEquals(new Bounds(2, 94, 232, 146), layout.colorWidget());
        assertEquals(250, layout.teamsLabelY());
        assertEquals(List.of(
                new Bounds(10, 264, 216, 21),
                new Bounds(10, 288, 216, 21)), layout.teamRows());
        assertEquals(new Bounds(0, 250, 236, 68), layout.teamScroll());
        assertEquals(318, layout.selectionLabelY());
        assertEquals(new Bounds(10, 363, 68, 23), layout.clear());
        assertEquals(new Bounds(10, 442, 78, 23), layout.cancel());
        assertEquals(new Bounds(134, 442, 92, 23), layout.save());
        assertEquals(new Bounds(236, 34, 564, 446), layout.map());
    }

    @Test
    void viewerLayoutOmitsTheManagerLockAndUsesTheFreedHeaderSpace() {
        PickerControlLayout layout = PickerControlLayout.create(320, 480, 42, false);

        assertEquals(new Bounds(240, 5, 70, 23), layout.resourceToggle());
        assertEquals(0, layout.lockToggle().width());
        assertFalse(layout.lockToggle().contains(310, 5));
    }

    @Test
    void newZoneSeesEveryTerritoryButCanOnlySelectUnownedTerritories() {
        WarPlannerSnapshot.Zone north = zone(1, "North", "Ragni");
        WarPlannerSnapshot.Zone south = zone(2, "South", "Almuj");
        WarPlannerSnapshot snapshot = snapshot(List.of(north, south), List.of("Ragni", "Detlas", "Almuj"));

        TerritoryAccess access = WarTerritoryPickerPolicy.territoryAccess(snapshot, null);

        assertEquals(Set.of("Ragni", "Detlas", "Almuj"), access.visibleNames());
        assertEquals(Set.of("Detlas"), access.selectableNames());
        assertSame(north, access.unavailableOwner("RAGNI"));
        assertSame(south, access.unavailableOwner("almuj"));
        assertTrue(access.isVisible("ragni"));
        assertFalse(access.isSelectable("Ragni"));
    }

    @Test
    void editedZoneCanReselectItsOwnTerritoriesWhileOtherZonesStayUnavailable() {
        WarPlannerSnapshot.Zone north = zone(1, "North", "Ragni");
        WarPlannerSnapshot.Zone south = zone(2, "South", "Almuj");
        WarPlannerSnapshot snapshot = snapshot(List.of(north, south), List.of("Ragni", "Detlas", "Almuj"));

        TerritoryAccess access = WarTerritoryPickerPolicy.territoryAccess(snapshot, north.id());

        assertEquals(Set.of("Ragni", "Detlas", "Almuj"), access.visibleNames());
        assertEquals(Set.of("Ragni", "Detlas"), access.selectableNames());
        assertNull(access.unavailableOwner("Ragni"));
        assertSame(south, access.unavailableOwner("Almuj"));
    }

    @Test
    void keyboardFocusUsesTheSameEnabledControlsAsMouseInput() {
        List<ControlTarget> idle = WarTerritoryPickerPolicy.keyboardOrder(true, 2, false);
        assertEquals(List.of(
                ControlTarget.named(ControlKind.NAME),
                ControlTarget.team(0),
                ControlTarget.team(1),
                ControlTarget.named(ControlKind.CLEAR),
                ControlTarget.named(ControlKind.CANCEL),
                ControlTarget.named(ControlKind.SAVE),
                ControlTarget.named(ControlKind.RESOURCE_COLORS),
                ControlTarget.named(ControlKind.LOCK_MAIN_MAP)), idle);

        assertEquals(
                List.of(
                        ControlTarget.named(ControlKind.CANCEL),
                        ControlTarget.named(ControlKind.RESOURCE_COLORS),
                        ControlTarget.named(ControlKind.LOCK_MAIN_MAP)),
                WarTerritoryPickerPolicy.keyboardOrder(true, 2, true));
        assertEquals(
                ControlTarget.named(ControlKind.NAME),
                WarTerritoryPickerPolicy.nextKeyboardTarget(
                        ControlTarget.named(ControlKind.LOCK_MAIN_MAP), false, true, 2, false));
        assertEquals(
                ControlTarget.named(ControlKind.LOCK_MAIN_MAP),
                WarTerritoryPickerPolicy.nextKeyboardTarget(
                        ControlTarget.named(ControlKind.NAME), true, true, 2, false));
    }

    @Test
    void customControlActivationAndHitEdgesAreConsistent() {
        assertTrue(WarTerritoryPickerPolicy.isActivationKey(GLFW.GLFW_KEY_ENTER));
        assertTrue(WarTerritoryPickerPolicy.isActivationKey(GLFW.GLFW_KEY_KP_ENTER));
        assertTrue(WarTerritoryPickerPolicy.isActivationKey(GLFW.GLFW_KEY_SPACE));
        assertFalse(WarTerritoryPickerPolicy.isActivationKey(GLFW.GLFW_KEY_A));

        Bounds bounds = new Bounds(10, 20, 30, 40);
        assertTrue(bounds.contains(10, 20));
        assertTrue(bounds.contains(39.99f, 59.99f));
        assertFalse(bounds.contains(40, 20));
        assertFalse(bounds.contains(10, 60));
        assertFalse(Bounds.empty(10, 20).contains(10, 20));

        assertEquals(0, WarTerritoryPickerPolicy.scrollStart(-5, 8, 4));
        assertEquals(4, WarTerritoryPickerPolicy.scrollStart(99, 8, 4));
        assertEquals(0, WarTerritoryPickerPolicy.scrollStart(5, 2, 4));
    }

    private static WarPlannerSnapshot.Zone zone(long id, String name, String... territories) {
        return new WarPlannerSnapshot.Zone(id, name, "#55B8C5", List.of(), 1L, List.of(territories));
    }

    private static WarPlannerSnapshot snapshot(
            List<WarPlannerSnapshot.Zone> zones, List<String> territories) {
        return new WarPlannerSnapshot(
                3,
                17,
                null,
                new WarPlannerSnapshot.Self("self", true),
                true,
                List.of(),
                List.of(),
                new WarPlannerSnapshot.SupportBoard(1L, List.of()),
                zones,
                territories,
                List.of());
    }
}
