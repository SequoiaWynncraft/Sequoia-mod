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
