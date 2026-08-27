package com.seqwawa.seq.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.config.Setting;
import org.junit.jupiter.api.Test;

class EnumWidgetTest {

    @Test
    void disabledDependentEnumCannotCycle() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "test", false);
        Setting.EnumSetting<Mode> setting = new Setting.EnumSetting<>("mode", "test", Mode.FIRST, Mode.class);
        setting.setParentSetting(parent);
        EnumWidget widget = new EnumWidget(setting);
        widget.setPosition(0, 0, 300, widget.getHeight());

        assertFalse(widget.mouseClicked(240, 14, 0));
        assertEquals(Mode.FIRST, setting.getValue());

        parent.setValue(true);
        assertTrue(widget.mouseClicked(240, 14, 0));
        assertEquals(Mode.SECOND, setting.getValue());
    }

    private enum Mode {
        FIRST,
        SECOND
    }
}
