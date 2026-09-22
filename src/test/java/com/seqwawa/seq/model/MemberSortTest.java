package com.seqwawa.seq.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class MemberSortTest {

    private static final Instant NOW = Instant.parse("2026-09-22T20:00:00Z");

    private static final Map<String, Instant> LOGINS = new HashMap<>();

    private static GuildMemberPresence member(String name, String world) {
        return new GuildMemberPresence(name, null, null, world, false);
    }

    private static final Function<GuildMemberPresence, Instant> LOGIN =
            member -> LOGINS.get(member.username());

    private static List<String> names(List<GuildMemberPresence> members) {
        return members.stream().map(GuildMemberPresence::username).toList();
    }

    private static List<String> sorted(List<GuildMemberPresence> members, MemberSort sort, boolean descending) {
        return names(MemberSort.sort(members, sort, descending, LOGIN));
    }

    @Test
    void namesSortAlphabeticallyIgnoringCase() {
        List<GuildMemberPresence> members =
                List.of(member("zeta", "NA1"), member("Ann", "NA1"), member("bob", "NA1"));

        assertEquals(List.of("Ann", "bob", "zeta"), sorted(members, MemberSort.NAME, false));
        assertEquals(List.of("zeta", "bob", "Ann"), sorted(members, MemberSort.NAME, true));
    }

    @Test
    void worldsGroupTogetherWithTheirNumberReadAsANumber() {
        List<GuildMemberPresence> members = List.of(
                member("Ann", "NA12"), member("Bob", "NA6"), member("Cid", "EU2"), member("Dan", null));

        assertEquals(
                List.of("Cid", "Bob", "Ann", "Dan"),
                sorted(members, MemberSort.WORLD, false),
                "EU before NA, NA6 before NA12, and the worldless member last");
    }

    @Test
    void aMemberWynncraftSaysNothingAboutStaysLastEitherWayRound() {
        List<GuildMemberPresence> members =
                List.of(member("Ann", "NA1"), member("Hidden", null), member("Bob", "EU1"));

        assertEquals("Hidden", sorted(members, MemberSort.WORLD, false).get(2));
        assertEquals("Hidden", sorted(members, MemberSort.WORLD, true).get(2));
    }

    @Test
    void theOnlineColumnSortsOnTimeOnlineLikeTheNumberItShows() {
        LOGINS.clear();
        LOGINS.put("JustOn", NOW.minusSeconds(120));
        LOGINS.put("AllDay", NOW.minusSeconds(6 * 3600));
        List<GuildMemberPresence> members =
                List.of(member("AllDay", "NA1"), member("JustOn", "NA1"), member("Hidden", "NA1"));

        assertEquals(
                List.of("AllDay", "JustOn", "Hidden"),
                sorted(members, MemberSort.ONLINE_SINCE, true),
                "descending is the longest online first");
        assertEquals(
                List.of("JustOn", "AllDay", "Hidden"),
                sorted(members, MemberSort.ONLINE_SINCE, false),
                "a member who hides their status has no login time and stays last either way");
    }

    @Test
    void tiesAlwaysBreakOnNameSoTheListDoesNotReshuffle() {
        LOGINS.clear();
        List<GuildMemberPresence> members =
                List.of(member("Zoe", "NA1"), member("Ann", "NA1"), member("Mia", "NA1"));

        assertEquals(List.of("Ann", "Mia", "Zoe"), sorted(members, MemberSort.WORLD, true));
        assertEquals(List.of("Ann", "Mia", "Zoe"), sorted(members, MemberSort.ONLINE_SINCE, false));
    }

    @Test
    void eachColumnStartsInTheDirectionThatReadsAsBestFirst() {
        assertFalse(MemberSort.NAME.descendingByDefault());
        assertFalse(MemberSort.WORLD.descendingByDefault());
        assertTrue(MemberSort.ONLINE_SINCE.descendingByDefault());
    }

    @Test
    void anEmptyListSortsToAnEmptyList() {
        assertTrue(MemberSort.sort(List.of(), MemberSort.WORLD, true, LOGIN).isEmpty());
        assertTrue(MemberSort.sort(null, MemberSort.WORLD, true, LOGIN).isEmpty());
    }
}
