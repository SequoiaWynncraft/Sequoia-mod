package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.PartyCloseReason;
import com.seqwawa.seq.model.PartyStatus;
import com.seqwawa.seq.ui.PartyFinderScreen.OpenClose;
import com.seqwawa.seq.ui.PartyFinderScreen.OpenCloseButton;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PartyFinderHeaderLayoutTest {
    @Test
    void roleSelectorSitsBesideNewPartyAndLeavesRoomForTheTitle() {
        var layout = PartyFinderScreen.computeHeaderControlsLayout(140, 600, List.of(85f), 80, 110, false);
        assertEquals(layout.newPartyButton().y(), layout.roleDropdown().y());
        assertEquals(layout.newPartyButton().x() + layout.newPartyButton().width() + 6, layout.roleDropdown().x());
        assertTrue(layout.roleDropdown().x() + layout.roleDropdown().width() <= layout.titleRight() - 110 - 6);
        assertEquals(140, layout.searchBar().width());
        assertNull(layout.manageButton());
    }

    @Test
    void newPartyAndRoleSelectorWrapTogetherBelowTheTitle() {
        var layout = PartyFinderScreen.computeHeaderControlsLayout(140, 320, List.of(85f), 80, 110, false);
        assertTrue(layout.newPartyButton().y() > layout.searchBar().y());
        assertEquals(layout.newPartyButton().y(), layout.roleDropdown().y());
        assertEquals(148, layout.newPartyButton().x());
        assertTrue(layout.height() >= layout.roleDropdown().y() + layout.roleDropdown().height());
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
                assertTrue(a.x() >= 148 && a.width() >= 0);
                assertTrue(a.x() + a.width() <= layout.titleRight());
                assertTrue(a.y() + a.height() <= layout.height());
                if (a.y() == layout.searchBar().y()) {
                    assertTrue(a.x() + a.width() <= layout.titleRight() - 110 - 6);
                }
                for (int j = i + 1; j < controls.size(); j++) {
                    var c = controls.get(j);
                    assertFalse(a.x() < c.x() + c.width() && a.x() + a.width() > c.x()
                            && a.y() < c.y() + c.height() && a.y() + a.height() > c.y());
                }
            }
        }
    }

    @Test
    void aClickOnTheFirstCardIsHitTestedAgainstTheHeaderAsDrawnNotAsAPromotionWouldWrapIt() {
        // Drawn for a member, where a leader's controls wrap onto more rows.
        var drawn = PartyFinderScreen.computeHeaderControlsLayout(140, 320, List.of(85f), 80, 110, false);
        var promoted = PartyFinderScreen.computeHeaderControlsLayout(
                140, 320, List.of(100f, 55f, 85f, 90f, 65f, 80f), 80, 110, true);
        var onFirstCard = new UiPointer(150, drawn.height() + 4);

        var hitTested = PartyFinderScreen.hitTestedHeader(drawn, () -> promoted);

        assertTrue(promoted.height() > onFirstCard.y(), "recomputed after the promotion, the header covers the card");
        assertTrue(onFirstCard.y() >= hitTested.height(), "the click reaches the cards");
        for (var control : java.util.Arrays.asList(hitTested.searchBar(), hitTested.manageButton(),
                hitTested.inviteButton(), hitTested.openCloseButton(), hitTested.delistButton(),
                hitTested.inviteAllButton(), hitTested.scanButton(), hitTested.newPartyButton(),
                hitTested.roleDropdown())) {
            assertFalse(control != null && control.contains(onFirstCard), "no header control takes it");
        }
    }

    @Test
    void theOpenCloseButtonRunsWhatItShowedOrNothing() {
        var drawn = OpenCloseButton.of(listing(7L, PartyStatus.OPEN, null), true);
        assertEquals(OpenClose.CLOSE, drawn.operation(), "drawn as Close party");

        // The backend closed the listing after the frame that drew Close party.
        assertNull(OpenCloseButton.clicked(drawn, listing(7L, PartyStatus.CLOSED, null), true),
                "the click does not open the party it showed as open");
        assertNull(OpenCloseButton.clicked(drawn, listing(8L, PartyStatus.OPEN, null), true), "another listing");
        assertNull(OpenCloseButton.clicked(drawn, listing(7L, PartyStatus.OPEN, null), false), "no longer the leader");
        assertNull(OpenCloseButton.clicked(drawn, null, false), "the listing has gone");
        assertNull(OpenCloseButton.clicked(null, listing(7L, PartyStatus.OPEN, null), true), "nothing drawn yet");
        assertEquals(OpenClose.CLOSE, OpenCloseButton.clicked(drawn, listing(7L, PartyStatus.OPEN, null), true));

        var autoClosed = OpenCloseButton.of(listing(7L, PartyStatus.CLOSED, PartyCloseReason.AUTO_CAPACITY), true);
        assertEquals(OpenClose.AUTO_CLOSED, autoClosed.operation());
        assertNull(OpenCloseButton.clicked(autoClosed, listing(7L, PartyStatus.CLOSED, null), true),
                "closed by the leader since, it is not reopened from the Auto-closed button");
    }

    private static Listing listing(long id, PartyStatus status, PartyCloseReason closeReason) {
        return new Listing(id, List.of(), null, "leader", null, status, closeReason, null, List.of(), List.of(), Instant.EPOCH);
    }
}
