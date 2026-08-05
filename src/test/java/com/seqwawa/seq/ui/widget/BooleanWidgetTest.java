package com.seqwawa.seq.ui.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.config.Setting;
import org.junit.jupiter.api.Test;

class BooleanWidgetTest {

    @Test
    void describedDependentToggleStaysVisibleButCannotChangeWhileDisabled() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "chat", false);
        Setting.BooleanSetting child = new Setting.BooleanSetting("child", "chat", false);
        child.setPresentation("Child", "Why this exists.", "Section");
        child.setParentSetting(parent);
        BooleanWidget widget = new BooleanWidget(child);
        widget.setPosition(0, 0, 200, widget.getHeight());

        assertEquals(42f, widget.getHeight(), "the description receives its own line");
        assertFalse(widget.mouseClicked(20, 20, 0));
        assertFalse(child.getValue());

        parent.setValue(true);
        assertTrue(widget.mouseClicked(20, 20, 0));
        assertTrue(child.getValue());
    }
}
