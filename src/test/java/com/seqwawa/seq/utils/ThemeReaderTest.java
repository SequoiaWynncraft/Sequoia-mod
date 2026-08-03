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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ThemeReaderTest {
    @Test
    void parsesBundledYamlThemeAndExtendedMapColors() throws IOException {
        Theme theme = bundledDefaultTheme();

        assertEquals("default", theme.name());
        assertEquals(new Color(160, 130, 220, 255), theme.color(UiColor.ACCENT_PRIMARY));
        assertEquals(new Color(75, 194, 205, 175), theme.color(UiColor.MAP_TERRITORY));
    }

    @Test
    void parsesCompleteDocumentedTemplate() throws IOException {
        Path templatePath = Path.of("docs", "theme-template.theme.yml");
        Theme theme = ThemeReader.fromFile(templatePath);

        assertEquals("example_theme", theme.name());
        assertEquals(new Color(255, 194, 72, 250), theme.color(UiColor.MAP_TRACKED_WORLD_EVENT));
        assertEquals(allColorPaths(), documentedColorPaths(Files.readAllLines(templatePath)));
    }

    @Test
    void supportsOptionalPaletteAliases() throws IOException {
        String yaml = bundledDefaultYaml()
                .replace("name: default", "name: aliased\npalette:\n  brand: &brand [1, 2, 3, 255]")
                .replace("primary: [160, 130, 220, 255]", "primary: *brand");

        Theme theme = ThemeReader.fromReader(new StringReader(yaml), "aliased.theme.yml");

        assertEquals(new Color(1, 2, 3, 255), theme.color(UiColor.ACCENT_PRIMARY));
    }

    @Test
    void usesFallbacksForMissingOptionalColors() throws IOException {
        String yaml = bundledDefaultYaml().replace("  world_event: [62, 190, 218, 245]\n", "");

        Theme theme = ThemeReader.fromReader(new StringReader(yaml), "optional.theme.yml");

        assertEquals(UiColor.MAP_WORLD_EVENT.fallback(), theme.color(UiColor.MAP_WORLD_EVENT));
    }

    @Test
    void rejectsMissingRequiredColorsAndUnknownKeys() throws IOException {
        String missing = bundledDefaultYaml().replace("  overlay: [10, 10, 16, 100]\n", "");
        IOException missingError = assertThrows(
                IOException.class,
                () -> ThemeReader.fromReader(new StringReader(missing), "missing.theme.yml"));
        assertTrue(missingError.getMessage().contains("background.overlay"));

        String unknown = bundledDefaultYaml().replace(
                "background:\n", "background:\n  unexpected: [0, 0, 0, 0]\n");
        IOException unknownError = assertThrows(
                IOException.class,
                () -> ThemeReader.fromReader(new StringReader(unknown), "unknown.theme.yml"));
        assertTrue(unknownError.getMessage().contains("background.unexpected"));
    }

    @Test
    void rejectsInvalidColorComponentsAndDuplicateKeys() throws IOException {
        String outOfRange = bundledDefaultYaml().replace(
                "overlay: [10, 10, 16, 100]", "overlay: [256, 10, 16, 100]");
        assertThrows(
                IOException.class,
                () -> ThemeReader.fromReader(new StringReader(outOfRange), "range.theme.yml"));

        String nonInteger = bundledDefaultYaml().replace(
                "overlay: [10, 10, 16, 100]", "overlay: [10.5, 10, 16, 100]");
        assertThrows(
                IOException.class,
                () -> ThemeReader.fromReader(new StringReader(nonInteger), "number.theme.yml"));

        String duplicate = bundledDefaultYaml().replace(
                "background:\n", "background:\n  overlay: [0, 0, 0, 0]\n");
        assertThrows(
                IOException.class,
                () -> ThemeReader.fromReader(new StringReader(duplicate), "duplicate.theme.yml"));
    }

    @Test
    void rejectsInvalidNamesAndTopLevelStructures() throws IOException {
        String invalidName = bundledDefaultYaml().replace("name: default", "name: Invalid Name");
        assertThrows(
                IOException.class,
                () -> ThemeReader.fromReader(new StringReader(invalidName), "name.theme.yml"));

        assertThrows(
                IOException.class,
                () -> ThemeReader.fromReader(new StringReader("- not\n- a\n- mapping\n"), "list.theme.yml"));
    }

    @Test
    void createsAlphaVariantsWithoutMutatingTheThemeColor() {
        Theme theme = Theme.defaults();

        Color translucent = theme.color(UiColor.ACCENT_PRIMARY, 42);

        assertEquals(42, translucent.getAlpha());
        assertEquals(255, theme.color(UiColor.ACCENT_PRIMARY).getAlpha());
    }

    private static Theme bundledDefaultTheme() throws IOException {
        try (InputStream input = ThemeReaderTest.class.getResourceAsStream(
                "/assets/seq/themes/default.theme.yml")) {
            if (input == null) {
                throw new IOException("Missing bundled default theme");
            }
            return ThemeReader.fromStream(input, "default.theme.yml");
        }
    }

    /**
     * The bundled theme with its line endings normalised to {@code \n}.
     * <p>
     * These tests build their fixtures by splicing the real theme text, and every
     * such edit is newline-sensitive. Git checks the resource out with CRLF wherever
     * {@code core.autocrlf} is on, which silently turns each splice into a no-op and
     * leaves the YAML valid — so the tests that expect a rejection stop testing
     * anything. Normalising here keeps them honest on every platform.
     */
    private static String bundledDefaultYaml() throws IOException {
        try (InputStream input = ThemeReaderTest.class.getResourceAsStream(
                "/assets/seq/themes/default.theme.yml")) {
            if (input == null) {
                throw new IOException("Missing bundled default theme");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static Set<String> allColorPaths() {
        Set<String> paths = new HashSet<>();
        for (UiColor token : UiColor.values()) {
            paths.add(token.key());
        }
        return paths;
    }

    private static Set<String> documentedColorPaths(Iterable<String> lines) {
        Set<String> paths = new HashSet<>();
        String group = null;
        for (String line : lines) {
            if (line.matches("^[a-z_]+:$")) {
                group = line.substring(0, line.length() - 1);
            } else if (group != null && line.matches("^  [a-z_]+:.*$")) {
                int separator = line.indexOf(':');
                paths.add(group + "." + line.substring(2, separator));
            }
        }
        return paths;
    }
}
