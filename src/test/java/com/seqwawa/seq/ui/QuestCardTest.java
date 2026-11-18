package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.seqwawa.seq.model.LeafQuests.Objective;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class QuestCardTest {

    // Six pixels a character, as the pixel font is at 10, and eleven more for the Leaf.
    private static final Function<String, Float> TEXT = text -> text.length() * 6f;
    private static final Function<String, Float> LEAF = text -> text.length() * 6f + 11;

    @Test
    void showsTheObjectivesOwnPixelPicture() {
        assertEquals("unlock_icon_nol", QuestCard.tileIcon(new Objective("raids", "nol")));
        assertEquals("unlock_icon_twp", QuestCard.tileIcon(new Objective("raids", "wtp")));
        assertEquals("unlock_raid", QuestCard.tileIcon(new Objective("raids", null)));
        assertEquals("unlock_raid", QuestCard.tileIcon(new Objective("raids", "a raid added later")));
        assertEquals("unlock_war", QuestCard.tileIcon(new Objective("wars", null)));
        assertEquals("star", QuestCard.tileIcon(new Objective("season_rating", null)));
        assertEquals("unlock_raid", QuestCard.tileIcon(null));
    }

    @Test
    void theRewardShortensToFit() {
        // "+12,480" with its Leaf is 53.
        assertEquals("+12,480", QuestCard.rewardText(12_480, 53, LEAF));
        assertEquals("+12.5k", QuestCard.rewardText(12_480, 50, LEAF));
        assertNull(QuestCard.rewardText(12_480, 20, LEAF));
    }

    @Test
    void aShortQuestsBarHasAPartForEachRaid() {
        assertEquals(3, QuestCard.notches(3));
        assertEquals(12, QuestCard.notches(12));
        assertEquals(0, QuestCard.notches(20), "too many to tell apart");
        assertEquals(0, QuestCard.notches(1), "one part is no division at all");
    }

    @Test
    void wrapsAtSpacesWithinTheWidth() {
        assertEquals(List.of("Complete 3", "NOL raids"), QuestCard.wrap("Complete 3 NOL raids", 60, TEXT));
        assertEquals(List.of("Complete 3 NOL raids"), QuestCard.wrap("Complete 3 NOL raids", 120, TEXT));
    }
}
