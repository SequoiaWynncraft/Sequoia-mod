package com.seqwawa.seq.courage;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.courage.CourageHud.Bounds;
import com.seqwawa.seq.courage.CourageHud.Position;
import com.seqwawa.seq.courage.CourageRangeTracker.CourageCircle;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;

/** The call to fire Courage: a full bar with the whole party standing in the aura. */
public final class CourageAlertHudRenderer {
    public static final float DEFAULT_X = 0.5f;
    public static final float DEFAULT_Y = 0.4f;
    public static final int DEFAULT_TEXT_SIZE = 24;

    /** A raid party is four, so three others in the aura means nobody is missing out. */
    static final int REQUIRED_PLAYERS = 3;

    private static final String LABEL = "Courage NOW!";
    private static final Color ALERT = new Color(0xFF3030);

    private CourageAlertHudRenderer() {}

    public static void render(UiCanvas canvas) {
        if (canvas == null || !CourageHud.enabled(SeqClient.getCourageAlertSetting())) {
            return;
        }
        if (!CourageRangeClient.isActive() || !CourageCharge.isFull()) {
            return;
        }
        CourageCircle own = CourageRangeTracker.ownCircle();
        if (own == null || own.playersInRange() < REQUIRED_PLAYERS) {
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

    static float textSize() {
        Setting.IntSetting setting = SeqClient.getCourageAlertTextSizeSetting();
        return setting == null || setting.getValue() == null ? DEFAULT_TEXT_SIZE : setting.getValue();
    }

    private static Bounds draw(UiCanvas canvas) {
        float textSize = textSize();
        Bounds bounds = layout(canvas.metrics().width(), canvas.metrics().height());
        CourageHud.text(canvas, LABEL, bounds.x(), bounds.y() + textSize / 2f, textSize, ALERT);
        return bounds;
    }

    private static Bounds layout(float screenWidth, float screenHeight) {
        float textSize = textSize();
        return CourageHud.place(
                screenWidth,
                screenHeight,
                CourageHud.measure(LABEL, textSize),
                textSize,
                CourageHud.position(SeqClient.getCourageAlertXSetting(), DEFAULT_X),
                CourageHud.position(SeqClient.getCourageAlertYSetting(), DEFAULT_Y));
    }
}
