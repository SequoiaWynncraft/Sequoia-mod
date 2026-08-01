package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RaidPartySnapshotTrackerTest {

    @AfterEach
    void resetTracker() {
        RaidPartySnapshotTracker.reset();
    }

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
                        4));
    }

    @Test
    void usesFullSnapshotWhenCompletionListsGuildSubset() {
        assertEquals(
                List.of("LocalPlayer", "ActualAlly", "OtherPlayer", "FourthPlayer"),
                RaidPartySnapshotTracker.choosePartyMembers(
                        List.of("AllyNickname", "OtherPlayer"),
                        List.of(
                                member("LocalPlayer", "LocalPlayer"),
                                member("AllyNickname", "ActualAlly"),
                                member("OtherPlayer", "OtherPlayer"),
                                member("FourthPlayer", "FourthPlayer")),
                        2));
    }

    @Test
    void usesCompleteSmallerRaidSnapshotWhenCompletionListsGuildSubset() {
        assertEquals(
                List.of("LocalPlayer", "ActualAlly", "OtherPlayer"),
                RaidPartySnapshotTracker.choosePartyMembers(
                        List.of("AllyNickname"),
                        List.of(
                                member("LocalPlayer", "LocalPlayer"),
                                member("AllyNickname", "ActualAlly"),
                                member("OtherPlayer", "OtherPlayer")),
                        1));
    }

    @Test
    void supportsAnnihilationSizedRaidSnapshots() {
        RaidPartySnapshotTracker.PartySnapshot annihilationParty = RaidPartySnapshotTracker.PartySnapshot.from(
                List.of(
                        member("One", "One"),
                        member("Two", "Two"),
                        member("Three", "Three"),
                        member("Four", "Four"),
                        member("Five", "Five"),
                        member("Six", "Six"),
                        member("Seven", "Seven"),
                        member("Eight", "Eight"),
                        member("Nine", "Nine"),
                        member("Ten", "Ten")),
                true,
                1_000);

        assertEquals(
                annihilationParty.usernames(),
                RaidPartySnapshotTracker.choosePartyMembers(
                        List.of("One", "Two"), annihilationParty, 2, 10));
    }

    @Test
    void rejectsOversizedSnapshotForRegularRaid() {
        List<String> parsed = List.of("One", "Two");
        RaidPartySnapshotTracker.PartySnapshot contaminated = RaidPartySnapshotTracker.PartySnapshot.from(
                List.of(
                        member("One", "One"),
                        member("Two", "Two"),
                        member("Three", "Three"),
                        member("Four", "Four"),
                        member("Stale", "Stale")),
                true,
                1_000);

        assertEquals(
                parsed,
                RaidPartySnapshotTracker.choosePartyMembers(parsed, contaminated, 2, 4));
    }

    @Test
    void acceptsCompleteCompatibleSupplementalRoster() {
        assertEquals(
                List.of("LocalPlayer", "ActualTwo", "ActualOne"),
                RaidPartySnapshotTracker.compatibleSupplementalRoster(
                        List.of(
                                member("FirstNick", "ActualOne"),
                                member("SecondNick", null),
                                member("LocalNick", "LocalPlayer")),
                        3,
                        List.of("LocalPlayer", "ActualTwo", "ActualOne")));
    }

    @Test
    void rejectsStaleOrWrongSizedSupplementalRoster() {
        List<RaidPartySnapshotTracker.SnapshotMember> scoreboard =
                List.of(member("FirstNick", "ActualOne"), member("SecondNick", null));

        assertEquals(
                List.of(),
                RaidPartySnapshotTracker.compatibleSupplementalRoster(
                        scoreboard, 2, List.of("ActualOne", "ActualTwo", "StalePlayer")));
        assertEquals(
                List.of(),
                RaidPartySnapshotTracker.compatibleSupplementalRoster(
                        scoreboard, 2, List.of("DifferentOne", "DifferentTwo")));
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
                        4));
    }

    @Test
    void usesCompleteSmallerSnapshotWhenCompletionDoesNotContainLocalPlayer() {
        assertEquals(
                List.of("ActualOne", "ActualTwo", "LocalPlayer"),
                RaidPartySnapshotTracker.choosePartyMembers(
                        List.of("FirstNickname", "SecondNickname"),
                        List.of(
                                member("FirstNickname", "ActualOne"),
                                member("SecondNickname", "ActualTwo"),
                                member("LocalPlayer", "LocalPlayer")),
                        2));
    }

    @Test
    void keepsParsedNamesWhenSnapshotDoesNotOverlapCompletion() {
        List<String> parsed = List.of("NewOne", "NewSecond", "NewThird", "NewFourth");

        assertEquals(
                parsed,
                RaidPartySnapshotTracker.choosePartyMembers(
                        parsed,
                        List.of(
                                member("OtherOne", "OtherOne"),
                                member("OldSecond", "OldSecond"),
                                member("Third", "Third"),
                                member("OldFourth", "OldFourth")),
                        4));
    }

    @Test
    void keepsPacketResolvedNamesWhenOnlyPartOfSnapshotMatches() {
        List<String> parsed = List.of("xmattypazox", "GuildPlayer", "a3pki", "Robbedoesgek");

        assertEquals(
                parsed,
                RaidPartySnapshotTracker.choosePartyMembers(
                        parsed,
                        List.of(
                                member("GuildPlayer", "GuildPlayer"),
                                member("a3pki", "a3pki"),
                                member("Robbedoestna", "Robbedoesgek"),
                                member("DrBavaro", "DrBavaro")),
                        4));
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
                        4));
    }

    @Test
    void keepsAnAmbiguousNicknameUnresolved() {
        assertEquals(
                List.of("SharedNickname"),
                RaidPartySnapshotTracker.choosePartyMembers(
                        List.of("SharedNickname"),
                        List.of(
                                member("SharedNickname", "ActualOne"),
                                member("SharedNickname", "ActualTwo")),
                        1));
    }

    @Test
    void doesNotResolveCrossPartyAliasesFromANormalPartySnapshot() {
        RaidPartySnapshotTracker.PartySnapshot normalParty = RaidPartySnapshotTracker.PartySnapshot.from(
                List.of(member("AllyNickname", "ActualAlly")), false, 1_000);

        assertEquals(
                List.of("AllyNickname"),
                RaidPartySnapshotTracker.choosePartyMembers(List.of("AllyNickname"), normalParty, 1));
    }

    @Test
    void locksThePreTransferPartyWhenTheRaidSidebarAppears() {
        RaidPartySnapshotTracker.TrackerState state = RaidPartySnapshotTracker.TrackerState.empty();
        RaidPartySnapshotTracker.PartySnapshot normalParty = snapshot(
                false,
                1_000,
                member("FirstNick", "ActualOne"),
                member("SecondNick", "ActualTwo"),
                member("LocalPlayer", "LocalPlayer"));

        state = state.observe(normalParty, true, false, 1_000);
        state = state.observe(empty(false, 3_000), false, false, 3_000);
        state = state.observe(
                snapshot(true, 4_000, member("LocalPlayer", "LocalPlayer")), false, true, 4_000);

        assertEquals(RaidPartySnapshotTracker.Phase.ACTIVE, state.phase());
        assertEquals(List.of("ActualOne", "ActualTwo", "LocalPlayer"), state.activeRaidParty().usernames());
        assertTrue(state.activeRaidParty().raidContext());
    }

    @Test
    void lockedRaidMembershipSurvivesForTheWholeMultiMinuteRaid() {
        RaidPartySnapshotTracker.TrackerState state = activeState(1_000);
        RaidPartySnapshotTracker.PartySnapshot incompatible = snapshot(
                true,
                190_000,
                member("ReplacementNick", "Replacement"),
                member("LocalPlayer", "LocalPlayer"));

        state = state.observe(incompatible, false, true, 190_000);
        state = state.observe(empty(true, 200_000), false, true, 200_000);

        assertEquals(RaidPartySnapshotTracker.Phase.ACTIVE, state.phase());
        assertEquals(List.of("ActualOne", "ActualTwo", "LocalPlayer"), state.activeRaidParty().usernames());
        assertFalse(state.activeRaidParty().aliases().containsKey("replacementnick"));
    }

    @Test
    void lockedRaidCanLearnAliasesForKnownMembersWithoutChangingMembership() {
        RaidPartySnapshotTracker.TrackerState state = activeState(1_000);

        state = state.observe(
                snapshot(
                        true,
                        3_000,
                        member("NewNickname", "ActualTwo"),
                        member("StrangerNick", "Stranger")),
                false,
                true,
                3_000);

        assertEquals(List.of("ActualOne", "ActualTwo", "LocalPlayer"), state.activeRaidParty().usernames());
        assertEquals("ActualTwo", state.activeRaidParty().aliases().get("newnickname"));
        assertNull(state.activeRaidParty().aliases().get("strangernick"));
    }

    @Test
    void fallbackAcquisitionUnionsEarlyRaidObservationsThenLocks() {
        RaidPartySnapshotTracker.TrackerState state = RaidPartySnapshotTracker.TrackerState.empty();
        state = state.observe(
                snapshot(true, 1_000, member("One", "One"), member("Local", "Local")),
                false,
                true,
                1_000);
        state = state.observe(
                snapshot(
                        true,
                        3_000,
                        member("One", "One"),
                        member("Two", "Two"),
                        member("Local", "Local")),
                false,
                true,
                3_000);
        state = state.observe(
                snapshot(
                        true,
                        5_000,
                        member("One", "One"),
                        member("Two", "Two"),
                        member("Local", "Local")),
                false,
                true,
                5_000);
        assertEquals(RaidPartySnapshotTracker.Phase.ACQUIRING, state.phase());
        state = state.observe(
                snapshot(
                        true,
                        7_000,
                        member("One", "One"),
                        member("Two", "Two"),
                        member("Local", "Local")),
                false,
                true,
                7_000);

        assertEquals(RaidPartySnapshotTracker.Phase.ACTIVE, state.phase());
        assertEquals(List.of("One", "Local", "Two"), state.activeRaidParty().usernames());
    }

    @Test
    void briefRaidSidebarLossDoesNotEndTheRaid() {
        RaidPartySnapshotTracker.TrackerState state = activeState(1_000);
        state = state.observe(empty(false, 10_000), false, false, 10_000);
        state = state.observe(empty(false, 24_999), false, false, 24_999);

        assertEquals(RaidPartySnapshotTracker.Phase.ACTIVE, state.phase());
        assertFalse(state.activeRaidParty().usernames().isEmpty());

        state = state.observe(empty(false, 25_000), false, false, 25_000);
        assertEquals(RaidPartySnapshotTracker.Phase.NORMAL, state.phase());
        assertTrue(state.activeRaidParty().usernames().isEmpty());
    }

    @Test
    void shortWorldTransferPreservesCandidateButARealDepartureExpiresIt() {
        RaidPartySnapshotTracker.TrackerState candidateState = RaidPartySnapshotTracker.TrackerState.empty()
                .observe(snapshot(false, 1_000, member("ActualOne", "ActualOne")), true, false, 1_000);
        RaidPartySnapshotTracker.setStateForTest(candidateState);

        RaidPartySnapshotTracker.onServerUnavailable(2_000);
        RaidPartySnapshotTracker.onServerUnavailable(91_999);
        assertEquals(List.of("ActualOne"),
                RaidPartySnapshotTracker.stateForTest().candidateParty().usernames());

        RaidPartySnapshotTracker.onServerUnavailable(92_000);
        assertTrue(RaidPartySnapshotTracker.stateForTest().candidateParty().usernames().isEmpty());
    }

    @Test
    void partyChangesCannotInvalidateAnActiveRaid() {
        RaidPartySnapshotTracker.setStateForTest(activeState(1_000));

        RaidPartySnapshotTracker.onPartyChanged();

        RaidPartySnapshotTracker.TrackerState state = RaidPartySnapshotTracker.stateForTest();
        assertEquals(RaidPartySnapshotTracker.Phase.ACTIVE, state.phase());
        assertEquals(List.of("ActualOne", "ActualTwo", "LocalPlayer"), state.activeRaidParty().usernames());
        assertTrue(state.candidateParty().usernames().isEmpty());
    }

    @Test
    void completionPreventsPostRaidSidebarFromReacquiringMembers() {
        RaidPartySnapshotTracker.TrackerState state = activeState(1_000).finishRaid();

        state = state.observe(
                snapshot(true, 3_000, member("Wrong", "Wrong")), false, true, 3_000);
        assertEquals(RaidPartySnapshotTracker.Phase.FINISHED_WAITING_FOR_EXIT, state.phase());
        assertTrue(state.activeRaidParty().usernames().isEmpty());

        state = state.observe(
                snapshot(false, 4_000, member("NextParty", "NextParty")), true, false, 4_000);
        assertEquals(RaidPartySnapshotTracker.Phase.NORMAL, state.phase());
        assertEquals(List.of("NextParty"), state.candidateParty().usernames());
    }

    private RaidPartySnapshotTracker.TrackerState activeState(long now) {
        RaidPartySnapshotTracker.TrackerState state = RaidPartySnapshotTracker.TrackerState.empty();
        state = state.observe(
                snapshot(
                        false,
                        now,
                        member("FirstNick", "ActualOne"),
                        member("SecondNick", "ActualTwo"),
                        member("LocalPlayer", "LocalPlayer")),
                true,
                false,
                now);
        return state.observe(
                snapshot(true, now + 1, member("LocalPlayer", "LocalPlayer")),
                false,
                true,
                now + 1);
    }

    private RaidPartySnapshotTracker.PartySnapshot snapshot(
            boolean raidContext, long capturedAtMs, RaidPartySnapshotTracker.SnapshotMember... members) {
        return RaidPartySnapshotTracker.PartySnapshot.from(List.of(members), raidContext, capturedAtMs);
    }

    private RaidPartySnapshotTracker.PartySnapshot empty(boolean raidContext, long capturedAtMs) {
        return RaidPartySnapshotTracker.PartySnapshot.from(List.of(), raidContext, capturedAtMs);
    }

    private RaidPartySnapshotTracker.SnapshotMember member(String displayedName, String username) {
        return new RaidPartySnapshotTracker.SnapshotMember(displayedName, username);
    }
}
