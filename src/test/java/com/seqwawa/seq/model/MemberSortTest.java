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

    private static final RaidType TNA = TestCatalogs.sequoia().raid("TNA");
    private static final Instant NOW = Instant.parse("2026-09-17T20:00:00Z");

    private static final Map<String, Instant> LOGINS = new HashMap<>();

    private static GuildMemberPresence member(String name, String world, int tnaClears, int wars) {
        return new GuildMemberPresence(
                name,
                null,
                null,
                world,
                false,
                new GuildMemberStats(
                        0d, Map.of("TNA", tnaClears), wars, 0, 0L, 0, null, RaidPerformance.unknown()));
    }

    private static final Function<GuildMemberPresence, Instant> LOGIN =
            member -> LOGINS.get(member.username());

    private static List<String> names(List<GuildMemberPresence> members) {
        return members.stream().map(GuildMemberPresence::username).toList();
    }

    private static List<String> sorted(List<GuildMemberPresence> members, MemberSort sort, boolean descending) {
        return names(MemberSort.sort(members, sort, descending, TNA, LOGIN));
    }

    @Test
    void namesSortAlphabeticallyIgnoringCase() {
        List<GuildMemberPresence> members =
                List.of(member("zeta", "NA1", 0, 0), member("Ann", "NA1", 0, 0), member("bob", "NA1", 0, 0));

        assertEquals(List.of("Ann", "bob", "zeta"), sorted(members, MemberSort.NAME, false));
        assertEquals(List.of("zeta", "bob", "Ann"), sorted(members, MemberSort.NAME, true));
    }

    @Test
    void worldsGroupTogetherWithTheirNumberReadAsANumber() {
        List<GuildMemberPresence> members = List.of(
                member("Ann", "NA12", 0, 0),
                member("Bob", "NA6", 0, 0),
                member("Cid", "EU2", 0, 0),
                member("Dan", null, 0, 0));

        assertEquals(
                List.of("Cid", "Bob", "Ann", "Dan"),
                sorted(members, MemberSort.WORLD, false),
                "EU before NA, NA6 before NA12, and the worldless member last");
    }

    @Test
    void aMemberWynncraftSaysNothingAboutStaysLastEitherWayRound() {
        List<GuildMemberPresence> members =
                List.of(member("Ann", "NA1", 0, 0), member("Hidden", null, 0, 0), member("Bob", "EU1", 0, 0));

        assertEquals("Hidden", sorted(members, MemberSort.WORLD, false).get(2));
        assertEquals("Hidden", sorted(members, MemberSort.WORLD, true).get(2));
    }

    @Test
    void guildRaidsSortByTheRaidBeingFilteredOn() {
        List<GuildMemberPresence> members =
                List.of(member("Few", "NA1", 3, 0), member("Many", "NA1", 900, 0), member("Some", "NA1", 40, 0));

        assertEquals(List.of("Many", "Some", "Few"), sorted(members, MemberSort.GUILD_RAIDS, true));
        assertEquals(List.of("Few", "Some", "Many"), sorted(members, MemberSort.GUILD_RAIDS, false));
    }

    @Test
    void warsSortOnTheirOwnCount() {
        List<GuildMemberPresence> members =
                List.of(member("Raider", "NA1", 900, 12), member("Warrer", "NA1", 3, 12056));

        assertEquals(List.of("Warrer", "Raider"), sorted(members, MemberSort.WARS, true));
    }

    @Test
    void theOnlineColumnSortsOnTimeOnlineLikeTheNumberItShows() {
        LOGINS.clear();
        LOGINS.put("JustOn", NOW.minusSeconds(120));
        LOGINS.put("AllDay", NOW.minusSeconds(6 * 3600));
        List<GuildMemberPresence> members =
                List.of(member("AllDay", "NA1", 0, 0), member("JustOn", "NA1", 0, 0), member("Hidden", "NA1", 0, 0));

        assertEquals(
                List.of("AllDay", "JustOn", "Hidden"),
                sorted(members, MemberSort.ONLINE_SINCE, true),
                "descending is the longest online first, as it is for wars and guild raids");
        assertEquals(
                List.of("JustOn", "AllDay", "Hidden"),
                sorted(members, MemberSort.ONLINE_SINCE, false),
                "a member who hides their status has no login time and stays last either way");
    }

    @Test
    void tiesAlwaysBreakOnNameSoTheListDoesNotReshuffle() {
        List<GuildMemberPresence> members =
                List.of(member("Zoe", "NA1", 5, 0), member("Ann", "NA1", 5, 0), member("Mia", "NA1", 5, 0));

        assertEquals(List.of("Ann", "Mia", "Zoe"), sorted(members, MemberSort.GUILD_RAIDS, true));
        assertEquals(List.of("Ann", "Mia", "Zoe"), sorted(members, MemberSort.WARS, false));
    }

    @Test
    void eachColumnStartsInTheDirectionThatReadsAsBestFirst() {
        assertFalse(MemberSort.NAME.descendingByDefault());
        assertFalse(MemberSort.WORLD.descendingByDefault());
        assertTrue(MemberSort.ONLINE_SINCE.descendingByDefault());
        assertTrue(MemberSort.GUILD_RAIDS.descendingByDefault());
        assertTrue(MemberSort.WARS.descendingByDefault());
    }

    @Test
    void anEmptyListSortsToAnEmptyList() {
        assertTrue(MemberSort.sort(List.of(), MemberSort.WARS, true, TNA, LOGIN).isEmpty());
        assertTrue(MemberSort.sort(null, MemberSort.WARS, true, TNA, LOGIN).isEmpty());
    }

    @Test
    void withoutARaidTheGuildRaidColumnSortsOnTheTotal() {
        GuildMemberPresence tnaMain = member("TnaMain", "NA1", 900, 0);
        GuildMemberPresence allRounder = new GuildMemberPresence(
                "AllRounder",
                null,
                null,
                "NA1",
                false,
                new GuildMemberStats(0d, Map.of("TNA", 100, "TCC", 900, "NOTG", 500)));

        assertEquals(
                List.of("AllRounder", "TnaMain"),
                names(MemberSort.sort(List.of(tnaMain, allRounder), MemberSort.GUILD_RAIDS, true, null, LOGIN)));
    }
}
