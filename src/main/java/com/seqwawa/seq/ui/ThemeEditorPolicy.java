package com.seqwawa.seq.ui;

import java.awt.Color;
import java.util.Locale;

final class ThemeEditorPolicy {
    private ThemeEditorPolicy() {}

    static String displayName(String raw) {
        String[] words = raw.split("_");
        StringBuilder display = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!display.isEmpty()) {
                display.append(' ');
            }
            display.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return display.toString();
    }

    static boolean isThemeNameCharacter(char character) {
        char lower = Character.toLowerCase(character);
        return lower >= 'a' && lower <= 'z'
                || lower >= '0' && lower <= '9'
                || lower == '_'
                || lower == '-';
    }

    static String normalizedNameClipboard(String clipboard) {
        if (clipboard == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder();
        for (int index = 0; index < clipboard.length() && normalized.length() < 64; index++) {
            char character = clipboard.charAt(index);
            if (isThemeNameCharacter(character)) {
                normalized.append(Character.toLowerCase(character));
            }
        }
        return normalized.toString();
    }

    static String toHex(Color value) {
        return String.format(
                Locale.ROOT,
                "#%02X%02X%02X%02X",
                value.getRed(),
                value.getGreen(),
                value.getBlue(),
                value.getAlpha());
    }

    static Color parseColor(String value) {
        if (value == null) {
            return null;
        }
        String digits = value.trim();
        if (digits.startsWith("#")) {
            digits = digits.substring(1);
        }
        if (digits.length() != 8) {
            return null;
        }
        try {
            long rgba = Long.parseLong(digits, 16);
            return new Color(
                    (int) (rgba >> 24) & 0xFF,
                    (int) (rgba >> 16) & 0xFF,
                    (int) (rgba >> 8) & 0xFF,
                    (int) rgba & 0xFF);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static boolean isHovered(float mouseX, float mouseY, float x, float y, float width, float height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
