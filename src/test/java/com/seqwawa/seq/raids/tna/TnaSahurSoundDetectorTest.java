package com.seqwawa.seq.raids.tna;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.raids.tna.TnaSahurSoundDetector.BeamKind;
import com.seqwawa.seq.raids.tna.TnaSahurSoundDetector.BeamTracker;
import com.seqwawa.seq.raids.tna.TnaSahurSoundDetector.IndicatorState;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class TnaSahurSoundDetectorTest {
    @Test
    void detectsAllSahurSoundsDuringChallengesThreeOfFour() {
        assertTrue(isSahurProc("item.trident.thunder", "item/trident/thunder1"));
        assertTrue(isSahurProc("item.trident.thunder", "item/trident/thunder2"));
        assertTrue(isSahurProc("entity.evoker.prepare_summon", "mob/evocation_illager/prepare_summon"));
    }

    @Test
    void rejectsOtherChallengesEventsAndResolvedSounds() {
        assertFalse(TnaSahurSoundDetector.isSahurProc(
                2, id("item.trident.thunder"), id("item/trident/thunder1")));
        assertFalse(TnaSahurSoundDetector.isSahurProc(
                3, id("entity.lightning_bolt.thunder"), id("item/trident/thunder1")));
        assertFalse(TnaSahurSoundDetector.isSahurProc(
                3, id("item.trident.thunder"), id("item/trident.return")));
    }

    @Test
    void groupsDuplicateCallbacksAndAdvancesTheCountdown() {
        BeamTracker tracker = new BeamTracker();

        boolean firstTimer = tracker.record(BeamKind.TIMER, 1_000L);
        boolean firstDuplicate = tracker.record(BeamKind.TIMER, 1_020L);
        boolean secondTimer = tracker.record(BeamKind.TIMER, 2_000L);
        IndicatorState armed = tracker.snapshot(3, 2_000L);

        assertTrue(firstTimer);
        assertFalse(firstDuplicate);
        assertTrue(secondTimer);
        assertTrue(armed.visible());
        assertEquals(2, armed.timerBeams());
        assertFalse(armed.firing());
        assertEquals(800L, armed.remainingMs());
    }

    @Test
    void flashesOnDangerThenResetsToWaiting() {
        BeamTracker tracker = new BeamTracker();
        tracker.record(BeamKind.TIMER, 1_000L);
        tracker.record(BeamKind.TIMER, 2_000L);

        boolean danger = tracker.record(BeamKind.DANGER, 3_000L);
        boolean dangerDuplicate = tracker.record(BeamKind.DANGER, 3_010L);
        IndicatorState firing = tracker.snapshot(3, 3_100L);
        IndicatorState reset = tracker.snapshot(3, 3_000L + TnaSahurSoundDetector.DANGER_DISPLAY_MS);

        assertTrue(danger);
        assertFalse(dangerDuplicate);
        assertTrue(firing.visible());
        assertTrue(firing.firing());
        assertEquals(0L, firing.remainingMs());
        assertFalse(reset.visible());
        assertFalse(reset.firing());
    }

    @Test
    void resetsImmediatelyOutsideChallengesThreeOfFour() {
        BeamTracker tracker = new BeamTracker();
        tracker.record(BeamKind.TIMER, 1_000L);

        IndicatorState hidden = tracker.snapshot(2, 1_100L);
        IndicatorState waiting = tracker.snapshot(3, 1_200L);

        assertFalse(hidden.visible());
        assertFalse(waiting.visible());
    }

    @Test
    void predictsFireAndExpiresWhenDangerSoundIsMissing() {
        BeamTracker tracker = new BeamTracker();
        tracker.record(BeamKind.TIMER, 1_000L);
        tracker.record(BeamKind.TIMER, 2_000L);

        IndicatorState warning = tracker.snapshot(
                3, 1_000L + TnaSahurSoundDetector.EXPECTED_DANGER_MS - 1L);
        IndicatorState firing = tracker.snapshot(
                3, 1_000L + TnaSahurSoundDetector.EXPECTED_DANGER_MS);
        IndicatorState reset = tracker.snapshot(
                3,
                1_000L + TnaSahurSoundDetector.EXPECTED_DANGER_MS
                        + TnaSahurSoundDetector.DANGER_DISPLAY_MS);

        assertFalse(warning.firing());
        assertEquals(1L, warning.remainingMs());
        assertTrue(firing.visible());
        assertTrue(firing.firing());
        assertFalse(reset.visible());
    }

    private static boolean isSahurProc(String eventId, String soundId) {
        return TnaSahurSoundDetector.isSahurProc(3, id(eventId), id(soundId));
    }

    private static Identifier id(String path) {
        return Identifier.withDefaultNamespace(path);
    }
}
