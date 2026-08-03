package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import org.junit.jupiter.api.Test;

class ThemeEditorPolicyTest {
    @Test
    void normalizesPastedThemeNamesWithTheExistingCharacterPolicy() {
        assertEquals("mytheme-2", ThemeEditorPolicy.normalizedNameClipboard("My Theme!-2"));
        assertEquals("", ThemeEditorPolicy.normalizedNameClipboard(null));
        assertEquals("a".repeat(64), ThemeEditorPolicy.normalizedNameClipboard("A".repeat(80)));
    }

    @Test
    void formatsAndParsesEightDigitRgbaColors() {
        Color color = new Color(0x12, 0x34, 0x56, 0x78);

        assertEquals("#12345678", ThemeEditorPolicy.toHex(color));
        assertEquals(color, ThemeEditorPolicy.parseColor(" 12345678 "));
        assertNull(ThemeEditorPolicy.parseColor("#123456"));
        assertNull(ThemeEditorPolicy.parseColor("not-a-color"));
    }

    @Test
    void preservesDisplayAndGeometryRules() {
        assertEquals("Accent Primary Hover", ThemeEditorPolicy.displayName("accent_primary_hover"));
        assertEquals(1, ThemeEditorPolicy.clamp(2, 0, 1));
        assertTrue(ThemeEditorPolicy.isHovered(10, 20, 0, 0, 10, 20));
    }
}
