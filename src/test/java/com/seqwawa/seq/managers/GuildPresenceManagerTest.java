package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.GuildMemberPresence;
import com.seqwawa.seq.model.GuildMemberPresence.GuildRank;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class GuildPresenceManagerTest {

    private static final Predicate<String> NOBODY_BUSY = username -> false;

    private static GuildMemberPresence member(String username, String world) {
        return new GuildMemberPresence(username, "uuid-" + username, GuildRank.RECRUIT, world, false);
    }

    // ── Grouping ──

    @Test
    void putsYourOwnWorldFirstThenTheBusiestWorlds() {
        List<GuildMemberPresence> members = List.of(
                member("alpha", "NA1"),
                member("bravo", "NA1"),
                member("charlie", "NA1"),
                member("delta", "EU2"),
                member("echo", "EU2"),
                member("foxtrot", "AS1"));

        List<GuildPresenceManager.WorldGroup> groups =
                GuildPresenceManager.groupByWorld(members, "AS1", NOBODY_BUSY);

        assertEquals(
                List.of("AS1", "NA1", "EU2"),
                groups.stream().map(GuildPresenceManager.WorldGroup::world).toList(),
                "your world leads, then worlds by descending headcount");
    }

    @Test
    void sinksTheWorldlessGroupBelowEveryRealWorld() {
        List<GuildMemberPresence> members = List.of(
                member("alpha", null),
                member("bravo", null),
                member("charlie", null),
                member("delta", "EU2"));

        List<GuildPresenceManager.WorldGroup> groups =
                GuildPresenceManager.groupByWorld(members, "NA9", NOBODY_BUSY);

        assertEquals(
                List.of("EU2", GuildPresenceManager.UNKNOWN_WORLD),
                groups.stream().map(GuildPresenceManager.WorldGroup::world).toList(),
                "an unknown world is last even though it holds more members");
        assertTrue(groups.get(0).hasSwitchTarget());
        assertFalse(groups.get(1).hasSwitchTarget());
    }

    @Test
    void ordersEqualSizedWorldsByName() {
        List<GuildMemberPresence> members =
                List.of(member("alpha", "NA3"), member("bravo", "EU1"), member("charlie", "AS2"));

        List<GuildPresenceManager.WorldGroup> groups =
                GuildPresenceManager.groupByWorld(members, null, NOBODY_BUSY);

        assertEquals(
                List.of("AS2", "EU1", "NA3"),
                groups.stream().map(GuildPresenceManager.WorldGroup::world).toList());
    }

    @Test
    void listsAvailableMembersBeforeBusyOnesWithinAWorld() {
        List<GuildMemberPresence> members = List.of(
                member("alpha", "NA1"), member("bravo", "NA1"), member("charlie", "NA1"), member("delta", "NA1"));
        Set<String> busy = Set.of("alpha", "charlie");

        List<GuildPresenceManager.WorldGroup> groups =
                GuildPresenceManager.groupByWorld(members, "NA1", busy::contains);

        assertEquals(
                List.of("bravo", "delta", "alpha", "charlie"),
                groups.get(0).members().stream()
                        .map(GuildMemberPresence::username)
                        .toList(),
                "free members first, each half alphabetical");
    }

    @Test
    void matchesYourWorldRegardlessOfCase() {
        List<GuildMemberPresence> members = List.of(member("alpha", "NA1"), member("bravo", "EU2"));

        List<GuildPresenceManager.WorldGroup> groups =
                GuildPresenceManager.groupByWorld(members, "na1", NOBODY_BUSY);

        assertEquals("NA1", groups.get(0).world());
    }

    @Test
    void returnsNoGroupsForAnEmptyRoster() {
        assertEquals(List.of(), GuildPresenceManager.groupByWorld(List.of(), "NA1", NOBODY_BUSY));
        assertEquals(List.of(), GuildPresenceManager.groupByWorld(null, "NA1", NOBODY_BUSY));
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
