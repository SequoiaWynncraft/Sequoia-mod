package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.GuildMemberPresence;
import com.seqwawa.seq.model.GuildMemberPresence.GuildRank;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class GuildPresenceManagerTest {

    private static final Predicate<String> NOBODY_BUSY = username -> false;

    private static GuildMemberPresence member(String username, String world) {
        return new GuildMemberPresence(username, "uuid-" + username, GuildRank.RECRUIT, world, false);
    }

    @Test
    void aFailedFetchIsRetriedSoonerThanWynncraftsCacheInterval() {
        assertEquals(
                GuildPresenceManager.RETRY_AFTER_FAILURE_MS, GuildPresenceManager.refreshIntervalMs(true));
        assertEquals(
                com.seqwawa.seq.network.WynncraftGuildClient.MINIMUM_REFRESH_INTERVAL.toMillis(),
                GuildPresenceManager.refreshIntervalMs(false));
        assertTrue(GuildPresenceManager.refreshIntervalMs(true) < GuildPresenceManager.refreshIntervalMs(false));
    }

    // ── Party finder link ──

    @Test
    void aRaidIsListedUnderTheNameThePartyFinderKnowsItBy() {
        var catalog = com.seqwawa.seq.model.TestCatalogs.sequoia();

        assertEquals("TNA", GuildPresenceManager.partyFinderActivityFor(catalog.raid("TNA")));
        assertEquals("NOL", GuildPresenceManager.partyFinderActivityFor(catalog.raid("NOL")));
        assertEquals(
                "TWP",
                GuildPresenceManager.partyFinderActivityFor(catalog.raid("WTP")),
                "the catalog's WTP is the party finder's TWP, found through the raid's full name");
    }

    @Test
    void aFilteredRaidOpensThatRaidAndNoFilterOpensEveryRaid() {
        var catalog = com.seqwawa.seq.model.TestCatalogs.sequoia();

        assertEquals(List.of("TNA"), GuildPresenceManager.partyFinderActivities(catalog.raid("TNA"), catalog));
        assertEquals(
                List.of("TNA", "TCC", "NOTG", "NOL", "TWP"),
                GuildPresenceManager.partyFinderActivities(null, catalog));
        assertTrue(GuildPresenceManager.partyFinderActivities(
                        null, com.seqwawa.seq.model.RaidCatalog.empty())
                .isEmpty());
    }

    @Test
    void aWorldNameGivesItsRegion() {
        assertEquals(com.seqwawa.seq.model.PartyRegion.EU, GuildPresenceManager.regionForWorld("EU3"));
        assertEquals(com.seqwawa.seq.model.PartyRegion.NA, GuildPresenceManager.regionForWorld("na12"));
        assertEquals(com.seqwawa.seq.model.PartyRegion.AS, GuildPresenceManager.regionForWorld(" AS2 "));
        assertEquals(null, GuildPresenceManager.regionForWorld("EU"), "a region with no world number is not a world");
        assertEquals(null, GuildPresenceManager.regionForWorld("WC12"));
        assertEquals(null, GuildPresenceManager.regionForWorld(null));
    }

    @Test
    void theListingOpensWhereYouPlayThenWhereYourProfileSaysThenNa() {
        var eu = com.seqwawa.seq.model.PartyRegion.EU;
        var as = com.seqwawa.seq.model.PartyRegion.AS;
        var na = com.seqwawa.seq.model.PartyRegion.NA;

        assertEquals(eu, GuildPresenceManager.listingRegion("EU5", as), "the world you are on wins");
        assertEquals(as, GuildPresenceManager.listingRegion(null, as), "no world, so the profile");
        assertEquals(as, GuildPresenceManager.listingRegion("lobby", as));
        assertEquals(na, GuildPresenceManager.listingRegion(null, null), "the party finder's own default");
    }

    // ── Ordering ──

    @Test
    void sortsMembersByNameIgnoringCase() {
        List<GuildMemberPresence> members = List.of(
                member("zeta", "NA1"),
                member("Alpha", "EU2"),
                member("mike", "AS1"),
                member("BRAVO", "NA1"));

        assertEquals(
                List.of("Alpha", "BRAVO", "mike", "zeta"),
                GuildPresenceManager.sortByName(members).stream()
                        .map(GuildMemberPresence::username)
                        .toList());
    }

    @Test
    void orderingIgnoresTheWorldSoANameStopsMovingWhenSomeoneSwitches() {
        GuildMemberPresence before = member("mike", "NA1");
        GuildMemberPresence after = member("mike", "EU9");
        List<GuildMemberPresence> others = List.of(member("alpha", "AS1"), member("zeta", "AS1"));

        List<String> withBefore = GuildPresenceManager.sortByName(
                        List.of(others.get(0), before, others.get(1)))
                .stream()
                .map(GuildMemberPresence::username)
                .toList();
        List<String> withAfter = GuildPresenceManager.sortByName(List.of(others.get(0), after, others.get(1)))
                .stream()
                .map(GuildMemberPresence::username)
                .toList();

        assertEquals(withBefore, withAfter);
        assertEquals(List.of("alpha", "mike", "zeta"), withBefore);
    }

    @Test
    void aMemberWithNoWorldIsListedLikeAnyOther() {
        List<GuildMemberPresence> members = List.of(member("zeta", "NA1"), member("alpha", null));

        List<GuildMemberPresence> sorted = GuildPresenceManager.sortByName(members);

        assertEquals(List.of("alpha", "zeta"), sorted.stream().map(GuildMemberPresence::username).toList());
        assertFalse(sorted.get(0).hasWorld());
        assertTrue(sorted.get(1).hasWorld());
    }

    @Test
    void returnsNoMembersForAnEmptyRoster() {
        assertEquals(List.of(), GuildPresenceManager.sortByName(List.of()));
        assertEquals(List.of(), GuildPresenceManager.sortByName(null));
    }

    // ── Invite decisions ──

    @Test
    void createsThePartyFirstWhenThereIsNotOneYet() {
        assertEquals(
                GuildPresenceManager.InviteAction.CREATE_THEN_INVITE,
                GuildPresenceManager.decideInviteAction("blousy", "Visroul", false, List.of()));
    }

    @Test
    void onlyInvitesWhenAPartyIsAlreadyOpen() {
        assertEquals(
                GuildPresenceManager.InviteAction.INVITE,
                GuildPresenceManager.decideInviteAction("blousy", "Visroul", true, List.of("Visroul")),
                "a party holding only you is still a party, so creating another would fail");

        assertEquals(
                GuildPresenceManager.InviteAction.INVITE,
                GuildPresenceManager.decideInviteAction("blousy", "Visroul", true, List.of("Visroul", "a3pki")));
    }

    @Test
    void refusesToInviteSomeoneAlreadyInTheParty() {
        assertEquals(
                GuildPresenceManager.InviteAction.ALREADY_IN_PARTY,
                GuildPresenceManager.decideInviteAction("BLOUSY", "Visroul", true, List.of("Visroul", "blousy")));
    }

    @Test
    void refusesToInviteYourself() {
        assertEquals(
                GuildPresenceManager.InviteAction.SELF,
                GuildPresenceManager.decideInviteAction("visroul", "Visroul", true, List.of("Visroul")));
        assertEquals(
                GuildPresenceManager.InviteAction.SELF,
                GuildPresenceManager.decideInviteAction("  ", "Visroul", true, List.of()));
        assertEquals(
                GuildPresenceManager.InviteAction.SELF,
                GuildPresenceManager.decideInviteAction(null, "Visroul", true, List.of()));
    }

    // ── Bulk invites ──

    @Test
    void bulkInviteSkipsYourselfAndAnyoneAlreadyInTheParty() {
        List<String> targets = GuildPresenceManager.inviteTargets(
                List.of("Blousy", "Visroul", "a3pki", "divvy"), "Visroul", List.of("Visroul", "A3PKI"));

        assertEquals(List.of("Blousy", "divvy"), targets);
    }

    @Test
    void bulkInviteCollapsesTheSameNameTypedTwice() {
        List<String> targets =
                GuildPresenceManager.inviteTargets(List.of("Blousy", "blousy", " BLOUSY "), null, List.of());

        assertEquals(List.of("Blousy"), targets);
    }

    @Test
    void bulkInviteIgnoresBlanksAndEmptyInput() {
        assertEquals(
                List.of("Blousy"),
                GuildPresenceManager.inviteTargets(Arrays.asList("Blousy", "", "   ", null), null, null));
        assertEquals(List.of(), GuildPresenceManager.inviteTargets(List.of(), "Visroul", List.of()));
        assertEquals(List.of(), GuildPresenceManager.inviteTargets(null, "Visroul", List.of()));
    }

    @Test
    void bulkInviteLeavesNobodyWhenTheWholePartyIsAlreadyThere() {
        assertEquals(
                List.of(),
                GuildPresenceManager.inviteTargets(
                        List.of("Blousy", "a3pki"), "Visroul", List.of("Visroul", "blousy", "a3pki")));
    }

    // ── Connected-user merge ──

    @Test
    void marksOnlyTheMembersRunningSequoia() {
        GuildPresenceManager manager = GuildPresenceManager.getInstance();
        try {
            manager.applyConnectedUsers(Arrays.asList("Visroul", "  BLOUSY  ", "", null));

            assertTrue(manager.isSequoiaConnected("visroul"), "names match regardless of case");
            assertTrue(manager.isSequoiaConnected("blousy"), "surrounding space is trimmed");
            assertFalse(manager.isSequoiaConnected("a3pki"), "an absent member is not connected");
            assertFalse(manager.isSequoiaConnected(null));
            assertFalse(manager.isSequoiaConnected(" "));
        } finally {
            manager.reset();
        }
    }

    @Test
    void forgetsConnectedUsersOnReset() {
        GuildPresenceManager manager = GuildPresenceManager.getInstance();
        manager.applyConnectedUsers(List.of("Visroul"));
        assertTrue(manager.isSequoiaConnected("Visroul"));

        manager.reset();

        assertFalse(manager.isSequoiaConnected("Visroul"));
        assertFalse(manager.hasLoaded());
    }
}
