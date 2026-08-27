package com.seqwawa.seq.raids.tna;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TnaRoomThreeHelperTest {

    @Test
    void detectsOnlyChallengeTwoOfFour() {
        assertTrue(TnaRoomThreeHelper.detectChallengeTwoOfFour(
                List.of("The Nameless Anomaly", "Challenges: 2/4", "Destroy the crystals")));
        assertTrue(TnaRoomThreeHelper.detectChallengeTwoOfFour(
                List.of("§dChallenge   2 / 4§r", "Challenge 2/4")));
        assertFalse(TnaRoomThreeHelper.detectChallengeTwoOfFour(
                List.of("Challenges: 1/4", "Challenges: 3/4", "Challenges: 2/5")));
    }

    @Test
    void rendersOnlyForTheActiveChallengeWithinRadius() {
        double radiusSquared = TnaRoomThreeHelper.DISPLAY_RADIUS * TnaRoomThreeHelper.DISPLAY_RADIUS;

        assertTrue(TnaRoomThreeHelper.shouldRender(true, radiusSquared));
        assertFalse(TnaRoomThreeHelper.shouldRender(true, radiusSquared + 0.01));
        assertFalse(TnaRoomThreeHelper.shouldRender(false, 0.0));
    }

    @Test
    void aimingGuideStartsAtTheStandingPositionAtFootLevel() {
        assertEquals(TnaRoomThreeHelper.STAND_POINT.x, TnaRoomThreeHelper.aimGuideStart().x);
        assertEquals(TnaRoomThreeHelper.STAND_POINT.z, TnaRoomThreeHelper.aimGuideStart().z);
        assertTrue(TnaRoomThreeHelper.aimGuideStart().y > TnaRoomThreeHelper.STAND_POINT.y);
    }
}
