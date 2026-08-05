package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class ColorRampTest {

    @Test
    void samplesTheEndStopsExactly() {
        ColorRamp ramp = ColorRamp.of(List.of(0x000000, 0xFFFFFF));

        assertEquals(0x000000, ramp.sample(0d));
        assertEquals(0xFFFFFF, ramp.sample(1d));
    }

    @Test
    void interpolatesBetweenTwoStops() {
        ColorRamp ramp = ColorRamp.of(List.of(0x000000, 0xFFFFFF));

        assertEquals(0x808080, ramp.sample(0.5039d));
    }

    @Test
    void walksEachSegmentOfAThreeStopRamp() {
        ColorRamp ramp = ColorRamp.of(List.of(0xFF0000, 0x00FF00, 0x0000FF));

        assertEquals(0xFF0000, ramp.sample(0d));
        assertEquals(0x00FF00, ramp.sample(0.5d), "the middle stop is reached exactly halfway");
        assertEquals(0x0000FF, ramp.sample(1d));
    }

    @Test
    void aSolidRampIgnoresPosition() {
        ColorRamp ramp = ColorRamp.of(0x4CB4FA);

        assertEquals(0x4CB4FA, ramp.sample(0d));
        assertEquals(0x4CB4FA, ramp.sample(1d));
        assertFalse(ramp.isGradient());
    }

    @Test
    void clampsPositionsOutsideTheRange() {
        ColorRamp ramp = ColorRamp.of(List.of(0x000000, 0xFFFFFF));

        assertEquals(0x000000, ramp.sample(-1d));
        assertEquals(0xFFFFFF, ramp.sample(2d));
    }

    @Test
    void reportsEmptinessForUncolouredRoles() {
        assertTrue(ColorRamp.of(List.of()).isEmpty());
        assertTrue(ColorRamp.of((List<Integer>) null).isEmpty());
        assertFalse(ColorRamp.of(0x000000).isEmpty());
    }

    @Test
    void refusesToSampleAnUncolouredRamp() {
        // Guild chat catches this and leaves the line undecorated, which beats
        // painting a pill some arbitrary colour.
        assertThrows(IllegalStateException.class, () -> ColorRamp.empty().sample(0d));
    }

    @Test
    void scrollingAtRestReproducesTheStaticGradient() {
        // Which is what lets the animation be switched on and off without the pill
        // jumping to a different set of colours.
        ColorRamp ramp = ColorRamp.of(List.of(0xFF0000, 0x00FF00, 0x0000FF));

        assertEquals(ramp.sample(0d), ramp.scroll(0d, 0d));
        assertEquals(ramp.sample(0.25d), ramp.scroll(0.25d, 0d));
        assertEquals(ramp.sample(0.5d), ramp.scroll(0.5d, 0d));
        assertEquals(ramp.sample(1d), ramp.scroll(1d, 0d));
    }

    @Test
    void scrollingRunsTheLastStopBackIntoTheFirst() {
        // A two stop ramp is a two segment loop, so half a turn lands on the far stop
        // and the second half is the seamless run home.
        ColorRamp ramp = ColorRamp.of(List.of(0x000000, 0xFFFFFF));

        assertEquals(0xFFFFFF, ramp.scroll(0d, 0.5d));
        assertEquals(0x808080, ramp.scroll(0d, 0.75d), "and back down through the middle");
        assertEquals(0x000000, ramp.scroll(0d, 1d), "a full turn is where it started");
    }

    @Test
    void scrollingWrapsRatherThanClampingThePhase() {
        // The phase comes off a clock, so it keeps growing; nothing may stall at an end.
        ColorRamp ramp = ColorRamp.of(List.of(0x000000, 0xFFFFFF));

        assertEquals(ramp.scroll(0.25d, 0.3d), ramp.scroll(0.25d, 3.3d));
        assertEquals(ramp.scroll(0.25d, 0.3d), ramp.scroll(0.25d, -0.7d));
    }

    @Test
    void aSolidRampHasNothingToScroll() {
        ColorRamp ramp = ColorRamp.of(0x4CB4FA);

        assertEquals(0x4CB4FA, ramp.scroll(0d, 0.5d));
    }

    @Test
    void blendMixesChannelsProportionally() {
        assertEquals(0xFFFFFF, ColorRamp.blend(0x000000, 0xFFFFFF, 1d));
        assertEquals(0x123456, ColorRamp.blend(0x123456, 0xFFFFFF, 0d));
    }
}
