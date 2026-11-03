package com.seqwawa.seq.courage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.courage.CourageHud.Bounds;
import com.seqwawa.seq.courage.CourageHud.Position;
import com.seqwawa.seq.model.PowderSpecialReading;
import java.awt.Color;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CourageHudTest {
    private static final float SCREEN_WIDTH = 400f;
    private static final float SCREEN_HEIGHT = 220f;
    private static final float PANEL_WIDTH = 100f;
    private static final float PANEL_HEIGHT = 30f;
    private static final long RAINBOW_HALF_PERIOD_MS = 1_250L;

    @Test
    void pinsAPanelInsideTheScreenMargins() {
        Bounds topLeft =
                CourageHud.place(SCREEN_WIDTH, SCREEN_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT, 0f, 0f);
        Bounds bottomRight =
                CourageHud.place(SCREEN_WIDTH, SCREEN_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT, 1f, 1f);

        assertEquals(CourageHud.MARGIN, topLeft.x());
        assertEquals(CourageHud.MARGIN, topLeft.y());
        assertEquals(SCREEN_WIDTH - CourageHud.MARGIN, bottomRight.x() + PANEL_WIDTH);
        assertEquals(SCREEN_HEIGHT - CourageHud.MARGIN, bottomRight.y() + PANEL_HEIGHT);
    }

    @Test
    void roundTripsADraggedPanelBackToItsPlacement() {
        Position dragged = CourageHud.positionForTopLeft(
                SCREEN_WIDTH, SCREEN_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT, 150f, 80f);
        Bounds placed = CourageHud.place(
                SCREEN_WIDTH, SCREEN_HEIGHT, PANEL_WIDTH, PANEL_HEIGHT, dragged.x(), dragged.y());

        assertEquals(150f, placed.x(), 0.01f);
        assertEquals(80f, placed.y(), 0.01f);
    }

    @Test
    void keepsAPanelTooLargeToMovePinnedToTheMargin() {
        Position dragged =
                CourageHud.positionForTopLeft(SCREEN_WIDTH, SCREEN_HEIGHT, SCREEN_WIDTH, 30f, 200f, 20f);

        assertEquals(0f, dragged.x());
    }

    @Test
    void treatsOnlyAFullFireBarAsChargedCourage() {
        assertTrue(CourageCharge.isFull(fire(1.0)));
        assertTrue(CourageCharge.isFull(fire(0.99999)));
        assertFalse(CourageCharge.isFull(fire(0.99)));
        assertFalse(CourageCharge.isFull(fire(0.0)));
        assertFalse(CourageCharge.isFull(otherElement(1.0)));
        assertFalse(CourageCharge.isFull(Optional.empty()));
    }

    @Test
    void holdsTheAuraClosedThroughTheBlankBarOfAWeaponSwap() {
        assertFalse(CourageCharge.allowsOwnAura(Optional.empty(), true));
        assertFalse(CourageCharge.allowsOwnAura(fire(0.4), true));
    }

    @Test
    void opensTheAuraOnlyOnAFullBarOnceTheBarIsReadable() {
        assertTrue(CourageCharge.allowsOwnAura(fire(1.0), true));
        assertFalse(CourageCharge.allowsOwnAura(otherElement(1.0), true));
    }

    @Test
    void skipsTheGateEntirelyWhenNoReadingHasEverArrived() {
        assertTrue(CourageCharge.allowsOwnAura(Optional.empty(), false));
    }

    @Test
    void givesEachCountItsOwnFlatColour() {
        assertEquals(new Color(0xAAAAAA), CourageOwnRangeHudRenderer.countColor(0, 0L));
        assertEquals(new Color(0x5555FF), CourageOwnRangeHudRenderer.countColor(1, 0L));
        assertEquals(new Color(0xFF5555), CourageOwnRangeHudRenderer.countColor(2, 0L));
    }

    @Test
    void cyclesTheHueOnceTheAuraIsFull() {
        assertEquals(CourageOwnRangeHudRenderer.rainbow(0L), CourageOwnRangeHudRenderer.countColor(3, 0L));
        assertEquals(CourageOwnRangeHudRenderer.rainbow(0L), CourageOwnRangeHudRenderer.countColor(9, 0L));
        assertNotEquals(
                CourageOwnRangeHudRenderer.countColor(3, 0L),
                CourageOwnRangeHudRenderer.countColor(3, RAINBOW_HALF_PERIOD_MS));
    }

    @Test
    void wrapsTheRainbowAtEachPeriodAndAcrossTheEpoch() {
        assertEquals(
                CourageOwnRangeHudRenderer.rainbow(0L),
                CourageOwnRangeHudRenderer.rainbow(RAINBOW_HALF_PERIOD_MS * 2L));
        assertEquals(
                CourageOwnRangeHudRenderer.rainbow(RAINBOW_HALF_PERIOD_MS),
                CourageOwnRangeHudRenderer.rainbow(-RAINBOW_HALF_PERIOD_MS));
    }

    private static Optional<PowderSpecialReading> fire(double charge) {
        return Optional.of(new PowderSpecialReading(true, charge));
    }

    private static Optional<PowderSpecialReading> otherElement(double charge) {
        return Optional.of(new PowderSpecialReading(false, charge));
    }
}
