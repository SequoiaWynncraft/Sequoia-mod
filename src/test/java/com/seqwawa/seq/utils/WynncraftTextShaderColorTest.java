package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
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
    @Test
    void detectsABlendThatPassesThroughAMovementMarker() {
        // Both ends are safe, but the pixels between them run through green 235.
        int start = 0x10F010;
        int end = 0x10D810;

        assertEquals(start, WynncraftTextShaderColor.safeRgb(start));
        assertEquals(end, WynncraftTextShaderColor.safeRgb(end));
        assertTrue(WynncraftTextShaderColor.crossesMarker(start, end));
        assertTrue(WynncraftTextShaderColor.crossesMarker(end, start), "whichever way it runs");
    }

    @Test
    void detectsAShadowBlendThatPassesThroughTheQuarterBrightMarker() {
        assertTrue(WynncraftTextShaderColor.crossesMarker(0x043B04, 0x043904));
    }

    @Test
    void detectsABlendThatPassesThroughAnEffectMarker() {
        assertTrue(WynncraftTextShaderColor.crossesMarker(0x00F600, 0x00EE00));
        assertFalse(WynncraftTextShaderColor.crossesMarker(0x0AF600, 0x0AEE00), "too much red to be one");
    }

    @Test
    void letsABlendThroughWhenItNeverMeetsAMarker() {
        assertFalse(WynncraftTextShaderColor.crossesMarker(0x10F060, 0x10D860), "too much blue at green 235");
        assertFalse(WynncraftTextShaderColor.crossesMarker(0x000000, 0xFFFFFF), "a grey ramp");
        assertFalse(WynncraftTextShaderColor.crossesMarker(0x10F010, 0x10EE10), "stops short of the band");
    }

    @Test
    void noPixelOfABlendItLetsThroughSetsOffTheShader() {
        // Blends drawn near every marker family; wherever this says a blend is clear,
        // every pixel along it, rounded as the shader rounds, must be clear too.
        Random random = new Random(20260926L);
        int checked = 0;
        for (int sample = 0; sample < 20_000; sample++) {
            int from = nearMarker(random);
            int to = nearMarker(random);
            if (WynncraftTextShaderColor.crossesMarker(from, to)) {
                continue;
            }
            checked++;
            for (int step = 0; step <= 256; step++) {
                int blended = blend(from, to, step / 256d);
                assertFalse(
                        matchesMovementShader(blended) || matchesEffectShader(blended, shaderTreatsAsShadow(blended)),
                        String.format("%06X to %06X lets %06X through", from, to, blended));
            }
        }
        assertTrue(checked > 1000, "enough clear blends were tried, was " + checked);
    }

    /** A colour close to one of the marker families, so blends between them often meet one. */
    private static int nearMarker(Random random) {
        int green = switch (random.nextInt(3)) {
            case 0 -> 220 + random.nextInt(30);
            case 1 -> 230 + random.nextInt(20);
            default -> 50 + random.nextInt(16);
        };
        int red = random.nextBoolean() ? random.nextInt(6) : random.nextInt(256);
        int blue = random.nextInt(100);
        return WynncraftTextShaderColor.safeRgb(red << 16 | green << 8 | blue);
    }

    /** The colour the GPU hands the shader {@code t} of the way along, rounded per channel. */
    private static int blend(int from, int to, double t) {
        int color = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            double channel = (from >>> shift & 0xFF) * (1 - t) + (to >>> shift & 0xFF) * t;
            color |= (int) Math.floor(channel + 0.5) << shift;
        }
        return color;
    }

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
