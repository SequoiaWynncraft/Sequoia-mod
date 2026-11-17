package com.seqwawa.seq.ui.unlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.ui.unlock.UnlockPopup.Kind;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class UnlockAnimationTest {

    @Test
    void eachKindPlaysForItsOwnLength() {
        assertTrue(UnlockAnimation.frame(Kind.TOAST, 3_199).active());
        assertFalse(UnlockAnimation.frame(Kind.TOAST, 3_200).active());
        assertTrue(UnlockAnimation.frame(Kind.CARD, 4_599).active());
        assertFalse(UnlockAnimation.frame(Kind.CEREMONY, 6_400).active());
        assertFalse(UnlockAnimation.frame(Kind.CARD, -1).active());
    }

    @Test
    void theMedalPopsThroughWholeScalesAndOvershootsBeforeItRests() {
        List<Integer> scales = LongStream.of(0, 160, 220, 300, 400, 3_000)
                .mapToObj(ms -> UnlockAnimation.medalScale(ms))
                .toList();

        assertEquals(List.of(0, 1, 2, 4, 3, 3), scales);
    }

    @Test
    void aCardDropsInStepsAndBouncesOncePastItsPlace() {
        assertEquals(1f, UnlockAnimation.cardSlide(0));
        assertEquals(0.5f, UnlockAnimation.cardSlide(150));
        assertTrue(UnlockAnimation.cardSlide(260) < 0);
        assertEquals(0f, UnlockAnimation.cardSlide(400));
    }

    @Test
    void slidesMoveInWholeSteps() {
        assertEquals(0.4f, UnlockAnimation.steps(0.33f, 5));
        assertEquals(1f, UnlockAnimation.steps(1.2f, 5));
        assertEquals(0f, UnlockAnimation.steps(0f, 5));
    }

    @Test
    void theCeremonyTitleTypesItselfAfterTheMedalLands() {
        assertEquals(0, UnlockAnimation.typedLetters(400, 7));
        assertEquals(1, UnlockAnimation.typedLetters(450, 7));
        assertEquals(7, UnlockAnimation.typedLetters(5_000, 7));
    }

    @Test
    void countersRollUpAndThePopupFadesAtTheEnd() {
        assertEquals(0f, UnlockAnimation.frame(Kind.CEREMONY, 1_000).counter());
        assertEquals(1f, UnlockAnimation.frame(Kind.CEREMONY, 2_500).counter());
        assertEquals(1f, UnlockAnimation.frame(Kind.CARD, 2_000).opacity());
        assertTrue(UnlockAnimation.frame(Kind.CARD, 4_500).opacity() < 0.5f);
    }

    @Test
    void theMedalLandsWithAFlashAShakeAndAShockwave() {
        long land = UnlockAnimation.LAND_MS;
        assertFalse(UnlockAnimation.flashing(land - 1));
        assertTrue(UnlockAnimation.flashing(land));
        assertFalse(UnlockAnimation.flashing(land + 100));
        assertEquals(List.of(0, 0), List.of(UnlockAnimation.shake(land - 1)[0], UnlockAnimation.shake(land - 1)[1]));
        assertEquals(1, UnlockAnimation.shake(land)[0]);
        assertEquals(0, UnlockAnimation.shake(land + 200)[0]);
        assertEquals(-1, UnlockAnimation.ringFrame(land - 1));
        assertEquals(0, UnlockAnimation.ringFrame(land));
        assertEquals(5, UnlockAnimation.ringFrame(land + 5 * UnlockAnimation.RING_FRAME_MS));
        assertEquals(-1, UnlockAnimation.ringFrame(land + 6 * UnlockAnimation.RING_FRAME_MS));
    }

    @Test
    void theBannerUnrollsInStepsAndEmbersStayOnScreen() {
        assertEquals(0f, UnlockAnimation.bannerOpen(300));
        assertEquals(1f, UnlockAnimation.bannerOpen(1_000));
        for (int seed = 1; seed <= 30; seed++) {
            for (long ms = 0; ms < 6_400; ms += 250) {
                float[] ember = UnlockAnimation.ember(seed, ms);
                if (ember == null) continue;
                assertTrue(ember[0] > -0.05f && ember[0] < 1.05f);
                assertTrue(ember[1] > -0.1f && ember[1] <= 1.08f);
            }
        }
    }

    @Test
    void fallingLeavesStartWhereTheyAreThrownAndThenFall() {
        int[] start = UnlockAnimation.leafOffset(3, 300);
        int[] later = UnlockAnimation.leafOffset(3, 2_300);

        assertEquals(0, start[0]);
        assertEquals(0, start[1]);
        assertTrue(later[1] > start[1] - 40, "gravity brings it back down");
    }
}
