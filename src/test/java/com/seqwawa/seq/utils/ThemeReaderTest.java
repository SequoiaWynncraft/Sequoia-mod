package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.ui.theme.Theme;
import com.seqwawa.seq.ui.theme.UiColor;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

class ThemeReaderTest {
    @Test
    void parsesBundledThemeAndExtendedMapColors() throws IOException {
        try (InputStream input = ThemeReaderTest.class.getResourceAsStream("/assets/seq/themes/default.theme.txt")) {
            Theme theme = ThemeReader.fromStream(input, "default.theme.txt");

            assertEquals("default", theme.name());
            assertEquals(new Color(160, 130, 220, 255), theme.color(UiColor.ACCENT_PRIMARY));
            assertEquals(new Color(75, 194, 205, 175), theme.color(UiColor.MAP_TERRITORY));
        }
    }

    @Test
    void usesFallbacksForNewOptionalTokensInOlderThemeFiles() throws IOException {
        Theme theme = ThemeReader.fromReader(new StringReader(requiredTheme("legacy")), "legacy.theme.txt");

        assertEquals(UiColor.MAP_WORLD_EVENT.fallback(), theme.color(UiColor.MAP_WORLD_EVENT));
    }

    @Test
    void rejectsMissingRequiredColors() {
        String theme = requiredTheme("incomplete").replaceFirst("background_overlay=.*\\n", "");

        IOException error = assertThrows(
                IOException.class,
                () -> ThemeReader.fromReader(new StringReader(theme), "incomplete.theme.txt"));

        assertTrue(error.getMessage().contains("background_overlay"));
    }

    @Test
    void rejectsInvalidComponentsAndDuplicateKeys() {
        String invalid = requiredTheme("invalid").replaceFirst("background_overlay=.*", "background_overlay=(256,0,0,255)");
        assertThrows(
                IOException.class,
                () -> ThemeReader.fromReader(new StringReader(invalid), "invalid.theme.txt"));

        String duplicate = requiredTheme("duplicate") + "text_primary=(1,2,3,4)\n";
        assertThrows(
                IOException.class,
                () -> ThemeReader.fromReader(new StringReader(duplicate), "duplicate.theme.txt"));
    }

    @Test
    void createsAlphaVariantsWithoutMutatingTheThemeColor() {
        Theme theme = Theme.defaults();

        Color translucent = theme.color(UiColor.ACCENT_PRIMARY, 42);

        assertEquals(42, translucent.getAlpha());
        assertEquals(255, theme.color(UiColor.ACCENT_PRIMARY).getAlpha());
    }

    private static String requiredTheme(String name) {
        StringBuilder theme = new StringBuilder("name=").append(name).append('\n');
        for (UiColor token : UiColor.values()) {
            if (!token.required()) {
                continue;
            }
            Color color = token.fallback();
            theme.append(token.key())
                    .append("=(")
                    .append(color.getRed()).append(',')
                    .append(color.getGreen()).append(',')
                    .append(color.getBlue()).append(',')
                    .append(color.getAlpha()).append(")\n");
        }
        return theme.toString();
    }
}
