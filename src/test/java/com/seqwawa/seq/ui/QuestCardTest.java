package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.model.LeafQuests.Goal;
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
    void eachGoalShowsItsOwnPicture() {
        assertEquals("unlock_icon_nol", QuestCard.goalIcon(new Goal("raids", "nol", "Complete 3 NOL raids", 1, 3)));
        assertEquals("unlock_war", QuestCard.goalIcon(new Goal("wars", null, "Take part in 9 wars", 4, 9)));
    }

    @Test
    void twoGoalsSplitTheCardIntoEqualColumnsOnWholePixels() {
        QuestCard.Columns wide = QuestCard.columns(100, 300, 2);

        assertEquals(new QuestCard.Columns(110, 136, 252, 138), wide,
                "inside the padding, 6 apart, and the second takes the odd pixel");
        assertEquals(400 - 10, wide.rightX() + wide.rightWidth());

        QuestCard.Columns narrowest = QuestCard.columns(0, 140, 2);
        assertEquals(56, narrowest.leftWidth());
        assertTrue(narrowest.leftWidth() >= QuestCard.TILE * QuestCard.P, "each goal's picture fits over its bar");
        assertTrue(narrowest.rightWidth() >= QuestCard.TILE * QuestCard.P);
    }

    @Test
    void theRewardShortensToFit() {
        // "+12,480" with its Leaf is 53.
        assertEquals("+12,480", QuestCard.rewardText(12_480, 53, LEAF));
        assertEquals("+12.5k", QuestCard.rewardText(12_480, 50, LEAF));
        assertNull(QuestCard.rewardText(12_480, 20, LEAF));
    }

    @Test
    void theRewardSaysTheXpBesideTheLeafWhileBothFit() {
        // "+300" with its Leaf is 35, a 6 gap, and "+150 XP" is 42: 83 in all.
        assertEquals(new QuestCard.RewardLine("+300", "+150 XP"), QuestCard.rewardLine(300, 150, 83, 6, LEAF, TEXT));
        assertEquals(new QuestCard.RewardLine("+300", null), QuestCard.rewardLine(300, 150, 82, 6, LEAF, TEXT),
                "the Leaf matters more, so the XP goes first");
        assertEquals(new QuestCard.RewardLine("+300", null), QuestCard.rewardLine(300, 0, 200, 6, LEAF, TEXT));
        assertNull(QuestCard.rewardLine(300, 150, 20, 6, LEAF, TEXT));
    }

    @Test
    void aShortQuestsBarHasAPartForEachRaid() {
        assertEquals(3, QuestCard.notches(3));
        assertEquals(12, QuestCard.notches(12));
        assertEquals(0, QuestCard.notches(20), "too many to tell apart");
        assertEquals(0, QuestCard.notches(1), "one part is no division at all");
    }

    @Test
    void aTitleWithTwoWaysBreaksBeforeItsOr() {
        assertEquals(List.of("Complete 3 NOL raids", "or 9 wars"),
                QuestCard.titleLines("Complete 3 NOL raids or 9 wars", 130, TEXT));
        assertEquals(List.of("Complete 3 NOL raids or 9 wars"),
                QuestCard.titleLines("Complete 3 NOL raids or 9 wars", 180, TEXT));
        assertEquals(List.of("Complete 3 NOL", "raids or 9 wars"),
                QuestCard.titleLines("Complete 3 NOL raids or 9 wars", 114, TEXT),
                "too narrow for each way on its own line, so it wraps as words");
        assertEquals(List.of("Earn 6,000 season", "rating"), QuestCard.titleLines("Earn 6,000 season rating", 110, TEXT));
    }

    @Test
    void wrapsAtSpacesWithinTheWidth() {
        assertEquals(List.of("Complete 3", "NOL raids"), QuestCard.wrap("Complete 3 NOL raids", 60, TEXT));
        assertEquals(List.of("Complete 3 NOL raids"), QuestCard.wrap("Complete 3 NOL raids", 120, TEXT));
    }

    @Test
    void aWordTooWideForTheCardIsCutToFit() {
        // "Antidisestablishment" is 120 pixels; with "..." the room for 60 keeps 7 letters: 10 characters, exactly 60.
        assertEquals(List.of("Antidis..."), QuestCard.wrap("Antidisestablishment", 60, TEXT));
        assertEquals(List.of("Do", "Antidis..."), QuestCard.wrap("Do Antidisestablishment", 60, TEXT));
    }
}
