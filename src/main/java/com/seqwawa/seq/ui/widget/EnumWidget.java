package com.seqwawa.seq.ui.widget;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.rendering.UiCanvas;

import java.awt.*;

public class EnumWidget extends SettingWidget<Setting.EnumSetting<?>> {
    private static final float BUTTON_WIDTH = 100;
    private static final float BUTTON_HEIGHT = 18;
    private static final float FONT_SIZE = 12;

    private static final Color BUTTON_COLOR = new Color(50, 50, 60, 200);
    private static final Color BUTTON_HOVER = new Color(70, 70, 85, 220);
    private static final Color LABEL_COLOR = new Color(220, 220, 220, 255);
    private static final Color VALUE_COLOR = new Color(160, 130, 220, 255);

    public EnumWidget(Setting.EnumSetting<?> setting) {
        super(setting);
        this.height = 28;
    }

    @Override
    public void render(UiCanvas canvas, float mouseX, float mouseY) {
        String fontName = SeqClient.getFontManager().getSelectedFont();

        canvas.drawText(getDisplayName(), x + 8, y + height / 2f,
                textStyle(fontName, LABEL_COLOR, UiCanvas.HorizontalAlign.LEFT));

        // Button
        float btnX = x + width - BUTTON_WIDTH - 8;
        float btnY = y + (height - BUTTON_HEIGHT) / 2f;
        boolean hovered = isHovered(mouseX, mouseY, btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT);
        canvas.fillRect(btnX, btnY, BUTTON_WIDTH, BUTTON_HEIGHT, hovered ? BUTTON_HOVER : BUTTON_COLOR);
        canvas.drawText(setting.getValue().name(), btnX + BUTTON_WIDTH / 2f, btnY + BUTTON_HEIGHT / 2f,
                textStyle(fontName, VALUE_COLOR, UiCanvas.HorizontalAlign.CENTER));
    }

    private static UiCanvas.TextStyle textStyle(
            String font, Color color, UiCanvas.HorizontalAlign horizontalAlign) {
        return new UiCanvas.TextStyle(
                font, FONT_SIZE, color, horizontalAlign, UiCanvas.VerticalAlign.MIDDLE);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != 0)
            return false;

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
