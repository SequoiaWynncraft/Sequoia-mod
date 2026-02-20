package org.sequoia.seq.ui.widget;

import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.config.Setting;
import org.sequoia.seq.utils.rendering.nvg.NVGContext;
import org.sequoia.seq.utils.rendering.nvg.NVGWrapper;

import java.awt.*;

import static org.lwjgl.nanovg.NanoVG.*;

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
    public void render(long nvg, float mouseX, float mouseY) {
        cursorBlink++;
        String fontName = SeqClient.getFontManager().getSelectedFont();

        // Label
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        var labelColor = NVGContext.nvgColor(LABEL_COLOR);
        nvgFillColor(nvg, labelColor);
        nvgText(nvg, x + TEXT_BOX_MARGIN, y + 2, setting.getName());
        labelColor.free();

        // Text box
        float boxX = x + TEXT_BOX_MARGIN;
        float boxY = y + 18;
        float boxWidth = width - TEXT_BOX_MARGIN * 2;

        Color boxBg = editing ? TEXT_BOX_ACTIVE : TEXT_BOX_BG;
        NVGWrapper.drawRect(nvg, boxX, boxY, boxWidth, TEXT_BOX_HEIGHT, boxBg);
        if (editing) {
            NVGWrapper.drawRectOutline(nvg, boxX, boxY, boxWidth, TEXT_BOX_HEIGHT, 1, TEXT_BOX_BORDER);
        }

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);

        String displayText = editing ? editBuffer : setting.getValue();
        boolean isEmpty = !editing && (displayText == null || displayText.isEmpty());
        var textColor = NVGContext.nvgColor(isEmpty ? PLACEHOLDER_COLOR : TEXT_COLOR);
        nvgFillColor(nvg, textColor);

        // Clip text to box bounds
        nvgSave(nvg);
        nvgScissor(nvg, boxX, boxY, boxWidth, TEXT_BOX_HEIGHT);
        String renderText = isEmpty ? "..." : displayText;
        nvgText(nvg, boxX + 4, boxY + TEXT_BOX_HEIGHT / 2f, renderText);
        nvgRestore(nvg);

        textColor.free();

        // Draw cursor separately so it doesn't affect text width
        if (editing && (cursorBlink / 1000) % 2 == 0) {
            float[] textBounds = new float[4];
            float textW = nvgTextBounds(nvg, 0, 0, editBuffer.isEmpty() ? " " : editBuffer, textBounds);
            float cursorX = boxX + 4 + (editBuffer.isEmpty() ? 0 : textW) + 1;
            NVGWrapper.drawRect(nvg, cursorX, boxY + 3, 1, TEXT_BOX_HEIGHT - 6, TEXT_COLOR);
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != 0) return false;

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
        if (!editing) return false;

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

        // Accept printable characters via the character field if available
        char c = (char) keyEvent.key();
        // Use scancode-based approach for letters and common chars
        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            boolean shift = (keyEvent.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            char letter = (char) ('a' + (keyCode - GLFW.GLFW_KEY_A));
            editBuffer += shift ? Character.toUpperCase(letter) : letter;
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            editBuffer += (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_SPACE) {
            editBuffer += ' ';
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PERIOD) {
            editBuffer += '.';
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_MINUS) {
            editBuffer += '-';
            return true;
        }

        return true;
    }

    private void applyEditBuffer() {
        setting.setValue(editBuffer);
    }
}