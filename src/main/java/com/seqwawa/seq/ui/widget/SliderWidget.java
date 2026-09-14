package com.seqwawa.seq.ui.widget;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.ui.DropdownMenu;
import com.seqwawa.seq.utils.TextInputHelper;
import com.seqwawa.seq.utils.rendering.UiCanvas;
import com.seqwawa.seq.utils.rendering.UiRenderer;
import java.awt.Color;
import java.util.Locale;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

public final class SliderWidget extends SettingWidget<Setting<? extends Number>> {
    private static final float SLIDER_HEIGHT = 8;
    private static final float KNOB_RADIUS = 4;
    private static final float FONT_SIZE = 12;
    private static final float TEXT_BOX_WIDTH = 50;
    private static final float TEXT_BOX_HEIGHT = 18;
    private static final float CONTROL_GAP = 8;
    private static final float MAX_CONTROL_WIDTH = 240;

    private boolean dragging = false;
    private boolean editing = false;
    private String editBuffer = "";
    private int cursorBlink = 0;

    private final double min;
    private final double max;
    private final double increment;
    private final String displayName;

    public SliderWidget(Setting<? extends Number> setting) {
        this(setting, null);
    }

    public SliderWidget(Setting<? extends Number> setting, String displayName) {
        super(setting);
        Range range = switch (setting) {
            case Setting.IntSetting value -> new Range(value.getMin(), value.getMax(), value.getIncrement());
            case Setting.DoubleSetting value -> new Range(value.getMin(), value.getMax(), value.getIncrement());
            case Setting.FloatSetting value -> new Range(value.getMin(), value.getMax(), value.getIncrement());
            default -> throw new IllegalArgumentException("Unsupported numeric setting: " + setting.getClass().getName());
        };
        min = range.min();
        max = range.max();
        increment = range.increment();
        this.displayName = displayName;
        height = 28;
    }

    @Override
    protected String getDisplayName() {
        return displayName != null ? displayName : super.getDisplayName();
    }

    private double getDoubleValue() {
        return setting.getValue().doubleValue();
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
        if (setting instanceof Setting.IntSetting)
            return String.valueOf((int) Math.round(val));
        return String.format(Locale.ROOT, "%.2f", val);
    }

    @Override
    public void render(UiCanvas canvas, float mouseX, float mouseY) {
        boolean enabled = prepareEnabledState();
        cursorBlink++;
        String fontName = SeqClient.getFontManager().getSelectedFont();

        SliderLayout layout = layout();
        drawParentGuide(canvas, enabled);
        DropdownMenu.label(canvas, indentedContentX(8), y + height / 2f,
                Math.max(0, layout.sliderX() - KNOB_RADIUS - CONTROL_GAP - indentedContentX(8)),
                getDisplayName(), color(enabled ? TEXT_PRIMARY : TEXT_DISABLED), FONT_SIZE);

        // Slider track
        float trackY = layout.sliderY() + (SLIDER_HEIGHT - 4) / 2f;
        canvas.fillRect(
                layout.sliderX(),
                trackY,
                layout.sliderWidth(),
                4,
                color(CONTROL_INPUT_SECONDARY));

        // Slider fill
        double value = getDoubleValue();
        float ratio = max > min ? (float) ((value - min) / (max - min)) : 0;
        ratio = Math.max(0, Math.min(1, ratio));
        float fillWidth = layout.sliderWidth() * ratio;
        canvas.fillRect(
                layout.sliderX(),
                trackY,
                fillWidth,
                4,
                enabled ? color(ACCENT_PRIMARY) : color(CONTROL_INPUT_SECONDARY));

        // Knob
        float knobX = layout.sliderX() + fillWidth;
        float knobY = layout.sliderY() + SLIDER_HEIGHT / 2f;
        canvas.fillRect(knobX - KNOB_RADIUS, knobY - KNOB_RADIUS, KNOB_RADIUS * 2, KNOB_RADIUS * 2,
                enabled ? color(TEXT_PRIMARY) : color(TEXT_DISABLED));

        // Text box
        Color boxBg = !enabled
                ? color(CONTROL_INPUT_SECONDARY)
                : editing ? color(CONTROL_INPUT_HOVER) : color(CONTROL_INPUT);
        canvas.fillRect(layout.textBoxX(), layout.textBoxY(), layout.textBoxWidth(), TEXT_BOX_HEIGHT, boxBg);
        if (enabled && editing) {
            canvas.strokeRect(layout.textBoxX(), layout.textBoxY(), layout.textBoxWidth(), TEXT_BOX_HEIGHT, 1,
                    color(CONTROL_BORDER));
        }

        String displayText = editing ? editBuffer : formatValue(value);
        canvas.drawText(displayText, layout.textBoxX() + layout.textBoxWidth() / 2f,
                layout.textBoxY() + TEXT_BOX_HEIGHT / 2f,
                textStyle(
                        fontName,
                        enabled ? color(TEXT_PRIMARY) : color(TEXT_DISABLED),
                        UiCanvas.HorizontalAlign.CENTER,
                        UiCanvas.VerticalAlign.MIDDLE));

        // Draw cursor separately so it doesn't affect text width
        if (enabled && editing && (cursorBlink / 1000) % 2 == 0) {
            float textW = UiRenderer.measureText(editBuffer, fontName, FONT_SIZE).width();
            float cursorX = layout.textBoxX() + (layout.textBoxWidth() + textW) / 2f + 1;
            canvas.fillRect(cursorX, layout.textBoxY() + 3, 1, TEXT_BOX_HEIGHT - 6, color(TEXT_PRIMARY));
        }
    }

    private UiCanvas.TextStyle textStyle(
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
        if (isHovered(mouseX, mouseY, layout.textBoxX(), layout.textBoxY(), layout.textBoxWidth(), TEXT_BOX_HEIGHT)) {
            editing = true;
            editBuffer = formatValue(getDoubleValue());
            cursorBlink = 0;
            return true;
        }

        // Click on slider area
        if (isHovered(mouseX, mouseY, layout.sliderX() - KNOB_RADIUS, layout.sliderY() - KNOB_RADIUS,
                layout.sliderWidth() + KNOB_RADIUS * 2, SLIDER_HEIGHT + KNOB_RADIUS * 2)) {
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
        float available = indentedContentWidth(8);
        float controlWidth = Math.min(MAX_CONTROL_WIDTH, available * .6f);
        float valueWidth = Math.min(TEXT_BOX_WIDTH, controlWidth * .4f);
        float textBoxX = indentedContentX(8) + available - valueWidth;
        float sliderX = indentedContentX(8) + available - controlWidth + KNOB_RADIUS;
        float sliderWidth = Math.max(1, controlWidth - valueWidth - CONTROL_GAP - KNOB_RADIUS * 2);
        float centerY = y + height / 2f;
        return new SliderLayout(sliderX, centerY - SLIDER_HEIGHT / 2f, sliderWidth,
                textBoxX, centerY - TEXT_BOX_HEIGHT / 2f, valueWidth);
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

    @Override
    public void onHidden() {
        dragging = false;
        editing = false;
        editBuffer = formatValue(getDoubleValue());
    }

    private boolean prepareEnabledState() {
        boolean enabled = isEnabled();
        if (!enabled) {
            onHidden();
        }
        return enabled;
    }

    private record SliderLayout(
            float sliderX,
            float sliderY,
            float sliderWidth,
            float textBoxX,
            float textBoxY,
            float textBoxWidth) {
    }

    private record Range(double min, double max, double increment) {}
}
