package com.seqwawa.seq.ui.widget;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.nvg.NVGContext;
import com.seqwawa.seq.utils.rendering.nvg.NVGWrapper;
import java.awt.Color;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import static org.lwjgl.nanovg.NanoVG.*;

public class ColorWidget extends SettingWidget<Setting.ColorSetting> {
    private static final float COLLAPSED_HEIGHT = 42;
    private static final float EXPANDED_HEIGHT = 146;
    private static final float FONT_SIZE = 12;
    private static final float MARGIN = 8;
    private static final float CONTROL_Y_OFFSET = 18;
    private static final float CONTROL_HEIGHT = 18;
    private static final float HEX_BOX_WIDTH = 96;
    private static final float SWATCH_WIDTH = 28;
    private static final float PREVIEW_BUTTON_WIDTH = 72;
    private static final float CONTROL_GAP = 6;
    private static final float PICKER_Y_OFFSET = 44;
    private static final float SATURATION_VALUE_HEIGHT = 72;
    private static final float HUE_BAR_GAP = 7;
    private static final float HUE_BAR_HEIGHT = 12;

    private static final Color LABEL_COLOR = new Color(220, 220, 220, 255);
    private static final Color TEXT_BOX_BG = new Color(30, 30, 40, 200);
    private static final Color TEXT_BOX_ACTIVE = new Color(50, 50, 70, 220);
    private static final Color TEXT_BOX_BORDER = new Color(130, 100, 200, 180);
    private static final Color INVALID_BORDER = new Color(225, 75, 85, 230);
    private static final Color TEXT_COLOR = new Color(255, 255, 255, 255);
    private static final Color BUTTON_BG = new Color(40, 40, 54, 220);
    private static final Color BUTTON_ACTIVE = new Color(85, 55, 145, 230);
    private static final Color PICKER_BORDER = new Color(15, 15, 20, 255);

    private final Consumer<Boolean> previewStateConsumer;
    private boolean editing;
    private boolean expanded;
    private boolean previewActive;
    private boolean draggingSaturationValue;
    private boolean draggingHue;
    private String editBuffer;
    private float hue;
    private float saturation;
    private float brightness;
    private int cursorBlink;

    public ColorWidget(Setting.ColorSetting setting, Consumer<Boolean> previewStateConsumer) {
        super(setting);
        this.previewStateConsumer = previewStateConsumer;
        this.editBuffer = hexDigits(setting);
        syncPickerFromSetting();
        this.height = COLLAPSED_HEIGHT;
    }

    @Override
    public void render(long nvg, float mouseX, float mouseY) {
        cursorBlink++;
        String fontName = SeqClient.getFontManager().getSelectedFont();

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        var labelColor = NVGContext.nvgColor(LABEL_COLOR);
        nvgFillColor(nvg, labelColor);
        nvgText(nvg, x + MARGIN, y + 2, getDisplayName());
        labelColor.free();

        drawHexInput(nvg, fontName);
        drawSwatch(nvg);
        if (previewStateConsumer != null) {
            drawPreviewButton(nvg, fontName);
        }

        if (expanded) {
            drawColorPicker(nvg);
        }
    }

    private void drawHexInput(long nvg, String fontName) {
        float boxX = hexBoxX();
        float boxY = controlY();
        Color boxColor = editing ? TEXT_BOX_ACTIVE : TEXT_BOX_BG;
        NVGWrapper.drawRect(nvg, boxX, boxY, HEX_BOX_WIDTH, CONTROL_HEIGHT, boxColor);

        boolean validBuffer = Setting.ColorSetting.isValidHex(editBuffer);
        if (editing || !validBuffer) {
            NVGWrapper.drawRectOutline(
                    nvg,
                    boxX,
                    boxY,
                    HEX_BOX_WIDTH,
                    CONTROL_HEIGHT,
                    1,
                    validBuffer ? TEXT_BOX_BORDER : INVALID_BORDER);
        }

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        var textColor = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, textColor);
        String renderedValue = "#" + (editing ? editBuffer : hexDigits(setting));
        nvgSave(nvg);
        nvgScissor(nvg, boxX, boxY, HEX_BOX_WIDTH, CONTROL_HEIGHT);
        nvgText(nvg, boxX + 5, boxY + CONTROL_HEIGHT / 2f, renderedValue);
        nvgRestore(nvg);
        textColor.free();

