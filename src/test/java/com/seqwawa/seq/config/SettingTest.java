package com.seqwawa.seq.config;

import com.google.gson.JsonPrimitive;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingTest {

    @Test
    void presentationMetadataAndParentDepthDescribeGroupedSettings() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "chat", true);
        Setting.BooleanSetting child = new Setting.BooleanSetting("child", "chat", false);
        Setting.BooleanSetting grandchild = new Setting.BooleanSetting("grandchild", "chat", false);
        child.setPresentation("Child control", "Explains the control", "Discord ranks and colors");
        child.setParentSetting(parent);
        grandchild.setParentSetting(child);

        assertEquals("Child control", child.getDisplayName());
        assertEquals("Explains the control", child.getDescription());
        assertEquals("Discord ranks and colors", child.getSection());
        assertEquals(1, child.getIndentLevel());
        assertEquals(2, grandchild.getIndentLevel());
        assertTrue(child.isEnabled());
        assertFalse(grandchild.isEnabled(), "its direct Boolean parent is off");

        child.setValue(true);
        assertTrue(grandchild.isEnabled());
        parent.setValue(false);
        assertFalse(child.isEnabled());
        assertFalse(grandchild.isEnabled(), "disabled state propagates through the parent chain");
    }

    @Test
    void explicitEnabledConditionComposesWithAParentAndCyclesAreRejected() {
        Setting.BooleanSetting parent = new Setting.BooleanSetting("parent", "chat", true);
        Setting.BooleanSetting child = new Setting.BooleanSetting("child", "chat", false);
        child.setParentSetting(parent);
        child.setEnabledCondition(() -> false);

        assertFalse(child.isEnabled());
        assertThrows(IllegalArgumentException.class, () -> parent.setParentSetting(child));
    }

    @Test
    void choiceSettingAcceptsKnownOptionsAndAppliesChanges() {
        AtomicReference<String> applied = new AtomicReference<>();
        Setting.ChoiceSetting setting = new Setting.ChoiceSetting(
                "theme", "ui", "default", List.of("default", "contrast"), applied::set);

        setting.setValue("contrast");

        assertEquals("contrast", setting.getValue());
        assertEquals("contrast", applied.get());
    }

    @Test
    void choiceSettingRejectsUnknownDeserializedOptions() {
        AtomicReference<String> applied = new AtomicReference<>();
        Setting.ChoiceSetting setting = new Setting.ChoiceSetting(
                "theme", "ui", "default", List.of("default", "contrast"), applied::set);

        setting.deserialize(new JsonPrimitive("missing"));

        assertEquals("default", setting.getValue());
        assertEquals(null, applied.get());
    }

    @Test
    void manualNumericValuesAreClampedByDefault() {
        Setting.IntSetting intSetting = new Setting.IntSetting("int", "test", 100, 75, 150, 5);
        Setting.DoubleSetting doubleSetting = new Setting.DoubleSetting("double", "test", 1.0, 0.5, 1.5, 0.1);
        Setting.FloatSetting floatSetting = new Setting.FloatSetting("float", "test", 1.0f, 0.5f, 1.5f, 0.1f);

        intSetting.setValueFromManualInput(175);
        doubleSetting.setValueFromManualInput(2.25);
        floatSetting.setValueFromManualInput(2.25f);

        assertEquals(150, intSetting.getValue());
        assertEquals(1.5, doubleSetting.getValue());
        assertEquals(1.5f, floatSetting.getValue());
    }

    @Test
    void manualNumericValuesCanExceedSliderRangesWhenOptedIn() {
        Setting.IntSetting intSetting = new Setting.IntSetting("int", "test", 100, 75, 150, 5)
                .allowOutOfRangeManualInput();
        Setting.DoubleSetting doubleSetting = new Setting.DoubleSetting("double", "test", 1.0, 0.5, 1.5, 0.1)
                .allowOutOfRangeManualInput();
        Setting.FloatSetting floatSetting = new Setting.FloatSetting("float", "test", 1.0f, 0.5f, 1.5f, 0.1f)
                .allowOutOfRangeManualInput();

        intSetting.setValueFromManualInput(175);
        doubleSetting.setValueFromManualInput(2.25);
        floatSetting.setValueFromManualInput(2.25f);

        assertEquals(175, intSetting.getValue());
        assertEquals(2.25, doubleSetting.getValue());
        assertEquals(2.25f, floatSetting.getValue());
    }

    @Test
    void normalUpdatesRemainClampedToSliderRanges() {
        Setting.IntSetting setting = new Setting.IntSetting("int", "test", 100, 75, 150, 5);

        setting.setValue(175);

        assertEquals(150, setting.getValue());
    }

    @Test
    void deserializationClampsNumericValuesByDefault() {
        Setting.IntSetting intSetting = new Setting.IntSetting("int", "test", 100, 75, 150, 5);
        Setting.DoubleSetting doubleSetting = new Setting.DoubleSetting("double", "test", 1.0, 0.5, 1.5, 0.1);
        Setting.FloatSetting floatSetting = new Setting.FloatSetting("float", "test", 1.0f, 0.5f, 1.5f, 0.1f);

        intSetting.deserialize(new JsonPrimitive(175));
        doubleSetting.deserialize(new JsonPrimitive(2.25));
        floatSetting.deserialize(new JsonPrimitive(2.25f));

        assertEquals(150, intSetting.getValue());
        assertEquals(1.5, doubleSetting.getValue());
        assertEquals(1.5f, floatSetting.getValue());
    }

    @Test
    void deserializationPreservesOutOfRangeValuesWhenOptedIn() {
        Setting.IntSetting intSetting = new Setting.IntSetting("int", "test", 100, 75, 150, 5)
                .allowOutOfRangeManualInput();
        Setting.DoubleSetting doubleSetting = new Setting.DoubleSetting("double", "test", 1.0, 0.5, 1.5, 0.1)
                .allowOutOfRangeManualInput();
        Setting.FloatSetting floatSetting = new Setting.FloatSetting("float", "test", 1.0f, 0.5f, 1.5f, 0.1f)
                .allowOutOfRangeManualInput();

        intSetting.deserialize(new JsonPrimitive(175));
        doubleSetting.deserialize(new JsonPrimitive(2.25));
        floatSetting.deserialize(new JsonPrimitive(2.25f));

        assertEquals(175, intSetting.getValue());
        assertEquals(2.25, doubleSetting.getValue());
        assertEquals(2.25f, floatSetting.getValue());
    }

    @Test
    void colorSettingAcceptsAndCanonicalizesSixDigitHex() {
        Setting.ColorSetting setting = new Setting.ColorSetting("color", "test", 0x00FFFF);

        assertTrue(setting.setHexValue(" #a1b2c3 "));

        assertEquals(0xA1B2C3, setting.getValue());
        assertEquals("#A1B2C3", setting.getHexValue());
        assertEquals(new JsonPrimitive("#A1B2C3"), setting.serialize());
    }

    @Test
    void colorSettingRejectsInvalidHexWithoutLosingLastValidColor() {
        Setting.ColorSetting setting = new Setting.ColorSetting("color", "test", 0x00FFFF);
        setting.setHexValue("#123456");

        assertFalse(setting.setHexValue("#XYZXYZ"));
        assertFalse(setting.setHexValue("#123"));
        assertFalse(setting.setHexValue("1234567"));

        assertEquals(0x123456, setting.getValue());
    }

    @Test
    void colorSettingIgnoresMalformedSerializedValues() {
        Setting.ColorSetting setting = new Setting.ColorSetting("color", "test", 0x00FFFF);

        setting.deserialize(new JsonPrimitive("#not-a-color"));
        setting.deserialize(new JsonPrimitive(0x123456));

        assertEquals("#00FFFF", setting.getHexValue());
    }

    @Test
    void colorSettingMasksAlphaFromDirectRgbUpdates() {
        Setting.ColorSetting setting = new Setting.ColorSetting("color", "test", 0x00FFFF);

        setting.setValue(0xFFA1B2C3);

        assertEquals(0xA1B2C3, setting.getValue());
    }

    @Test
    void colorSettingOverrideIsTemporaryAndIsNotSerialized() {
        int[] override = {0xFF5DD6};
        Setting.ColorSetting setting = new Setting.ColorSetting("color", "test", 0x123456)
                .withValueOverride(() -> override[0]);

        assertEquals(0xFF5DD6, setting.getValue());
        assertEquals("#FF5DD6", setting.getHexValue());
        assertEquals(new JsonPrimitive("#123456"), setting.serialize());

        override[0] = 0xABCDEF;
        setting.setValue(0x654321);

        assertEquals(0xABCDEF, setting.getValue());
        assertEquals(new JsonPrimitive("#654321"), setting.serialize());
    }

    @Test
    void colorSettingUsesConfiguredValueWhenOverrideReturnsNull() {
        Setting.ColorSetting setting = new Setting.ColorSetting("color", "test", 0x123456)
                .withValueOverride(() -> null);

        assertEquals(0x123456, setting.getValue());
        assertEquals("#123456", setting.getHexValue());
    }
}
