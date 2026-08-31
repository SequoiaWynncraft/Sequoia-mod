package com.seqwawa.seq.raids.tna;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.config.Setting;
import org.junit.jupiter.api.Test;

class TnaBeamIndicatorHudRendererTest {
    @Test
    void enableSettingDefaultsOnAndCanDisableTheHud() {
        assertTrue(TnaBeamIndicatorHudRenderer.isEnabled(null));
        assertTrue(TnaBeamIndicatorHudRenderer.isEnabled(
                new Setting.BooleanSetting("test", "test", true)));
        assertFalse(TnaBeamIndicatorHudRenderer.isEnabled(
                new Setting.BooleanSetting("test", "test", false)));
    }

    @Test
    void sizeSettingFallsBackAndClamps() {
        assertEquals(1f, TnaBeamIndicatorHudRenderer.sizeScale(null));
        assertEquals(0.25f, TnaBeamIndicatorHudRenderer.sizeScale(
                new Setting.IntSetting("test", "test", 25, 25, 400, 5)));
        assertEquals(1.5f, TnaBeamIndicatorHudRenderer.sizeScale(
                new Setting.IntSetting("test", "test", 150, 25, 400, 5)));
        assertEquals(4f, TnaBeamIndicatorHudRenderer.sizeScale(
                new Setting.IntSetting("test", "test", 400, 25, 400, 5)));
    }

    @Test
    void positionsAndClampsTheDraggableIndicator() {
        assertEquals(
                new TnaBeamIndicatorHudRenderer.Bounds(76f, 36f, 48f, 28f),
                TnaBeamIndicatorHudRenderer.positionBounds(200f, 100f, 0.5f, 0.5f, 1f));
        assertEquals(
                new TnaBeamIndicatorHudRenderer.Bounds(52f, 22f, 96f, 56f),
                TnaBeamIndicatorHudRenderer.positionBounds(200f, 100f, 0.5f, 0.5f, 2f));
        assertEquals(
                new TnaBeamIndicatorHudRenderer.Position(0f, 1f),
                TnaBeamIndicatorHudRenderer.positionForTopLeft(200f, 100f, -20f, 500f));
    }
}
