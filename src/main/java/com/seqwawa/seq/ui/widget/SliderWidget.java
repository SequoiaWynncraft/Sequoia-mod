package com.seqwawa.seq.ui.widget;

import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.utils.rendering.nvg.NVGContext;
import com.seqwawa.seq.utils.rendering.nvg.NVGWrapper;

import java.awt.*;

import static org.lwjgl.nanovg.NanoVG.*;

public class SliderWidget extends SettingWidget<Setting<?>> {
    private static final float SLIDER_HEIGHT = 8;
    private static final float KNOB_RADIUS = 6;
    private static final float FONT_SIZE = 12;
    private static final float TEXT_BOX_WIDTH = 50;
    private static final float TEXT_BOX_HEIGHT = 18;
    private static final float CONTROL_GAP = 8;
    private static final float COMPACT_SLIDER_WIDTH_RATIO = 0.25f;

    private static final Color TRACK_COLOR = new Color(50, 50, 60, 200);
    private static final Color FILL_COLOR = new Color(160, 130, 220, 255);
    private static final Color KNOB_COLOR = new Color(255, 255, 255, 255);
    private static final Color LABEL_COLOR = new Color(220, 220, 220, 255);
    private static final Color TEXT_BOX_BG = new Color(30, 30, 40, 200);
    private static final Color TEXT_BOX_ACTIVE = new Color(50, 50, 70, 220);
    private static final Color TEXT_BOX_BORDER = new Color(130, 100, 200, 180);
    private static final Color TEXT_COLOR = new Color(255, 255, 255, 255);

    private boolean dragging = false;
    private boolean editing = false;
    private String editBuffer = "";
    private int cursorBlink = 0;

    private final double min;
    private final double max;
    private final double increment;
    private final boolean isInteger;
    private final boolean compact;

    public SliderWidget(Setting.IntSetting setting) {
        this(setting, false);
    }

    public SliderWidget(Setting.IntSetting setting, boolean compact) {
        super(setting);
        this.min = setting.getMin();
        this.max = setting.getMax();
        this.increment = setting.getIncrement();
        this.isInteger = true;
        this.compact = compact;
        this.height = 40;
    }

    public SliderWidget(Setting.DoubleSetting setting) {
        super(setting);
        this.min = setting.getMin();
        this.max = setting.getMax();
        this.increment = setting.getIncrement();
        this.isInteger = false;
        this.compact = false;
        this.height = 40;
    }

    public SliderWidget(Setting.FloatSetting setting) {
        super(setting);
        this.min = setting.getMin();
        this.max = setting.getMax();
        this.increment = setting.getIncrement();
        this.isInteger = false;
        this.compact = false;
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
    public void render(long nvg, float mouseX, float mouseY) {
        cursorBlink++;
        String fontName = SeqClient.getFontManager().getSelectedFont();

        // Label
        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_TOP);
        var labelColor = NVGContext.nvgColor(LABEL_COLOR);
        nvgFillColor(nvg, labelColor);
        nvgText(nvg, x + 8, y + 2, getDisplayName());
        labelColor.free();

        SliderLayout layout = layout();

        // Slider track
        float trackY = layout.sliderY() + (SLIDER_HEIGHT - 4) / 2f;
        NVGWrapper.drawRect(nvg, layout.sliderX(), trackY, layout.sliderWidth(), 4, TRACK_COLOR);

        // Slider fill
        double value = getDoubleValue();
        float ratio = (float) ((value - min) / (max - min));
        ratio = Math.max(0, Math.min(1, ratio));
        float fillWidth = layout.sliderWidth() * ratio;
        NVGWrapper.drawRect(nvg, layout.sliderX(), trackY, fillWidth, 4, FILL_COLOR);

        // Knob
        float knobX = layout.sliderX() + fillWidth;
        float knobY = layout.sliderY() + SLIDER_HEIGHT / 2f - KNOB_RADIUS / 2f;
        NVGWrapper.drawRect(nvg, knobX - KNOB_RADIUS, knobY - KNOB_RADIUS / 2, KNOB_RADIUS * 2, KNOB_RADIUS * 2,
                KNOB_COLOR);

        // Text box
        Color boxBg = editing ? TEXT_BOX_ACTIVE : TEXT_BOX_BG;
        NVGWrapper.drawRect(nvg, layout.textBoxX(), layout.textBoxY(), TEXT_BOX_WIDTH, TEXT_BOX_HEIGHT, boxBg);
        if (editing) {
            NVGWrapper.drawRectOutline(
                    nvg, layout.textBoxX(), layout.textBoxY(), TEXT_BOX_WIDTH, TEXT_BOX_HEIGHT, 1, TEXT_BOX_BORDER);
        }

        nvgFontFace(nvg, fontName);
        nvgFontSize(nvg, FONT_SIZE);
        nvgTextAlign(nvg, NVG_ALIGN_CENTER | NVG_ALIGN_MIDDLE);
        var textColor = NVGContext.nvgColor(TEXT_COLOR);
        nvgFillColor(nvg, textColor);

        String displayText = editing ? editBuffer : formatValue(value);
        nvgText(nvg, layout.textBoxX() + TEXT_BOX_WIDTH / 2f,
                layout.textBoxY() + TEXT_BOX_HEIGHT / 2f, displayText);
        textColor.free();

        // Draw cursor separately so it doesn't affect text width
        if (editing && (cursorBlink / 1000) % 2 == 0) {
            float[] textBounds = new float[4];
            nvgTextAlign(nvg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
            float textW = nvgTextBounds(nvg, 0, 0, editBuffer, textBounds);
            float cursorX = layout.textBoxX() + (TEXT_BOX_WIDTH + textW) / 2f + 1;
            NVGWrapper.drawRect(nvg, cursorX, layout.textBoxY() + 3, 1, TEXT_BOX_HEIGHT - 6, TEXT_COLOR);
        }
    }

    @Override
    public boolean mouseClicked(float mouseX, float mouseY, int button) {
        if (button != 0)
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
        if (dragging) {
            dragging = false;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(float mouseX, float mouseY) {
        if (dragging) {
            SliderLayout layout = layout();
            updateValueFromMouse(mouseX, layout.sliderX(), layout.sliderWidth());
            return true;
        }
        return false;
    }

    private SliderLayout layout() {
        float sliderX = x + 8;
        float fullSliderWidth = Math.max(1, width - TEXT_BOX_WIDTH - 24);
        float sliderWidth = compact ? fullSliderWidth * COMPACT_SLIDER_WIDTH_RATIO : fullSliderWidth;
        float textBoxX = compact
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
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            editBuffer += (char) ('0' + (keyCode - GLFW.GLFW_KEY_0));
            return true;
        }

        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            editBuffer += (char) ('0' + (keyCode - GLFW.GLFW_KEY_KP_0));
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_PERIOD || keyCode == GLFW.GLFW_KEY_KP_DECIMAL) {
            editBuffer += '.';
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_KP_SUBTRACT) {
            editBuffer += '-';
            return true;
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

    private record SliderLayout(
            float sliderX,
            float sliderY,
            float sliderWidth,
            float textBoxX,
            float textBoxY) {
    }
}
