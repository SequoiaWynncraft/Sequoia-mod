package com.seqwawa.seq.ui.widget;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.ACCENT_PRIMARY;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT_HOVER;
import static com.seqwawa.seq.ui.theme.UiColor.CONTROL_INPUT_SECONDARY;
import static com.seqwawa.seq.ui.theme.UiColor.TEXT_SECONDARY;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;
import java.util.List;

public final class ChoiceWidget extends SettingWidget<Setting.ChoiceSetting> {
    private static final float BUTTON_WIDTH = 140;
    private static final float BUTTON_HEIGHT = 18;
    private static final float FONT_SIZE = 12;

    public ChoiceWidget(Setting.ChoiceSetting setting) {
        super(setting);
        this.height = 28;
    }

    @Override
    public void render(UiCanvas canvas, float mouseX, float mouseY) {
        String fontName = SeqClient.getFontManager().getSelectedFont();
        canvas.drawText(getDisplayName(), x + 8, y + height / 2f,
                textStyle(fontName, color(TEXT_SECONDARY), UiCanvas.HorizontalAlign.LEFT));

        float buttonX = x + width - BUTTON_WIDTH - 8;
        float buttonY = y + (height - BUTTON_HEIGHT) / 2f;
        boolean hovered = isHovered(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        canvas.fillRect(
                buttonX,
                buttonY,
                BUTTON_WIDTH,
                BUTTON_HEIGHT,
                hovered ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT_SECONDARY));
        canvas.save();
        canvas.scissor(buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT);
        canvas.drawText(
                "<  " + toDisplayName(setting.getValue()) + "  >",
                buttonX + BUTTON_WIDTH / 2f,
                buttonY + BUTTON_HEIGHT / 2f,
                textStyle(fontName, color(ACCENT_PRIMARY), UiCanvas.HorizontalAlign.CENTER));
        canvas.restore();
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != 0) {
            return false;
        }
        float buttonX = x + width - BUTTON_WIDTH - 8;
        float buttonY = y + (height - BUTTON_HEIGHT) / 2f;
        if (!isHovered(mouseX, mouseY, buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)) {
            return false;
        }

        List<String> options = setting.getOptions();
        int currentIndex = options.indexOf(setting.getValue());
        setting.setValue(options.get((currentIndex + 1) % options.size()));
        return true;
    }

    private static UiCanvas.TextStyle textStyle(
            String font, Color color, UiCanvas.HorizontalAlign horizontalAlign) {
        return new UiCanvas.TextStyle(
                font, FONT_SIZE, color, horizontalAlign, UiCanvas.VerticalAlign.MIDDLE);
    }
}
