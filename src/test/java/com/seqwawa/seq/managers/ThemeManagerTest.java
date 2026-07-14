package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.ui.theme.UiColor;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ThemeManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void discoversBundledThemesAndSelectsDefault() throws IOException {
        Path externalThemes = tempDir.resolve("config/sequoia/themes");
        ThemeManager.initialize(externalThemes);

        assertTrue(Files.isDirectory(externalThemes));
        assertTrue(ThemeManager.loadedThemeNames().contains("default"));
        assertTrue(ThemeManager.loadedThemeNames().contains("high_contrast"));
        assertEquals("default", ThemeManager.currentTheme().name());
        assertEquals(new Color(160, 130, 220, 255), ThemeManager.color(UiColor.ACCENT_PRIMARY));
    }

    @Test
    void unknownThemeDoesNotReplaceCurrentTheme() {
        ThemeManager.initialize(tempDir.resolve("themes"));
        String selected = ThemeManager.currentTheme().name();

        assertFalse(ThemeManager.setCurrentTheme("missing"));
        assertEquals(selected, ThemeManager.currentTheme().name());
    }

    @Test
    void discoversExternalThemesAndSkipsMalformedFiles() throws IOException {
        Path externalThemes = tempDir.resolve("config/sequoia/themes");
        Files.createDirectories(externalThemes);
        Files.writeString(
                externalThemes.resolve("custom.theme.txt"),
                bundledDefaultTheme().replaceFirst("name=default", "name=custom"));
        Files.writeString(externalThemes.resolve("broken.theme.txt"), "name=broken\ntext_primary=invalid\n");

        ThemeManager.initialize(externalThemes);

        assertTrue(ThemeManager.loadedThemeNames().contains("custom"));
        assertFalse(ThemeManager.loadedThemeNames().contains("broken"));
        assertTrue(ThemeManager.setCurrentTheme("custom"));
        assertEquals("custom", ThemeManager.currentTheme().name());
    }

    private static String bundledDefaultTheme() throws IOException {
        try (InputStream input = ThemeManagerTest.class.getResourceAsStream(
                "/assets/seq/themes/default.theme.txt")) {
            if (input == null) {
                throw new IOException("Missing bundled default theme");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
