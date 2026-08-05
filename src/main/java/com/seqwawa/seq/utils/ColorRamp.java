package com.seqwawa.seq.utils;

import java.util.List;

/**
 * An ordered list of {@code 0xRRGGBB} stops sampled by position, used to paint a
 * Discord role's gradient across the glyphs of a chat rank pill or speaker name.
 * <p>
 * Minecraft gives a text component a single colour, so a gradient can only be
 * drawn by splitting the text and colouring each piece. Wynncraft's pills are
 * are therefore built one glyph at a time where a gradient is required.
 */
public final class ColorRamp {

    private static final ColorRamp EMPTY = new ColorRamp(List.of());

    private final List<Integer> stops;

    private ColorRamp(List<Integer> stops) {
        this.stops = stops;
    }

    /** A ramp over {@code stops}, or the empty ramp when there are none. */
    public static ColorRamp of(List<Integer> stops) {
        if (stops == null || stops.isEmpty()) {
            return EMPTY;
        }
        return new ColorRamp(List.copyOf(stops));
    }

    /** A ramp of one stop, i.e. a solid colour. */
    public static ColorRamp of(int rgb) {
        return new ColorRamp(List.of(rgb));
    }

    /** The ramp of an uncoloured role, which cannot be sampled. */
    public static ColorRamp empty() {
        return EMPTY;
    }

    /** True when no colour is published, so the caller must supply a fallback. */
    public boolean isEmpty() {
        return stops.isEmpty();
    }

    /** True when there is more than one stop, i.e. sampling actually varies. */
    public boolean isGradient() {
        return stops.size() > 1;
    }

    /** The stops in Discord's order. */
    public List<Integer> stops() {
        return stops;
    }

    /** The first stop, which is the role's primary colour. */
    public int first() {
        return stops.getFirst();
    }

    /**
     * The colour at {@code position} in {@code [0, 1]}, interpolated between the two
     * surrounding stops. Positions outside the range clamp to the end stops.
     *
     * @throws IllegalStateException when the ramp is empty, i.e. the role is
     *                               uncoloured and the caller should have substituted
     *                               a fallback before painting anything
     */
    public int sample(double position) {
        if (stops.isEmpty()) {
            throw new IllegalStateException("An uncoloured ramp has no colour to sample");
        }
        if (stops.size() == 1) {
            return stops.getFirst();
        }

        double clamped = Math.clamp(position, 0d, 1d);
        double scaled = clamped * (stops.size() - 1);
        int index = Math.min((int) scaled, stops.size() - 2);
        return blend(stops.get(index), stops.get(index + 1), scaled - index);
    }

    /**
     * The colour at {@code position} once the ramp has been scrolled {@code phase}
     * turns along itself, which is how a gradient role is animated.
     * <p>
     * Scrolling runs the ramp as a loop: past the last stop it returns to the first, so
     * a phase that advances with time never reaches an end to stop at and there is no
     * seam where it wraps. That closing run is a segment of its own, which is why a
     * phase of one is a full turn over {@code stops.size()} segments rather than over
     * the {@code stops.size() - 1} a static sweep covers.
     * <p>
     * A phase of zero reproduces {@link #sample} exactly, so the animation can be
     * turned on and off without the pill jumping.
     *
     * @throws IllegalStateException when the ramp is empty, as for {@link #sample}
     */
    public int scroll(double position, double phase) {
        if (stops.isEmpty()) {
            throw new IllegalStateException("An uncoloured ramp has no colour to sample");
        }
        if (stops.size() == 1) {
            return stops.getFirst();
        }

        double segments = stops.size();
        double scaled = Math.clamp(position, 0d, 1d) * (stops.size() - 1) + phase * segments;
        double wrapped = scaled - Math.floor(scaled / segments) * segments;
        int index = Math.min((int) wrapped, stops.size() - 1);
        return blend(stops.get(index), stops.get((index + 1) % stops.size()), wrapped - index);
    }

    /** Mixes {@code target} over {@code base}, {@code weight} being the share of target. */
    public static int blend(int base, int target, double weight) {
        return channel(base, target, weight, 16) << 16
                | channel(base, target, weight, 8) << 8
                | channel(base, target, weight, 0);
    }

    private static int channel(int base, int target, double weight, int shift) {
        double mixed = ((base >> shift) & 0xFF) * (1 - weight) + ((target >> shift) & 0xFF) * weight;
        return Math.clamp(Math.round(mixed), 0, 255);
    }
}
