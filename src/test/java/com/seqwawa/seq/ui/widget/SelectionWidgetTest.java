package com.seqwawa.seq.ui.widget;

import static org.junit.jupiter.api.Assertions.*;
import com.seqwawa.seq.config.Setting;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import net.minecraft.client.input.KeyEvent;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class SelectionWidgetTest {
    @Test
    void choosingInvokesCallbackOnceAndOutsideClickDoesNotChangeValue() {
        var changes = new AtomicInteger();
        var setting = new Setting.ChoiceSetting("theme", "ui", "default", List.of("default", "high_contrast"), v -> changes.incrementAndGet());
        var widget = new ChoiceWidget(setting);
        widget.setPosition(0, 0, 300, 28);
        assertTrue(widget.mouseClicked(200, 14, 0));
        assertEquals(0, changes.get());
        assertTrue(widget.mouseClicked(200, 56, 0));
        assertEquals("high_contrast", setting.getValue());
        assertEquals(1, changes.get());
        assertFalse(widget.isOpen());
        widget.mouseClicked(200, 14, 0);
        assertTrue(widget.mouseClicked(10, 80, 0));
        assertEquals(1, changes.get());
        assertFalse(widget.isOpen());
    }

    @Test
    void popupOpensUpwardAndUsesTheSameBoundsForSelection() {
        var setting = new Setting.ChoiceSetting("theme", "ui", "default", List.of("default", "high_contrast"), v -> {});
        var widget = new ChoiceWidget(setting);
        widget.setPosition(0, 70, 300, 28);
        widget.setViewport(0, 100);
        widget.mouseClicked(200, 84, 0);
        widget.mouseClicked(200, 64, 0);
        assertEquals("high_contrast", setting.getValue());
    }

    @Test
    void keyboardNavigatesLongAndNewlyAddedChoicesAndEscapeCancels() {
        var values = IntStream.range(0, 12).mapToObj(i -> "theme" + i).toList();
        var setting = new Setting.ChoiceSetting("theme", "ui", "theme0", values, v -> {});
        var widget = new ChoiceWidget(setting);
        widget.setPosition(0, 0, 300, 28);
        widget.setViewport(0, 100);
        widget.mouseClicked(200, 14, 0);
        assertTrue(widget.keyPressed(key(GLFW.GLFW_KEY_END)));
        assertTrue(widget.keyPressed(key(GLFW.GLFW_KEY_ENTER)));
        assertEquals("theme11", setting.getValue());
        setting.addOption("new_theme");
        widget.mouseClicked(200, 14, 0);
        widget.keyPressed(key(GLFW.GLFW_KEY_DOWN));
        widget.keyPressed(key(GLFW.GLFW_KEY_ESCAPE));
        assertEquals("theme11", setting.getValue());
        widget.mouseClicked(200, 14, 0);
        widget.keyPressed(key(GLFW.GLFW_KEY_DOWN));
        widget.keyPressed(key(GLFW.GLFW_KEY_ENTER));
        assertEquals("new_theme", setting.getValue());
    }

    @Test
    void scrollingDoesNotSelectAndClippedOrDisabledControlsLoseTheirPopup() {
        var values = IntStream.range(0, 12).mapToObj(i -> "theme" + i).toList();
        var setting = new Setting.ChoiceSetting("theme", "ui", "theme0", values, v -> {});
        var parent = new Setting.BooleanSetting("enabled", "ui", true);
        setting.setParentSetting(parent);
        var widget = new ChoiceWidget(setting);
        widget.setPosition(0, 0, 300, 28);
        widget.setViewport(0, 100);
        widget.mouseClicked(200, 14, 0);
        assertTrue(widget.scroll(-1));
        assertEquals("theme0", setting.getValue());
        widget.mouseClicked(200, 30, 0);
        assertEquals("theme1", setting.getValue());
        widget.mouseClicked(200, 14, 0);
        parent.setValue(false);
        assertFalse(widget.keyPressed(key(GLFW.GLFW_KEY_ENTER)));
        assertFalse(widget.isOpen());
        parent.setValue(true);
        widget.mouseClicked(200, 14, 0);
        widget.setViewport(40, 100);
        assertFalse(widget.isOpen());
        widget.setViewport(0, 100);
        widget.mouseClicked(200, 14, 0);
        widget.onHidden();
        assertFalse(widget.isOpen());
    }

    private KeyEvent key(int key) { return new KeyEvent(key, 0, 0); }
}
