package com.seqwawa.seq.raids.tna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class TnaLineupHelperTest {

    @Test
    void detectsBerryAndRoomThreeChallengeProgress() {
        assertEquals(0, TnaLineupHelper.detectChallengeProgress(
                List.of("The Nameless Anomaly", "Challenges: 0/4", "Prepare")));
        assertEquals(2, TnaLineupHelper.detectChallengeProgress(List.of("§dChallenge   2 / 4§r")));
        assertEquals(-1, TnaLineupHelper.detectChallengeProgress(
                List.of("Challenges: 2/5", "No challenge progress")));
    }

    @Test
    void rendersOnlyForTheExpectedChallengeWithinRadius() {
        double radiusSquared = TnaLineupHelper.DISPLAY_RADIUS * TnaLineupHelper.DISPLAY_RADIUS;

        assertTrue(TnaLineupHelper.shouldRender(0, 0, radiusSquared));
        assertFalse(TnaLineupHelper.shouldRender(0, 2, 0.0));
        assertFalse(TnaLineupHelper.shouldRender(0, 0, radiusSquared + 0.01));
    }

    @Test
    void berryMarkersUseTheSuppliedCoordinates() {
        Vec3 standCenter = TnaLineupHelper.floorMarkerCenter(TnaLineupHelper.BERRY_STAND_POINT);
        Vec3 aimCenter =
                TnaLineupHelper.wallMarkerCenter(TnaLineupHelper.BERRY_STAND_POINT, TnaLineupHelper.BERRY_AIM_POINT);

        assertEquals(27_758.2, standCenter.x);
        assertTrue(standCenter.y > 6.0);
        assertEquals(-22_049.5, standCenter.z);
        assertTrue(aimCenter.x > 27_739.0);
        assertEquals(9.0, aimCenter.y);
        assertEquals(-22_049.6, aimCenter.z);
    }

    @Test
    void roomThreeGuideStillStartsAtTheStandingPositionAtFootLevel() {
        Vec3 start = TnaLineupHelper.floorMarkerCenter(TnaLineupHelper.ROOM_THREE_STAND_POINT);

        assertEquals(TnaLineupHelper.ROOM_THREE_STAND_POINT.x, start.x);
        assertEquals(TnaLineupHelper.ROOM_THREE_STAND_POINT.z, start.z);
        assertTrue(start.y > TnaLineupHelper.ROOM_THREE_STAND_POINT.y);
    }
}
