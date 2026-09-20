package com.seqwawa.seq.raids.tna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.network.WynncraftServerPolicy;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class TnaLineupHelperTest {

    @Test
    void allowsTransientWynncraftTransfersButBlocksOtherServers() {
        assertTrue(TnaLineupHelper.supportsScope(WynncraftServerPolicy.Scope.MAIN));
        assertTrue(TnaLineupHelper.supportsScope(WynncraftServerPolicy.Scope.UNKNOWN));
        assertFalse(TnaLineupHelper.supportsScope(WynncraftServerPolicy.Scope.BLOCKED));
    }

    @Test
    void recognizesOnlyTheNamelessAnomalyTitle() {
        assertTrue(TnaLineupHelper.isTnaTitle("§9§lThe Nameless Anomaly"));
        assertFalse(TnaLineupHelper.isTnaTitle("The Canyon Colossus"));
        assertFalse(TnaLineupHelper.isTnaTitle(null));
    }

    @Test
    void detectsTnaChallengeProgress() {
        assertEquals(0, TnaLineupHelper.detectChallengeProgress(
                List.of("The Nameless Anomaly", "Challenges: 0/4", "Prepare")));
        assertEquals(2, TnaLineupHelper.detectChallengeProgress(List.of("§dChallenge   2 / 4§r")));
        assertEquals(3, TnaLineupHelper.detectChallengeProgress(List.of("Challenges: 3/4")));
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
    void roomThreeGuideStillStartsAtTheStandingPositionAtFootLevel() {
        Vec3 start = TnaLineupHelper.floorMarkerCenter(TnaLineupHelper.ROOM_THREE_STAND_POINT);

        assertEquals(TnaLineupHelper.ROOM_THREE_STAND_POINT.x, start.x);
        assertEquals(TnaLineupHelper.ROOM_THREE_STAND_POINT.z, start.z);
        assertTrue(start.y > TnaLineupHelper.ROOM_THREE_STAND_POINT.y);
    }
}
