package com.seqwawa.seq.ui;

import static com.seqwawa.seq.ui.SequoiaSidebarNavigation.Destination.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SequoiaSidebarNavigationTest {
    @Test
    void hitTestingMatchesEveryRenderedRowIncludingCompactAndAuthorizedSidebars() {
        for (boolean authorized : new boolean[] {false, true}) {
            for (float height : new float[] {160, 540}) {
                var destinations = SequoiaSidebarNavigation.destinations(authorized);
                var layout = SequoiaSidebarNavigation.sidebarLayout(height, destinations.size(), 22, 6);
                for (int row = 0; row < destinations.size(); row++) {
                    float y = layout.buttonY(row) + layout.buttonHeight() / 2;
                    assertEquals(destinations.get(row), SequoiaSidebarNavigation.destinationAt(70, y, height, authorized));
                    assertEquals(null, SequoiaSidebarNavigation.destinationAt(150, y, height, authorized));
                    assertEquals(null, SequoiaSidebarNavigation.destinationAt(70, layout.buttonY(row) - 0.5f, height, authorized));
                }
            }
        }
    }

    @Test
    void warPlannerIsOnlyListedForAuthorizedMembers() {
        assertEquals(
                List.of(PARTY_FINDER, SETTINGS, ACHIEVEMENTS, CONNECTION, GITHUB, INGREDIENTS, MAP),
                SequoiaSidebarNavigation.destinations(false));
        assertEquals(
                List.of(PARTY_FINDER, SETTINGS, ACHIEVEMENTS, CONNECTION, GITHUB, INGREDIENTS, MAP, WAR),
                SequoiaSidebarNavigation.destinations(true));
    }

    @Test
    void allMenusStartWithPartyFinderAndSettingsThenUseAlphabeticalOrder() {
        Stream.of(SequoiaSidebarNavigation.mainMenuDestinations(),
                SequoiaSidebarNavigation.destinations(false),
                SequoiaSidebarNavigation.destinations(true)).forEach(destinations -> {
            assertEquals(PARTY_FINDER, destinations.getFirst());
            assertEquals(SETTINGS, destinations.get(1));

            List<String> labels = destinations.stream()
                    .skip(2)
                    .map(SequoiaSidebarNavigation.Destination::label)
                    .toList();

            assertEquals(labels.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), labels);
        });
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
