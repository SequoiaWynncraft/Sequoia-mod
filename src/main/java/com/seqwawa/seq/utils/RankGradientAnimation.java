package com.seqwawa.seq.utils;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.network.chat.TextColor;

/**
 * Paints a gradient rank's colours along its pill and speaker name, and scrolls them,
 * so a role whose colour is a Discord gradient reads as one rather than as a fixed smear.
 * <p>
 * A chat line or nametag is drawn from a component built once, long before the frame
 * it appears on, so a colour cannot be animated by rebuilding it. What survives into
 * rendering is the {@link TextColor} instance itself: {@code TextColor.fromRgb} mints a
 * new one per call and every step from component to glyph copies the reference rather
 * than the value. Each decoration colour is therefore recognisable by identity, and the
 * remembered stop behind it says where it sits on which gradient.
 * <p>
 * Where a glyph sits is measured in font pixels along an {@link Axis}: a pill's
 * background, or a name. At render time a glyph's corners are coloured by where they
 * fall on that axis ({@link #shade}), so the gradient runs smoothly across and between
 * glyphs, and moves at the same speed in pixels whatever it is painted on. A pill and
 * the name after it can be {@link #join joined}, and then read as one gradient whenever
 * both show theirs.
 * <p>
 * Every lookup here runs for glyphs the game draws, most of which are not decorations,
 * so each starts with one identity probe and only consults settings on a hit.
 * Registration and rendering both happen on the render thread, so the registry is
 * updated in place rather than republished.
 */
public final class RankGradientAnimation {

    /** Independently configurable places where a Discord role gradient is rendered. */
    public enum Target {
        RANK_BADGE,
        USERNAME
    }

    /**
     * How fast an animated gradient travels at 100% speed, in font pixels per second. A
     * speed rather than a period, so a short pill and a longer name move together: a
     * fixed period had the longer of the two race past the other. The speed setting
     * scales it.
     */
    static final double PIXELS_PER_SECOND = 20d;

    /** How far animated gradients have travelled, in font pixels, as of {@link #travelledAt}. */
    private static double travelledPixels;
    private static long travelledAt = Long.MIN_VALUE;

    /**
     * How many rank-decoration colours stay configurable. A pill contributes one per
     * letter and a name one per glyph, so this covers a full chat history; older
     * decorations simply keep their stored colour rather than being rebuilt.
     */
    static final int MAX_REMEMBERED_STOPS = 4096;

    /** Registered decoration colours. Written under the class lock, read without one. */
    private static final Map<TextColor, Stop> STOPS = new IdentityHashMap<>();

    /** Registration order, so the stops dropped on overflow are the oldest ones. */
    private static final ArrayDeque<TextColor> REGISTRATION_ORDER = new ArrayDeque<>();

    /** Fixed-colour glyphs that are part of a decoration but do not animate. */
    private static volatile Map<TextColor, Boolean> fixedDecorationColors = new IdentityHashMap<>();

    /**
     * Registrations made while one decoration is being built. Nested batches share the
     * outer list, so composing a pill and a name still registers once.
     */
    private static final ThreadLocal<Batch> PENDING_REGISTRATIONS = new ThreadLocal<>();

    /** Monotonic registration count, exposed package-locally for the batching test. */
    private static long publicationCount;

    /** The phase every axis is at now, from how far a gradient has travelled. */
    private static final Clock WALL_CLOCK =
            (ramp, length) -> phaseAt(ramp, length, travelled(System.nanoTime() / 1_000_000L));

    private RankGradientAnimation() {}

    /** Turns a ramp and an axis length into how far round the ramp it has scrolled. */
    @FunctionalInterface
    interface Clock {
        double phase(ColorRamp ramp, double length);
    }

    /**
     * A run of glyphs sharing one gradient, measured in font pixels from its first pixel
     * to its last: a pill's background, or a name.
     */
    public static final class Axis {
        private final ColorRamp displayRamp;
        private final ColorRamp roleRamp;
        private final Target target;
        private final float length;
        /** Where this axis starts on the one it was joined into, and how long that is. */
        private float jointOffset;
        private float jointLength = Float.NaN;

        private Axis(ColorRamp displayRamp, ColorRamp roleRamp, Target target, float length) {
            this.displayRamp = Objects.requireNonNull(displayRamp, "displayRamp");
            this.roleRamp = Objects.requireNonNull(roleRamp, "roleRamp");
            this.target = Objects.requireNonNull(target, "target");
            this.length = Math.max(1f, length);
        }

