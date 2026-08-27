package com.seqwawa.seq.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.config.Setting;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.input.CharacterEvent;
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

    @Test
    void disabledDependentColorRejectsEveryMouseControl() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "test", false);
        Setting.ColorSetting setting = new Setting.ColorSetting("color", "test", 0x123456);
        setting.setParentSetting(parent);
        setting.setValue(0xABCDEF);
        AtomicReference<Boolean> preview = new AtomicReference<>();
        ColorWidget widget = new ColorWidget(setting, preview::set);
        widget.setPosition(0, 0, 400, widget.getHeight());

        assertFalse(widget.mouseClicked(30, 20, 0), "hex input is disabled");
        assertFalse(widget.mouseClicked(130, 20, 0), "swatch expansion is disabled");
        assertFalse(widget.mouseClicked(130, 20, 1), "right-click reset is disabled");
        assertFalse(widget.mouseClicked(330, 20, 0), "preview is disabled");
        assertEquals(0xABCDEF, setting.getValue());
        assertEquals(42f, widget.getHeight());
        assertEquals(null, preview.get());
    }

    @Test
    void disablingColorDiscardsEditingAndClosesExpansionAndPreview() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "test", true);
        Setting.ColorSetting setting = new Setting.ColorSetting("color", "test", 0x123456);
        setting.setParentSetting(parent);
        AtomicReference<Boolean> preview = new AtomicReference<>();
        ColorWidget widget = new ColorWidget(setting, preview::set);
        widget.setPosition(0, 0, 400, widget.getHeight());

        assertTrue(widget.mouseClicked(130, 20, 0));
        assertEquals(146f, widget.getHeight());
        assertTrue(widget.mouseClicked(330, 20, 0));
        assertEquals(Boolean.TRUE, preview.get());
        assertTrue(widget.mouseClicked(30, 20, 0));

        parent.setValue(false);

        assertFalse(widget.charTyped(new CharacterEvent('A', 0)));
        assertEquals(0x123456, setting.getValue(), "the unfinished hex value is discarded");
        assertEquals(42f, widget.getHeight());
        assertEquals(Boolean.FALSE, preview.get());

        parent.setValue(true);
        assertFalse(widget.charTyped(new CharacterEvent('B', 0)), "editing stays cleared after re-enable");
    }

    @Test
    void disablingColorStopsAnActivePickerDragWithoutChangingTheValueAgain() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "test", true);
        Setting.ColorSetting setting = new Setting.ColorSetting("color", "test", 0x123456);
        setting.setParentSetting(parent);
        ColorWidget widget = new ColorWidget(setting, null);
        widget.setPosition(0, 0, 400, widget.getHeight());

        assertTrue(widget.mouseClicked(130, 20, 0));
        assertTrue(widget.mouseClicked(50, 60, 0));
        int valueAtDisable = setting.getValue();

        parent.setValue(false);

        assertFalse(widget.mouseDragged(220, 100));
        assertEquals(valueAtDisable, setting.getValue());
        assertEquals(42f, widget.getHeight());

        parent.setValue(true);
        assertFalse(widget.mouseDragged(220, 100), "the old drag does not resume");
        assertEquals(valueAtDisable, setting.getValue());
    }
}
