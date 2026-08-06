package com.seqwawa.seq.managers;

/** Session-scoped state for the Princess settings-screen easter egg. */
public final class PrincessMode {
    public static final int COLOR = 0xFF5DD6;
    static final String THEME_NAME = "princess";

    private static volatile boolean enabled;

    private PrincessMode() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static boolean setEnabled(boolean requested) {
        if (requested) {
            if (!ThemeManager.setThemeOverride(THEME_NAME)) {
                return false;
            }
            enabled = true;
            return true;
        }

        enabled = false;
        ThemeManager.clearThemeOverride();
        return true;
    }

    public static boolean toggle() {
        setEnabled(!enabled);
        return enabled;
    }

    /** Temporary color exposed by raid/chat palette settings while the mode is active. */
    public static Integer paletteColorOverride() {
        return enabled ? COLOR : null;
    }

    static void resetForThemeInitialization() {
        enabled = false;
    }
}
