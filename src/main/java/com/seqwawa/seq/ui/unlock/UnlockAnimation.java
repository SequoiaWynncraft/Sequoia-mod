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
     * @param burstFrame which of the four burst frames shows
     * @param bob 1 every other beat, so small icons hop by one pixel
     */
    public record Frame(
            boolean active,
            float opacity,
            float slide,
            int medalScale,
            float counter,
            float backdrop,
            int burstFrame,
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
                    (int) (elapsedMs / 110 % 4),
                    bob);
        };
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
