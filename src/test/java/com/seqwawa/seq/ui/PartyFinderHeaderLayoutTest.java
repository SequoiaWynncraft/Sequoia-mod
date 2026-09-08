package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartyFinderHeaderLayoutTest {
    @Test
    void roleSelectorSitsBesideNewPartyAndLeavesRoomForTheTitle() {
        var layout = PartyFinderScreen.computeHeaderControlsLayout(140, 600, List.of(85f), 80, 110, false);
        assertEquals(layout.newPartyButton().y(), layout.roleDropdown().y());
        assertEquals(layout.newPartyButton().x() + layout.newPartyButton().w() + 6, layout.roleDropdown().x());
        assertTrue(layout.roleDropdown().x() + layout.roleDropdown().w() <= layout.titleRight() - 110 - 6);
        assertEquals(140, layout.searchBar().w());
        assertNull(layout.manageButton());
    }

    @Test
    void newPartyAndRoleSelectorWrapTogetherBelowTheTitle() {
        var layout = PartyFinderScreen.computeHeaderControlsLayout(140, 320, List.of(85f), 80, 110, false);
        assertTrue(layout.newPartyButton().y() > layout.searchBar().y());
        assertEquals(layout.newPartyButton().y(), layout.roleDropdown().y());
        assertEquals(148, layout.newPartyButton().x());
        assertTrue(layout.height() >= layout.roleDropdown().y() + layout.roleDropdown().h());
    }

    @Test
    void leaderControlsRemainInsideTheHeaderWithoutOverlapsAcrossWidths() {
        for (float width : new float[] {180, 240, 320, 480, 800}) {
            var layout = PartyFinderScreen.computeHeaderControlsLayout(
                    140, width, List.of(100f, 55f, 85f, 90f, 65f, 80f), 80, 110, true);
            assertNull(layout.newPartyButton());
            var controls = new ArrayList<>(List.of(layout.searchBar(), layout.manageButton(),
                    layout.roleDropdown(), layout.inviteButton(), layout.openCloseButton(),
                    layout.delistButton(), layout.inviteAllButton(), layout.scanButton()));
            for (int i = 0; i < controls.size(); i++) {
                var a = controls.get(i);
                assertTrue(a.x() >= 148 && a.w() >= 0);
                assertTrue(a.x() + a.w() <= layout.titleRight());
                assertTrue(a.y() + a.h() <= layout.height());
                if (a.y() == layout.searchBar().y()) {
                    assertTrue(a.x() + a.w() <= layout.titleRight() - 110 - 6);
                }
                for (int j = i + 1; j < controls.size(); j++) {
                    var c = controls.get(j);
                    assertFalse(a.x() < c.x() + c.w() && a.x() + a.w() > c.x()
                            && a.y() < c.y() + c.h() && a.y() + a.h() > c.y());
                }
            }
        }
    }
}
