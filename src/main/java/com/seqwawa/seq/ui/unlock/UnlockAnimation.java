package com.seqwawa.seq.ui.unlock;

import com.seqwawa.seq.ui.unlock.UnlockPopup.Kind;

/**
 * Where each popup is in its animation at a moment. Pure, so it is tested
 * without a game. Movement happens in whole art pixels, as pixel art moves:
 * a slide is a few steps, the medal pops through whole scales.
 */
public final class UnlockAnimation {

    private UnlockAnimation() {}

    static final long TOAST_MS = 3_200;
    static final long CARD_MS = 4_600;
    static final long CEREMONY_MS = 6_400;

    /** How long the title takes to type itself, per letter. */
    static final long TYPE_MS_PER_LETTER = 40;

    public static long duration(Kind kind) {
        return switch (kind) {
            case TOAST -> TOAST_MS;
            case CARD -> CARD_MS;
            case CEREMONY -> CEREMONY_MS;
        };
    }

    /**
     * @param opacity how visible the whole popup is
     * @param slide how far it still is from its place, from 1 (off screen) to 0 (in place), in steps
     * @param medalScale the medal's whole-number scale, 0 while it has not appeared
     * @param counter how far the Leaf amount has counted up, from 0 to 1
     * @param backdrop how dark the ceremony's backdrop is, from 0 to 1
     * @param raysFrame which of the eight ray frames shows
     * @param bob 1 every other beat, so small icons hop by one pixel
     */
    public record Frame(
            boolean active,
            float opacity,
            float slide,
            int medalScale,
            float counter,
            float backdrop,
            int raysFrame,
            int bob) {
        static final Frame INACTIVE = new Frame(false, 0, 1, 0, 0, 0, 0, 0);
    }

    public static Frame frame(Kind kind, long elapsedMs) {
        long total = duration(kind);
        if (elapsedMs < 0 || elapsedMs >= total) return Frame.INACTIVE;
        int bob = (int) (elapsedMs / 300 % 2);
        return switch (kind) {
            case TOAST -> new Frame(true,
                    fadeOut(elapsedMs, total, 300),
                    Math.max(steps(1 - progress(elapsedMs, 0, 200), 5), steps(progress(elapsedMs, total - 250, total), 5)),
                    0,
                    progress(elapsedMs, 150, 850),
                    0, 0, bob);
            case CARD -> new Frame(true,
                    fadeOut(elapsedMs, total, 400),
                    cardSlide(elapsedMs),
                    0,
                    progress(elapsedMs, 450, 1_350),
                    0, 0, bob);
            case CEREMONY -> new Frame(true,
                    fadeOut(elapsedMs, total, 600),
                    0,
                    medalScale(elapsedMs),
                    progress(elapsedMs, 1_200, 2_400),
                    Math.min(1f, elapsedMs / 400f) * fadeOut(elapsedMs, total, 600),
                    (int) (elapsedMs / 90 % 8),
                    bob);
        };
    }

    /** When the medal lands, at its overshoot: the flash, the shake and the shockwave start here. */
    static final long LAND_MS = 270;
    static final long RING_FRAME_MS = 60;
    static final int RING_FRAMES = 6;

    /** The medal shows as a white silhouette for the first beat after it lands. */
    static boolean flashing(long elapsedMs) {
        return elapsedMs >= LAND_MS && elapsedMs < LAND_MS + 70;
    }

    /** A small shake on landing, in art pixels: four beats, then still. */
    static int[] shake(long elapsedMs) {
        if (elapsedMs < LAND_MS || elapsedMs >= LAND_MS + 160) return new int[] {0, 0};
        int[][] beats = {{1, 0}, {-1, 1}, {0, -1}, {1, 1}};
        return beats[(int) ((elapsedMs - LAND_MS) / 40 % beats.length)];
    }

    /** Which shockwave frame shows, or -1 when there is none. */
    static int ringFrame(long elapsedMs) {
        if (elapsedMs < LAND_MS) return -1;
        long frame = (elapsedMs - LAND_MS) / RING_FRAME_MS;
        return frame < RING_FRAMES ? (int) frame : -1;
    }

    /** The second, slower ray layer turns the other way. */
    static int counterRaysFrame(long elapsedMs) {
        return 7 - (int) (elapsedMs / 130 % 8);
    }

    /** How far the title banner has unrolled, in eight steps, as the title starts to type. */
    static float bannerOpen(long elapsedMs) {
        return steps(progress(elapsedMs, 380, 560), 8);
    }

    /**
     * Where a rising ember is, as a fraction of the screen: it drifts up from below
     * the bottom, sways, and starts again. Null while it is between flickers.
     */
    static float[] ember(int seed, long elapsedMs) {
        float t = elapsedMs / 1000f;
        float x = (seed * 0.61803398875f) % 1f;
        float speed = 0.16f + (seed * 7 % 11) * 0.02f;
        float y = 1.08f - ((t * speed + (seed * 0.37f) % 1f) % 1.16f);
        x += (float) Math.sin(t * 2.2f + seed) * 0.012f;
        boolean lit = (elapsedMs / 110 + seed) % 6 != 0;
        return lit ? new float[] {x, y} : null;
    }

    /** Drops in from above in four steps, overshoots by one step, settles. */
    static float cardSlide(long elapsedMs) {
        if (elapsedMs < 60) return 1f;
        if (elapsedMs < 120) return 0.75f;
        if (elapsedMs < 180) return 0.5f;
        if (elapsedMs < 240) return 0.25f;
        if (elapsedMs < 300) return -0.1f;
        return 0f;
    }

    /** Pops through whole scales: nothing, 1, 2, 4 (the overshoot), then rests at 3. */
    static int medalScale(long elapsedMs) {
        if (elapsedMs < 150) return 0;
        if (elapsedMs < 210) return 1;
        if (elapsedMs < 270) return 2;
        if (elapsedMs < 380) return 4;
        return 3;
    }

    /** How many letters of a ceremony title show: they type in after the medal lands. */
    static int typedLetters(long elapsedMs, int length) {
        if (elapsedMs < 450) return 0;
        return (int) Math.min(length, (elapsedMs - 450) / TYPE_MS_PER_LETTER + 1);
    }

    /** Where a falling leaf is, in art pixels from where it starts: thrown up and out, then falling. */
    static int[] leafOffset(int seed, long elapsedMs) {
        float t = Math.max(0, elapsedMs - 300) / 1000f;
        float angle = (float) (-Math.PI / 2 + ((seed * 0.61803398875f) % 1f - 0.5f) * 2.2f);
        float speed = 34 + (seed * 7 % 13);
        float x = (float) Math.cos(angle) * speed * t + (float) Math.sin(t * 3 + seed) * 3;
        float y = (float) Math.sin(angle) * speed * t + 26 * t * t;
        return new int[] {Math.round(x), Math.round(y)};
    }

    /** Whole art-pixel steps, so a slide moves like pixel art instead of gliding. */
    static float steps(float value, int count) {
        return (float) Math.ceil(Math.max(0, Math.min(1, value)) * count) / count;
    }

    static float progress(long elapsedMs, long from, long to) {
        if (elapsedMs <= from) return 0f;
        if (elapsedMs >= to) return 1f;
        float t = (float) (elapsedMs - from) / (to - from);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    static float fadeOut(long elapsedMs, long total, long length) {
        return elapsedMs < total - length ? 1f : Math.max(0f, (total - elapsedMs) / (float) length);
    }
}
