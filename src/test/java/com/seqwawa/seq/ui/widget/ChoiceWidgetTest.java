package com.seqwawa.seq.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.config.Setting;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChoiceWidgetTest {

    @Test
    void disabledDependentChoiceCannotCycle() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "test", false);
        Setting.ChoiceSetting setting =
                new Setting.ChoiceSetting("choice", "test", "alpha", List.of("alpha", "beta"), ignored -> {});
        setting.setParentSetting(parent);
        ChoiceWidget widget = new ChoiceWidget(setting);
        widget.setPosition(0, 0, 300, widget.getHeight());

        assertFalse(widget.mouseClicked(200, 14, 0));
        assertEquals("alpha", setting.getValue());

        parent.setValue(true);
        assertTrue(widget.mouseClicked(200, 14, 0));
        assertEquals("beta", setting.getValue());
    }
}
