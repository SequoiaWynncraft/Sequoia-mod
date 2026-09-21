package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.List;
import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;

class WynncraftTextShaderColorTest {
    @Test
    void movesEveryMovementAndShadowMarkerBucketOutOfBand() {
        for (int red = 0; red <= 0xFF; red++) {
            for (int green = 0xE8; green <= 0xEB; green++) {
                for (int blue = 0; blue <= 0x4B; blue++) {
                    int input = red << 16 | green << 8 | blue;
                    int expected = red << 16 | 0xEC << 8 | blue;

                    assertEquals(expected, WynncraftTextShaderColor.safeRgb(input));
                    assertSafeForTextAndDefaultShadow(expected);
                }
            }
        }
    }

    @Test
    void movesEveryDirectQuarterBrightMovementMarkerOutOfBand() {
        for (int red = 0; red <= 0xFF; red++) {
            for (int blue = 0; blue <= 0x12; blue++) {
                int input = red << 16 | 0x3A << 8 | blue;
                int expected = red << 16 | 0x3B << 8 | blue;

                assertEquals(expected, WynncraftTextShaderColor.safeRgb(input));
                assertSafeForTextAndDefaultShadow(expected);
            }
        }
    }

    @Test
    void movesEveryEffectAndShadowMarkerBucketOutOfBand() {
        for (int red = 0; red <= 0x03; red++) {
            for (int green = 0xF0; green <= 0xF3; green++) {
                for (int blue = 0; blue <= 0x27; blue++) {
                    int input = red << 16 | green << 8 | blue;
                    int expected = red << 16 | 0xF4 << 8 | blue;

                    assertEquals(expected, WynncraftTextShaderColor.safeRgb(input));
                    assertSafeForTextAndDefaultShadow(expected);
                }
            }
        }
    }

    @Test
    void movesEveryDirectQuarterBrightEffectMarkerOutOfBand() {
        for (int blue = 0; blue <= 0x09; blue++) {
            int input = 0x003C00 | blue;
            int expected = 0x003D00 | blue;

            assertEquals(expected, WynncraftTextShaderColor.safeRgb(input));
            assertSafeForTextAndDefaultShadow(expected);
        }
    }

    @Test
    void textColorWrapperUsesTheSameSafeValuesIdempotently() {
        int[][] pairs = {
            {0x49EB00, 0x49EC00},
            {0x9AEA13, 0x9AEC13},
            {0x403A00, 0x403B00},
            {0x00F000, 0x00F400},
            {0x03F327, 0x03F427},
            {0x003C00, 0x003D00}
        };
        for (int[] pair : pairs) {
            int input = pair[0];
            int expected = pair[1];

            assertEquals(expected, WynncraftTextShaderColor.safeRgb(input));
            assertEquals(expected, WynncraftTextShaderColor.safeTextColor(input).getValue());
            assertEquals(expected, WynncraftTextShaderColor.safeRgb(expected), "sanitizing must be idempotent");
            assertSafeForTextAndDefaultShadow(expected);
        }
    }

    @Test
    void createsFreshTextColorsForTheIdentityKeyedAnimationRegistry() {
        assertNotSame(
                WynncraftTextShaderColor.safeTextColor(0x49EB00),
                WynncraftTextShaderColor.safeTextColor(0x49EB00));
    }

    @Test
    void leavesNearbyAndOrdinaryColorsUnchanged() {
        for (int rgb : List.of(
                0x40E700,
                0x40EC00,
                0x40EB4C,
                0x40EBFF,
                0x403900,
                0x403B00,
                0x403A13,
                0x04F000,
                0x00EF00,
                0x00F400,
                0x00F028,
                0x013C00,
                0x003B00,
                0x003D00,
                0x003C0A,
                0x40F000,
                0x25FF00,
                0x72D400,
                0xFFFFFF)) {
            assertEquals(rgb, WynncraftTextShaderColor.safeRgb(rgb));
        }
    }

    @Test
    void movesTheReportedGradientCrossingWithoutChangingItsRamp() {
        ColorRamp ramp = ColorRamp.of(List.of(0x25FF00, 0x72D400));
        int sampled = ramp.sample(8d / 17d);
        int shadowOnlySample = ramp.sample(9d / 17d);

        assertEquals(0x49EB00, sampled);
        assertEquals(0x49EC00, WynncraftTextShaderColor.safeRgb(sampled));
        assertEquals(0x4EE800, shadowOnlySample);
        assertEquals(0x4EEC00, WynncraftTextShaderColor.safeRgb(shadowOnlySample));
        assertEquals(List.of(0x25FF00, 0x72D400), ramp.stops());
    }

    @Test
    void movesTheSecondReportedGradientOutOfItsShadowMarkerBucket() {
        ColorRamp ramp = ColorRamp.of(List.of(0x40FF40, 0xC0E100));
        int sampled = ramp.sample(12d / 17d);
        int nextSample = ramp.sample(13d / 17d);

        assertEquals(0x9AEA13, sampled);
        assertEquals(0x9AEC13, WynncraftTextShaderColor.safeRgb(sampled));
        assertEquals(0xA2E80F, nextSample);
        assertEquals(0xA2EC0F, WynncraftTextShaderColor.safeRgb(nextSample));
    }

    @Test
    void escapesMinecraftsQuarterBrightMovementAndEffectBuckets() {
        int movementShadow = ARGB.scaleRGB(0xFF49EB00, 0.25f);
        int safeMovementShadow = ARGB.scaleRGB(0xFF49EC00, 0.25f);
        int effectShadow = ARGB.scaleRGB(0xFF03F327, 0.25f);
        int safeEffectShadow = ARGB.scaleRGB(0xFF03F427, 0.25f);

        assertEquals(0x123A00, movementShadow & 0xFFFFFF);
        assertEquals(0x123B00, safeMovementShadow & 0xFFFFFF);
        assertEquals(0x003C09, effectShadow & 0xFFFFFF);
        assertEquals(0x003D09, safeEffectShadow & 0xFFFFFF);
    }

    /** Ports the two relevant GLSL predicates so every escaped result is checked end to end. */
    private static void assertSafeForTextAndDefaultShadow(int rgb) {
        assertFalse(matchesMovementShader(rgb));
        assertFalse(matchesEffectShader(rgb, shaderTreatsAsShadow(rgb)));

        int shadow = ARGB.scaleRGB(0xFF000000 | rgb, 0.25f) & 0xFFFFFF;
        assertFalse(matchesMovementShader(shadow));
        assertFalse(matchesEffectShader(shadow, shaderTreatsAsShadow(shadow)));
    }

    private static boolean matchesMovementShader(int rgb) {
        int green = rgb >>> 8 & 0xFF;
        int blue = rgb & 0xFF;
        boolean foregroundMarker = green == 235 && blue <= 72 && blue % 4 == 0;
        boolean quarterBrightMarker = green == (235 >> 2) && blue <= (72 >> 2);
        return foregroundMarker || quarterBrightMarker;
    }

    private static boolean shaderTreatsAsShadow(int rgb) {
        return (rgb >>> 16) <= 234 && (rgb >>> 8 & 0xFF) <= 234 && (rgb & 0xFF) <= 234;
    }

    private static boolean matchesEffectShader(int rgb, boolean shadow) {
        int red = rgb >>> 16;
        int green = rgb >>> 8 & 0xFF;
        int blue = rgb & 0xFF;
        if (!shadow) {
            red /= 4;
            green /= 4;
            blue /= 4;
        }
        return red == 0 && green == 240 / 4 && blue <= 36 / 4;
    }
}
