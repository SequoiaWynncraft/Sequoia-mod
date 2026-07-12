package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import org.junit.jupiter.api.Test;

class RaidPartySnapshotTrackerTest {

    @Test
    void usesCompleteSnapshotContainingLocalPlayer() {
        assertEquals(
                List.of("ActualOne", "LocalPlayer", "ActualThree", "ActualFour"),
                RaidPartySnapshotTracker.choosePartyMembers(
                        List.of("Nickname", "LocalPlayer", "ActualThree", "Fourth"),
                        List.of(
                                member("Nickname", "ActualOne"),
                                member("LocalPlayer", "LocalPlayer"),
                                member("ActualThree", "ActualThree"),
                                member("Fourth", "ActualFour")),
                        4,
                        "LocalPlayer"));
    }

    @Test
    void keepsParsedNamesWhenSnapshotHasWrongSize() {
        List<String> parsed = List.of("LocalPlayer", "Second", "Third", "Fourth");

        assertEquals(
                parsed,
                RaidPartySnapshotTracker.choosePartyMembers(
                        parsed,
                        List.of(
                                member("LocalPlayer", "LocalPlayer"),
                                member("Second", "Second"),
                                member("Third", "Third")),
                        4,
                        "LocalPlayer"));
    }

    @Test
    void keepsParsedNamesWhenSnapshotDoesNotContainLocalPlayer() {
        List<String> parsed = List.of("LocalPlayer", "Second", "Third", "Fourth");

        assertEquals(
                parsed,
                RaidPartySnapshotTracker.choosePartyMembers(
                        parsed,
                        List.of(
                                member("OtherOne", "OtherOne"),
                                member("OtherTwo", "OtherTwo"),
                                member("OtherThree", "OtherThree"),
                                member("OtherFour", "OtherFour")),
                        4,
                        "LocalPlayer"));
    }

    @Test
    void keepsAReplacementMemberThatIsNotInTheSnapshot() {
        List<String> parsed = List.of("LocalPlayer", "Second", "Third", "NewFourth");

        assertEquals(
                parsed,
                RaidPartySnapshotTracker.choosePartyMembers(
                        parsed,
                        List.of(
                                member("LocalPlayer", "LocalPlayer"),
                                member("Second", "Second"),
                                member("Third", "Third"),
                                member("OldFourth", "OldFourth")),
                        4,
                        "LocalPlayer"));
    }

    @Test
    void ignoresInvalidAndDuplicateSnapshotNames() {
        List<String> parsed = List.of("LocalPlayer", "Second", "Third", "ParsedFourth");

        assertEquals(
                List.of("LocalPlayer", "Second", "Third", "ActualFourth"),
                RaidPartySnapshotTracker.choosePartyMembers(
                        parsed,
                        List.of(
                                member("LocalPlayer", "LocalPlayer"),
                                member("localplayer", "LocalPlayer"),
                                member("Second", "Second"),
                                member("Third", "Third"),
                                member("ParsedFourth", "ActualFourth"),
                                member("bad name", "bad name")),
                        4,
                        "LocalPlayer"));
    }

    @Test
    void preservesTheLastSnapshotWhileTheRaidSidebarIsActive() {
        RaidPartySnapshotTracker.PartySnapshot current = RaidPartySnapshotTracker.PartySnapshot.from(
                List.of(member("Nickname", "ActualPlayer"), member("LocalPlayer", "LocalPlayer")), 1_000);
        RaidPartySnapshotTracker.PartySnapshot empty =
                RaidPartySnapshotTracker.PartySnapshot.from(List.of(), 3_000);

        RaidPartySnapshotTracker.PartySnapshot updated =
                RaidPartySnapshotTracker.updateSnapshot(current, empty, true, 3_000);

        assertEquals(List.of("ActualPlayer", "LocalPlayer"), updated.usernames());
        assertEquals(3_000, updated.capturedAtMs());
        assertEquals("ActualPlayer", updated.aliases().get("nickname"));
    }

    @Test
    void preservesAliasesWhenTheObservedRosterIsUnchanged() {
        RaidPartySnapshotTracker.PartySnapshot current = RaidPartySnapshotTracker.PartySnapshot.from(
                List.of(member("Nickname", "ActualPlayer"), member("LocalPlayer", "LocalPlayer")), 1_000);
        RaidPartySnapshotTracker.PartySnapshot observed = RaidPartySnapshotTracker.PartySnapshot.from(
                List.of(member("ActualPlayer", "ActualPlayer"), member("LocalPlayer", "LocalPlayer")), 3_000);

        RaidPartySnapshotTracker.PartySnapshot updated =
                RaidPartySnapshotTracker.updateSnapshot(current, observed, true, 3_000);

        assertEquals("ActualPlayer", updated.aliases().get("nickname"));
        assertEquals(3_000, updated.capturedAtMs());
    }

    @Test
    void replacesAliasesWhenTheObservedRosterChanges() {
        RaidPartySnapshotTracker.PartySnapshot current = RaidPartySnapshotTracker.PartySnapshot.from(
                List.of(member("OldNickname", "OldPlayer"), member("LocalPlayer", "LocalPlayer")), 1_000);
        RaidPartySnapshotTracker.PartySnapshot observed = RaidPartySnapshotTracker.PartySnapshot.from(
                List.of(member("NewNickname", "NewPlayer"), member("LocalPlayer", "LocalPlayer")), 3_000);

        RaidPartySnapshotTracker.PartySnapshot updated =
                RaidPartySnapshotTracker.updateSnapshot(current, observed, true, 3_000);

        assertEquals(List.of("NewPlayer", "LocalPlayer"), updated.usernames());
        assertNull(updated.aliases().get("oldnickname"));
        assertEquals("NewPlayer", updated.aliases().get("newnickname"));
    }

    @Test
    void doesNotRefreshAnEmptyObservationOutsideARaid() {
        RaidPartySnapshotTracker.PartySnapshot current = RaidPartySnapshotTracker.PartySnapshot.from(
                List.of(member("LocalPlayer", "LocalPlayer")), 1_000);
        RaidPartySnapshotTracker.PartySnapshot empty =
                RaidPartySnapshotTracker.PartySnapshot.from(List.of(), 3_000);

        RaidPartySnapshotTracker.PartySnapshot updated =
                RaidPartySnapshotTracker.updateSnapshot(current, empty, false, 3_000);

        assertEquals(1_000, updated.capturedAtMs());
    }

    private RaidPartySnapshotTracker.SnapshotMember member(String displayedName, String username) {
        return new RaidPartySnapshotTracker.SnapshotMember(displayedName, username);
    }
}
