package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PartyFinderViewLayoutTest {
    @Test
    void laysOutLeaderHeaderControlsWithoutChangingSpacing() {
        PartyFinderViewLayout.HeaderControls layout = PartyFinderViewLayout.headerControls(140, true);

        assertEquals(new PartyFinderViewLayout.Bounds(148, 6, 140, 18), layout.searchBar());
        assertEquals(new PartyFinderViewLayout.Bounds(296, 6, 88, 18), layout.manageButton());
        assertEquals(new PartyFinderViewLayout.Bounds(390, 6, 56, 18), layout.inviteButton());
        assertEquals(new PartyFinderViewLayout.Bounds(452, 6, 84, 18), layout.openCloseButton());
        assertEquals(new PartyFinderViewLayout.Bounds(542, 6, 72, 18), layout.delistButton());
        assertEquals(new PartyFinderViewLayout.Bounds(620, 6, 68, 18), layout.inviteAllButton());
        assertNull(layout.newPartyButton());
        assertEquals(new PartyFinderViewLayout.Bounds(694, 6, 80, 18), layout.roleDropdown());
    }

    @Test
    void laysOutMemberHeaderControlsAndUsesInclusiveHitBounds() {
        PartyFinderViewLayout.HeaderControls layout = PartyFinderViewLayout.headerControls(140, false);

        assertEquals(new PartyFinderViewLayout.Bounds(296, 6, 80, 18), layout.newPartyButton());
        assertEquals(new PartyFinderViewLayout.Bounds(382, 6, 80, 18), layout.roleDropdown());
        assertTrue(layout.newPartyButton().contains(376, 24));
        assertFalse(layout.newPartyButton().contains(377, 24));
    }

    @Test
    void fitsLabelsUsingTheExistingThreeDotSuffix() {
        assertEquals("abcdef", PartyFinderViewLayout.fitText("abcdef", 6, String::length));
        assertEquals("ab...", PartyFinderViewLayout.fitText("abcdef", 5, String::length));
        assertEquals("", PartyFinderViewLayout.fitText("abcdef", 2, String::length));
        assertEquals("", PartyFinderViewLayout.fitText(null, 10, String::length));
    }
}
