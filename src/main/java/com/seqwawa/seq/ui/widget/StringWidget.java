package com.seqwawa.seq.ui.widget;

import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;

import java.awt.*;

public class StringWidget extends SettingWidget<Setting.StringSetting> {
    private static final float FONT_SIZE = 12;
    private static final float TEXT_BOX_HEIGHT = 18;
    private static final float TEXT_BOX_MARGIN = 8;

    private static final Color LABEL_COLOR = new Color(220, 220, 220, 255);
    private static final Color TEXT_BOX_BG = new Color(30, 30, 40, 200);
    private static final Color TEXT_BOX_ACTIVE = new Color(50, 50, 70, 220);
    private static final Color TEXT_BOX_BORDER = new Color(130, 100, 200, 180);
    private static final Color TEXT_COLOR = new Color(255, 255, 255, 255);
    private static final Color PLACEHOLDER_COLOR = new Color(120, 120, 140, 180);

    private boolean editing = false;
    private String editBuffer = "";
    private int cursorBlink = 0;

    public StringWidget(Setting.StringSetting setting) {
        super(setting);
        this.height = 40;
    }

    @Override
    public void render(UiCanvas canvas, float mouseX, float mouseY) {
        cursorBlink++;
        String fontName = SeqClient.getFontManager().getSelectedFont();

        canvas.drawText(getDisplayName(), x + TEXT_BOX_MARGIN, y + 2,
                textStyle(fontName, LABEL_COLOR, UiCanvas.VerticalAlign.TOP));

        // Text box
        float boxX = x + TEXT_BOX_MARGIN;
        float boxY = y + 18;
        float boxWidth = width - TEXT_BOX_MARGIN * 2;

        Color boxBg = editing ? TEXT_BOX_ACTIVE : TEXT_BOX_BG;
        canvas.fillRect(boxX, boxY, boxWidth, TEXT_BOX_HEIGHT, boxBg);
        if (editing) {
            canvas.strokeRect(boxX, boxY, boxWidth, TEXT_BOX_HEIGHT, 1, TEXT_BOX_BORDER);
        }

        String displayText = editing ? editBuffer : setting.getValue();
        boolean isEmpty = !editing && (displayText == null || displayText.isEmpty());

        // Clip text to box bounds
        canvas.save();
        canvas.scissor(boxX, boxY, boxWidth, TEXT_BOX_HEIGHT);
        String renderText = isEmpty ? "..." : displayText;
        canvas.drawText(renderText, boxX + 4, boxY + TEXT_BOX_HEIGHT / 2f,
                textStyle(fontName, isEmpty ? PLACEHOLDER_COLOR : TEXT_COLOR, UiCanvas.VerticalAlign.MIDDLE));
        canvas.restore();

        // Draw cursor separately so it doesn't affect text width
        if (editing && (cursorBlink / 1000) % 2 == 0) {
            float textW = UiRenderer.measureText(
                    editBuffer.isEmpty() ? " " : editBuffer, fontName, FONT_SIZE).width();
            float cursorX = boxX + 4 + (editBuffer.isEmpty() ? 0 : textW) + 1;
            canvas.fillRect(cursorX, boxY + 3, 1, TEXT_BOX_HEIGHT - 6, TEXT_COLOR);
        }
    }

    private static UiCanvas.TextStyle textStyle(String font, Color color, UiCanvas.VerticalAlign verticalAlign) {
        return new UiCanvas.TextStyle(
                font, FONT_SIZE, color, UiCanvas.HorizontalAlign.LEFT, verticalAlign);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != 0)
            return false;

        float boxX = x + TEXT_BOX_MARGIN;
        float boxY = y + 18;
        float boxWidth = width - TEXT_BOX_MARGIN * 2;

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
        Character typedCharacter = TextInputHelper.getTypedCharacter(keyEvent);
        if (typedCharacter != null && TextInputHelper.isPrintableCharacter(typedCharacter)) {
            editBuffer += typedCharacter;
            return true;
        }

        return true;
    }

    private void applyEditBuffer() {
        setting.setValue(editBuffer);
    }
}
