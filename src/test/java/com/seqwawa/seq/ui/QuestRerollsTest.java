package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.managers.LeafQuestService.Reroll;
import com.seqwawa.seq.model.LeafQuests;
import com.seqwawa.seq.model.LeafQuests.Quest;
import com.seqwawa.seq.ui.QuestCard.RerollLook;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class QuestRerollsTest {

    private static Quest quest(String id, String slot, Long rerollLeaf, Instant completedAt) {
        return new Quest(id, slot, "Complete 2 raids", new LeafQuests.Objective("raids", null), 0, 2, null,
                new LeafQuests.Reward(200, 100), null, null, completedAt, rerollLeaf);
    }

    private static final Quest DAILY = quest("daily:4", LeafQuests.DAILY, 0L, null);
    private static final Quest WEEKLY = quest("weekly:2", LeafQuests.WEEKLY, 100L, null);

    @Test
    void theFirstPressOnlyArmsTheButtonAndASecondSoonAfterRerolls() {
        QuestRerolls rerolls = new QuestRerolls();

        assertFalse(rerolls.press(DAILY, 1_000));
        assertEquals(RerollLook.ARMED, rerolls.look(DAILY, false, 2_000));
        assertTrue(rerolls.press(DAILY, 3_000));
        assertEquals(RerollLook.PENDING, rerolls.look(DAILY, true, 3_000));
    }

    @Test
    void anArmedButtonGoesBackAfterAFewSecondsAndThenNeedsTwoPressesAgain() {
        QuestRerolls rerolls = new QuestRerolls();
        rerolls.press(DAILY, 1_000);

        long later = 1_000 + QuestRerolls.ARM_MS;
        assertEquals(RerollLook.IDLE, rerolls.look(DAILY, false, later));
        assertFalse(rerolls.press(DAILY, later), "a late second press arms it again");
        assertTrue(rerolls.press(DAILY, later + 500));
    }

    @Test
    void pressingAnotherCardsButtonArmsThatOneInstead() {
        QuestRerolls rerolls = new QuestRerolls();
        rerolls.press(DAILY, 1_000);

        assertFalse(rerolls.press(WEEKLY, 1_500));
        assertEquals(RerollLook.IDLE, rerolls.look(DAILY, false, 1_600));
        assertEquals(RerollLook.ARMED, rerolls.look(WEEKLY, false, 1_600));
    }

    @Test
    void onlyOneRerollRunsAtATime() {
        QuestRerolls rerolls = new QuestRerolls();
        rerolls.press(DAILY, 1_000);
        rerolls.press(DAILY, 1_100);

        assertFalse(rerolls.press(WEEKLY, 1_200));
        assertFalse(rerolls.press(WEEKLY, 1_300));
        rerolls.finished(DAILY, Reroll.DONE, 2_000);
        assertFalse(rerolls.press(WEEKLY, 2_100));
        assertTrue(rerolls.press(WEEKLY, 2_200));
    }

    @Test
    void showsAButtonOnlyForAQuestLeafLetsTheMemberSwap() {
        QuestRerolls rerolls = new QuestRerolls();

        assertEquals(RerollLook.IDLE, rerolls.look(DAILY, false, 0));
        assertEquals(RerollLook.HOVERED, rerolls.look(WEEKLY, true, 0));
        assertNull(rerolls.look(quest("timed:1", LeafQuests.TIMED, null, null), true, 0));
        assertNull(rerolls.look(quest("weekly:2", LeafQuests.WEEKLY, 100L, Instant.EPOCH), true, 0), "a finished quest");
        assertNull(rerolls.look(null, true, 0));
        assertFalse(rerolls.press(quest("timed:1", LeafQuests.TIMED, null, null), 0));
    }

    @Test
    void saysWhyARerollDidNotHappenOnThatCardForAFewSeconds() {
        QuestRerolls rerolls = new QuestRerolls();
        rerolls.press(WEEKLY, 1_000);
        rerolls.press(WEEKLY, 1_100);

        rerolls.finished(WEEKLY, Reroll.NOT_ENOUGH_LEAF, 2_000);

        assertEquals("Not enough Leaf to reroll", rerolls.notice(LeafQuests.WEEKLY, 2_500));
        assertNull(rerolls.notice(LeafQuests.DAILY, 2_500));
        assertNull(rerolls.notice(LeafQuests.WEEKLY, 2_000 + QuestRerolls.NOTICE_MS));
        assertEquals(RerollLook.IDLE, rerolls.look(WEEKLY, false, 2_500), "the button is free again");
    }

    @Test
    void aRerollThatWorkedSaysNothingAndTheNextPressClearsAnyNotice() {
        QuestRerolls rerolls = new QuestRerolls();
        rerolls.press(DAILY, 0);
        rerolls.press(DAILY, 1);
        rerolls.finished(DAILY, Reroll.FAILED, 10);
        assertEquals("Couldn't reroll, try again", rerolls.notice(LeafQuests.DAILY, 20));

        rerolls.press(DAILY, 30);
        assertNull(rerolls.notice(LeafQuests.DAILY, 40));
        rerolls.press(DAILY, 50);
        rerolls.finished(DAILY, Reroll.DONE, 60);
        assertNull(rerolls.notice(LeafQuests.DAILY, 70));
    }
}
