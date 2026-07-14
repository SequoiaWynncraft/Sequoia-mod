package com.seqwawa.seq.ui.widget;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.rendering.UiCanvas;

import java.awt.*;

public class BooleanWidget extends SettingWidget<Setting.BooleanSetting> {
    private static final float TOGGLE_WIDTH = 36;
    private static final float TOGGLE_HEIGHT = 18;
    private static final float KNOB_PADDING = 2;
    private static final float FONT_SIZE = 12;

    private static final Color ON_COLOR = new Color(160, 130, 220, 255);
    private static final Color OFF_COLOR = new Color(80, 80, 90, 200);
    private static final Color KNOB_COLOR = new Color(255, 255, 255, 255);
    private static final Color LABEL_COLOR = new Color(220, 220, 220, 255);

    public BooleanWidget(Setting.BooleanSetting setting) {
        super(setting);
        this.height = 28;
    }

    @Override
    public void render(UiCanvas canvas, float mouseX, float mouseY) {
        String fontName = SeqClient.getFontManager().getSelectedFont();

        canvas.drawText(getDisplayName(), x + 8, y + height / 2f, textStyle(
                fontName, LABEL_COLOR, UiCanvas.HorizontalAlign.LEFT));

        // Toggle
        float toggleX = x + width - TOGGLE_WIDTH - 8;
        float toggleY = y + (height - TOGGLE_HEIGHT) / 2f;
        boolean on = setting.getValue();

        Color bgColor = on ? ON_COLOR : OFF_COLOR;
        canvas.fillRect(toggleX, toggleY, TOGGLE_WIDTH, TOGGLE_HEIGHT, bgColor);

        float knobSize = TOGGLE_HEIGHT - KNOB_PADDING * 2;
        float knobX = on
                ? toggleX + TOGGLE_WIDTH - knobSize - KNOB_PADDING
                : toggleX + KNOB_PADDING;
        float knobY = toggleY + KNOB_PADDING;
        canvas.fillRect(knobX, knobY, knobSize, knobSize, KNOB_COLOR);
    }

    private static UiCanvas.TextStyle textStyle(
            String font, Color color, UiCanvas.HorizontalAlign horizontalAlign) {
        return new UiCanvas.TextStyle(
                font, FONT_SIZE, color, horizontalAlign, UiCanvas.VerticalAlign.MIDDLE);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button == 0 && isHovered(mouseX, mouseY, x, y, width, height)) {
            setting.setValue(!setting.getValue());
            return true;
        }
        return false;
    }
}
