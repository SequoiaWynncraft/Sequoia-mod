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
 * Scrolls a gradient rank's colours along its chat pill and speaker name, so a role
 * whose colour is a Discord gradient reads as one rather than as a fixed smear.
 * <p>
 * A chat line is drawn from a component that was built once, long before the frame it
 * appears on, so a colour cannot be animated by rebuilding it. What survives into
 * rendering is the {@link TextColor} instance itself: {@code TextColor.fromRgb} mints a
 * new one per call and every step from component to glyph copies the reference rather
 * than the value. Each pill colour is therefore recognisable by identity, and the
 * remembered stops below say where on which ramp it was sampled from.
 * <p>
 * The lookup runs for every glyph the game draws (see {@code
 * FontPreparedTextBuilderMixin}), so it starts with one identity probe that misses on
 * ordinary text and only consults settings for a registered decoration.
 */
public final class RankGradientAnimation {

    /** Independently configurable places where a Discord role gradient is rendered. */
    public enum Target {
        RANK_BADGE,
        USERNAME
    }

    /** One full turn around a role's ramp, slow enough to read as a sheen. */
    private static final long CYCLE_MILLIS = 5000L;

    /**
     * How many rank-decoration colours stay configurable. A pill and speaker name each
     * contribute one stop per glyph, so this covers the recent chat history; older
     * decorations simply keep their stored colour rather than being rebuilt.
     */
    private static final int MAX_REMEMBERED_STOPS = 4096;

    /** Where a remembered colour was sampled from, its target, and its uncoloured base. */
    private record Stop(
            ColorRamp displayRamp,
            ColorRamp roleRamp,
            double position,
            Target target,
            TextColor baseColor) {}

    /** A stop waiting to be included in the next registry publication. */
    private record Registration(TextColor color, Stop stop) {}

    /** Registration order, so the stops dropped on overflow are the oldest ones. */
    private static final ArrayDeque<TextColor> REGISTRATION_ORDER = new ArrayDeque<>();

    /**
     * Published to the render thread by replacement rather than by mutation. It is read
     * once per drawn glyph, and a lock there would be paid by every piece of text the
     * game draws, not just by chat.
     */
    private static volatile Map<TextColor, Stop> stops = new IdentityHashMap<>();

    /** Fixed-colour glyphs that are part of a decoration but do not animate. */
    private static volatile Map<TextColor, Boolean> fixedDecorationColors = new IdentityHashMap<>();

    /**
     * Registrations made while one chat decoration is being built. Nested batches
     * share the outer list, so composing a pill and a name still publishes once.
     */
    private static final ThreadLocal<Batch> PENDING_REGISTRATIONS = new ThreadLocal<>();

    /** Monotonic publication count, exposed package-locally for the batching test. */
    private static long publicationCount;

    /** Colours collected for one decoration, and whether they are exempt from eviction. */
    private record Batch(List<Registration> registrations, boolean pinned) {}

    /**
     * A decoration and the colours registered for it, which stay configurable until
     * they are handed back to {@link #release}.
     */
    public record Pinned<T>(T value, List<TextColor> colors) {}

    private RankGradientAnimation() {}

    /**
     * Builds one decoration while collecting all of its colours, then publishes them
     * to the render thread in a single copy-on-write update. Calls may be nested: only
     * the outermost call publishes. A pinned build cannot be nested inside this
     * evictable batch because no caller would receive ownership of its colours.
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

    private static <T> Pinned<T> batch(Supplier<T> build, boolean pinned) {
        Objects.requireNonNull(build, "build");
        Batch outer = PENDING_REGISTRATIONS.get();
        if (outer != null) {
            if (pinned && !outer.pinned()) {
                throw new IllegalStateException("A pinned decoration cannot be built inside an evictable batch");
            }
            // An inner batch: the outermost call owns the publication and the colours.
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

    /** Forgets several pinned decorations with one copy-on-write publication. */
    public static synchronized void releaseAll(Iterable<? extends Iterable<TextColor>> colorGroups) {
        if (colorGroups == null) {
            return;
        }
        IdentityHashMap<TextColor, Stop> updated = null;
        boolean removed = false;
        for (Iterable<TextColor> colors : colorGroups) {
            if (colors == null) {
                continue;
            }
            for (TextColor color : colors) {
                if (updated == null) {
                    updated = new IdentityHashMap<>(stops);
                }
                removed |= updated.remove(color) != null;
            }
        }
        if (!removed) {
            return;
        }
        stops = updated;
        publicationCount++;
    }

