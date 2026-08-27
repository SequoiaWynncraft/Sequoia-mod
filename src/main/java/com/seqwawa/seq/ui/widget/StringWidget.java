package com.seqwawa.seq.ui.widget;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import java.awt.Color;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;


public class StringWidget extends SettingWidget<Setting.StringSetting> {
    private static final float FONT_SIZE = 12;
    private static final float TEXT_BOX_HEIGHT = 18;
    private static final float TEXT_BOX_MARGIN = 8;

    private boolean editing = false;
    private String editBuffer = "";
    private int cursorBlink = 0;

    public StringWidget(Setting.StringSetting setting) {
        super(setting);
        this.height = 40;
    }

    @Override
    public void render(UiCanvas canvas, float mouseX, float mouseY) {
        boolean enabled = prepareEnabledState();
        cursorBlink++;
        String fontName = SeqClient.getFontManager().getSelectedFont();

        drawParentGuide(canvas, enabled);
        canvas.drawText(
                getDisplayName(),
                indentedContentX(TEXT_BOX_MARGIN),
                y + 2,
                textStyle(
                        fontName,
                        enabled ? color(TEXT_SECONDARY) : color(TEXT_DISABLED),
                        UiCanvas.VerticalAlign.TOP));

        // Text box
        float boxX = indentedContentX(TEXT_BOX_MARGIN);
        float boxY = y + 18;
        float boxWidth = indentedContentWidth(TEXT_BOX_MARGIN);

        Color boxBg = !enabled
                ? color(CONTROL_INPUT_SECONDARY, 120)
                : editing ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT, 200);
        canvas.fillRect(boxX, boxY, boxWidth, TEXT_BOX_HEIGHT, boxBg);
        if (enabled && editing) {
            canvas.strokeRect(boxX, boxY, boxWidth, TEXT_BOX_HEIGHT, 1, color(CONTROL_BORDER));
        }

        String displayText = editing ? editBuffer : setting.getValue();
        boolean isEmpty = !editing && (displayText == null || displayText.isEmpty());

        // Clip text to box bounds
        canvas.save();
        canvas.scissor(boxX, boxY, boxWidth, TEXT_BOX_HEIGHT);
        String renderText = isEmpty ? "..." : displayText;
        canvas.drawText(renderText, boxX + 4, boxY + TEXT_BOX_HEIGHT / 2f,
                textStyle(
                        fontName,
                        !enabled || isEmpty ? color(TEXT_DISABLED, 180) : color(TEXT_PRIMARY),
                        UiCanvas.VerticalAlign.MIDDLE));
        canvas.restore();

        // Draw cursor separately so it doesn't affect text width
        if (enabled && editing && (cursorBlink / 1000) % 2 == 0) {
            float textW = UiRenderer.measureText(
                    editBuffer.isEmpty() ? " " : editBuffer, fontName, FONT_SIZE).width();
            float cursorX = boxX + 4 + (editBuffer.isEmpty() ? 0 : textW) + 1;
            canvas.fillRect(cursorX, boxY + 3, 1, TEXT_BOX_HEIGHT - 6, color(TEXT_PRIMARY));
        }
    }

    private static UiCanvas.TextStyle textStyle(String font, Color color, UiCanvas.VerticalAlign verticalAlign) {
        return new UiCanvas.TextStyle(
                font, FONT_SIZE, color, UiCanvas.HorizontalAlign.LEFT, verticalAlign);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!prepareEnabledState() || button != 0)
            return false;

        float boxX = indentedContentX(TEXT_BOX_MARGIN);
        float boxY = y + 18;
        float boxWidth = indentedContentWidth(TEXT_BOX_MARGIN);

        if (isHovered(mouseX, mouseY, boxX, boxY, boxWidth, TEXT_BOX_HEIGHT)) {
            editing = true;
            editBuffer = setting.getValue() != null ? setting.getValue() : "";
            cursorBlink = 0;
            return true;
        }

        if (editing) {
            applyEditBuffer();
            editing = false;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (!prepareEnabledState()) {
            return false;
        }
        if (!editing)
            return false;

        int keyCode = keyEvent.key();

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyEditBuffer();
            editing = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            editing = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!editBuffer.isEmpty()) {
                editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            }
            return true;
        }
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        if (!prepareEnabledState()) {
            return false;
        }
        if (!editing) {
            return false;
        }
        String typedText = TextInputHelper.getTypedText(characterEvent);
        if (typedText != null) {
            editBuffer += typedText;
        }
        return true;
    }

    private void applyEditBuffer() {
        setting.setValue(editBuffer);
    }

    private boolean prepareEnabledState() {
        boolean enabled = isEnabled();
        if (!enabled) {
            editing = false;
            editBuffer = setting.getValue() != null ? setting.getValue() : "";
        }
        return enabled;
    }
}