        /**
         * The colour of a glyph drawn at {@code offset} pixels along this axis and
         * {@code width} pixels wide, remembered so it can be graded and moved later.
         * {@code baseColor} is what it returns to when role colouring is switched off;
         * {@code null} restores Minecraft's inherited text colour.
         */
        public TextColor colorAt(float offset, float width, TextColor baseColor) {
            float center = offset + Math.max(0f, width) / 2f;
            TextColor color = WynncraftTextShaderColor.safeTextColor(displayRamp.sample(center / length));
            remember(color, new Stop(this, offset, center, baseColor));
            return color;
        }

        public float length() {
            return length;
        }

        private boolean joined() {
            return !Float.isNaN(jointLength);
        }
    }

    /** An axis {@code length} font pixels long, painted with a member's palettes. */
    public static Axis axis(ColorRamp displayRamp, ColorRamp roleRamp, Target target, float length) {
        return new Axis(displayRamp, roleRamp, target, length);
    }

    /**
     * Makes {@code first} and {@code second} one gradient whenever both show theirs: a
     * pill and the name after it. {@code gap} is how many pixels lie between the end
     * of {@code first} and the start of {@code second}.
     */
    public static void join(Axis first, Axis second, float gap) {
        float length = first.length + Math.max(0f, gap) + second.length;
        first.jointOffset = 0f;
        first.jointLength = length;
        second.jointOffset = length - second.length;
        second.jointLength = length;
    }

    /** Where one glyph sits on its axis, what it returns to, and its last flat colour. */
    private static final class Stop {
        final Axis axis;
        final float offset;
        final float center;
        final TextColor baseColor;
        /** The flat colour last worked out for {@link #memoMode}, so it is not re-minted. */
        int memoMode = -1;
        TextColor memoColor;

        Stop(Axis axis, float offset, float center, TextColor baseColor) {
            this.axis = axis;
            this.offset = offset;
            this.center = center;
            this.baseColor = baseColor;
        }
    }

    /**
     * How to colour one glyph's corners along its gradient: {@code origin} is where the
     * glyph starts on an axis {@code length} long, and {@code phase} how far round the
     * ramp it has scrolled, or {@code NaN} when it is still.
     */
    public record Shade(ColorRamp ramp, double origin, double length, double phase) {

        /** The colour {@code x} font pixels from the glyph's origin, as RGB. */
        public int rgbAt(float x) {
            double position = (origin + x) / length;
            return WynncraftTextShaderColor.safeRgb(
                    Double.isNaN(phase) ? ramp.sample(position) : ramp.scroll(position, phase));
        }
    }

    /**
     * Builds one decoration while collecting all of its colours, then registers them
     * together. Calls may be nested: only the outermost call registers. A pinned build
     * cannot be nested inside this evictable batch because no caller would receive
     * ownership of its colours.
     */
    public static <T> T batchRegistrations(Supplier<T> build) {
        return batch(build, false).value();
    }

    /**
     * Builds a decoration whose colours are exempt from the eviction below, and hands
     * them back so the caller can release them when the decoration is dropped.
     * <p>
     * For decorations that stay on screen indefinitely, such as an in-world nametag.
     * An evicted colour stops responding to the settings and reverts to the value it
     * was sampled at, and since eviction takes one glyph at a time, a long-lived
     * decoration would come apart into differently coloured pieces as chat pushes its
     * stops out — which reads as flickering rather than as a colour change.
     */
    public static <T> Pinned<T> pin(Supplier<T> build) {
        return batch(build, true);
    }

    /**
     * A decoration and the colours registered for it, which stay configurable until
     * they are handed back to {@link #release}.
     */
    public record Pinned<T>(T value, List<TextColor> colors) {}

    private record Registration(TextColor color, Stop stop) {}

    /** Colours collected for one decoration, and whether they are exempt from eviction. */
    private record Batch(List<Registration> registrations, boolean pinned) {}

    private static <T> Pinned<T> batch(Supplier<T> build, boolean pinned) {
        Objects.requireNonNull(build, "build");
        Batch outer = PENDING_REGISTRATIONS.get();
        if (outer != null) {
            if (pinned && !outer.pinned()) {
                throw new IllegalStateException("A pinned decoration cannot be built inside an evictable batch");
            }
            // An inner batch: the outermost call owns the registration and the colours.
            return new Pinned<>(build.get(), List.of());
        }

        Batch pending = new Batch(new ArrayList<>(), pinned);
        PENDING_REGISTRATIONS.set(pending);
        try {
            T result = build.get();
            rememberAll(pending.registrations(), pinned);
            return new Pinned<>(
                    result, pending.registrations().stream().map(Registration::color).toList());
        } finally {
            PENDING_REGISTRATIONS.remove();
        }
    }

