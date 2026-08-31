package com.seqwawa.seq.raids.tna;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.raids.tna.TnaSahurSoundDetector.BeamKind;
import com.seqwawa.seq.raids.tna.TnaSahurSoundDetector.BeamTracker;
import com.seqwawa.seq.raids.tna.TnaSahurSoundDetector.IndicatorState;
import com.seqwawa.seq.raids.tna.TnaSahurSoundDetector.ProcResult;
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
    void groupsDuplicateCallbacksAndArmsTheThirdLamp() {
        BeamTracker tracker = new BeamTracker();

        ProcResult firstTimer = tracker.record(BeamKind.TIMER, 1_000L);
        ProcResult firstDuplicate = tracker.record(BeamKind.TIMER, 1_020L);
        ProcResult secondTimer = tracker.record(BeamKind.TIMER, 2_000L);
        IndicatorState armed = tracker.snapshot(3, 2_000L);

        assertTrue(firstTimer.accepted());
        assertFalse(firstDuplicate.accepted());
        assertEquals(20L, firstDuplicate.rawDeltaMs());
        assertTrue(secondTimer.accepted());
        assertEquals(1_000L, secondTimer.sequenceElapsedMs());
        assertEquals(2, armed.timerBeams());
        assertTrue(armed.visible());
        assertFalse(armed.danger());
    }

    @Test
    void flashesOnDangerThenResetsToWaiting() {
        BeamTracker tracker = new BeamTracker();
        tracker.record(BeamKind.TIMER, 1_000L);
        tracker.record(BeamKind.TIMER, 2_000L);

        ProcResult danger = tracker.record(BeamKind.DANGER, 3_000L);
        ProcResult dangerDuplicate = tracker.record(BeamKind.DANGER, 3_010L);
        IndicatorState firing = tracker.snapshot(3, 3_100L);
        IndicatorState reset = tracker.snapshot(3, 3_000L + TnaSahurSoundDetector.DANGER_DISPLAY_MS);

        assertTrue(danger.accepted());
        assertEquals(2_000L, danger.sequenceElapsedMs());
        assertFalse(dangerDuplicate.accepted());
        assertTrue(firing.visible());
        assertTrue(firing.danger());
        assertFalse(reset.visible());
        assertEquals(0, reset.timerBeams());
        assertFalse(reset.danger());
    }

    @Test
    void resetsImmediatelyOutsideChallengesThreeOfFour() {
        BeamTracker tracker = new BeamTracker();
        tracker.record(BeamKind.TIMER, 1_000L);

        IndicatorState hidden = tracker.snapshot(2, 1_100L);
        IndicatorState waiting = tracker.snapshot(3, 1_200L);

        assertFalse(hidden.visible());
        assertFalse(waiting.visible());
        assertEquals(0, waiting.timerBeams());
    }

    private static boolean isSahurProc(String eventId, String soundId) {
        return TnaSahurSoundDetector.isSahurProc(3, id(eventId), id(soundId));
    }

    private static Identifier id(String path) {
        return Identifier.withDefaultNamespace(path);
    }
}
