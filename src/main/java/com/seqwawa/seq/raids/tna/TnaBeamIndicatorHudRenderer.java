package com.seqwawa.seq.raids.tna;

import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_DANGER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_SUCCESS;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.managers.ThemeManager;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;

/** Three beam-progress dots shown below the crosshair during TNA's third challenge. */
public final class TnaBeamIndicatorHudRenderer {
    static final float DEFAULT_X = 0.5f;
    static final float DEFAULT_Y = 0.58f;
    static final int DEFAULT_SIZE_PERCENT = 100;
    private static final float HUD_MARGIN = 7f;
    private static final float LAMP_RADIUS = 6f;
    private static final float LAMP_GAP = 18f;
    private static final float CONTENT_WIDTH = LAMP_GAP * 2f + LAMP_RADIUS * 2f;
    private static final float CONTENT_HEIGHT = 28f;

    private TnaBeamIndicatorHudRenderer() {}

    public static void render(UiCanvas canvas) {
        if (canvas == null || !isEnabled(SeqClient.getTnaBeamIndicatorSetting())) {
            return;
        }
        TnaSahurSoundDetector.IndicatorState state =
                TnaSahurSoundDetector.indicatorState(monotonicMillis());
        if (!state.visible()) {
            return;
        }

        Bounds bounds = bounds(canvas.metrics().width(), canvas.metrics().height());
        int completedBeams = state.danger() ? 3 : state.timerBeams();
        renderDots(canvas, bounds, completedBeams, state.danger(), scale());
    }

    public static Bounds renderPreview(UiCanvas canvas) {
        Bounds bounds = previewBounds(canvas.metrics().width(), canvas.metrics().height());
        renderDots(canvas, bounds, 2, false, scale());
        return bounds;
    }

    public static Bounds previewBounds(float screenWidth, float screenHeight) {
        return bounds(screenWidth, screenHeight);
    }

    private static void renderDots(
            UiCanvas canvas, Bounds bounds, int completedBeams, boolean fire, float scale) {
        float centerX = bounds.x() + bounds.width() / 2f;
        float centerY = bounds.y() + LAMP_RADIUS * scale;
        for (int index = 1; index <= 3; index++) {
            float x = centerX + (index - 2) * LAMP_GAP * scale;
            Color color = opaque(ThemeManager.color(index <= completedBeams ? CONTROL_SUCCESS : CONTROL_DANGER));
            canvas.fillCircle(x, centerY, LAMP_RADIUS * scale, color);
        }

        if (fire) {
            drawText(
                    canvas,
                    "FIRE !",
                    centerX,
                    centerY + 14f * scale,
                    11f * scale,
                    opaque(ThemeManager.color(CONTROL_DANGER)));
        }
    }

    private static Bounds bounds(float screenWidth, float screenHeight) {
        return positionBounds(
                screenWidth,
                screenHeight,
                position(SeqClient.getTnaBeamIndicatorXSetting(), DEFAULT_X),
                position(SeqClient.getTnaBeamIndicatorYSetting(), DEFAULT_Y),
                scale());
    }

    static boolean isEnabled(Setting.BooleanSetting setting) {
        return setting == null || Boolean.TRUE.equals(setting.getValue());
    }

    static float sizeScale(Setting.IntSetting setting) {
        int percent = setting == null || setting.getValue() == null
                ? DEFAULT_SIZE_PERCENT
                : setting.getValue();
        return Math.clamp(percent, 25, 400) / 100f;
    }

    private static float scale() {
        return sizeScale(SeqClient.getTnaBeamIndicatorSizeSetting());
    }

    private static float position(Setting.FloatSetting setting, float fallback) {
        return setting == null || setting.getValue() == null
                ? fallback
                : Math.clamp(setting.getValue(), 0f, 1f);
    }

    static Bounds positionBounds(
            float screenWidth, float screenHeight, float normalizedX, float normalizedY, float scale) {
        float contentWidth = CONTENT_WIDTH * scale;
        float contentHeight = CONTENT_HEIGHT * scale;
        float travelX = Math.max(0f, screenWidth - HUD_MARGIN * 2f - contentWidth);
        float travelY = Math.max(0f, screenHeight - HUD_MARGIN * 2f - contentHeight);
        return new Bounds(
                HUD_MARGIN + Math.clamp(normalizedX, 0f, 1f) * travelX,
                HUD_MARGIN + Math.clamp(normalizedY, 0f, 1f) * travelY,
                contentWidth,
                contentHeight);
    }

    public static Position positionForTopLeft(
            float screenWidth, float screenHeight, float left, float top) {
        float scale = scale();
        float travelX = Math.max(0f, screenWidth - HUD_MARGIN * 2f - CONTENT_WIDTH * scale);
        float travelY = Math.max(0f, screenHeight - HUD_MARGIN * 2f - CONTENT_HEIGHT * scale);
        return new Position(
                travelX == 0f ? 0f : Math.clamp((left - HUD_MARGIN) / travelX, 0f, 1f),
                travelY == 0f ? 0f : Math.clamp((top - HUD_MARGIN) / travelY, 0f, 1f));
    }

    private static void drawText(UiCanvas canvas, String text, float x, float y, float size, Color color) {
        String font = SeqClient.getFontManager() == null
                ? "mc"
                : SeqClient.getFontManager().getSelectedFont();
        canvas.drawText(text, x, y, new UiCanvas.TextStyle(
                font,
                size,
                color,
                UiCanvas.HorizontalAlign.CENTER,
                UiCanvas.VerticalAlign.MIDDLE));
    }

    private static long monotonicMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private static Color opaque(Color color) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue());
    }

    public record Bounds(float x, float y, float width, float height) {
        public boolean contains(float pointX, float pointY, float padding) {
            return pointX >= x - padding
                    && pointX <= x + width + padding
                    && pointY >= y - padding
                    && pointY <= y + height + padding;
        }
    }

    public record Position(float x, float y) {}
}
