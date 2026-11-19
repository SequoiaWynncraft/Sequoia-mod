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

        assertFalse(rerolls.press(DAILY, null, 1_000));
        assertEquals(RerollLook.ARMED, rerolls.look(DAILY, false, null, 2_000));
        assertTrue(rerolls.press(DAILY, null, 3_000));
        assertEquals(RerollLook.PENDING, rerolls.look(DAILY, true, "daily:4", 3_000), "while Leaf answers");
    }

    @Test
    void anArmedButtonGoesBackAfterAFewSecondsAndThenNeedsTwoPressesAgain() {
        QuestRerolls rerolls = new QuestRerolls();
        rerolls.press(DAILY, null, 1_000);

        long later = 1_000 + QuestRerolls.ARM_MS;
        assertEquals(RerollLook.IDLE, rerolls.look(DAILY, false, null, later));
        assertFalse(rerolls.press(DAILY, null, later), "a late second press arms it again");
        assertTrue(rerolls.press(DAILY, null, later + 500));
    }

    @Test
    void pressingAnotherCardsButtonArmsThatOneInstead() {
        QuestRerolls rerolls = new QuestRerolls();
        rerolls.press(DAILY, null, 1_000);

        assertFalse(rerolls.press(WEEKLY, null, 1_500));
        assertEquals(RerollLook.IDLE, rerolls.look(DAILY, false, null, 1_600));
        assertEquals(RerollLook.ARMED, rerolls.look(WEEKLY, false, null, 1_600));
    }

    @Test
    void noButtonDoesAnythingWhileARerollWaitsOnLeafEvenOnABoardOpenedSince() {
        QuestRerolls reopened = new QuestRerolls();

        assertFalse(reopened.press(WEEKLY, "daily:4", 1_200));
        assertFalse(reopened.press(WEEKLY, "daily:4", 1_300));
        assertEquals(RerollLook.PENDING, reopened.look(DAILY, false, "daily:4", 1_300));
        assertEquals(RerollLook.IDLE, reopened.look(WEEKLY, false, "daily:4", 1_300));
        assertFalse(reopened.press(WEEKLY, null, 2_100));
        assertTrue(reopened.press(WEEKLY, null, 2_200));
    }

    @Test
    void showsAButtonOnlyForAQuestLeafLetsTheMemberSwap() {
        QuestRerolls rerolls = new QuestRerolls();

        assertEquals(RerollLook.IDLE, rerolls.look(DAILY, false, null, 0));
        assertEquals(RerollLook.HOVERED, rerolls.look(WEEKLY, true, null, 0));
        assertNull(rerolls.look(quest("timed:1", LeafQuests.TIMED, null, null), true, null, 0));
        assertNull(rerolls.look(quest("weekly:2", LeafQuests.WEEKLY, 100L, Instant.EPOCH), true, null, 0), "a finished quest");
        assertNull(rerolls.look(null, true, null, 0));
        assertFalse(rerolls.press(quest("timed:1", LeafQuests.TIMED, null, null), null, 0));
    }

    @Test
    void saysWhyARerollDidNotHappenOnThatCardForAFewSeconds() {
        QuestRerolls rerolls = new QuestRerolls();
        rerolls.press(WEEKLY, null, 1_000);
        rerolls.press(WEEKLY, null, 1_100);

        rerolls.finished(WEEKLY, Reroll.NOT_ENOUGH_LEAF, 2_000);

        assertEquals("Not enough Leaf", rerolls.notice(LeafQuests.WEEKLY, 2_500));
        assertNull(rerolls.notice(LeafQuests.DAILY, 2_500));
        assertNull(rerolls.notice(LeafQuests.WEEKLY, 2_000 + QuestRerolls.NOTICE_MS));
        assertEquals(RerollLook.IDLE, rerolls.look(WEEKLY, false, null, 2_500));
    }

    @Test
    void aRerollThatWorkedSaysNothingAndTheNextPressClearsAnyNotice() {
        QuestRerolls rerolls = new QuestRerolls();
        rerolls.press(DAILY, null, 0);
        rerolls.press(DAILY, null, 1);
        rerolls.finished(DAILY, Reroll.FAILED, 10);
        assertEquals("Couldn't reroll", rerolls.notice(LeafQuests.DAILY, 20));

        rerolls.press(DAILY, null, 30);
        assertNull(rerolls.notice(LeafQuests.DAILY, 40));
        rerolls.press(DAILY, null, 50);
        rerolls.finished(DAILY, Reroll.DONE, 60);
        assertNull(rerolls.notice(LeafQuests.DAILY, 70));
    }
}
