package org.sequoia.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import org.junit.jupiter.api.Test;

class RaidTrackerTest {

    @Test
    void parseRaidCompletionHandlesSplitNamesAndRewards() {
        Component message = Component.empty()
                .append(Component.literal("󏿼󐀆 "))
                .append(Component.literal("bubblebouncy").withStyle(Style.EMPTY.withInsertion("Visroul")))
                .append(Component.literal(", xmattypazox, "))
                .append(Component.literal("death by choking").withStyle(Style.EMPTY.withInsertion("a3pki")))
                .append(Component.literal(", and divvy\n󏿼󐀆 "))
                .append(Component.literal("lunne").withStyle(Style.EMPTY.withInsertion("blousy")))
                .append(Component.literal(" finished The Nameless Anomaly and claimed 2x Aspects\n󏿼󐀆 , 2048x Emeralds, and +10367m Guild Experience"));

        RaidTracker.ParsedRaidCompletion parsed = RaidTracker.parseRaidCompletion(message);

        assertNotNull(parsed);
        assertEquals(List.of("Visroul", "xmattypazox", "a3pki", "blousy"), parsed.partyMembers());
        assertEquals("The Nameless Anomaly", parsed.raidName());
        assertEquals(2, parsed.aspects());
        assertEquals(2048, parsed.emeralds());
        assertEquals(10.367, parsed.guildExp(), 0.000001);
        assertEquals(0, parsed.seasonalRating());
    }

    @Test
    void parseRaidCompletionHandlesSplitRaidNameAcrossLines() {
        RaidTracker.ParsedRaidCompletion parsed = RaidTracker.parseRaidCompletion(Component.literal(
                "󏿼󏿿󏿾 Tannslee, JeongSooMin, wisedrag, and D4MIT finished Nest\n"
                        + "󏿼󐀆 of the Grootslangs and claimed 2x Aspects, 2048x Emeralds\n"
                        + "󏿼󐀆 , and +10367m Guild Experience"));

        assertNotNull(parsed);
        assertEquals(List.of("Tannslee", "JeongSooMin", "wisedrag", "D4MIT"), parsed.partyMembers());
        assertEquals("Nest of the Grootslangs", parsed.raidName());
        assertEquals(2048, parsed.emeralds());
    }

    @Test
    void parseRaidCompletionHandlesSplitBeforeFinishedAndBeforeGuildExp() {
        RaidTracker.ParsedRaidCompletion parsed = RaidTracker.parseRaidCompletion(Component.literal(
                "󏿼󏿿󏿾 space527, krackeryuh, ArcLeRetour, and MrRickroll\n"
                        + "󏿼󐀆 finished The Nameless Anomaly and claimed 2x Aspects, \n"
                        + "󏿼󐀆 2048x Emeralds, and +10367m Guild Experience"));

        assertNotNull(parsed);
        assertEquals(List.of("space527", "krackeryuh", "ArcLeRetour", "MrRickroll"), parsed.partyMembers());
        assertEquals("The Nameless Anomaly", parsed.raidName());
        assertEquals(2, parsed.aspects());
    }

    @Test
    void parseRaidCompletionHandlesOrphionNameSplitAcrossLine() {
        RaidTracker.ParsedRaidCompletion parsed = RaidTracker.parseRaidCompletion(Component.literal(
                "󏿼󏿿󏿾 kittycat317, Glacade, a3pki, and 5up3rZ finished Orphion's\n"
                        + "󏿼󐀆 Nexus of Light and claimed 2x Aspects, 2048x Emeralds, and \n"
                        + "󏿼󐀆 +10367m Guild Experience"));

        assertNotNull(parsed);
        assertEquals("Orphion's Nexus of Light", parsed.raidName());
        assertEquals(List.of("kittycat317", "Glacade", "a3pki", "5up3rZ"), parsed.partyMembers());
    }

    @Test
    void parseRaidCompletionHandlesSeasonalRatingClause() {
        RaidTracker.ParsedRaidCompletion parsed = RaidTracker.parseRaidCompletion(Component.literal(
                "󏿼󐀆 AAA, BBB, CCC, and DDD finished The Nameless Anomaly and claimed 2x Aspects,\n"
                        + "󏿼󐀆 2048x Emeralds, and +10367m Guild Experience, and +410 Seasonal Rating"));

        assertNotNull(parsed);
        assertEquals(List.of("AAA", "BBB", "CCC", "DDD"), parsed.partyMembers());
        assertEquals(410, parsed.seasonalRating());
    }

    @Test
    void parseRaidCompletionPrefersDisplayedUsernamesWhenAllDisplayedNamesAreValid() {
        Component message = Component.empty()
                .append(Component.literal("Tannslee").withStyle(Style.EMPTY.withInsertion("eep")))
                .append(Component.literal(", 99922, wisedrag, and nessabarrett finished The Canyon Colossus and claimed "
                        + "2x Aspects, 2048x Emeralds, and +10367m Guild Experience"));

        RaidTracker.ParsedRaidCompletion parsed = RaidTracker.parseRaidCompletion(message);

        assertNotNull(parsed);
        assertEquals(List.of("Tannslee", "99922", "wisedrag", "nessabarrett"), parsed.partyMembers());
        assertEquals("The Canyon Colossus", parsed.raidName());
        assertEquals(10.367, parsed.guildExp(), 0.000001);
    }

    @Test
    void parseRaidCompletionPrefersHoverRealNamesOverUsernameLikeDisplayedNicknames() {
        Style nickStyle = Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(
                Component.literal("Tannslee's real name is eep")));
        Component message = Component.empty()
                .append(Component.literal("Tannslee").withStyle(nickStyle))
                .append(Component.literal(", 99922, wisedrag, and nessabarrett finished The Canyon Colossus and claimed "
                        + "2x Aspects, 2048x Emeralds, and +10367m Guild Experience"));

        RaidTracker.ParsedRaidCompletion parsed = RaidTracker.parseRaidCompletion(message);

        assertNotNull(parsed);
        assertEquals(List.of("eep", "99922", "wisedrag", "nessabarrett"), parsed.partyMembers());
        assertEquals("The Canyon Colossus", parsed.raidName());
    }

    @Test
    void parseRaidCompletionPrefersHoverRealNameWhenInsertionContainsNickname() {
        Style nickStyle = Style.EMPTY
                .withInsertion("eep")
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("eep's real name is wisedrag")));
        Component message = Component.empty()
                .append(Component.literal("eep").withStyle(nickStyle))
                .append(Component.literal(", 99922, Tannslee, and nessabarrett finished The Canyon Colossus and claimed "
                        + "2x Aspects, 2048x Emeralds, and +10367m Guild Experience"));

        RaidTracker.ParsedRaidCompletion parsed = RaidTracker.parseRaidCompletion(message);

        assertNotNull(parsed);
        assertEquals(List.of("wisedrag", "99922", "Tannslee", "nessabarrett"), parsed.partyMembers());
        assertEquals("The Canyon Colossus", parsed.raidName());
    }

    @Test
    void parseRaidCompletionIgnoresNonRaidGuildMessages() {
        assertNull(RaidTracker.parseRaidCompletion(Component.literal("󏿼󐀆 xmattypazox: 3/4 tna")));
        assertNull(RaidTracker.parseRaidCompletion(Component.literal("󏿼󐀆 Purprated deposited 1x MR dagger [100%] to the Guild Bank (Everyone)")));
    }
}
