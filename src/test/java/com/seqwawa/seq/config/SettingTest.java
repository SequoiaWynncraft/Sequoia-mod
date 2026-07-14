package com.seqwawa.seq.config;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingTest {

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
}
