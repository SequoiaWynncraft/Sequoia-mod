package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonParser;
import com.seqwawa.seq.model.LeafEvents;
import com.seqwawa.seq.ui.RecentLeaf.Kind;
import com.seqwawa.seq.ui.RecentLeaf.Row;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecentLeafTest {

    private static final Instant AT = Instant.parse("2026-11-18T12:00:00Z");

    private static LeafEvents.Event event(long id, String type, String data) {
        return new LeafEvents.Event(id, type, AT, JsonParser.parseString(data).getAsJsonObject());
    }

    @Test
    void saysWhereEachAmountCameFrom() {
        List<Row> rows = RecentLeaf.rows(LeafEvents.of(List.of(
                event(5, "leaf_earned", "{\"source\":\"raid\",\"leaf\":330,\"xp\":330,\"detail\":\"The Nameless Anomaly\"}"),
                event(4, "leaf_earned", "{\"source\":\"war\",\"leaf\":146,\"xp\":146,\"detail\":\"Detlas\",\"in_claim\":true}"),
                event(3, "leaf_earned", "{\"source\":\"war\",\"leaf\":26,\"xp\":26,\"detail\":\"Ragni\"}"),
                event(2, "quest_completed", "{\"quest\":{\"slot\":\"daily\",\"title\":\"Complete 3 NOL raids\","
                        + "\"objective\":{\"type\":\"raids\",\"raid\":\"nol\"},\"reward\":{\"leaf\":300,\"xp\":150}}}"),
                event(1, "achievement_paid", "{\"ladder\":\"raid:NOTG\",\"tier\":\"gold\",\"threshold\":100,"
                        + "\"label\":\"Gold\",\"leaf\":1000,\"xp\":1000}"))), 10);

        assertEquals(List.of(
                new Row(Kind.RAID, "unlock_icon_tna", null, "The Nameless Anomaly", "Raid", 330, 0, AT),
                new Row(Kind.DEFENCE, "unlock_war", null, "Detlas", "Defence", 146, 0, AT),
                new Row(Kind.WAR, "unlock_war", null, "Ragni", "War", 26, 0, AT),
                new Row(Kind.QUEST, "unlock_icon_nol", "gold", "Complete 3 NOL raids", "Daily quest", 300, 0, AT),
                new Row(Kind.MILESTONE, "unlock_icon_notg", "gold", "100 NOTG raids", "Gold milestone", 1000, 0, AT)),
                rows);
    }

    @Test
    void aQuestFinishedByWarsIsListedByItsWarGoal() {
        Row row = RecentLeaf.row(event(1, "quest_completed", "{\"quest\":{\"slot\":\"repeatable\","
                + "\"title\":\"Complete 8 raids or 24 wars\",\"objective\":{\"type\":\"wars\",\"raid\":null},"
                + "\"goals\":[{\"type\":\"raids\",\"raid\":null,\"title\":\"Complete 8 raids\",\"progress\":5,\"target\":8},"
                + "{\"type\":\"wars\",\"raid\":null,\"title\":\"Take part in 24 wars\",\"progress\":24,\"target\":24}],"
                + "\"reward\":{\"leaf\":200,\"xp\":100}}}"));

        assertEquals(new Row(Kind.QUEST, "unlock_war", "bronze", "Take part in 24 wars", "Endless quest", 200, 0, AT), row);
    }

    @Test
    void aLevelUpShowsTheLevelAndTheLeafItPaid() {
        Row row = RecentLeaf.row(event(1, "level_up", "{\"level\":8,\"leaf\":250}"));

        assertEquals(new Row(Kind.LEVEL, null, "level", "Level 8", "Level up", 250, 8, AT), row);
        assertEquals(0, RecentLeaf.row(event(1, "level_up", "{\"level\":8}")).leaf());
    }

    @Test
    void showsTheNewestFewByIdWhateverOrderLeafSendsThem() {
        // Leaf sends the latest events oldest first; the list still starts with the newest.
        LeafEvents oldestFirst = LeafEvents.of(List.of(
                event(6, "quest_completed", "{}"),
                event(7, "level_up", "{\"level\":2}"),
                event(8, "level_up", "{\"level\":3}"),
                event(9, "something_new", "{}")));

        List<Row> rows = RecentLeaf.rows(oldestFirst, 1);

        assertEquals(1, rows.size());
        assertEquals("Level 3", rows.get(0).title(), "the newest the list can show; it skips a type it doesn't know");
        assertEquals(rows, RecentLeaf.rows(LeafEvents.of(oldestFirst.newestFirst()), 1), "the order sent changes nothing");
        assertNull(RecentLeaf.row(event(6, "quest_completed", "{}")), "a quest event without its quest");
        assertEquals(List.of(), RecentLeaf.rows(null, 6));
    }

    @Test
    void aMilestoneTileUsesTheLaddersSmallPicture() {
        assertEquals("unlock_icon_tna", RecentLeaf.ladderIcon("raid:TNA"));
        assertEquals("unlock_war", RecentLeaf.ladderIcon("war:completed"));
        assertEquals("unlock_raid", RecentLeaf.ladderIcon("raid:all raids"));
        assertEquals("unlock_raid", RecentLeaf.ladderIcon(null));
    }

    @Test
    void saysHowLongAgoInTheLargestWholeUnit() {
        assertEquals("just now", RecentLeaf.ago(AT, AT.plusSeconds(59)));
        assertEquals("12m ago", RecentLeaf.ago(AT, AT.plus(Duration.ofMinutes(12))));
        assertEquals("3h ago", RecentLeaf.ago(AT, AT.plus(Duration.ofMinutes(200))));
        assertEquals("47h ago", RecentLeaf.ago(AT, AT.plus(Duration.ofHours(47))));
        assertEquals("2d ago", RecentLeaf.ago(AT, AT.plus(Duration.ofHours(48))));
        assertEquals("just now", RecentLeaf.ago(AT, AT.minusSeconds(30)), "a clock a little behind the server's");
    }

    @Test
    void aListWithNothingInItKeepsOneRowsRoomForSayingSo() {
        assertEquals(RecentLeaf.height(1), RecentLeaf.height(0));
        assertEquals((13 + 6 * 16 + 3) * 2, RecentLeaf.height(6));
    }
}
