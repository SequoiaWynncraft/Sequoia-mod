package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.model.GuildMemberPresence;
import java.util.List;
import org.junit.jupiter.api.Test;

class WynncraftGuildClientTest {

    /** Shaped exactly like a real {@code /v3/guild/prefix/{prefix}} response, trimmed. */
    private static final String GUILD_PAYLOAD =
            """
            {
              "name": "Sequoia",
              "prefix": "SEQ",
              "members": {
                "total": 149,
                "owner": {
                  "GaztheCat": {"uuid": "66efb975", "online": true, "server": "NA6"}
                },
                "chief": {
                  "MrHmar": {"uuid": "35f63806", "online": false, "server": null}
                },
                "recruit": {
                  "blousy": {"uuid": "aaaa", "online": true, "server": "EU2"},
                  "hiddenPlayer": {"uuid": "bbbb", "online": false, "server": null},
                  "noServer": {"uuid": "cccc", "online": true, "server": null}
                }
              }
            }
            """;

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    void keepsOnlyOnlineMembersAndCarriesTheirWorldAndRank() {
        WynncraftGuildClient.GuildRoster roster = WynncraftGuildClient.parseRoster(parse(GUILD_PAYLOAD));

        assertEquals("Sequoia", roster.guildName());
        assertEquals("SEQ", roster.guildPrefix());
        assertEquals(149, roster.totalMembers());

        List<String> usernames =
                roster.online().stream().map(GuildMemberPresence::username).sorted().toList();
        assertEquals(List.of("GaztheCat", "blousy", "noServer"), usernames);

        GuildMemberPresence owner = findByUsername(roster.online(), "GaztheCat");
        assertEquals(GuildMemberPresence.GuildRank.OWNER, owner.rank());
        assertEquals("NA6", owner.world());
        assertTrue(owner.hasWorld());
        assertFalse(owner.sequoiaConnected());

        assertEquals(GuildMemberPresence.GuildRank.RECRUIT, findByUsername(roster.online(), "blousy").rank());
    }

    @Test
    void treatsAnOnlineMemberWithoutAServerAsWorldless() {
        WynncraftGuildClient.GuildRoster roster = WynncraftGuildClient.parseRoster(parse(GUILD_PAYLOAD));

        GuildMemberPresence worldless = findByUsername(roster.online(), "noServer");
        assertNull(worldless.world());
        assertFalse(worldless.hasWorld());
    }

    @Test
    void survivesAPayloadWithNoMembersObject() {
        WynncraftGuildClient.GuildRoster roster =
                WynncraftGuildClient.parseRoster(parse("{\"name\": \"Sequoia\", \"prefix\": \"SEQ\"}"));

        assertTrue(roster.online().isEmpty());
        assertEquals(0, roster.totalMembers());
        assertEquals("Sequoia", roster.displayName());
    }

    @Test
    void fallsBackToThePrefixWhenTheGuildHasNoName() {
        WynncraftGuildClient.GuildRoster roster = WynncraftGuildClient.parseRoster(parse("{\"prefix\": \"seq\"}"));

        assertEquals("SEQ", roster.displayName());
    }

    @Test
    void readsTheGuildPrefixOffAPlayerPayload() {
        assertEquals(
                "SEQ",
                WynncraftGuildClient.parseGuildPrefix(parse(
                        "{\"username\": \"GaztheCat\", \"guild\": {\"name\": \"Sequoia\", \"prefix\": \"SEQ\"}}")));
    }

    @Test
    void returnsNoPrefixForAGuildlessPlayer() {
        assertNull(WynncraftGuildClient.parseGuildPrefix(parse("{\"username\": \"Nobody\", \"guild\": null}")));
        assertNull(WynncraftGuildClient.parseGuildPrefix(parse("{\"username\": \"Nobody\"}")));
        assertNull(WynncraftGuildClient.parseGuildPrefix(null));
    }

    @Test
    void mapsUnknownRankKeysToRecruitRatherThanFailing() {
        assertEquals(
                GuildMemberPresence.GuildRank.RECRUIT,
                GuildMemberPresence.GuildRank.fromApiKey("someFutureRank"));
        assertEquals(GuildMemberPresence.GuildRank.RECRUIT, GuildMemberPresence.GuildRank.fromApiKey(null));
        assertEquals(GuildMemberPresence.GuildRank.STRATEGIST, GuildMemberPresence.GuildRank.fromApiKey("strategist"));
    }

    private static GuildMemberPresence findByUsername(List<GuildMemberPresence> members, String username) {
        return members.stream()
                .filter(member -> member.username().equals(username))
                .findFirst()
                .orElseThrow(() -> new AssertionError(username + " missing from roster"));
    }
}
