package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.ui.theme.Theme;
import com.seqwawa.seq.ui.theme.UiColor;
import java.awt.Color;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
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
        assertFalse(ThemeManager.loadedThemeNames().contains("princess"));
        assertTrue(ThemeManager.theme("princess").isPresent());
        assertEquals("default", ThemeManager.currentTheme().name());
        assertEquals(new Color(160, 130, 220, 255), ThemeManager.color(UiColor.ACCENT_PRIMARY));
    }

    @Test
    void princessModeTemporarilyOverridesAndThenRestoresTheSelectedTheme() {
        ThemeManager.initialize(tempDir.resolve("themes"));

        assertTrue(PrincessMode.setEnabled(true));
        assertTrue(PrincessMode.isEnabled());
        assertEquals("princess", ThemeManager.currentTheme().name());
        assertEquals(new Color(255, 93, 214, 255), ThemeManager.color(UiColor.ACCENT_PRIMARY));
        assertEquals(0xFF5DD6, PrincessMode.paletteColorOverride());

        assertTrue(ThemeManager.setCurrentTheme("high_contrast"));
        assertEquals("princess", ThemeManager.currentTheme().name());
        ThemeManager.previewTheme(Theme.defaults());
        assertEquals("princess", ThemeManager.currentTheme().name());

        PrincessMode.setEnabled(false);

        assertFalse(PrincessMode.isEnabled());
        assertEquals("high_contrast", ThemeManager.currentTheme().name());
        assertNull(PrincessMode.paletteColorOverride());
    }

    @Test
    void hiddenPrincessThemeCannotBeSelectedDirectly() {
        ThemeManager.initialize(tempDir.resolve("themes"));

        assertFalse(ThemeManager.setCurrentTheme("princess"));
        assertEquals("default", ThemeManager.currentTheme().name());
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
                externalThemes.resolve("custom.theme.yml"),
                bundledDefaultTheme().replaceFirst("name: default", "name: custom"));
        Files.writeString(externalThemes.resolve("broken.theme.yml"), "name: broken\ntext:\n  primary: invalid\n");
        Files.writeString(
                externalThemes.resolve("ignored.theme.txt"),
                bundledDefaultTheme().replaceFirst("name: default", "name: ignored"));

        ThemeManager.initialize(externalThemes);

        assertTrue(ThemeManager.loadedThemeNames().contains("custom"));
        assertFalse(ThemeManager.loadedThemeNames().contains("broken"));
        assertFalse(ThemeManager.loadedThemeNames().contains("ignored"));
        assertTrue(ThemeManager.setCurrentTheme("custom"));
        assertEquals("custom", ThemeManager.currentTheme().name());
    }

    @Test
    void externalThemesCannotReplaceBundledThemeNames() throws IOException {
        Path externalThemes = tempDir.resolve("config/sequoia/themes");
        Files.createDirectories(externalThemes);
        Files.writeString(
                externalThemes.resolve("replacement.theme.yml"),
                bundledDefaultTheme()
                        .replaceFirst("name: default", "name: high_contrast")
                        .replace("primary: [160, 130, 220, 255]", "primary: [1, 2, 3, 255]"));

        ThemeManager.initialize(externalThemes);

        assertTrue(ThemeManager.setCurrentTheme("high_contrast"));
        assertEquals(new Color(64, 220, 210, 255), ThemeManager.color(UiColor.ACCENT_PRIMARY));
    }

    @Test
    void savesAndSelectsPersonalThemeWithoutRestarting() throws IOException {
        Path externalThemes = tempDir.resolve("config/sequoia/themes");
        ThemeManager.initialize(externalThemes);
        EnumMap<UiColor, Color> colors = new EnumMap<>(ThemeManager.currentTheme().colors());
        colors.put(UiColor.ACCENT_PRIMARY, new Color(12, 34, 56, 78));
        Theme personal = new Theme("my_theme", colors);

        Path saved = ThemeManager.savePersonalTheme(personal);

        assertEquals(externalThemes.resolve("my_theme.theme.yml").toAbsolutePath(), saved);
        assertTrue(Files.isRegularFile(saved));
        assertTrue(ThemeManager.isPersonalTheme("my_theme"));
        assertTrue(ThemeManager.setCurrentTheme("my_theme"));
        assertEquals(new Color(12, 34, 56, 78), ThemeManager.color(UiColor.ACCENT_PRIMARY));

        ThemeManager.initialize(externalThemes);
        assertTrue(ThemeManager.setCurrentTheme("my_theme"));
        assertEquals(new Color(12, 34, 56, 78), ThemeManager.color(UiColor.ACCENT_PRIMARY));
    }

    @Test
    void refusesToOverwriteBundledTheme() {
        ThemeManager.initialize(tempDir.resolve("themes"));

        IOException exception = assertThrows(
                IOException.class,
                () -> ThemeManager.savePersonalTheme(new Theme("default", ThemeManager.currentTheme().colors())));

        assertTrue(exception.getMessage().contains("cannot be overwritten"));
    }

    private static String bundledDefaultTheme() throws IOException {
        try (InputStream input = ThemeManagerTest.class.getResourceAsStream(
                "/assets/seq/themes/default.theme.yml")) {
            if (input == null) {
                throw new IOException("Missing bundled default theme");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
