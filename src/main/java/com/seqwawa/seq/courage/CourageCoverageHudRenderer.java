package com.seqwawa.seq.courage;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.courage.CourageHud.Bounds;
import com.seqwawa.seq.courage.CourageHud.Position;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;

/** A single line shown only while the local player is standing in another Shaman's Courage aura. */
public final class CourageCoverageHudRenderer {
    public static final float DEFAULT_X = 0.5f;
    public static final float DEFAULT_Y = 0.7f;

    private static final String LABEL = "In Courage";

    /** Being covered is the good state, so it reads green rather than the aura's red. */
    private static final Color COVERED = new Color(0x55FF55);

    private CourageCoverageHudRenderer() {}

    public static void render(UiCanvas canvas) {
        if (canvas == null
                || !CourageHud.enabled(SeqClient.getCourageCoverageHudSetting())
                || !CourageRangeClient.isActive()
                || CourageRangeTracker.coveringCircles().isEmpty()) {
            return;
        }
        draw(canvas);
    }

    public static Bounds renderPreview(UiCanvas canvas) {
        return draw(canvas);
    }

    public static Bounds previewBounds(float screenWidth, float screenHeight) {
        return layout(screenWidth, screenHeight);
    }

    public static Position positionForTopLeft(
            float screenWidth, float screenHeight, float left, float top) {
        Bounds bounds = previewBounds(screenWidth, screenHeight);
        return CourageHud.positionForTopLeft(
                screenWidth, screenHeight, bounds.width(), bounds.height(), left, top);
    }

    private static Bounds draw(UiCanvas canvas) {
        float textSize = CourageHud.textSize();
        Bounds bounds = layout(canvas.metrics().width(), canvas.metrics().height());
        CourageHud.text(canvas, LABEL, bounds.x(), bounds.y() + textSize / 2f, textSize, COVERED);
        return bounds;
    }

    private static Bounds layout(float screenWidth, float screenHeight) {
        float textSize = CourageHud.textSize();
        return CourageHud.place(
                screenWidth,
                screenHeight,
                CourageHud.measure(LABEL, textSize),
                textSize,
                CourageHud.position(SeqClient.getCourageCoverageHudXSetting(), DEFAULT_X),
                CourageHud.position(SeqClient.getCourageCoverageHudYSetting(), DEFAULT_Y));
    }
}