    /**
     * The decoration colour at {@code position} along {@code ramp}, remembered so it
     * can be moved later.
     * <p>
     * Solid roles are remembered too, so their target can return to its base colour
     * immediately when role colouring is switched off.
     */
    public static TextColor colorAt(ColorRamp ramp, double position) {
        return colorAt(ramp, position, Target.RANK_BADGE);
    }

    /**
     * The decoration colour for {@code target}, registered with enough information to
     * flatten or animate it immediately when its settings change.
     */
    public static TextColor colorAt(ColorRamp ramp, double position, Target target) {
        return colorAt(ramp, position, target, null);
    }

    /**
     * The decoration colour for {@code target}, paired with the colour it should return
     * to when role colouring is disabled. A null base restores Minecraft's inherited
     * text colour.
     */
    public static TextColor colorAt(ColorRamp ramp, double position, Target target, TextColor baseColor) {
        return colorAt(ramp, ramp, position, target, baseColor);
    }

    /**
     * A member decoration that can switch between their individual display palette
     * and the palette shared by their progression rank.
     */
    public static TextColor colorAt(
            ColorRamp displayRamp,
            ColorRamp roleRamp,
            double position,
            Target target,
            TextColor baseColor) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(displayRamp, "displayRamp");
        Objects.requireNonNull(roleRamp, "roleRamp");
        TextColor color = TextColor.fromRgb(displayRamp.sample(position));
        remember(new Registration(color, new Stop(displayRamp, roleRamp, position, target, baseColor)));
        return color;
    }

    /**
     * The colour {@code color} should be drawn as at this instant, or {@code color}
     * itself when it is not a remembered role decoration or its settings leave it unchanged.
     */
    public static TextColor animate(TextColor color) {
        return animate(color, phase());
    }

    /** Animation core, parameterised on the phase so it stays unit-testable. */
    static TextColor animate(TextColor color, double phase) {
        if (color == null) {
            return color;
        }
        Stop stop = stops.get(color);
        if (stop == null) {
            return color;
        }
        if (!coloringEnabled(stop.target())) {
            return stop.baseColor();
        }
        ColorRamp ramp = perUserColorsEnabled() ? stop.displayRamp() : stop.roleRamp();
        boolean storedRampActive = ramp == stop.displayRamp();
        if (!ramp.isGradient()) {
            return storedRampActive ? color : TextColor.fromRgb(ramp.first());
        }
        if (!gradientsEnabled(stop.target())) {
            return TextColor.fromRgb(ramp.first());
        }
        if (animationEnabled(stop.target())) {
            return TextColor.fromRgb(ramp.scroll(stop.position(), phase));
        }
        return storedRampActive ? color : TextColor.fromRgb(ramp.sample(stop.position()));
    }

    /** How far through the current turn the clock is, in {@code [0, 1)}. */
    private static double phase() {
        return Math.floorMod(System.nanoTime() / 1_000_000L, CYCLE_MILLIS) / (double) CYCLE_MILLIS;
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

    private static void remember(Registration registration) {
        Batch pending = PENDING_REGISTRATIONS.get();
        if (pending != null) {
            pending.registrations().add(registration);
            return;
        }
        rememberAll(List.of(registration), false);
    }

    private static synchronized void rememberAll(List<Registration> registrations, boolean pinned) {
        if (registrations.isEmpty()) {
            return;
        }
        IdentityHashMap<TextColor, Stop> updated = new IdentityHashMap<>(stops);
        for (Registration registration : registrations) {
            updated.put(registration.color(), registration.stop());
            if (pinned) {
                continue;
            }
            REGISTRATION_ORDER.addLast(registration.color());
            while (REGISTRATION_ORDER.size() > MAX_REMEMBERED_STOPS) {
                updated.remove(REGISTRATION_ORDER.removeFirst());
            }
        }
        stops = updated;
        publicationCount++;
    }

    /** How many evictable stops are held; pinned ones are counted by their owner. */
    static synchronized int rememberedStopCount() {
        return REGISTRATION_ORDER.size();
    }

    /**
     * Whether {@code color} is a rank decoration this minted, rather than a colour the
     * game or another mod set. Read without locking, like {@link #animate}, because it
     * is consulted while text is being laid out.
     */
    public static boolean isDecorationColor(TextColor color) {
        return color != null && (stops.containsKey(color) || fixedDecorationColors.containsKey(color));
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
        Stop stop = stops.get(color);
        return stop != null && stop.target() == Target.RANK_BADGE;
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
