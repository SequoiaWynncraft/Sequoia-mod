package com.seqwawa.seq.courage;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.courage.CourageHud.Bounds;
import com.seqwawa.seq.courage.CourageHud.Position;
import com.seqwawa.seq.courage.CourageRangeTracker.CourageCircle;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;

/** The number of players standing inside the local Shaman's Courage aura. */
public final class CourageOwnRangeHudRenderer {
    public static final float DEFAULT_X = 0f;
    public static final float DEFAULT_Y = 0.3f;

    private static final String LABEL = "Courage";
    /** A trailing space in the label is not always measured, so the gap is explicit. */
    private static final float COUNT_GAP_RATIO = 0.4f;
    private static final int PREVIEW_COUNT = 3;
    private static final long RAINBOW_PERIOD_MS = 2_500L;

    /** The red Wynncraft prints the Courage ability's own name in. */
    private static final Color LABEL_COLOR = new Color(0xFF5555);

    private static final Color EMPTY = new Color(0xAAAAAA);
    private static final Color ONE = new Color(0x5555FF);
    private static final Color TWO = new Color(0xFF5555);

    private CourageOwnRangeHudRenderer() {}

    public static void render(UiCanvas canvas) {
        if (canvas == null
                || !CourageHud.enabled(SeqClient.getCourageOwnRangeHudSetting())
                || !CourageRangeClient.isActive()) {
            return;
        }
        CourageCircle own = CourageRangeTracker.ownCircle();
        if (own == null) {
            return;
        }
        draw(canvas, own.playersInRange());
    }

    public static Bounds renderPreview(UiCanvas canvas) {
        return draw(canvas, PREVIEW_COUNT);
    }

    public static Bounds previewBounds(float screenWidth, float screenHeight) {
        return layout(screenWidth, screenHeight, PREVIEW_COUNT);
    }

    public static Position positionForTopLeft(
            float screenWidth, float screenHeight, float left, float top) {
        Bounds bounds = previewBounds(screenWidth, screenHeight);
        return CourageHud.positionForTopLeft(
                screenWidth, screenHeight, bounds.width(), bounds.height(), left, top);
    }

    /** A full aura cycles its hue; every other count is a flat colour. */
    static Color countColor(int playersInRange, long nowMs) {
        return switch (Math.max(0, playersInRange)) {
            case 0 -> EMPTY;
            case 1 -> ONE;
            case 2 -> TWO;
            default -> rainbow(nowMs);
        };
    }

    static Color rainbow(long nowMs) {
        float hue = Math.floorMod(nowMs, RAINBOW_PERIOD_MS) / (float) RAINBOW_PERIOD_MS;
        return Color.getHSBColor(hue, 0.75f, 1f);
    }

    private static Bounds draw(UiCanvas canvas, int playersInRange) {
        float textSize = CourageHud.textSize();
        Bounds bounds = layout(canvas.metrics().width(), canvas.metrics().height(), playersInRange);
        float baseline = bounds.y() + textSize / 2f;

        CourageHud.text(canvas, LABEL, bounds.x(), baseline, textSize, LABEL_COLOR);
        CourageHud.text(
                canvas,
                String.valueOf(playersInRange),
                bounds.x() + CourageHud.measure(LABEL, textSize) + textSize * COUNT_GAP_RATIO,
                baseline,
                textSize,
                countColor(playersInRange, System.currentTimeMillis()));
        return bounds;
    }

    private static Bounds layout(float screenWidth, float screenHeight, int playersInRange) {
        float textSize = CourageHud.textSize();
        return CourageHud.place(
                screenWidth,
                screenHeight,
                CourageHud.measure(LABEL, textSize)
                        + textSize * COUNT_GAP_RATIO
                        + CourageHud.measure(String.valueOf(playersInRange), textSize),
                textSize,
                CourageHud.position(SeqClient.getCourageOwnRangeHudXSetting(), DEFAULT_X),
                CourageHud.position(SeqClient.getCourageOwnRangeHudYSetting(), DEFAULT_Y));
    }
}
