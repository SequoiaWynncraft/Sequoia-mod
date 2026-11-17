package com.seqwawa.seq.ui.unlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.ui.unlock.UnlockPopup.Kind;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnlockPopupTest {

    private static UnlockPopup from(String type, String data) {
        JsonObject json = JsonParser.parseString(data).getAsJsonObject();
        return UnlockPopup.fromEvent(type, json);
    }

    @Test
    void leafFromARaidOrWarIsASmallToast() {
        assertEquals(new UnlockPopup(Kind.TOAST, null, "+330", "The Nameless Anomaly", 330, 330, null),
                from("leaf_earned", "{\"source\":\"raid\",\"leaf\":330,\"xp\":330,\"detail\":\"The Nameless Anomaly\"}"));
        assertEquals("War for Detlas",
                from("leaf_earned", "{\"source\":\"war\",\"leaf\":60,\"xp\":60,\"detail\":\"Detlas\"}").detail());
    }

    @Test
    void aFinishedQuestIsACardInItsSlotsColours() {
        UnlockPopup popup = from("quest_completed",
                "{\"quest\":{\"slot\":\"weekly\",\"title\":\"Complete 12 TCC raids\",\"reward\":{\"leaf\":800,\"xp\":400}}}");

        assertEquals(new UnlockPopup(Kind.CARD, "Weekly quest completed", "Complete 12 TCC raids", null, 800, 400, "diamond"),
                popup);
    }

    @Test
    void milestonesFromPlatinumUpGetTheWholeScreen() {
        List<String> tiers = List.of("bronze", "silver", "gold", "platinum", "diamond", "obsidian", "mythril");
        List<Kind> kinds = tiers.stream()
                .map(tier -> from("achievement_paid", "{\"ladder\":\"raid:TNA\",\"milestone\":\"" + tier + "\",\"tier\":\""
                        + tier + "\",\"threshold\":25,\"leaf\":250,\"xp\":250}").kind())
                .toList();

        assertEquals(List.of(Kind.CARD, Kind.CARD, Kind.CARD, Kind.CEREMONY, Kind.CEREMONY, Kind.CEREMONY, Kind.CEREMONY),
                kinds);
    }

    @Test
    void aMilestoneSaysWhatWasReachedInWords() {
        UnlockPopup popup = from("achievement_paid", "{\"ladder\":\"raid:TNA\",\"milestone\":\"diamond\",\"tier\":\"diamond\","
                + "\"threshold\":1000,\"label\":\"Diamond\",\"leaf\":5000,\"xp\":5000}");

        assertEquals("Diamond", popup.title());
        assertEquals("1,000 TNA raids", popup.detail());
        assertEquals("Milestone reached", popup.eyebrow());
        assertEquals("Silver", from("achievement_paid",
                "{\"ladder\":\"war:completed\",\"milestone\":\"silver\",\"tier\":\"silver\",\"threshold\":50,\"label\":null}")
                .title());
    }

    @Test
    void namesEachLadderTheWayMembersSayIt() {
        assertEquals("TNA raids", UnlockPopup.ladderPhrase("raid:TNA"));
        assertEquals("guild raids", UnlockPopup.ladderPhrase("raid:all raids"));
        assertEquals("guild wars", UnlockPopup.ladderPhrase("war:completed"));
    }

    @Test
    void aLevelUpIsACeremony() {
        assertEquals(new UnlockPopup(Kind.CEREMONY, "Sequoia level", "Level 8", "Leaf level up", 0, 0, "level"),
                from("level_up", "{\"level\":8}"));
    }

    @Test
    void showsNothingForEventsItDoesNotKnow() {
        assertNull(from("leaf_spent", "{\"leaf\":100}"));
        assertNull(from("quest_completed", "{}"));
    }
}
