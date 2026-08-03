package com.seqwawa.seq.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.config.Setting;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class SeqClientSettingsCatalogTest {
    @Test
    void settingsMatchCompatibilitySnapshot() {
        SeqClientSettingsCatalog catalog =
                SeqClientSettingsCatalog.create("default", List.of("default"), ignored -> {});

        assertEquals(
                resourceText("/snapshots/seq-client-settings.txt").strip(),
                catalog.settings().stream().map(SeqClientSettingsCatalogTest::describe).reduce(
                        (left, right) -> left + "\n" + right).orElse(""));
    }

    private static String describe(Setting<?> setting) {
        String type;
        String constraints = "";
        if (setting instanceof Setting.BooleanSetting) {
            type = "boolean";
        } else if (setting instanceof Setting.ColorSetting) {
            type = "color";
        } else if (setting instanceof Setting.IntSetting integer) {
            type = "int";
            constraints = " | min=" + integer.getMin()
                    + " | max=" + integer.getMax()
                    + " | increment=" + integer.getIncrement();
        } else if (setting instanceof Setting.ChoiceSetting) {
            type = "choice";
        } else {
            type = setting.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        }
        String defaultValue = setting instanceof Setting.ColorSetting
                ? String.format(Locale.ROOT, "#%06X", (Integer) setting.getDefaultValue())
                : String.valueOf(setting.getDefaultValue());
        return setting.getCategory() + "." + setting.getName()
                + " | type=" + type
                + " | default=" + defaultValue
                + constraints;
    }

    private static String resourceText(String path) {
        try (InputStream stream = SeqClientSettingsCatalogTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new AssertionError("Missing test resource " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new AssertionError("Failed to read test resource " + path, exception);
        }
    }
}
