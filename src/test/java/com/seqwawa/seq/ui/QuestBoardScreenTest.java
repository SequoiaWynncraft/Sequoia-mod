package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.seqwawa.seq.managers.LeafQuestService.State;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.model.LeafQuests.Daily;
import com.seqwawa.seq.model.LeafQuests.Objective;
import com.seqwawa.seq.model.LeafQuests.Quest;
import com.seqwawa.seq.model.LeafQuests.Reward;
import com.seqwawa.seq.model.SeqRaid;
import com.seqwawa.seq.ui.theme.UiColor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestBoardScreenTest {

    private static final Instant NOW = Instant.parse("2026-11-17T17:48:00Z");

    private static Quest quest(String slot, Objective objective, long progress, long target, Instant expiresAt,
            Instant completedAt) {
        return new Quest(slot + ":1", slot, "A quest", objective, progress, target, new Reward(300, 150),
                NOW.minusSeconds(3600), expiresAt, completedAt);
    }

    @Test
    void showsOneCardPerSlotInTheOrderDailyWeeklyRepeatable() {
        LeafQuests quests = new LeafQuests(new Daily(2, 3, Instant.parse("2026-11-18T00:00:00Z")), List.of(
                quest("repeatable", new Objective("raids", null), 3, 8, null, null),
                quest("daily", new Objective("raids", "nol"), 1, 3, null, null),
                quest("weekly", new Objective("raids", "tcc"), 5, 12, Instant.parse("2026-11-23T00:00:00Z"), null)),
                List.of());

        List<QuestBoardScreen.Card> cards = QuestBoardScreen.cards(quests, NOW);

        assertEquals(List.of("daily", "weekly", "repeatable"), cards.stream().map(QuestBoardScreen.Card::slot).toList());
        assertEquals(List.of("Daily (2/3)", "Weekly", "Repeatable"),
                cards.stream().map(QuestBoardScreen.Card::heading).toList());
        assertEquals(List.of("Next daily in 6h 12m", "Ends in 5d 6h", "Next one when done"),
                cards.stream().map(QuestBoardScreen.Card::timer).toList());
    }

    @Test
    void saysTheBankIsFullWhenNoMoreDailiesCanArrive() {
        assertEquals("Bank full", QuestBoardScreen.timer("daily", null, new Daily(3, 3, null), NOW));
    }

    @Test
    void anEmptyDailySlotSaysWhenTheNextOneArrives() {
        Daily daily = new Daily(0, 3, Instant.parse("2026-11-18T00:00:00Z"));
        List<QuestBoardScreen.Card> cards = QuestBoardScreen.cards(new LeafQuests(daily, List.of(), List.of()), NOW);

        QuestBoardScreen.Card card = cards.getFirst();
        assertNull(card.quest());
        assertEquals("Daily", card.heading());
        assertEquals("Next daily in 6h 12m", card.timer());
    }

    @Test
    void aFinishedWeeklySaysWhenTheNextOneStarts() {
        Quest weekly = quest("weekly", new Objective("wars", null), 15, 15, Instant.parse("2026-11-23T00:00:00Z"),
                NOW.minusSeconds(60));

        assertEquals("Done · new weekly in 5d 6h", QuestBoardScreen.timer("weekly", weekly, new Daily(1, 3, null), NOW));
        assertEquals(1f, QuestBoardScreen.progressRatio(weekly));
    }

    @Test
    void writesDurationsTheWayATimerReads() {
        assertEquals("2d 22h", QuestBoardScreen.durationLabel(Duration.ofHours(70).plusMinutes(5)));
        assertEquals("6h 12m", QuestBoardScreen.durationLabel(Duration.ofMinutes(372)));
        assertEquals("12m", QuestBoardScreen.durationLabel(Duration.ofMinutes(12).plusSeconds(40)));
        assertEquals("under a minute", QuestBoardScreen.durationLabel(Duration.ofSeconds(30)));
        assertEquals("under a minute", QuestBoardScreen.durationLabel(Duration.ofMinutes(-5)));
    }

    @Test
    void namesEachObjectiveForItsTile() {
        assertEquals("NOL", QuestBoardScreen.objectiveCode(new Objective("raids", "nol")));
        assertEquals("TWP", QuestBoardScreen.objectiveCode(new Objective("raids", "wtp")));
        assertEquals("ALL", QuestBoardScreen.objectiveCode(new Objective("raids", null)));
        assertEquals("WAR", QuestBoardScreen.objectiveCode(new Objective("wars", null)));
        assertEquals("SR", QuestBoardScreen.objectiveCode(new Objective("season_rating", null)));
        assertEquals("?", QuestBoardScreen.objectiveCode(null));
    }

    @Test
    void usesTheRaidsOwnArtworkAndTheSequoiaIconForAnyRaid() {
        assertEquals(SeqRaid.TWP, QuestBoardScreen.objectiveRaid(new Objective("raids", "wtp")));
        assertEquals("twp", QuestBoardScreen.objectiveAsset(new Objective("raids", "wtp")));
        assertEquals("icon", QuestBoardScreen.objectiveAsset(new Objective("raids", null)));
        assertNull(QuestBoardScreen.objectiveAsset(new Objective("wars", null)));
        assertNull(QuestBoardScreen.objectiveAsset(new Objective("raids", "a raid added later")));
    }

    @Test
    void showsProgressAsCountsAndSeasonRatingWithItsUnit() {
        assertEquals("1 / 3", QuestBoardScreen.progressLabel(quest("daily", new Objective("raids", "nol"), 1, 3, null, null)));
        assertEquals("2,350 / 6,000 SR", QuestBoardScreen.progressLabel(
                quest("weekly", new Objective("season_rating", null), 2350, 6000, null, null)));
        assertEquals(0.5f, QuestBoardScreen.progressRatio(quest("daily", null, 4, 8, null, null)));
        assertEquals(0f, QuestBoardScreen.progressRatio(quest("daily", null, 4, 0, null, null)));
    }

    @Test
    void framesEachSlotInItsOwnTierColour() {
        assertEquals(UiColor.ACHIEVEMENT_GOLD, QuestBoardScreen.slotAccent("daily"));
        assertEquals(UiColor.ACHIEVEMENT_DIAMOND, QuestBoardScreen.slotAccent("weekly"));
        assertEquals(UiColor.ACHIEVEMENT_BRONZE, QuestBoardScreen.slotAccent("repeatable"));
    }

    @Test
    void summarisesLevelAndRewardsInWords() {
        assertEquals("Level 7  ·  640 / 2,500 XP", QuestBoardScreen.levelLine(new LeafAccount.Level(7, 640, 2500)));
        assertEquals("+1,000 Leaf, +500 XP", QuestBoardScreen.rewardLabel(new Reward(1000, 500)));
    }

    @Test
    void explainsWhyThereAreNoQuestsToShow() {
        assertEquals("Loading your quests...", QuestBoardScreen.statusLine(State.LOADING));
        assertEquals("Quests are for current Sequoia members.", QuestBoardScreen.statusLine(State.NOT_MEMBER));
        assertEquals("Quests are unavailable right now. Try again in a minute.",
                QuestBoardScreen.statusLine(State.UNAVAILABLE));
    }

    @Test
    void layoutPutsTheThreeCardsSideBySideWhenThereIsRoomAndStacksThemOtherwise() {
        assertEquals(3, QuestBoardScreen.columnCount(640));
        // A 1280x720 window at GUI scale 2 leaves about 484 UI pixels beside the sidebar: still three.
        assertEquals(3, QuestBoardScreen.columnCount(484));
        assertEquals(2, QuestBoardScreen.columnCount(300));
        assertEquals(1, QuestBoardScreen.columnCount(140));

        List<QuestBoardScreen.Placement> wide = QuestBoardScreen.placements(624, 3);
        assertEquals(List.of(0f, 210f, 420f), wide.stream().map(QuestBoardScreen.Placement::x).toList());
        assertEquals(QuestBoardScreen.SUMMARY_HEIGHT + QuestBoardScreen.CARD_HEIGHT,
                QuestBoardScreen.contentHeight(624, 3));
        assertEquals(QuestBoardScreen.SUMMARY_HEIGHT + 3 * QuestBoardScreen.CARD_HEIGHT + 2 * 6,
                QuestBoardScreen.contentHeight(140, 3));
    }

    @Test
    void aRewardShrinksAndThenShortensToStayInItsHalfOfTheRow() {
        // Six pixels a character at 13, five at 11.
        java.util.function.BiFunction<String, Float, Float> measure = (text, size) -> text.length() * (size >= 13 ? 6f : 5f);

        assertEquals(new QuestBoardScreen.LeafFit("+1,000", 13, 52), QuestBoardScreen.leafFit(1_000, 60, true, measure));
        assertEquals(new QuestBoardScreen.LeafFit("+1,000", 11, 44), QuestBoardScreen.leafFit(1_000, 50, true, measure));
        assertEquals(new QuestBoardScreen.LeafFit("+12.5k", 11, 44), QuestBoardScreen.leafFit(12_480, 45, true, measure));
        assertNull(QuestBoardScreen.leafFit(12_480, 20, true, measure));
    }

    @Test
    void withoutTheIconTheWordLeafCountsTowardsTheRoom() {
        java.util.function.BiFunction<String, Float, Float> measure = (text, size) -> text.length() * (size >= 13 ? 6f : 5f);

        // "+1,000" and "Leaf" at 13: 36 + 3 + 24.
        assertEquals(new QuestBoardScreen.LeafFit("+1,000", 13, 63), QuestBoardScreen.leafFit(1_000, 63, false, measure));
        assertEquals(new QuestBoardScreen.LeafFit("+1,000", 11, 53), QuestBoardScreen.leafFit(1_000, 60, false, measure));
        assertEquals(new QuestBoardScreen.LeafFit("+1k", 11, 38), QuestBoardScreen.leafFit(1_000, 40, false, measure));
        assertNull(QuestBoardScreen.leafFit(1_000, 30, false, measure));
    }

    @Test
    void shortCountsKeepOneDecimalOnlyWhenItSaysSomething() {
        assertEquals("950", QuestBoardScreen.shortCount(950));
        assertEquals("1k", QuestBoardScreen.shortCount(1_000));
        assertEquals("12.5k", QuestBoardScreen.shortCount(12_480));
        assertEquals("250k", QuestBoardScreen.shortCount(250_000));
        assertEquals("1.5M", QuestBoardScreen.shortCount(1_500_000));
    }
}
