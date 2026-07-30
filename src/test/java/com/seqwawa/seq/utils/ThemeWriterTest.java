package com.seqwawa.seq.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.ui.theme.Theme;
import com.seqwawa.seq.ui.theme.UiColor;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeWriterTest {
    @TempDir
    Path tempDir;

    @Test
    void writesCompleteRoundTrippableThemeIncludingAlpha() throws IOException {
        EnumMap<UiColor, Color> colors = new EnumMap<>(UiColor.class);
        for (UiColor token : UiColor.values()) {
            colors.put(token, token.fallback());
        }
        colors.put(UiColor.BACKGROUND_OVERLAY, new Color(1, 2, 3, 4));
        Theme original = new Theme("personal", colors);
        Path destination = tempDir.resolve("nested/personal.theme.yml");

        ThemeWriter.write(original, destination);
        Theme loaded = ThemeReader.fromFile(destination);

        assertTrue(Files.readString(destination).contains("overlay: [1, 2, 3, 4]"));
        assertEquals(original.name(), loaded.name());
        assertEquals(original.colors(), loaded.colors());
    }

    @Test
    void supportsSingleCharacterThemeNames() throws IOException {
        Theme original = new Theme("x", java.util.Map.of());
        Path destination = tempDir.resolve("x.theme.yml");

        ThemeWriter.write(original, destination);

        assertEquals("x", ThemeReader.fromFile(destination).name());
    }
}
