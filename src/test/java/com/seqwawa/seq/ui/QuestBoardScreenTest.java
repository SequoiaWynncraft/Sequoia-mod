package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.seqwawa.seq.managers.LeafQuestService.State;
import com.seqwawa.seq.model.LeafAccount;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.model.LeafQuests.Daily;
import com.seqwawa.seq.model.LeafQuests.Goal;
import com.seqwawa.seq.model.LeafQuests.Objective;
import com.seqwawa.seq.model.LeafQuests.Quest;
import com.seqwawa.seq.model.LeafQuests.Reward;
import com.seqwawa.seq.ui.theme.UiColor;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestBoardScreenTest {

    private static final Instant NOW = Instant.parse("2026-11-17T17:48:00Z");
    // The 16:00 window, open until 18:00; the next opens at 20:00.
    private static final LeafQuests.Timed TIMED =
            new LeafQuests.Timed(Instant.parse("2026-11-17T18:00:00Z"), Instant.parse("2026-11-17T20:00:00Z"));

    private static Quest quest(String slot, Objective objective, long progress, long target, Instant expiresAt,
            Instant completedAt) {
        return new Quest(slot + ":1", slot, "A quest", objective, progress, target, null, new Reward(300, 150),
                NOW.minusSeconds(3600), expiresAt, completedAt);
    }

    @Test
    void showsOneCardPerSlotInTheOrderDailyWeeklyTimed() {
        Instant timedEnds = Instant.parse("2026-11-17T19:00:00Z");
        LeafQuests quests = new LeafQuests(new Daily(2, 3, Instant.parse("2026-11-18T00:00:00Z")), TIMED, List.of(
                quest("repeatable", new Objective("raids", null), 3, 8, null, null),
                quest("timed", new Objective("raids", null), 1, 2, timedEnds, null),
                quest("daily", new Objective("raids", "nol"), 1, 3, null, null),
                quest("weekly", new Objective("raids", "tcc"), 5, 12, Instant.parse("2026-11-23T00:00:00Z"), null)),
                List.of());

        List<QuestBoardScreen.Card> cards = QuestBoardScreen.cards(quests, NOW);

        assertEquals(List.of("daily", "weekly", "timed"), cards.stream().map(QuestBoardScreen.Card::slot).toList(),
                "a repeatable quest gets no card");
        assertEquals(List.of("Daily (2/3)", "Weekly", "Timed"),
                cards.stream().map(QuestBoardScreen.Card::heading).toList());
        assertEquals(List.of("Next daily in 6h 12m", "Ends in 5d 6h", "Ends in 1h 12m"),
                cards.stream().map(QuestBoardScreen.Card::timer).toList());
        assertEquals(List.of(2, 0, 0), cards.stream().map(QuestBoardScreen.Card::banked).toList());
        assertEquals(List.of(3, 0, 0), cards.stream().map(QuestBoardScreen.Card::bankSize).toList(),
                "only the daily has a bank, drawn as pips");
    }

    @Test
    void saysTheBankIsFullWhenNoMoreDailiesCanArrive() {
        assertEquals("Bank full", QuestBoardScreen.timer("daily", null, new Daily(3, 3, null), null, NOW));
    }

    @Test
    void anEmptyDailySlotSaysWhenTheNextOneArrives() {
        Daily daily = new Daily(0, 3, Instant.parse("2026-11-18T00:00:00Z"));
        List<QuestBoardScreen.Card> cards = QuestBoardScreen.cards(new LeafQuests(daily, null, List.of(), List.of()), NOW);

        QuestBoardScreen.Card card = cards.getFirst();
        assertNull(card.quest());
        assertEquals("Daily", card.heading());
        assertEquals("Next daily in 6h 12m", card.timer());
    }

    @Test
    void aFinishedWeeklySaysWhenTheNextOneStarts() {
        Quest weekly = quest("weekly", new Objective("wars", null), 15, 15, Instant.parse("2026-11-23T00:00:00Z"),
                NOW.minusSeconds(60));

        assertEquals("Done · new weekly in 5d 6h",
                QuestBoardScreen.timer("weekly", weekly, new Daily(1, 3, null), null, NOW));
    }

    @Test
    void aTimedCardSaysWhenTheNextQuestOpensOnceThisOneIsDoneOrBetweenWindows() {
        Daily daily = new Daily(1, 3, null);
        Quest done = quest("timed", new Objective("wars", null), 3, 3, TIMED.endsAt(), NOW.minusSeconds(60));
        LeafQuests.Timed between = new LeafQuests.Timed(null, TIMED.nextAt());

        assertEquals("Done · next in 2h 12m", QuestBoardScreen.timer("timed", done, daily, TIMED, NOW));
        assertEquals("Next in 2h 12m", QuestBoardScreen.timer("timed", null, daily, between, NOW));
        QuestBoardScreen.Card empty = QuestBoardScreen.cards(new LeafQuests(daily, between, List.of(), List.of()), NOW)
                .getLast();
        assertEquals("A timed quest opens for the whole guild every four hours.", empty.emptyLine());
        assertEquals("Every four hours", QuestBoardScreen.timer("timed", null, daily, null, NOW),
                "an older Leaf sends no timed block");
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
    void showsProgressAsCountsAndSeasonRatingWithItsUnit() {
        assertEquals("1 / 3", QuestBoardScreen.progressLabel(quest("daily", new Objective("raids", "nol"), 1, 3, null, null)));
        assertEquals("2,350 / 6,000 SR", QuestBoardScreen.progressLabel(
                quest("weekly", new Objective("season_rating", null), 2350, 6000, null, null)));
        assertEquals(0.5f, QuestBoardScreen.progressRatio(new Goal("raids", null, "Complete 8 raids", 4, 8)));
        assertEquals(0f, QuestBoardScreen.progressRatio(new Goal("raids", null, "A quest", 4, 0)));
    }

    @Test
    void readsOutEachGoalsCountForAQuestRaidsOrWarsCanFinish() {
        Quest either = new Quest("daily:1", "daily", "Complete 3 NOL raids or 9 wars", new Objective("wars", null), 4, 9,
                List.of(new Goal("raids", "nol", "Complete 3 NOL raids", 1, 3),
                        new Goal("wars", null, "Take part in 9 wars", 4, 9)),
                new Reward(300, 150), NOW.minusSeconds(3600), null, null);

        assertEquals("1 / 3 raids, 4 / 9 wars", QuestBoardScreen.progressLabel(either));
    }

    @Test
    void framesEachSlotInItsOwnTierColour() {
        assertEquals(UiColor.ACHIEVEMENT_GOLD, QuestBoardScreen.slotAccent("daily"));
        assertEquals(UiColor.ACHIEVEMENT_DIAMOND, QuestBoardScreen.slotAccent("weekly"));
        assertEquals(UiColor.ACHIEVEMENT_OBSIDIAN, QuestBoardScreen.slotAccent("timed"));
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

        List<QuestBoardScreen.Placement> wide = QuestBoardScreen.placements(624, 3, 98);
        assertEquals(List.of(0f, 210f, 420f), wide.stream().map(QuestBoardScreen.Placement::x).toList());
        assertEquals(List.of(98f, 98f, 98f), wide.stream().map(QuestBoardScreen.Placement::y).toList());
        assertEquals(QuestBoardScreen.CARD_HEIGHT, QuestBoardScreen.cardsHeight(624, 3));
        List<QuestBoardScreen.Placement> uneven = QuestBoardScreen.placements(479, 3, 0);
        assertEquals(List.of(154f, 154f, 159f), uneven.stream().map(QuestBoardScreen.Placement::width).toList(),
                "whole art pixels wide, the last taking what is left so the row ends with the board");
        List<QuestBoardScreen.Placement> stacked = QuestBoardScreen.placements(140, 3, 98);
        assertEquals(List.of(98f, 264f, 430f), stacked.stream().map(QuestBoardScreen.Placement::y).toList());
        assertEquals(3 * QuestBoardScreen.CARD_HEIGHT + 2 * 6, QuestBoardScreen.cardsHeight(140, 3));
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
