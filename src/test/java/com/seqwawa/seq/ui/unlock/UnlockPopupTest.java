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
        assertEquals(new UnlockPopup(Kind.TOAST, null, "+330", "The Nameless Anomaly", 330, 330, null, "unlock_icon_tna"),
                from("leaf_earned", "{\"source\":\"raid\",\"leaf\":330,\"xp\":330,\"detail\":\"The Nameless Anomaly\"}"));
        UnlockPopup war = from("leaf_earned", "{\"source\":\"war\",\"leaf\":60,\"xp\":60,\"detail\":\"Detlas\"}");
        assertEquals("War for Detlas", war.detail());
        assertEquals("unlock_war", war.icon());
    }

    @Test
    void aWarOverSequoiasClaimIsCalledADefence() {
        UnlockPopup defence = from("leaf_earned",
                "{\"source\":\"war\",\"leaf\":146,\"xp\":146,\"detail\":\"Detlas\",\"in_claim\":true}");
        UnlockPopup snipe = from("leaf_earned",
                "{\"source\":\"war\",\"leaf\":26,\"xp\":26,\"detail\":\"Ragni\",\"in_claim\":false}");

        assertEquals("Defended Detlas", defence.detail());
        assertEquals("War for Ragni", snipe.detail());
    }

    @Test
    void aFinishedQuestIsACardInItsSlotsColours() {
        UnlockPopup popup = from("quest_completed",
                "{\"quest\":{\"slot\":\"weekly\",\"title\":\"Complete 12 TCC raids\",\"objective\":{\"type\":\"raids\","
                        + "\"raid\":\"tcc\"},\"reward\":{\"leaf\":800,\"xp\":400}}}");

        assertEquals(new UnlockPopup(Kind.CARD, "Weekly quest completed", "Complete 12 TCC raids", null, 800, 400, "diamond", "unlock_icon_tcc"),
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
        assertEquals(new UnlockPopup(Kind.CEREMONY, "Sequoia level", "Level 8", "Leaf level up", 0, 0, "level", null),
                from("level_up", "{\"level\":8}"));
    }

    @Test
    void showsWhereTheRewardCameFrom() {
        assertEquals("unlock_icon_nol", UnlockPopup.raidIconByName("Orphion's Nexus of Light"));
        assertEquals("unlock_icon_twp", UnlockPopup.raidIconByName("The Wartorn Palace"));
        assertEquals("unlock_raid", UnlockPopup.raidIconByName("A raid added next season"));
        assertEquals("unlock_icon_twp", UnlockPopup.objectiveIcon("raids", "wtp"));
        assertEquals("unlock_raid", UnlockPopup.objectiveIcon("raids", null));
        assertEquals("unlock_war", UnlockPopup.objectiveIcon("wars", null));
        assertEquals("star", UnlockPopup.objectiveIcon("season_rating", null));
    }

    @Test
    void aMilestonesMedalCarriesItsRaidsArtOrThePortalOrTheTower() {
        assertEquals("unlock_emblem_tna", UnlockPopup.emblem("raid:TNA"));
        assertEquals("unlock_emblem_twp", UnlockPopup.emblem("raid:TWP"));
        assertEquals("unlock_raid", UnlockPopup.emblem("raid:all raids"));
        assertEquals("unlock_war", UnlockPopup.emblem("war:completed"));
        assertEquals("unlock_raid", UnlockPopup.emblem("raid:SOMETHING NEW"));
        assertEquals(8, UnlockPopup.fromEvent("level_up", com.google.gson.JsonParser.parseString("{\"level\":8}")
                .getAsJsonObject()).level());
    }

    @Test
    void showsNothingForEventsItDoesNotKnow() {
        assertNull(from("leaf_spent", "{\"leaf\":100}"));
        assertNull(from("quest_completed", "{}"));
    }
}
