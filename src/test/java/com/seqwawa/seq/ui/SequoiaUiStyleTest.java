package com.seqwawa.seq.ui;

import static com.seqwawa.seq.ui.theme.UiColor.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.managers.ThemeManager;
import com.seqwawa.seq.ui.theme.Theme;
import java.awt.Color;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SequoiaUiStyleTest {
    @Test
    void sidebarStatesUseTheExactConfiguredColorsIncludingTransparentAlpha() {
        Theme previous = ThemeManager.currentTheme();
        Color active = new Color(80, 50, 140, 0);
        Color hover = new Color(40, 30, 70, 73);
        Color normal = new Color(20, 10, 35, 255);
        try {
            ThemeManager.previewTheme(new Theme("sidebar-check", Map.of(
                    ACCENT_PRIMARY_DARK, active,
                    BACKGROUND_CONTENT_FOCUSED, hover,
                    BACKGROUND_CONTENT, normal)));
            assertEquals(active, SequoiaUiStyle.sidebarButtonColor(true, false));
            assertEquals(active, SequoiaUiStyle.sidebarButtonColor(true, true));
            assertEquals(hover, SequoiaUiStyle.sidebarButtonColor(false, true));
            assertEquals(normal, SequoiaUiStyle.sidebarButtonColor(false, false));
        } finally {
            ThemeManager.previewTheme(previous);
        }
    }

    @Test
    void unknownDefenseMarkersKeepTheConfiguredFallbackAlpha() {
        Theme previous = ThemeManager.currentTheme();
        Color fallback = new Color(10, 20, 30, 0);
        try {
            ThemeManager.previewTheme(new Theme("marker-alpha-check", Map.of(TEXT_SECONDARY, fallback)));
            assertEquals(fallback, WarPlannerScreen.warQueuePulseColor(null, 0));
            assertEquals(fallback, WarPlannerScreen.warQueuePulseColor(null, 750));
        } finally {
            ThemeManager.previewTheme(previous);
        }
    }

    @Test
    void searchesMatchPartyFinderAndShrinkOnlyWhenSpaceIsLimited() {
        assertEquals(140, SequoiaUiStyle.searchWidth(320));
        assertEquals(140, SequoiaUiStyle.searchWidth(180));
        assertEquals(140, SequoiaUiStyle.searchWidth(140));
        assertEquals(80, SequoiaUiStyle.searchWidth(80));
        assertEquals(20, SequoiaUiStyle.searchWidth(20));
        assertEquals(0, SequoiaUiStyle.searchWidth(0));
        assertEquals(0, SequoiaUiStyle.searchWidth(-20));
    }
}
