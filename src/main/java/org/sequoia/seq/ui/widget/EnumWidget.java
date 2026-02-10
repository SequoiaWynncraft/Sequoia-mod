package org.sequoia.seq.ui.widget;

import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.config.Setting;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

import java.awt.*;

import static org.lwjgl.nanovg.NanoVG.*;

public class EnumWidget extends SettingWidget<Setting.EnumSetting<?>> {
    private static final float BUTTON_WIDTH = 100;
    private static final float BUTTON_HEIGHT = 18;
    private static final float FONT_SIZE = 12;

    private static final Color BUTTON_COLOR = new Color(50, 50, 60, 200);
    private static final Color BUTTON_HOVER = new Color(70, 70, 85, 220);
    private static final Color LABEL_COLOR = new Color(220, 220, 220, 255);
    private static final Color VALUE_COLOR = new Color(80, 200, 120, 255);

    public EnumWidget(Setting.EnumSetting<?> setting) {
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

        // Button
        float btnX = x + width - BUTTON_WIDTH - 8;
        float btnY = y + (height - BUTTON_HEIGHT) / 2f;
        boolean hovered = isHovered(mouseX, mouseY, btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT);
        NVGWrapper.drawRoundedRect(nvg, btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, 3, hovered ? BUTTON_HOVER : BUTTON_COLOR);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var valColor = NVGContext.nvgColor(VALUE_COLOR);
        nvgFillColor(nvg, valColor);
        nvgText(nvg, btnX + BUTTON_WIDTH / 2f, btnY + BUTTON_HEIGHT / 2f, setting.getValue().name());
        valColor.free();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != 0) return false;

        float btnX = x + width - BUTTON_WIDTH - 8;
        float btnY = y + (height - BUTTON_HEIGHT) / 2f;

        if (isHovered(mouseX, mouseY, btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            cycleEnum((Setting.EnumSetting) setting);
            return true;
        }
        return false;
    }

    private <E extends Enum<E>> void cycleEnum(Setting.EnumSetting<E> enumSetting) {
        E[] constants = enumSetting.getEnumClass().getEnumConstants();
        int currentOrdinal = enumSetting.getValue().ordinal();
        int next = (currentOrdinal + 1) % constants.length;
        enumSetting.setValue(constants[next]);
    }
}
