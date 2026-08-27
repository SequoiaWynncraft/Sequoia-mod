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

        assertFalse(widget.mouseClicked(120, 24, 0), "slider track is disabled");
        assertFalse(widget.mouseClicked(250, 20, 0), "manual input is disabled");
        assertEquals(50, setting.getValue());
    }

    @Test
    void disablingSliderStopsDragAndDiscardsManualInput() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "test", true);
        Setting.IntSetting setting = new Setting.IntSetting("amount", "test", 50, 0, 100);
        setting.setParentSetting(parent);
        SliderWidget widget = new SliderWidget(setting);
        widget.setPosition(0, 0, 300, widget.getHeight());

        assertTrue(widget.mouseClicked(120, 24, 0));
        int valueAtDisable = setting.getValue();
        parent.setValue(false);
        assertFalse(widget.mouseDragged(230, 24));
        assertEquals(valueAtDisable, setting.getValue());

        parent.setValue(true);
        assertFalse(widget.mouseDragged(230, 24), "the old drag does not resume");
        assertTrue(widget.mouseClicked(250, 20, 0));
        assertTrue(widget.charTyped(new CharacterEvent('9', 0)));

        parent.setValue(false);
        assertFalse(widget.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
        assertEquals(valueAtDisable, setting.getValue(), "the unfinished manual value is discarded");

        parent.setValue(true);
        assertFalse(widget.keyPressed(
                new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)), "editing stays cleared after re-enable");
        assertEquals(valueAtDisable, setting.getValue());
    }
}