        if (editing && (cursorBlink / 1000) % 2 == 0) {
            float[] bounds = new float[4];
            float textWidth = nvgTextBounds(nvg, 0, 0, "#" + editBuffer, bounds);
            NVGWrapper.drawRect(
                    nvg,
                    boxX + 5 + textWidth + 1,
                    boxY + 3,
                    1,
                    CONTROL_HEIGHT - 6,
                    TEXT_COLOR);
        }
    }

    private void drawSwatch(long nvg) {
        float swatchX = swatchX();
        float swatchY = controlY();
        Color selected = new Color(setting.getValue());
        NVGWrapper.drawRect(nvg, swatchX, swatchY, SWATCH_WIDTH, CONTROL_HEIGHT, selected);
        NVGWrapper.drawRectOutline(
                nvg,
                swatchX,
                swatchY,
                SWATCH_WIDTH,
                CONTROL_HEIGHT,
                expanded ? 2 : 1,
                expanded ? TEXT_BOX_BORDER : PICKER_BORDER);
    }

    private void drawPreviewButton(long nvg, String fontName) {
        float buttonX = previewButtonX();
        float buttonY = controlY();
        NVGWrapper.drawRect(
                nvg,
                buttonX,
                buttonY,
                PREVIEW_BUTTON_WIDTH,
                CONTROL_HEIGHT,
                previewActive ? BUTTON_ACTIVE : BUTTON_BG);

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, FONT_SIZE - 1);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var textColor = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, textColor);
        nvgText(
                nvg,
                buttonX + PREVIEW_BUTTON_WIDTH / 2f,
                buttonY + CONTROL_HEIGHT / 2f,
                previewActive ? "Preview on" : "Preview");
        textColor.free();
    }

    private void drawColorPicker(long nvg) {
        float pickerX = pickerX();
        float pickerY = pickerY();
        float pickerWidth = pickerWidth();
        Color pureHue = new Color(Color.HSBtoRGB(hue, 1f, 1f));

        NVGWrapper.drawHorizontalGradient(
                nvg,
                pickerX,
                pickerY,
                pickerWidth,
                SATURATION_VALUE_HEIGHT,
                Color.WHITE,
                pureHue);
        NVGWrapper.drawVerticalGradient(
                nvg,
                pickerX,
                pickerY,
                pickerWidth,
                SATURATION_VALUE_HEIGHT,
                new Color(0, 0, 0, 0),
                Color.BLACK);
        NVGWrapper.drawRectOutline(
                nvg, pickerX, pickerY, pickerWidth, SATURATION_VALUE_HEIGHT, 1, PICKER_BORDER);

        float markerX = pickerX + saturation * pickerWidth;
        float markerY = pickerY + (1f - brightness) * SATURATION_VALUE_HEIGHT;
        NVGWrapper.drawRectOutline(nvg, markerX - 3, markerY - 3, 6, 6, 1, Color.BLACK);
        NVGWrapper.drawRectOutline(nvg, markerX - 2, markerY - 2, 4, 4, 1, Color.WHITE);

        float hueY = hueBarY();
        Color[] hueStops = {
                Color.RED,
                Color.YELLOW,
                Color.GREEN,
                Color.CYAN,
                Color.BLUE,
                Color.MAGENTA,
                Color.RED
        };
        float sectionWidth = pickerWidth / (hueStops.length - 1);
        for (int i = 0; i < hueStops.length - 1; i++) {
            NVGWrapper.drawHorizontalGradient(
                    nvg,
                    pickerX + sectionWidth * i,
                    hueY,
                    sectionWidth,
                    HUE_BAR_HEIGHT,
                    hueStops[i],
                    hueStops[i + 1]);
        }
        NVGWrapper.drawRectOutline(nvg, pickerX, hueY, pickerWidth, HUE_BAR_HEIGHT, 1, PICKER_BORDER);

        float hueMarkerX = pickerX + hue * pickerWidth;
        NVGWrapper.drawRectOutline(
                nvg,
                hueMarkerX - 2,
                hueY - 2,
                4,
                HUE_BAR_HEIGHT + 4,
                1,
                Color.WHITE);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != 0) {
            return false;
        }

        if (isHovered(mouseX, mouseY, hexBoxX(), controlY(), HEX_BOX_WIDTH, CONTROL_HEIGHT)) {
            editing = true;
            editBuffer = hexDigits(setting);
            cursorBlink = 0;
            return true;
        }

        if (isHovered(mouseX, mouseY, swatchX(), controlY(), SWATCH_WIDTH, CONTROL_HEIGHT)) {
            finishEditing();
            expanded = !expanded;
            height = expanded ? EXPANDED_HEIGHT : COLLAPSED_HEIGHT;
            return true;
        }

        if (previewStateConsumer != null && isHovered(
                mouseX,
                mouseY,
                previewButtonX(),
                controlY(),
                PREVIEW_BUTTON_WIDTH,
                CONTROL_HEIGHT)) {
            finishEditing();
            previewActive = !previewActive;
            previewStateConsumer.accept(previewActive);
            return true;
        }

        if (expanded && isHovered(
                mouseX,
                mouseY,
                pickerX(),
                pickerY(),
                pickerWidth(),
                SATURATION_VALUE_HEIGHT)) {
            finishEditing();
            draggingSaturationValue = true;
            updateSaturationValue(mouseX, mouseY);
            return true;
        }

        if (expanded && isHovered(
                mouseX,
                mouseY,
                pickerX(),
                hueBarY(),
                pickerWidth(),
                HUE_BAR_HEIGHT)) {
            finishEditing();
            draggingHue = true;
            updateHue(mouseX);
            return true;
        }

        if (editing) {
            finishEditing();
        }
        return false;
    }

    @Override
    public boolean mouseReleased(float mouseX, float mouseY, int button) {
        if (button != 0) {
            return false;
        }
        boolean wasDragging = draggingSaturationValue || draggingHue;
        draggingSaturationValue = false;
        draggingHue = false;
        return wasDragging;
    }

    @Override
    public boolean mouseDragged(float mouseX, float mouseY) {
        if (draggingSaturationValue) {
            updateSaturationValue(mouseX, mouseY);
            return true;
        }
        if (draggingHue) {
            updateHue(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (!editing) {
            return false;
        }

        int keyCode = keyEvent.key();
        boolean shortcutModifier =
                (keyEvent.modifiers() & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SUPER)) != 0;

        if (shortcutModifier && keyCode == GLFW.GLFW_KEY_A) {
            editBuffer = "";
            return true;
        }
        if (shortcutModifier && keyCode == GLFW.GLFW_KEY_V) {
            pasteHexValue(SeqClient.mc.keyboardHandler.getClipboard());
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (Setting.ColorSetting.isValidHex(editBuffer)) {
                finishEditing();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            editBuffer = hexDigits(setting);
            editing = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!editBuffer.isEmpty()) {
                editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
            }
            applyValidEditBuffer();
            return true;
        }

        Character typedCharacter = TextInputHelper.getTypedCharacter(keyEvent);
        if (typedCharacter != null
                && Character.digit(typedCharacter, 16) >= 0
                && editBuffer.length() < 6) {
            editBuffer += Character.toUpperCase(typedCharacter);
            applyValidEditBuffer();
            return true;
        }
        return true;
    }

    public void deactivatePreview() {
        if (!previewActive) {
            return;
        }
        previewActive = false;
        if (previewStateConsumer != null) {
            previewStateConsumer.accept(false);
        }
    }

    private void pasteHexValue(String clipboardValue) {
        String normalized = Setting.ColorSetting.normalizeHex(clipboardValue);
        if (normalized == null) {
            String pasted = clipboardValue == null
                    ? ""
                    : clipboardValue.trim().replaceFirst("^#", "").toUpperCase(Locale.ROOT);
            editBuffer = pasted.substring(0, Math.min(6, pasted.length()));
            return;
        }

        editBuffer = normalized.substring(1);
        applyValidEditBuffer();
    }

    private void applyValidEditBuffer() {
        if (!Setting.ColorSetting.isValidHex(editBuffer)) {
            return;
        }
        setting.setHexValue(editBuffer);
        syncPickerFromSetting();
    }

    private void finishEditing() {
        if (editing && Setting.ColorSetting.isValidHex(editBuffer)) {
            setting.setHexValue(editBuffer);
            syncPickerFromSetting();
        } else {
            editBuffer = hexDigits(setting);
        }
        editing = false;
    }

    private void updateSaturationValue(float mouseX, float mouseY) {
        saturation = clamp01((mouseX - pickerX()) / pickerWidth());
        brightness = 1f - clamp01((mouseY - pickerY()) / SATURATION_VALUE_HEIGHT);
        applyPickerColor();
    }

    private void updateHue(float mouseX) {
        hue = clamp01((mouseX - pickerX()) / pickerWidth());
        applyPickerColor();
    }

    private void applyPickerColor() {
        setting.setValue(Color.HSBtoRGB(hue, saturation, brightness));
        editBuffer = hexDigits(setting);
    }

    private void syncPickerFromSetting() {
        int rgb = setting.getValue();
        float[] hsb = Color.RGBtoHSB((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, null);
        hue = hsb[0];
        saturation = hsb[1];
        brightness = hsb[2];
    }

    private static String hexDigits(Setting.ColorSetting setting) {
        return setting.getHexValue().substring(1);
    }

    private float hexBoxX() {
        return x + MARGIN;
    }

    private float controlY() {
        return y + CONTROL_Y_OFFSET;
    }

    private float swatchX() {
        return hexBoxX() + HEX_BOX_WIDTH + CONTROL_GAP;
    }

    private float previewButtonX() {
        return x + width - MARGIN - PREVIEW_BUTTON_WIDTH;
    }

    private float pickerX() {
        return x + MARGIN;
    }

    private float pickerY() {
        return y + PICKER_Y_OFFSET;
    }

    private float pickerWidth() {
        return Math.max(100, Math.min(240, width - MARGIN * 2));
    }

    private float hueBarY() {
        return pickerY() + SATURATION_VALUE_HEIGHT + HUE_BAR_GAP;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
