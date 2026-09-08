package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingsSectionExpansionTest {
    @Test
    void sectionsStartFoldedAndCanBeExpandedAndFoldedIndependently() {
        SettingsSectionExpansion sections = new SettingsSectionExpansion();
        assertTrue(sections.isCollapsed("chat", "Colors", false));
        assertFalse(sections.isCollapsed("chat", null, false));

        sections.toggle("chat", "Colors");
        assertFalse(sections.isCollapsed("chat", "Colors", false));
        assertTrue(sections.isCollapsed("raids", "Colors", false));
        assertTrue(sections.isCollapsed("chat", "Ranks", false));

        sections.toggle("chat", "Colors");
        assertTrue(sections.isCollapsed("chat", "Colors", false));
    }

    @Test
    void searchRevealsFoldedSectionsWithoutLosingExpansionChoices() {
        SettingsSectionExpansion sections = new SettingsSectionExpansion();
        sections.toggle("chat", "Colors");

        assertFalse(sections.isCollapsed("chat", "Colors", true));
        assertFalse(sections.isCollapsed("chat", "Ranks", true));
        assertFalse(sections.isCollapsed("chat", "Colors", false));
        assertTrue(sections.isCollapsed("chat", "Ranks", false));
    }

    @Test
    void newScreenStartsFoldedAgain() {
        SettingsSectionExpansion previousScreen = new SettingsSectionExpansion();
        previousScreen.toggle("chat", "Colors");

        assertTrue(new SettingsSectionExpansion().isCollapsed("chat", "Colors", false));
    }
}
