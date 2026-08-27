package com.seqwawa.seq.ui.widget;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import java.awt.Color;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import com.seqwawa.seq.utils.rendering.UiRenderer;


public class SliderWidget extends SettingWidget<Setting<?>> {
    private static final float SLIDER_HEIGHT = 8;
    private static final float KNOB_RADIUS = 6;
    private static final float FONT_SIZE = 12;
    private static final float TEXT_BOX_WIDTH = 50;
    private static final float TEXT_BOX_HEIGHT = 18;
    private static final float CONTROL_GAP = 8;
    private static final float COMPACT_SLIDER_WIDTH_RATIO = 0.25f;

    private boolean dragging = false;
    private boolean editing = false;
    private String editBuffer = "";
    private int cursorBlink = 0;

    private final double min;
    private final double max;
    private final double increment;
    private final boolean isInteger;
    private final float sliderWidthRatio;

    public SliderWidget(Setting.IntSetting setting) {
        this(setting, false);
    }

    public SliderWidget(Setting.IntSetting setting, boolean compact) {
        this(setting, compact ? COMPACT_SLIDER_WIDTH_RATIO : 1f);
    }

    public SliderWidget(Setting.IntSetting setting, float sliderWidthRatio) {
        super(setting);
        this.min = setting.getMin();
        this.max = setting.getMax();
        this.increment = setting.getIncrement();
        this.isInteger = true;
        this.sliderWidthRatio = Math.max(0f, Math.min(1f, sliderWidthRatio));
        this.height = 40;
    }

    public SliderWidget(Setting.DoubleSetting setting) {
        super(setting);
        this.min = setting.getMin();
        this.max = setting.getMax();
        this.increment = setting.getIncrement();
        this.isInteger = false;
        this.sliderWidthRatio = 1f;
        this.height = 40;
    }

    public SliderWidget(Setting.FloatSetting setting) {
        super(setting);
        this.min = setting.getMin();
        this.max = setting.getMax();
        this.increment = setting.getIncrement();
        this.isInteger = false;
        this.sliderWidthRatio = 1f;
        this.height = 40;
    }

    private double getDoubleValue() {
        Object val = setting.getValue();
        if (val instanceof Integer i)
            return i;
        if (val instanceof Double d)
            return d;
        if (val instanceof Float f)
            return f;
        return 0;
    }

    private void setSliderValue(double val) {
        val = Math.max(min, Math.min(max, val));
        if (increment > 0) {
            val = Math.round(val / increment) * increment;
            val = Math.max(min, Math.min(max, val));
        }
        if (setting instanceof Setting.IntSetting s) {
            s.setValue((int) Math.round(val));
        } else if (setting instanceof Setting.DoubleSetting s) {
            s.setValue(val);
        } else if (setting instanceof Setting.FloatSetting s) {
            s.setValue((float) val);
        }
    }

    private void setManualValue(double value) {
        if (!Double.isFinite(value)) {
            return;
        }
        if (setting instanceof Setting.IntSetting s) {
            long rounded = Math.round(value);
            if (rounded >= Integer.MIN_VALUE && rounded <= Integer.MAX_VALUE) {
                s.setValueFromManualInput((int) rounded);
            }
        } else if (setting instanceof Setting.DoubleSetting s) {
            s.setValueFromManualInput(value);
        } else if (setting instanceof Setting.FloatSetting s) {
            float floatValue = (float) value;
            if (Float.isFinite(floatValue)) {
                s.setValueFromManualInput(floatValue);
            }
        }
    }

    private String formatValue(double val) {
        if (isInteger)
            return String.valueOf((int) Math.round(val));
        return String.format("%.2f", val);
    }

    @Override
    public void render(UiCanvas canvas, float mouseX, float mouseY) {
        boolean enabled = prepareEnabledState();
        cursorBlink++;
        String fontName = SeqClient.getFontManager().getSelectedFont();

        drawParentGuide(canvas, enabled);
        canvas.drawText(
                getDisplayName(),
                indentedContentX(8),
                y + 2,
                textStyle(
                        fontName,
                        enabled ? color(TEXT_SECONDARY) : color(TEXT_DISABLED),
                        UiCanvas.HorizontalAlign.LEFT,
                        UiCanvas.VerticalAlign.TOP));

        SliderLayout layout = layout();

        // Slider track
        float trackY = layout.sliderY() + (SLIDER_HEIGHT - 4) / 2f;
        canvas.fillRect(
                layout.sliderX(),
                trackY,
                layout.sliderWidth(),
                4,
                enabled ? color(CONTROL_INPUT_SECONDARY) : color(CONTROL_INPUT_SECONDARY, 120));

        // Slider fill
        double value = getDoubleValue();
        float ratio = (float) ((value - min) / (max - min));
        ratio = Math.max(0, Math.min(1, ratio));
        float fillWidth = layout.sliderWidth() * ratio;
        canvas.fillRect(
                layout.sliderX(),
                trackY,
                fillWidth,
                4,
                enabled ? color(ACCENT_PRIMARY) : color(CONTROL_INPUT_SECONDARY, 120));

        // Knob
        float knobX = layout.sliderX() + fillWidth;
        float knobY = layout.sliderY() + SLIDER_HEIGHT / 2f - KNOB_RADIUS / 2f;
        canvas.fillRect(knobX - KNOB_RADIUS, knobY - KNOB_RADIUS / 2, KNOB_RADIUS * 2, KNOB_RADIUS * 2,
                enabled ? color(TEXT_PRIMARY) : color(TEXT_DISABLED));

        // Text box
        Color boxBg = !enabled
                ? color(CONTROL_INPUT_SECONDARY, 120)
                : editing ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT, 200);
        canvas.fillRect(layout.textBoxX(), layout.textBoxY(), TEXT_BOX_WIDTH, TEXT_BOX_HEIGHT, boxBg);
        if (enabled && editing) {
            canvas.strokeRect(layout.textBoxX(), layout.textBoxY(), TEXT_BOX_WIDTH, TEXT_BOX_HEIGHT, 1,
                    color(CONTROL_BORDER));
        }

