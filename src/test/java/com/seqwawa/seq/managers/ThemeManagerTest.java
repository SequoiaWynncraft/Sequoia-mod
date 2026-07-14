package com.seqwawa.seq.managers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seqwawa.seq.ui.theme.UiColor;
import java.awt.Color;
import org.junit.jupiter.api.Test;

class ThemeManagerTest {
    @Test
    void discoversBundledThemesAndSelectsDefault() {
        ThemeManager.initialize();

        assertTrue(ThemeManager.loadedThemeNames().contains("default"));
        assertEquals("default", ThemeManager.currentTheme().name());
        assertEquals(new Color(160, 130, 220, 255), ThemeManager.color(UiColor.ACCENT_PRIMARY));
    }

    @Test
    void unknownThemeDoesNotReplaceCurrentTheme() {
        ThemeManager.initialize();
        String selected = ThemeManager.currentTheme().name();

        assertFalse(ThemeManager.setCurrentTheme("missing"));
        assertEquals(selected, ThemeManager.currentTheme().name());
    }
}
