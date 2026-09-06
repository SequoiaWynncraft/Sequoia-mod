package com.seqwawa.seq.ui;

import static com.seqwawa.seq.managers.ThemeManager.color;
import static com.seqwawa.seq.ui.theme.UiColor.*;

import java.awt.Color;

/** Shared navigation and search conventions, matching Party Finder. */
final class SequoiaUiStyle {
    private static final float SEARCH_BAR_WIDTH = 140;

    private SequoiaUiStyle() {}

    static float searchWidth(float availableWidth) {
        return Math.max(0, Math.min(SEARCH_BAR_WIDTH, availableWidth));
    }

    static Color sidebarButtonColor(boolean active, boolean hovered) {
        return color(active ? ACCENT_PRIMARY_DARK : hovered ? BACKGROUND_CONTENT_FOCUSED : BACKGROUND_CONTENT);
    }
}
