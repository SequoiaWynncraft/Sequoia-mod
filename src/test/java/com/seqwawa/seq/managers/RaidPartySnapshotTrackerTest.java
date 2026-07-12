package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class RaidPartySnapshotTrackerTest {

    @Test
    void usesCompleteSnapshotContainingLocalPlayer() {
        assertEquals(
                List.of("ActualOne", "LocalPlayer", "ActualThree", "ActualFour"),
                RaidPartySnapshotTracker.choosePartyMembers(
                        List.of("Nickname", "LocalPlayer", "Third", "Fourth"),
                        List.of("ActualOne", "LocalPlayer", "ActualThree", "ActualFour"),
                        4,
                        "LocalPlayer"));
    }

    @Test
    void keepsParsedNamesWhenSnapshotHasWrongSize() {
        List<String> parsed = List.of("LocalPlayer", "Second", "Third", "Fourth");

        assertEquals(
                parsed,
                RaidPartySnapshotTracker.choosePartyMembers(
                        parsed, List.of("LocalPlayer", "Second", "Third"), 4, "LocalPlayer"));
    }

    @Test
    void keepsParsedNamesWhenSnapshotDoesNotContainLocalPlayer() {
        List<String> parsed = List.of("LocalPlayer", "Second", "Third", "Fourth");

        assertEquals(
                parsed,
                RaidPartySnapshotTracker.choosePartyMembers(
                        parsed,
                        List.of("OtherOne", "OtherTwo", "OtherThree", "OtherFour"),
                        4,
                        "LocalPlayer"));
    }

    @Test
    void ignoresInvalidAndDuplicateSnapshotNames() {
        List<String> parsed = List.of("LocalPlayer", "Second", "Third", "Fourth");

        assertEquals(
                parsed,
                RaidPartySnapshotTracker.choosePartyMembers(
                        parsed,
                        List.of("LocalPlayer", "localplayer", "bad name", "Fourth"),
                        4,
                        "LocalPlayer"));
    }
}
