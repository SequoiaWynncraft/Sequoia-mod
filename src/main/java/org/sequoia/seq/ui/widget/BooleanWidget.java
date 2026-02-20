package org.sequoia.seq.ui.widget;

import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.config.Setting;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

import java.awt.*;

import static org.lwjgl.nanovg.NanoVG.*;

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
    public void render(long nvg, float mouseX, float mouseY) {
        String fontName = SeqClient.getFontManager().getSelectedFont();

        // Label
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var labelColor = NVGContext.nvgColor(LABEL_COLOR);
        nvgFillColor(nvg, labelColor);
        nvgText(nvg, x + 8, y + height / 2f, setting.getName());
        labelColor.free();

        // Toggle
        float toggleX = x + width - TOGGLE_WIDTH - 8;
        float toggleY = y + (height - TOGGLE_HEIGHT) / 2f;
        boolean on = setting.getValue();

        Color bgColor = on ? ON_COLOR : OFF_COLOR;
        NVGWrapper.drawRect(nvg, toggleX, toggleY, TOGGLE_WIDTH, TOGGLE_HEIGHT, bgColor);

        float knobSize = TOGGLE_HEIGHT - KNOB_PADDING * 2;
        float knobX = on
                ? toggleX + TOGGLE_WIDTH - knobSize - KNOB_PADDING
                : toggleX + KNOB_PADDING;
        float knobY = toggleY + KNOB_PADDING;
        NVGWrapper.drawRect(nvg, knobX, knobY, knobSize, knobSize, KNOB_COLOR);
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
