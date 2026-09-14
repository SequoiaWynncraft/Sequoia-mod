package com.seqwawa.seq.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.model.GuildMemberPresence;
import com.seqwawa.seq.model.GuildMemberStats;
import com.seqwawa.seq.model.RaidCatalog;
import com.seqwawa.seq.model.RaidType;
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
                  "GaztheCat": {
                    "uuid": "66efb975", "online": true, "server": "NA6",
                    "globalData": {
                      "playtime": 12040.17,
                      "raids": {
                        "total": 21760,
                        "list": {
                          "The Canyon Colossus": 5612,
                          "Orphion's Nexus of Light": 1193,
                          "The Nameless Anomaly": 12726,
                          "Nest of the Grootslangs": 2070,
                          "The Wartorn Palace": 159
                        }
                      },
                      "currentGuildRaids": {
                        "total": 18202,
                        "list": {
                          "The Canyon Colossus": 5241,
                          "Orphion's Nexus of Light": 1160,
                          "The Nameless Anomaly": 9631,
                          "Nest of the Grootslangs": 2011,
                          "The Wartorn Palace": 159,
                          "Some Future Raid": 7
                        }
                      }
                    }
                  }
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

    /** The meta the backend publishes, which is what names Wynncraft's raid keys. */
    private static final RaidCatalog CATALOG = com.seqwawa.seq.model.TestCatalogs.sequoia();

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static RaidType raid(String key) {
        return CATALOG.raid(key);
    }

    @Test
    void keepsOnlyOnlineMembersAndCarriesTheirWorldAndRank() {
        WynncraftGuildClient.GuildRoster roster = WynncraftGuildClient.parseRoster(parse(GUILD_PAYLOAD), CATALOG);

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
        WynncraftGuildClient.GuildRoster roster = WynncraftGuildClient.parseRoster(parse(GUILD_PAYLOAD), CATALOG);

        GuildMemberPresence worldless = findByUsername(roster.online(), "noServer");
        assertNull(worldless.world());
        assertFalse(worldless.hasWorld());
    }

    @Test
    void survivesAPayloadWithNoMembersObject() {
        WynncraftGuildClient.GuildRoster roster =
                WynncraftGuildClient.parseRoster(parse("{\"name\": \"Sequoia\", \"prefix\": \"SEQ\"}"), CATALOG);

        assertTrue(roster.online().isEmpty());
        assertEquals(0, roster.totalMembers());
        assertEquals("Sequoia", roster.displayName());
    }

    @Test
    void fallsBackToThePrefixWhenTheGuildHasNoName() {
        WynncraftGuildClient.GuildRoster roster = WynncraftGuildClient.parseRoster(parse("{\"prefix\": \"seq\"}"), CATALOG);

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
    void aRaidTheCatalogDoesNotListIsNotCounted() {
        // "Some Future Raid" is in the payload but not in the catalog, so it is
        // skipped rather than inflating the total.
        GuildMemberStats stats = findByUsername(
                        WynncraftGuildClient.parseRoster(parse(GUILD_PAYLOAD), CATALOG).online(), "GaztheCat")
                .stats();

        assertEquals(0, stats.completions("SOME FUTURE RAID"));
        assertEquals(5, stats.raidCompletions().size(), "only the five catalog raids are kept");
    }

    @Test
    void withoutACatalogNoClearCountCanBeRead() {
        // Wynncraft keys counts by the raid's full name, and only the catalog says
        // which name is which raid.
        GuildMemberStats stats = findByUsername(
                        WynncraftGuildClient.parseRoster(parse(GUILD_PAYLOAD), RaidCatalog.empty()).online(),
                        "GaztheCat")
                .stats();

        assertTrue(stats.raidCompletions().isEmpty());
        assertEquals("12040h", stats.playtimeLabel(), "playtime does not need the catalog");
    }

    @Test
    void mapsUnknownRankKeysToRecruitRatherThanFailing() {
        assertEquals(
                GuildMemberPresence.GuildRank.RECRUIT,
                GuildMemberPresence.GuildRank.fromApiKey("someFutureRank"));
        assertEquals(GuildMemberPresence.GuildRank.RECRUIT, GuildMemberPresence.GuildRank.fromApiKey(null));
        assertEquals(GuildMemberPresence.GuildRank.STRATEGIST, GuildMemberPresence.GuildRank.fromApiKey("strategist"));
    }

    @Test
    void countsOnlyTheRaidsRunInsideTheGuild() {
        WynncraftGuildClient.GuildRoster roster = WynncraftGuildClient.parseRoster(parse(GUILD_PAYLOAD), CATALOG);
        GuildMemberStats stats = findByUsername(roster.online(), "GaztheCat").stats();

        assertTrue(stats.isKnown());
        assertEquals("12040h", stats.playtimeLabel());
        assertEquals(
                9631,
                stats.completions(raid("TNA")),
                "the guild count, not the 12726 lifetime total that includes previous guilds");
        assertEquals(159, stats.completions(raid("WTP")));
        assertEquals(
                5241 + 1160 + 9631 + 2011 + 159,
                stats.totalRaidCompletions(),
                "a raid this build does not know is not counted");
    }

    @Test
    void wordsTheRaidCountForARow() {
        GuildMemberStats many = new GuildMemberStats(10d, java.util.Map.of("TNA", 9631));
        GuildMemberStats one = new GuildMemberStats(10d, java.util.Map.of("TNA", 1));

        assertEquals("9631 guild raids", many.raidCountLabel(raid("TNA")));
        assertEquals("1 guild raid", one.raidCountLabel(raid("TNA")));
        assertEquals("0 guild raids", one.raidCountLabel(raid("NOTG")));
    }

    @Test
    void aMemberWithoutGlobalDataHasUnknownStats() {
        WynncraftGuildClient.GuildRoster roster = WynncraftGuildClient.parseRoster(parse(GUILD_PAYLOAD), CATALOG);
        GuildMemberStats stats = findByUsername(roster.online(), "blousy").stats();

        assertFalse(stats.isKnown());
        assertEquals("?", stats.playtimeLabel());
        assertEquals(0, stats.completions(raid("TNA")));
        assertEquals(0, stats.completions((RaidType) null));
    }

    private static GuildMemberPresence findByUsername(List<GuildMemberPresence> members, String username) {
        return members.stream()
                .filter(member -> member.username().equals(username))
                .findFirst()
                .orElseThrow(() -> new AssertionError(username + " missing from roster"));
    }
}