    /** Forgets pinned colours, so the decoration they belonged to stops being tracked. */
    public static void release(List<TextColor> colors) {
        if (colors == null || colors.isEmpty()) {
            return;
        }
        releaseAll(List.of(colors));
    }

    /** Forgets several pinned decorations at once. */
    public static synchronized void releaseAll(Iterable<? extends Iterable<TextColor>> colorGroups) {
        if (colorGroups == null) {
            return;
        }
        boolean removed = false;
        for (Iterable<TextColor> colors : colorGroups) {
            if (colors == null) {
                continue;
            }
            for (TextColor color : colors) {
                removed |= STOPS.remove(color) != null;
            }
        }
        if (removed) {
            publicationCount++;
        }
    }

    /**
     * The flat colour {@code color} is laid out in, or {@code color} itself when it is
     * not a remembered decoration or its settings leave it unchanged.
     * <p>
     * This is the colour a glyph gets as a whole: its base when role colouring is off,
     * the role's first colour when the gradient is hidden, and otherwise the gradient at
     * the glyph's middle. The gradient itself, and its movement, are applied to the
     * glyph's corners as it is drawn; see {@link #shade}.
     */
    public static TextColor resolve(TextColor color) {
        if (color == null) {
            return null;
        }
        Stop stop = STOPS.get(color);
        if (stop == null) {
            return color;
        }
        Axis axis = stop.axis;
        if (!coloringEnabled(axis.target)) {
            return stop.baseColor;
        }
        boolean perUser = perUserColorsEnabled();
        ColorRamp ramp = perUser ? axis.displayRamp : axis.roleRamp;
        boolean storedRampActive = ramp == axis.displayRamp;
        boolean gradient = ramp.isGradient() && gradientsEnabled(axis.target);
        boolean joint = gradient && axis.joined() && jointGradientsVisible();
        if (storedRampActive && (!ramp.isGradient() || (gradient && !joint))) {
            return color;
        }

        int mode = (perUser ? 1 : 0) | (gradient ? 2 : 0) | (joint ? 4 : 0);
        if (stop.memoMode != mode) {
            stop.memoColor = WynncraftTextShaderColor.safeTextColor(!gradient
                    ? ramp.first()
                    : ramp.sample(joint
                            ? (axis.jointOffset + stop.center) / axis.jointLength
                            : stop.center / axis.length));
            stop.memoMode = mode;
        }
        return stop.memoColor;
    }

    /**
     * How to colour the corners of a glyph drawn in {@code color}, for this instant, or
     * {@code null} when it is not a decoration or its settings draw it flat.
     */
    public static Shade shade(TextColor color) {
        return shade(color, WALL_CLOCK);
    }

    /** Shading at a fixed {@code phase} on every axis, for tests. */
    static Shade shade(TextColor color, double phase) {
        return shade(color, (ramp, length) -> phase);
    }

    private static Shade shade(TextColor color, Clock clock) {
        if (color == null) {
            return null;
        }
        Stop stop = STOPS.get(color);
        if (stop == null) {
            return null;
        }
        Axis axis = stop.axis;
        if (!coloringEnabled(axis.target)) {
            return null;
        }
        ColorRamp ramp = perUserColorsEnabled() ? axis.displayRamp : axis.roleRamp;
        if (!ramp.isGradient() || !gradientsEnabled(axis.target)) {
            return null;
        }
        boolean joint = axis.joined() && jointGradientsVisible();
        boolean animated = joint ? anyAnimationEnabled() : animationEnabled(axis.target);
        double length = joint ? axis.jointLength : axis.length;
        double origin = (joint ? axis.jointOffset : 0f) + stop.offset;
        return new Shade(ramp, origin, length, animated ? clock.phase(ramp, length) : Double.NaN);
    }

    /**
     * How far round {@code ramp} an axis {@code length} pixels long has scrolled once
     * gradients have travelled {@code travelled} pixels. One full turn brings every
     * stop, and the step from the last back to the first, past a point, so it covers
     * {@code stops / (stops - 1)} axis lengths.
     */
    static double phaseAt(ColorRamp ramp, double length, double travelled) {
        int stops = Math.max(2, ramp.stops().size());
        double turn = stops * Math.max(1d, length) / (stops - 1);
        return (travelled % turn) / turn;
    }

    /**
     * How far animated gradients have travelled by {@code millis}, in font pixels.
     * <p>
     * The distance is added up as time passes, at whatever speed is set at the time,
     * rather than worked out from the clock alone. Changing the speed then changes how
     * fast gradients move from that moment on, instead of throwing them to wherever the
     * new speed would have taken them since the game started.
     */
    static double travelled(long millis) {
        if (travelledAt != Long.MIN_VALUE && millis > travelledAt) {
            travelledPixels += (millis - travelledAt) * pixelsPerSecond() / 1000d;
        }
        if (millis > travelledAt) {
            travelledAt = millis;
        }
        return travelledPixels;
    }

