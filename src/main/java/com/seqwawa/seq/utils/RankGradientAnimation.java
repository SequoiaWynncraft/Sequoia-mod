package com.seqwawa.seq.utils;

import com.google.common.collect.MapMaker;
import com.seqwawa.seq.accessors.GradientTagHolder;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import java.util.Map;
import java.util.Objects;
import net.minecraft.network.chat.TextColor;

/**
 * Paints a gradient rank's colours along its pill and speaker name, and scrolls them,
 * so a role whose colour is a Discord gradient reads as one rather than as a fixed smear.
 * <p>
 * A chat line or nametag is drawn from a component built once, long before the frame it
 * appears on, so a colour cannot be animated by rebuilding it. What survives into
 * rendering is the {@link TextColor} instance itself: {@code TextColor.fromRgb} mints a
 * new one per call, and every step from component to glyph copies the reference rather
 * than the value. Each decoration colour therefore carries its own {@link Glyph}: where
 * that glyph sits on which gradient, what it returns to, and what drawing it has worked
 * out so far. It lives and dies with the text that uses it, so nothing has to be
 * registered, evicted or released.
 * <p>
 * Where a glyph sits is measured in font pixels along an {@link Axis}: a pill's
 * background, or a name. As a glyph is drawn its corners are coloured by where they fall
 * on that axis (see {@link GradientPainter}), so the gradient runs smoothly across and
 * between glyphs and moves at the same speed in pixels whatever it is painted on. A pill
 * and the name after it can be {@link #join joined}, and then read as one gradient
 * whenever both show theirs.
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

    /** How much Minecraft thickens each edge of a bold glyph's quads. */
    private static final float BOLD_THICKNESS = 0.1f;

    /** Tags shared pill lettering: part of a decoration, but always one fixed colour. */
    private static final Object LABEL = new Object();

    /**
     * Tags for colours with no field of their own to hold one, which only happens without
     * the {@code TextColor} mixin, as in unit tests. Keys are held weakly and compared by
     * identity, so this keeps nothing alive either.
     */
    private static final Map<TextColor, Object> DETACHED_TAGS = new MapMaker().weakKeys().makeMap();

    /** The settings a decoration's colours depend on, as bits of one number. */
    private static final int COLOR_BADGE = 1;
    private static final int COLOR_NAME = 1 << 1;
    private static final int GRADIENT_BADGE = 1 << 2;
    private static final int GRADIENT_NAME = 1 << 3;
    private static final int PER_USER = 1 << 4;
    private static final int ANIMATE_BADGE = 1 << 5;
    private static final int ANIMATE_NAME = 1 << 6;
    private static final int JOINT_VISIBLE = COLOR_BADGE | COLOR_NAME | GRADIENT_BADGE | GRADIENT_NAME;
    /** Keeps a moving glyph's cache key apart from every still one. */
    private static final long MOVING = 1L << 7;

    /** How far animated gradients have travelled, in font pixels, as of {@link #travelledAt}. */
    private static double travelledPixels;
    private static long travelledAt = Long.MIN_VALUE;

    /** The frame being drawn, and how far gradients have travelled by it; see {@link #beginFrame}. */
    private static long frame;
    private static double frameTravelled;

    private RankGradientAnimation() {}

    /**
     * Samples the animation clock for the frame about to be drawn. Every moving glyph in
     * the frame is drawn at this one instant, and works out its colours at most once.
     */
    public static void beginFrame() {
        beginFrame(System.nanoTime() / 1_000_000L);
    }

    static void beginFrame(long millis) {
        frameTravelled = travelled(millis);
        frame++;
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
         * {@code width} pixels wide, tagged so it can be graded and moved as it is drawn.
         * {@code baseColor} is what it returns to when role colouring is switched off;
         * {@code null} restores Minecraft's inherited text colour.
         */
        public TextColor colorAt(float offset, float width, TextColor baseColor) {
            float center = offset + Math.max(0f, width) / 2f;
            TextColor color = WynncraftTextShaderColor.safeTextColor(displayRamp.sample(center / length));
            tag(color, new Glyph(this, offset, center, baseColor));
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

    /**
     * One glyph of a decoration: where it sits on which gradient, what it returns to, and
     * what drawing it has worked out so far. A still gradient is worked out once for the
     * current settings and the glyph's size, a moving one once per frame, and a glyph
     * that is not drawn works nothing out at all.
     */
    static final class Glyph {
        private static final int GRADED = -1;
        private static final int SAMPLE_SLOTS = 8;

        final Axis axis;
        final float offset;
        final float center;
        final TextColor baseColor;

        /** The flat colour last resolved, and the settings it was resolved under. */
        private int resolvedFor = -1;
        private TextColor resolved;

        /** How the glyph was last painted, and what that was worked out for. */
        private long paintedFor = Long.MIN_VALUE;
        private float paintedLeft;
        private float paintedRight;
        private float paintedShearTop;
        private float paintedShearBottom;
        private float paintedBoldOffset;
        private boolean paintedShadow;
        private Shade shade;
        private int flatRgb = GRADED;
        /** Corner colours already sampled: a glyph's corners share a few x positions. */
        private float[] sampledX;
        private int[] sampledRgb;
        private int samples;

        private Glyph(Axis axis, float offset, float center, TextColor baseColor) {
            this.axis = axis;
            this.offset = offset;
            this.center = center;
            this.baseColor = baseColor;
        }

        /**
         * Gets the glyph ready to be drawn from a bitmap whose edges lie {@code left} and
         * {@code right} from its origin, sheared {@code shearTop} and {@code shearBottom}
         * by italics, with a bold copy {@code boldOffset} further on unless that is
         * {@code NaN}, and a shadow coloured along with it when {@code shadow}. False
         * when it is to be drawn flat in its resolved colour.
         */
        boolean prepare(
                float left, float right, float shearTop, float shearBottom, float boldOffset, boolean shadow) {
            int settings = settings();
            long key = paintKey(settings);
            if (key == paintedFor
                    && left == paintedLeft
                    && right == paintedRight
                    && shearTop == paintedShearTop
                    && shearBottom == paintedShearBottom
                    && Float.compare(boldOffset, paintedBoldOffset) == 0
                    && shadow == paintedShadow) {
                return shade != null;
            }
            shade = describe(this, settings, frameTravelled, Double.NaN);
            flatRgb = shade == null
                    ? GRADED
                    : markerSafeFlatColor(shade, left, right, shearTop, shearBottom, boldOffset, shadow);
            samples = 0;
            paintedFor = key;
            paintedLeft = left;
            paintedRight = right;
            paintedShearTop = shearTop;
            paintedShearBottom = shearBottom;
            paintedBoldOffset = boldOffset;
            paintedShadow = shadow;
            return shade != null;
        }

        /** The colour {@code x} pixels from the glyph's origin, as last prepared. */
        int rgbAt(float x) {
            if (flatRgb != GRADED) {
                return flatRgb;
            }
            for (int index = 0; index < samples; index++) {
                if (sampledX[index] == x) {
                    return sampledRgb[index];
                }
            }
            int rgb = shade.rgbAt(x);
            if (sampledX == null) {
                sampledX = new float[SAMPLE_SLOTS];
                sampledRgb = new int[SAMPLE_SLOTS];
            }
            if (samples < SAMPLE_SLOTS) {
                sampledX[samples] = x;
                sampledRgb[samples++] = rgb;
            }
            return rgb;
        }

        /** Still glyphs are keyed by their settings alone, moving ones by frame as well. */
        private long paintKey(int settings) {
            Target target = axis.target;
            boolean joint = axis.joined() && (settings & JOINT_VISIBLE) == JOINT_VISIBLE;
            boolean animated = joint
                    ? (settings & (ANIMATE_BADGE | ANIMATE_NAME)) != 0
                    : (settings & animateBit(target)) != 0;
            return animated ? frame << 8 | MOVING | settings : settings;
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
     * The flat colour {@code color} is laid out in, or {@code color} itself when it is
     * not a decoration or its settings leave it unchanged.
     * <p>
     * This is the colour a glyph gets as a whole: its base when role colouring is off,
     * the role's first colour when the gradient is hidden, and otherwise the gradient at
     * the glyph's middle. The gradient itself, and its movement, are applied to the
     * glyph's corners as it is drawn.
     */
    public static TextColor resolve(TextColor color) {
        Glyph glyph = glyphOf(color);
        if (glyph == null) {
            return color;
        }
        int settings = settings();
        Axis axis = glyph.axis;
        if ((settings & colorBit(axis.target)) == 0) {
            return glyph.baseColor;
        }
        boolean perUser = (settings & PER_USER) != 0;
        ColorRamp ramp = perUser ? axis.displayRamp : axis.roleRamp;
        boolean storedRampActive = ramp == axis.displayRamp;
        boolean gradient = ramp.isGradient() && (settings & gradientBit(axis.target)) != 0;
        boolean joint = gradient && axis.joined() && (settings & JOINT_VISIBLE) == JOINT_VISIBLE;
        if (storedRampActive && (!ramp.isGradient() || (gradient && !joint))) {
            return color;
        }

        int mode = (perUser ? 1 : 0) | (gradient ? 2 : 0) | (joint ? 4 : 0);
        if (glyph.resolvedFor != mode) {
            glyph.resolved = WynncraftTextShaderColor.safeTextColor(!gradient
                    ? ramp.first()
                    : ramp.sample(joint
                            ? (axis.jointOffset + glyph.center) / axis.jointLength
                            : glyph.center / axis.length));
            glyph.resolvedFor = mode;
        }
        return glyph.resolved;
    }

    /**
     * How the corners of a glyph drawn in {@code color} are coloured in the current
     * frame, or {@code null} when it is not a decoration or its settings draw it flat.
     */
    public static Shade shade(TextColor color) {
        Glyph glyph = glyphOf(color);
        return glyph == null ? null : describe(glyph, settings(), frameTravelled, Double.NaN);
    }

    /** Shading at a fixed {@code phase} on every axis, for tests. */
    static Shade shade(TextColor color, double phase) {
        Glyph glyph = glyphOf(color);
        return glyph == null ? null : describe(glyph, settings(), 0d, phase);
    }

    private static Shade describe(Glyph glyph, int settings, double travelled, double fixedPhase) {
        Axis axis = glyph.axis;
        if ((settings & colorBit(axis.target)) == 0) {
            return null;
        }
        ColorRamp ramp = (settings & PER_USER) != 0 ? axis.displayRamp : axis.roleRamp;
        if (!ramp.isGradient() || (settings & gradientBit(axis.target)) == 0) {
            return null;
        }
        boolean joint = axis.joined() && (settings & JOINT_VISIBLE) == JOINT_VISIBLE;
        boolean animated = joint
                ? (settings & (ANIMATE_BADGE | ANIMATE_NAME)) != 0
                : (settings & animateBit(axis.target)) != 0;
        double length = joint ? axis.jointLength : axis.length;
        double origin = (joint ? axis.jointOffset : 0f) + glyph.offset;
        double phase = !animated ? Double.NaN : Double.isNaN(fixedPhase) ? phaseAt(ramp, length, travelled) : fixedPhase;
        return new Shade(ramp, origin, length, phase);
    }

    /**
     * The glyph's middle colour when any colour drawn across it would land on one of
     * Wynncraft's shader markers, or {@link Glyph#GRADED} when it can be graded; see
     * {@link Glyph#prepare} for what describes the glyph.
     * <p>
     * Wynncraft's text shader reads colours pixel by pixel and paints white any pixel
     * whose blended colour lands on a marker, so two safe corners are not enough. The
     * blend is checked quad by quad, between the corners exactly where Minecraft puts
     * them: the gradient between them may bend at a ramp stop, but the GPU blends
     * straight across. A bold glyph's second copy and a graded shadow are checked too.
     */
    static int markerSafeFlatColor(
            Shade shade,
            float left,
            float right,
            float shearTop,
            float shearBottom,
            float boldOffset,
            boolean shadow) {
        boolean bold = !Float.isNaN(boldOffset);
        float thickness = bold ? BOLD_THICKNESS : 0f;
        float topLeft = left + shearTop - thickness;
        float bottomLeft = left + shearBottom - thickness;
        float bottomRight = right + shearBottom + thickness;
        float topRight = right + shearTop + thickness;
        boolean crosses = quadCrosses(shade, topLeft, bottomLeft, bottomRight, topRight, shadow)
                || bold && quadCrosses(
                        shade,
                        topLeft + boldOffset,
                        bottomLeft + boldOffset,
                        bottomRight + boldOffset,
                        topRight + boldOffset,
                        shadow);
        return crosses ? shade.rgbAt((left + right) / 2f) : Glyph.GRADED;
    }

    /** Whether a quad with corners at these x positions blends through a marker, or its shadow does. */
    private static boolean quadCrosses(
            Shade shade, float topLeft, float bottomLeft, float bottomRight, float topRight, boolean shadow) {
        int a = shade.rgbAt(topLeft);
        int b = shade.rgbAt(bottomLeft);
        int c = shade.rgbAt(bottomRight);
        int d = shade.rgbAt(topRight);
        return blendCrosses(a, b, c, d)
                || shadow && blendCrosses(darkened(a), darkened(b), darkened(c), darkened(d));
    }

    /**
     * Whether the GPU, blending between a quad's four corner colours, can land on a
     * marker. An upright glyph's left corners share one colour and its right ones
     * another, so every colour it draws lies between those two. A slanted glyph's
     * corners all differ, and are checked pair by pair.
     */
    private static boolean blendCrosses(int topLeft, int bottomLeft, int bottomRight, int topRight) {
        if (topLeft == bottomLeft && topRight == bottomRight) {
            return WynncraftTextShaderColor.crossesMarker(topLeft, topRight);
        }
        return WynncraftTextShaderColor.crossesMarker(topLeft, bottomLeft)
                || WynncraftTextShaderColor.crossesMarker(topLeft, bottomRight)
                || WynncraftTextShaderColor.crossesMarker(topLeft, topRight)
                || WynncraftTextShaderColor.crossesMarker(bottomLeft, bottomRight)
                || WynncraftTextShaderColor.crossesMarker(bottomLeft, topRight)
                || WynncraftTextShaderColor.crossesMarker(bottomRight, topRight);
    }

    /** A shadow's colour, as Minecraft works it out: each channel at a quarter. */
    static int darkened(int rgb) {
        return (((rgb >> 16) & 0xFF) / 4) << 16 | (((rgb >> 8) & 0xFF) / 4) << 8 | (rgb & 0xFF) / 4;
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

    /** The latest moment the animation clock has been brought up to, for tests. */
    static long clockMillis() {
        return travelledAt;
    }

    /** The configured animation speed, in font pixels per second. */
    static double pixelsPerSecond() {
        Setting.IntSetting speed = SeqClient.getGradientAnimationSpeedSetting();
        return PIXELS_PER_SECOND * (speed == null ? 100 : speed.getValue()) / 100d;
    }

    /** The decoration {@code color} was minted for, or {@code null}. */
    static Glyph glyphOf(TextColor color) {
        return tagOf(color) instanceof Glyph glyph ? glyph : null;
    }

    private static Object tagOf(TextColor color) {
        if (color == null) {
            return null;
        }
        // Through Object: TextColor is final, and only the mixin makes it a holder.
        return (Object) color instanceof GradientTagHolder holder ? holder.seq$gradientTag() : DETACHED_TAGS.get(color);
    }

    private static void tag(TextColor color, Object tag) {
        if ((Object) color instanceof GradientTagHolder holder) {
            holder.seq$setGradientTag(tag);
        } else {
            DETACHED_TAGS.put(color, tag);
        }
    }

    /**
     * Whether {@code color} is a rank decoration this minted, rather than a colour the
     * game or another mod set.
     */
    public static boolean isDecorationColor(TextColor color) {
        return tagOf(color) != null;
    }

    /** Shared foreground lettering, drawn slightly in front of a nametag pill's fill. */
    public static boolean isBadgeLabelColor(TextColor color) {
        return tagOf(color) == LABEL;
    }

    /** Marks a shared, never graded colour as belonging to a Sequoia decoration. */
    public static TextColor markDecorationColor(TextColor color) {
        tag(Objects.requireNonNull(color, "color"), LABEL);
        return color;
    }

    /** The settings a decoration's colours depend on, read as they are now. */
    private static int settings() {
        return bit(SeqClient.getColorRankPillsSetting(), true, COLOR_BADGE)
                | bit(SeqClient.getColorUsernamesSetting(), true, COLOR_NAME)
                | bit(SeqClient.getShowRankPillGradientsSetting(), true, GRADIENT_BADGE)
                | bit(SeqClient.getShowUsernameGradientsSetting(), true, GRADIENT_NAME)
                | bit(SeqClient.getUsePerUserColorsSetting(), true, PER_USER)
                | bit(SeqClient.getAnimateRankGradientsSetting(), false, ANIMATE_BADGE)
                | bit(SeqClient.getAnimateUsernameGradientsSetting(), false, ANIMATE_NAME);
    }

    private static int bit(Setting.BooleanSetting setting, boolean unset, int bit) {
        return (setting == null ? unset : setting.getValue()) ? bit : 0;
    }

    private static int colorBit(Target target) {
        return target == Target.RANK_BADGE ? COLOR_BADGE : COLOR_NAME;
    }

    private static int gradientBit(Target target) {
        return target == Target.RANK_BADGE ? GRADIENT_BADGE : GRADIENT_NAME;
    }

    private static int animateBit(Target target) {
        return target == Target.RANK_BADGE ? ANIMATE_BADGE : ANIMATE_NAME;
    }
}
