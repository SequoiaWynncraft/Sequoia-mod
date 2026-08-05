package com.seqwawa.seq.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.config.Setting;
import org.junit.jupiter.api.Test;

class ColorWidgetTest {

    @Test
    void rightClickingTheSwatchRestoresTheDefaultColor() {
        Setting.ColorSetting setting = new Setting.ColorSetting("color", "test", 0x123456);
        setting.setValue(0xABCDEF);
        ColorWidget widget = new ColorWidget(setting, null);
        widget.setPosition(0, 0, 200, widget.getHeight());

        assertTrue(widget.mouseClicked(120, 20, 1));
        assertEquals(0x123456, setting.getValue());
    }

    @Test
    void rightClickingOutsideTheSwatchLeavesTheColorAlone() {
        Setting.ColorSetting setting = new Setting.ColorSetting("color", "test", 0x123456);
        setting.setValue(0xABCDEF);
        ColorWidget widget = new ColorWidget(setting, null);
        widget.setPosition(0, 0, 200, widget.getHeight());

        assertFalse(widget.mouseClicked(20, 20, 1));
        assertEquals(0xABCDEF, setting.getValue());
    }
}
