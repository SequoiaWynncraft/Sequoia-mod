package com.seqwawa.seq.ui;

import static com.seqwawa.seq.ui.SequoiaSidebarNavigation.Destination.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SequoiaSidebarNavigationTest {
    @Test
    void warPlannerIsOnlyListedForAuthorizedMembers() {
        assertEquals(
                List.of(CONNECTION, ACHIEVEMENTS, GITHUB, INGREDIENTS, MAP, PARTY_FINDER, SETTINGS),
                SequoiaSidebarNavigation.destinations(false));
        assertEquals(
                List.of(CONNECTION, ACHIEVEMENTS, GITHUB, INGREDIENTS, MAP, PARTY_FINDER, SETTINGS, WAR),
                SequoiaSidebarNavigation.destinations(true));
    }

    @Test
    void eightRowsFitACompactSidebar() {
        SequoiaSidebarNavigation.SidebarLayout layout =
                SequoiaSidebarNavigation.sidebarLayout(160, 8, 22, 6);

        assertTrue(layout.rowStep() >= layout.buttonHeight());
        assertTrue(layout.buttonHeight() >= 10);
        assertTrue(layout.bottom() <= 152);
        assertFalse(SettingsScreen.princessPromptFits(160, layout.bottom()));

        SequoiaSidebarNavigation.SidebarLayout normal =
                SequoiaSidebarNavigation.sidebarLayout(320, 8, 22, 6);
        assertTrue(SettingsScreen.princessPromptFits(320, normal.bottom()));
    }
}