    /** The configured animation speed, in font pixels per second. */
    static double pixelsPerSecond() {
        Setting.IntSetting speed = SeqClient.getGradientAnimationSpeedSetting();
        return PIXELS_PER_SECOND * (speed == null ? 100 : speed.getValue()) / 100d;
    }

    /** Pill and name read as one gradient only while both are coloured and graded. */
    private static boolean jointGradientsVisible() {
        return coloringEnabled(Target.RANK_BADGE)
                && coloringEnabled(Target.USERNAME)
                && gradientsEnabled(Target.RANK_BADGE)
                && gradientsEnabled(Target.USERNAME);
    }

    /** A joint gradient moves as one: asking either half to move moves both. */
    private static boolean anyAnimationEnabled() {
        return animationEnabled(Target.RANK_BADGE) || animationEnabled(Target.USERNAME);
    }

    private static boolean gradientsEnabled(Target target) {
        Setting.BooleanSetting setting = switch (target) {
            case RANK_BADGE -> SeqClient.getShowRankPillGradientsSetting();
            case USERNAME -> SeqClient.getShowUsernameGradientsSetting();
        };
        return setting == null || setting.getValue();
    }

    private static boolean coloringEnabled(Target target) {
        Setting.BooleanSetting setting = switch (target) {
            case RANK_BADGE -> SeqClient.getColorRankPillsSetting();
            case USERNAME -> SeqClient.getColorUsernamesSetting();
        };
        return setting == null || setting.getValue();
    }

    private static boolean perUserColorsEnabled() {
        Setting.BooleanSetting setting = SeqClient.getUsePerUserColorsSetting();
        return setting == null || setting.getValue();
    }

    private static boolean animationEnabled(Target target) {
        Setting.BooleanSetting setting = switch (target) {
            case RANK_BADGE -> SeqClient.getAnimateRankGradientsSetting();
            case USERNAME -> SeqClient.getAnimateUsernameGradientsSetting();
        };
        return setting != null && setting.getValue();
    }

    private static void remember(TextColor color, Stop stop) {
        Batch pending = PENDING_REGISTRATIONS.get();
        if (pending != null) {
            pending.registrations().add(new Registration(color, stop));
            return;
        }
        rememberAll(List.of(new Registration(color, stop)), false);
    }

    private static synchronized void rememberAll(List<Registration> registrations, boolean pinned) {
        if (registrations.isEmpty()) {
            return;
        }
        for (Registration registration : registrations) {
            STOPS.put(registration.color(), registration.stop());
            if (pinned) {
                continue;
            }
            REGISTRATION_ORDER.addLast(registration.color());
            while (REGISTRATION_ORDER.size() > MAX_REMEMBERED_STOPS) {
                STOPS.remove(REGISTRATION_ORDER.removeFirst());
            }
        }
        publicationCount++;
    }

    /** How many evictable stops are held; pinned ones are counted by their owner. */
    static synchronized int rememberedStopCount() {
        return REGISTRATION_ORDER.size();
    }

    /**
     * Whether {@code color} is a rank decoration this minted, rather than a colour the
     * game or another mod set.
     */
    public static boolean isDecorationColor(TextColor color) {
        return color != null && (STOPS.containsKey(color) || fixedDecorationColors.containsKey(color));
    }

    /** Shared foreground lettering, drawn slightly in front of a nametag pill's fill. */
    public static boolean isBadgeLabelColor(TextColor color) {
        return color != null && fixedDecorationColors.containsKey(color);
    }

    /**
     * Whether {@code color} belongs to a rank badge rather than to a decorated name.
     * A badge is built by laying glyphs on top of one another and a name is not, so
     * the two have to be drawn differently in the world; see {@code NametagTextPass}.
     */
    public static boolean isBadgeColor(TextColor color) {
        if (color == null) {
            return false;
        }
        if (fixedDecorationColors.containsKey(color)) {
            return true;
        }
        Stop stop = STOPS.get(color);
        return stop != null && stop.axis.target == Target.RANK_BADGE;
    }

    /** Marks a shared, non-animated colour as belonging to a Sequoia decoration. */
    public static synchronized TextColor markDecorationColor(TextColor color) {
        Objects.requireNonNull(color, "color");
        IdentityHashMap<TextColor, Boolean> updated = new IdentityHashMap<>(fixedDecorationColors);
        updated.put(color, Boolean.TRUE);
        fixedDecorationColors = updated;
        return color;
    }

    static synchronized long publicationCount() {
        return publicationCount;
    }
}
