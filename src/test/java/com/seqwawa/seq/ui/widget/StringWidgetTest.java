package com.seqwawa.seq.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.config.Setting;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class StringWidgetTest {

    @Test
    void disablingDependentStringDiscardsEditingAndBlocksInput() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "test", false);
        Setting.StringSetting setting = new Setting.StringSetting("value", "test", "saved");
        setting.setParentSetting(parent);
        StringWidget widget = new StringWidget(setting);
        widget.setPosition(0, 0, 300, widget.getHeight());

        assertFalse(widget.mouseClicked(30, 25, 0));
        assertEquals("saved", setting.getValue());

        parent.setValue(true);
        assertTrue(widget.mouseClicked(30, 25, 0));
        assertTrue(widget.charTyped(new CharacterEvent('x', 0)));

        parent.setValue(false);
        assertFalse(widget.charTyped(new CharacterEvent('y', 0)));
        assertFalse(widget.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
        assertEquals("saved", setting.getValue());

        parent.setValue(true);
        assertFalse(widget.keyPressed(
                new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)), "editing stays cleared after re-enable");
        assertEquals("saved", setting.getValue());
    }
}
