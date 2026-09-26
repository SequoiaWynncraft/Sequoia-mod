package com.seqwawa.seq.utils;

import net.minecraft.network.chat.TextColor;

/**
 * Prevents ordinary rank palettes from accidentally selecting one of Wynncraft's
 * resource-pack text shaders.
 *
 * <p>Wynncraft's resource pack has two relevant marker families. Effects divide all
 * three channels by four, so source colours with red {@code 0..3}, green
 * {@code 240..243}, and blue {@code 0..39} select one of its configured effects.
 * Movements ignore red and match green {@code 235} with every fourth blue value
 * through {@code 72}. Minecraft draws text shadows at quarter brightness, broadening
 * that movement family to source green {@code 232..235} and blue {@code 0..75}. The
 * shaders also accept their already quarter-bright encodings directly: movement green
 * {@code 58} with blue {@code 0..18}, and effect red {@code 0}, green {@code 60},
 * with blue {@code 0..9}.
 *
 * <p>A matched colour is moved to the next green quantisation bucket. This is visually
 * negligible, deterministic, and idempotent, and it also escapes the quarter-bright
 * shadow marker. The source palette and its interpolation stay untouched.
 *
 * @see <a href="https://rp-cdn.wynncraft.com/PRODUCTION_afc9c1319759bfa97738a704083971f12cbb4493.zip">Wynncraft resource pack inspected on 2026-08-27</a>
 */
public final class WynncraftTextShaderColor {
    private static final int RGB_MASK = 0xFFFFFF;
    private static final int RED_BLUE_MASK = 0xFF00FF;

    private static final int MOVEMENT_GREEN_MIN = 0xE8;
    private static final int MOVEMENT_GREEN_MAX = 0xEB;
    private static final int MOVEMENT_BLUE_MAX = 0x4B;
    private static final int SAFE_MOVEMENT_GREEN = 0xEC;
    private static final int DIRECT_MOVEMENT_GREEN = 0x3A;
    private static final int DIRECT_MOVEMENT_BLUE_MAX = 0x12;
    private static final int SAFE_DIRECT_MOVEMENT_GREEN = 0x3B;

    private static final int EFFECT_RED_MAX = 0x03;
    private static final int EFFECT_GREEN_MIN = 0xF0;
    private static final int EFFECT_GREEN_MAX = 0xF3;
    private static final int EFFECT_BLUE_MAX = 0x27;
    private static final int SAFE_EFFECT_GREEN = 0xF4;
    private static final int DIRECT_EFFECT_GREEN = 0x3C;
    private static final int DIRECT_EFFECT_BLUE_MAX = 0x09;
    private static final int SAFE_DIRECT_EFFECT_GREEN = 0x3D;

    private WynncraftTextShaderColor() {}

    /** Creates a Minecraft text colour after moving any shader marker out of band. */
    public static TextColor safeTextColor(int rgb) {
        return TextColor.fromRgb(safeRgb(rgb));
    }

    static int safeRgb(int rgb) {
        int normalized = rgb & RGB_MASK;
        int red = normalized >>> 16;
        int green = normalized >>> 8 & 0xFF;
        int blue = normalized & 0xFF;

        if (isMovementMarkerBucket(green, blue)) {
            return withGreen(normalized, SAFE_MOVEMENT_GREEN);
        }
        if (isDirectMovementMarker(green, blue)) {
            return withGreen(normalized, SAFE_DIRECT_MOVEMENT_GREEN);
        }
        if (isEffectMarkerBucket(red, green, blue)) {
            return withGreen(normalized, SAFE_EFFECT_GREEN);
        }
        if (isDirectEffectMarker(red, green, blue)) {
            return withGreen(normalized, SAFE_DIRECT_EFFECT_GREEN);
        }
        return normalized;
    }

    /**
     * Whether blending from {@code from} to {@code to} passes through a marker, as the
     * GPU blends a glyph whose corners are coloured differently.
     * <p>
     * Wynncraft's text shader tests colours per pixel too, not only per corner: a pixel
     * whose blended colour lands on a movement marker is painted white. Two safe corner
     * colours are therefore not enough; every colour between them, rounded per channel
     * as the shader rounds it, has to stay clear of the marker families above.
     */
    public static boolean crossesMarker(int from, int to) {
        return crosses(from, to, 0, 0xFF, MOVEMENT_GREEN_MIN, MOVEMENT_GREEN_MAX, MOVEMENT_BLUE_MAX)
                || crosses(from, to, 0, 0xFF, DIRECT_MOVEMENT_GREEN, DIRECT_MOVEMENT_GREEN, DIRECT_MOVEMENT_BLUE_MAX)
                || crosses(from, to, 0, EFFECT_RED_MAX, EFFECT_GREEN_MIN, EFFECT_GREEN_MAX, EFFECT_BLUE_MAX)
                || crosses(from, to, 0, 0, DIRECT_EFFECT_GREEN, DIRECT_EFFECT_GREEN, DIRECT_EFFECT_BLUE_MAX);
    }

    /**
     * Whether the blend from {@code from} to {@code to} has a point whose rounded red,
     * green and blue all fall in the given ranges, blue counting up from zero. Each
     * channel narrows down the stretch of the blend where it is in range, and a marker
     * is only reached where all three stretches overlap.
     */
    private static boolean crosses(int from, int to, int redMin, int redMax, int greenMin, int greenMax, int blueMax) {
        double[] window = {0d, 1d};
        return narrow(window, from >>> 16 & 0xFF, to >>> 16 & 0xFF, redMin, redMax)
                && narrow(window, from >>> 8 & 0xFF, to >>> 8 & 0xFF, greenMin, greenMax)
                && narrow(window, from & 0xFF, to & 0xFF, 0, blueMax);
    }

    /** Narrows {@code window} to where a channel blending from {@code a} to {@code b} rounds into range. */
    private static boolean narrow(double[] window, int a, int b, int min, int max) {
        if (a == b) {
            return a >= min && a <= max;
        }
        // A shader rounds to the nearest level, so everything within half a level counts.
        double start = (min - 0.5d - a) / (b - a);
        double end = (max + 0.5d - a) / (b - a);
        window[0] = Math.max(window[0], Math.min(start, end));
        window[1] = Math.min(window[1], Math.max(start, end));
        return window[0] <= window[1];
    }

    private static int withGreen(int rgb, int green) {
        return (rgb & RED_BLUE_MASK) | (green << 8);
    }

    private static boolean isMovementMarkerBucket(int green, int blue) {
        return green >= MOVEMENT_GREEN_MIN && green <= MOVEMENT_GREEN_MAX && blue <= MOVEMENT_BLUE_MAX;
    }

    private static boolean isDirectMovementMarker(int green, int blue) {
        return green == DIRECT_MOVEMENT_GREEN && blue <= DIRECT_MOVEMENT_BLUE_MAX;
    }

    private static boolean isEffectMarkerBucket(int red, int green, int blue) {
        return red <= EFFECT_RED_MAX
                && green >= EFFECT_GREEN_MIN
                && green <= EFFECT_GREEN_MAX
                && blue <= EFFECT_BLUE_MAX;
    }

    private static boolean isDirectEffectMarker(int red, int green, int blue) {
        return red == 0 && green == DIRECT_EFFECT_GREEN && blue <= DIRECT_EFFECT_BLUE_MAX;
    }
}
