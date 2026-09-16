package com.seqwawa.seq.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.config.Setting;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class SliderWidgetTest {

    @Test
    void disabledDependentSliderRejectsClicks() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "test", false);
        Setting.IntSetting setting = new Setting.IntSetting("amount", "test", 50, 0, 100);
        setting.setParentSetting(parent);
        SliderWidget widget = new SliderWidget(setting);
        widget.setPosition(0, 0, 300, widget.getHeight());

        assertFalse(widget.mouseClicked(180, 14, 0), "slider track is disabled");
        assertFalse(widget.mouseClicked(270, 14, 0), "manual input is disabled");
        assertEquals(50, setting.getValue());
    }

    @Test
    void disablingSliderStopsDragAndDiscardsManualInput() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "test", true);
        Setting.IntSetting setting = new Setting.IntSetting("amount", "test", 50, 0, 100);
        setting.setParentSetting(parent);
        SliderWidget widget = new SliderWidget(setting);
        widget.setPosition(0, 0, 300, widget.getHeight());

        assertTrue(widget.mouseClicked(180, 14, 0));
        int valueAtDisable = setting.getValue();
        parent.setValue(false);
        assertFalse(widget.mouseDragged(230, 14));
        assertEquals(valueAtDisable, setting.getValue());

        parent.setValue(true);
        assertFalse(widget.mouseDragged(230, 14), "the old drag does not resume");
        assertTrue(widget.mouseClicked(270, 14, 0));
        assertTrue(widget.charTyped(new CharacterEvent('9', 0)));

        parent.setValue(false);
        assertFalse(widget.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
        assertEquals(valueAtDisable, setting.getValue(), "the unfinished manual value is discarded");

        parent.setValue(true);
        assertFalse(widget.keyPressed(
                new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)), "editing stays cleared after re-enable");
        assertEquals(valueAtDisable, setting.getValue());
    }
    @Test
    void hidingASliderStopsDraggingAndDiscardsManualInput() {
        Setting.IntSetting setting = new Setting.IntSetting("amount", "test", 50, 0, 100);
        SliderWidget widget = new SliderWidget(setting);
        widget.setPosition(0, 0, 300, widget.getHeight());
        assertTrue(widget.mouseClicked(180, 14, 0));
        int valueBeforeHiding = setting.getValue();

        widget.onHidden();

        assertFalse(widget.mouseDragged(230, 14));
        assertEquals(valueBeforeHiding, setting.getValue());
        assertTrue(widget.mouseClicked(270, 14, 0));
        assertTrue(widget.charTyped(new CharacterEvent('9', 0)));
        widget.onHidden();
        assertFalse(widget.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
        assertEquals(valueBeforeHiding, setting.getValue());
    }

    @Test
    void numericTypesShareDraggingAndManualEntry() {
        for (Setting<? extends Number> setting : java.util.List.of(
                new Setting.IntSetting("amount", "test", 25, 0, 100, 5),
                new Setting.DoubleSetting("amount", "test", .25, 0, 1, .05),
                new Setting.FloatSetting("amount", "test", .25f, 0, 1, .05f))) {
            SliderWidget widget = new SliderWidget(setting);
            widget.setPosition(0, 0, 300, widget.getHeight());
            assertTrue(widget.mouseClicked(180, 14, 0));
            assertTrue(widget.mouseDragged(-100, 14));
            assertEquals(0, setting.getValue().doubleValue(), .0001);
            assertTrue(widget.mouseDragged(1000, 14));
            assertEquals(setting instanceof Setting.IntSetting ? 100 : 1, setting.getValue().doubleValue(), .0001);
            widget.mouseReleased(1000, 14, 0);
            assertFalse(widget.mouseDragged(180, 14));
            assertTrue(widget.mouseClicked(270, 14, 0));
            for (int i = 0; i < 8; i++) widget.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
            String value = setting instanceof Setting.IntSetting ? "37" : "0.37";
            value.chars().forEach(c -> widget.charTyped(new CharacterEvent(c, 0)));
            widget.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertEquals(Double.parseDouble(value), setting.getValue().doubleValue(), .0001);
        }
    }

    @Test
    void toolbarHeightUsesSameSliderAndKeepsLabelOutsideHitArea() {
        var setting = new Setting.IntSetting("opacity", "test", 50, 0, 100, 5);
        var widget = new SliderWidget(setting, "Opacity %");
        widget.setPosition(0, 0, 240, 18);
        assertFalse(widget.mouseClicked(20, 9, 0));
        assertEquals(50, setting.getValue());
        assertTrue(widget.mouseClicked(130, 9, 0));
        assertTrue(widget.mouseDragged(1000, 9));
        assertEquals(100, setting.getValue());
        widget.mouseReleased(1000, 9, 0);
        assertTrue(widget.mouseClicked(210, 9, 0));
        widget.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, 0, 0));
        assertEquals(100, setting.getValue());
    }

    @Test
    void decimalEditingRoundTripsWithCommaLocale() {
        var previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.FRANCE);
            var setting = new Setting.DoubleSetting("amount", "test", .5, 0, 1);
            var widget = new SliderWidget(setting);
            widget.setPosition(0, 0, 300, widget.getHeight());
            assertTrue(widget.mouseClicked(270, 14, 0));
            widget.keyPressed(new KeyEvent(GLFW.GLFW_KEY_BACKSPACE, 0, 0));
            widget.charTyped(new CharacterEvent('7', 0));
            widget.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            assertEquals(.57, setting.getValue(), .0001);
        } finally {
            java.util.Locale.setDefault(previous);
        }
    }

}
