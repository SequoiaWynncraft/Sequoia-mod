package com.seqwawa.seq.ui;

import com.seqwawa.seq.managers.LeafQuestService.Reroll;
import com.seqwawa.seq.model.LeafQuests.Quest;

/**
 * The quest board's reroll buttons. A press arms a quest's button and says what it
 * costs; a second press within a few seconds asks Leaf to swap the quest, since a
 * reroll loses the quest's progress and may cost Leaf. One reroll runs at a time.
 * Pure apart from what the board passes in, so it is tested without a game.
 */
final class QuestRerolls {

    /** How long an armed button waits for its second press. */
    static final long ARM_MS = 4_000;
    /** How long a card says why a reroll didn't happen. */
    static final long NOTICE_MS = 5_000;

    private String armed;
    private long armedUntil;
    private String pending;
    private String noticeSlot;
    private String notice;
    private long noticeUntil;

    /** How a quest's button looks, or null when the quest can't be rerolled. */
    QuestCard.RerollLook look(Quest quest, boolean hovered, long now) {
        if (quest == null || !quest.rerollable()) return null;
        if (quest.id().equals(pending)) return QuestCard.RerollLook.PENDING;
        if (quest.id().equals(armed) && now < armedUntil) return QuestCard.RerollLook.ARMED;
        return hovered ? QuestCard.RerollLook.HOVERED : QuestCard.RerollLook.IDLE;
    }

    /** A press on a quest's button. Returns true when it is the second press, and the reroll should go ahead. */
    boolean press(Quest quest, long now) {
        if (pending != null || quest == null || !quest.rerollable()) return false;
        notice = null;
        if (quest.id().equals(armed) && now < armedUntil) {
            armed = null;
            pending = quest.id();
            return true;
        }
        armed = quest.id();
        armedUntil = now + ARM_MS;
        return false;
    }

    /** The reroll of {@code quest} ended: the button frees up, and the card says why when it didn't happen. */
    void finished(Quest quest, Reroll outcome, long now) {
        if (quest.id().equals(pending)) pending = null;
        String said = switch (outcome) {
            case DONE -> null;
            case NOT_ENOUGH_LEAF -> "Not enough Leaf to reroll";
            case CHANGED -> "That quest changed, try again";
            case FAILED -> "Couldn't reroll, try again";
        };
        noticeSlot = quest.slot();
        notice = said;
        noticeUntil = now + NOTICE_MS;
    }

    /** What the card for {@code slot} says in place of its clock, or null. */
    String notice(String slot, long now) {
        return notice != null && slot != null && slot.equals(noticeSlot) && now < noticeUntil ? notice : null;
    }
}
