package com.seqwawa.seq.LightRoomTnaRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LightRoomTest {

    @Test
    void detectsRoomStatesWithoutCombiningSidebarText() {
        LightRoom.RoomState state = LightRoom.detectRoomState(
                List.of("The Nameless Anomaly", "Gather the Light!", "Find and kill the enemies"));

        assertTrue(state.prepRoom());
        assertTrue(state.inRoom());
    }

    @Test
    void stopsReadingAfterBothRoomMarkersAreFound() {
        Iterable<String> lines = () -> new Iterator<>() {
            private final Iterator<String> values =
                    List.of("Gather the Light!", "Find and kill the enemies").iterator();

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public String next() {
                if (!values.hasNext()) {
                    throw new AssertionError("sidebar scan did not stop after finding both markers");
                }
                return values.next();
            }
        };

        LightRoom.RoomState state = LightRoom.detectRoomState(lines);

        assertTrue(state.prepRoom());
        assertTrue(state.inRoom());
    }

    @Test
    void leavesRoomStatesFalseWhenMarkersAreMissing() {
        LightRoom.RoomState state =
                LightRoom.detectRoomState(List.of("The Nameless Anomaly", "Defeat the raid boss"));

        assertFalse(state.prepRoom());
        assertFalse(state.inRoom());
    }

    @Test
    void countsOnlyConsecutiveTicksForTheSameSoleCandidate() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        LightRoom.HolderCounter firstTick = LightRoom.updateHolderCounter(null, 0, first);
        LightRoom.HolderCounter secondTick =
                LightRoom.updateHolderCounter(firstTick.candidateId(), firstTick.ticks(), first);
        LightRoom.HolderCounter changed =
                LightRoom.updateHolderCounter(secondTick.candidateId(), secondTick.ticks(), second);
        LightRoom.HolderCounter ambiguous =
                LightRoom.updateHolderCounter(changed.candidateId(), changed.ticks(), null);

        assertEquals(new LightRoom.HolderCounter(first, 1), firstTick);
        assertEquals(new LightRoom.HolderCounter(first, 2), secondTick);
        assertEquals(new LightRoom.HolderCounter(second, 1), changed);
        assertEquals(new LightRoom.HolderCounter(null, 0), ambiguous);
    }

    @Test
    void capsConfirmedHolderCounter() {
        UUID candidate = UUID.randomUUID();

        assertEquals(
                new LightRoom.HolderCounter(candidate, 60),
                LightRoom.updateHolderCounter(candidate, 60, candidate));
    }
}