        String displayText = editing ? editBuffer : formatValue(value);
        canvas.drawText(displayText, layout.textBoxX() + TEXT_BOX_WIDTH / 2f,
                layout.textBoxY() + TEXT_BOX_HEIGHT / 2f,
                textStyle(
                        fontName,
                        enabled ? color(TEXT_PRIMARY) : color(TEXT_DISABLED),
                        UiCanvas.HorizontalAlign.CENTER,
                        UiCanvas.VerticalAlign.MIDDLE));

        // Draw cursor separately so it doesn't affect text width
        if (enabled && editing && (cursorBlink / 1000) % 2 == 0) {
            float textW = UiRenderer.measureText(editBuffer, fontName, FONT_SIZE).width();
            float cursorX = layout.textBoxX() + (TEXT_BOX_WIDTH + textW) / 2f + 1;
            canvas.fillRect(cursorX, layout.textBoxY() + 3, 1, TEXT_BOX_HEIGHT - 6, color(TEXT_PRIMARY));
        }
    }

    private static UiCanvas.TextStyle textStyle(
            String font,
            Color color,
            UiCanvas.HorizontalAlign horizontalAlign,
            UiCanvas.VerticalAlign verticalAlign) {
        return new UiCanvas.TextStyle(font, FONT_SIZE, color, horizontalAlign, verticalAlign);
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (!prepareEnabledState() || button != 0)
            return false;

        SliderLayout layout = layout();

        // Click on text box - enter edit mode
        if (isHovered(mouseX, mouseY, layout.textBoxX(), layout.textBoxY(), TEXT_BOX_WIDTH, TEXT_BOX_HEIGHT)) {
            editing = true;
            editBuffer = formatValue(getDoubleValue());
            cursorBlink = 0;
            return true;
        }

        // Click on slider area
        if (isHovered(mouseX, mouseY, layout.sliderX(), layout.sliderY() - KNOB_RADIUS,
                layout.sliderWidth(), SLIDER_HEIGHT + KNOB_RADIUS * 2)) {
            editing = false;
            dragging = true;
            updateValueFromMouse(mouseX, layout.sliderX(), layout.sliderWidth());
            return true;
        }

        // Click elsewhere exits editing
        if (editing) {
            applyEditBuffer();
            editing = false;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(float mouseX, float mouseY, int button) {
        if (!prepareEnabledState()) {
            return false;
        }
        if (dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(float mouseX, float mouseY) {
        if (!prepareEnabledState()) {
            return false;
        }
        if (dragging) {
            SliderLayout layout = layout();
            updateValueFromMouse(mouseX, layout.sliderX(), layout.sliderWidth());
            return true;
        }
        return false;
    }

    private SliderLayout layout() {
        float sliderX = indentedContentX(8);
        float fullSliderWidth = Math.max(1, width - TEXT_BOX_WIDTH - 24 - labelIndent());
        float sliderWidth = fullSliderWidth * sliderWidthRatio;
        float textBoxX = sliderWidthRatio < 1f
                ? sliderX + sliderWidth + CONTROL_GAP
                : x + width - TEXT_BOX_WIDTH - 8;
        return new SliderLayout(
                sliderX,
                y + 22,
                sliderWidth,
                textBoxX,
                y + (height - TEXT_BOX_HEIGHT) / 2f);
    }

    private void updateValueFromMouse(float mouseX, float sliderX, float sliderWidth) {
        float ratio = (mouseX - sliderX) / sliderWidth;
        ratio = Math.max(0, Math.min(1, ratio));
        double val = min + ratio * (max - min);
        setSliderValue(val);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (!prepareEnabledState()) {
            return false;
        }
        int keyCode = keyEvent.key();
        if (!editing)
            return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            applyEditBuffer();
            editing = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            editing = false;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !editBuffer.isEmpty()) {
            editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
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
        if (typedText != null && typedText.length() == 1) {
            char character = typedText.charAt(0);
            if (character >= '0' && character <= '9' || character == '.' || character == ',') {
                editBuffer += character == ',' ? '.' : character;
            } else if (character == '-' && editBuffer.isEmpty()) {
                editBuffer += character;
            }
        }
        return true;
    }

    private void applyEditBuffer() {
        try {
            double val = Double.parseDouble(editBuffer);
            setManualValue(val);
        } catch (NumberFormatException ignored) {
        }
    }

    private boolean prepareEnabledState() {
        boolean enabled = isEnabled();
        if (!enabled) {
            dragging = false;
            editing = false;
            editBuffer = formatValue(getDoubleValue());
        }
        return enabled;
    }

    private record SliderLayout(
            float sliderX,
            float sliderY,
            float sliderWidth,
            float textBoxX,
            float textBoxY) {
    }
}
