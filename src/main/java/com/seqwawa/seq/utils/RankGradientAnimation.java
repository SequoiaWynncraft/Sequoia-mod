package com.seqwawa.seq.utils;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.network.chat.TextColor;

/**
 * Scrolls a gradient rank's colours along its chat pill, so a role whose colour is a
 * Discord gradient reads as one rather than as a fixed smear.
 * <p>
 * A chat line is drawn from a component that was built once, long before the frame it
 * appears on, so a colour cannot be animated by rebuilding it. What survives into
 * rendering is the {@link TextColor} instance itself: {@code TextColor.fromRgb} mints a
 * new one per call and every step from component to glyph copies the reference rather
 * than the value. Each pill colour is therefore recognisable by identity, and the
 * remembered stops below say where on which ramp it was sampled from.
 * <p>
 * The lookup runs for every glyph the game draws (see {@code
 * FontPreparedTextBuilderMixin}), so it is kept to a setting check and one identity
 * probe that misses on ordinary text.
 */
public final class RankGradientAnimation {

    /** One full turn around a role's ramp, slow enough to read as a sheen. */
    private static final long CYCLE_MILLIS = 5000L;

    /**
     * How many pill colours stay animatable. A pill contributes one stop per glyph, so
     * this covers a few dozen messages; pills older than that simply hold still rather
     * than being redrawn, which is invisible so far up the chat log.
     */
    private static final int MAX_REMEMBERED_STOPS = 512;

    /** Where a remembered colour was sampled from, and on which ramp. */
    private record Stop(ColorRamp ramp, double position) {}

    /** Registration order, so the stops dropped on overflow are the oldest ones. */
    private static final ArrayDeque<TextColor> REGISTRATION_ORDER = new ArrayDeque<>();

    /**
     * Published to the render thread by replacement rather than by mutation. It is read
     * once per drawn glyph, and a lock there would be paid by every piece of text the
     * game draws, not just by chat.
     */
    private static volatile Map<TextColor, Stop> stops = new IdentityHashMap<>();

    private RankGradientAnimation() {}

    /**
     * The pill colour at {@code position} along {@code ramp}, remembered so it can be
     * moved later.
     * <p>
     * Only gradient roles are remembered: a solid role has one colour, and scrolling it
     * would produce that same colour back again.
     */
    public static TextColor colorAt(ColorRamp ramp, double position) {
        TextColor color = TextColor.fromRgb(ramp.sample(position));
        if (ramp.isGradient()) {
            remember(color, new Stop(ramp, position));
        }
        return color;
    }

    /**
     * The colour {@code color} should be drawn as at this instant, or {@code color}
     * itself when it is not a remembered gradient stop or the animation is off.
     */
    public static TextColor animate(TextColor color) {
        return animate(color, phase());
    }

    /** Animation core, parameterised on the phase so it stays unit-testable. */
    static TextColor animate(TextColor color, double phase) {
        if (color == null || !isEnabled()) {
            return color;
        }
        Stop stop = stops.get(color);
        return stop == null ? color : TextColor.fromRgb(stop.ramp().scroll(stop.position(), phase));
    }

    /** How far through the current turn the clock is, in {@code [0, 1)}. */
    private static double phase() {
        return Math.floorMod(System.nanoTime() / 1_000_000L, CYCLE_MILLIS) / (double) CYCLE_MILLIS;
    }

    private static boolean isEnabled() {
        Setting.BooleanSetting setting = SeqClient.getAnimateRankGradientsSetting();
        return setting != null && setting.getValue();
    }

    private static synchronized void remember(TextColor color, Stop stop) {
        IdentityHashMap<TextColor, Stop> updated = new IdentityHashMap<>(stops);
        updated.put(color, stop);
        REGISTRATION_ORDER.addLast(color);
        while (REGISTRATION_ORDER.size() > MAX_REMEMBERED_STOPS) {
            updated.remove(REGISTRATION_ORDER.removeFirst());
        }
        stops = updated;
    }
}
