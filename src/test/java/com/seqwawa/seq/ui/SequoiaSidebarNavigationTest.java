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
    void warPlannerIsOnlyListedForAuthorizedMembers() {
        assertEquals(
                List.of(PARTY_FINDER, ACHIEVEMENTS, CONNECTION, GITHUB, INGREDIENTS, MAP, SEQ_POINTS, SETTINGS),
                SequoiaSidebarNavigation.destinations(false));
        assertEquals(
                List.of(PARTY_FINDER, ACHIEVEMENTS, CONNECTION, GITHUB, INGREDIENTS, MAP, SEQ_POINTS, SETTINGS, WAR),
                SequoiaSidebarNavigation.destinations(true));
    }

    @Test
    void partyFinderStaysFirstAndOtherDestinationsStayInAlphabeticalOrder() {
        Stream.of(false, true).forEach(authorized -> {
            List<SequoiaSidebarNavigation.Destination> destinations =
                    SequoiaSidebarNavigation.destinations(authorized);
            assertEquals(PARTY_FINDER, destinations.getFirst());

            List<String> labels = destinations.stream()
                    .skip(1)
                    .map(SequoiaSidebarNavigation.Destination::label)
                    .toList();

            assertEquals(labels.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), labels);
        });
    }

    @Test
    void nineRowsFitACompactSidebar() {
        SequoiaSidebarNavigation.SidebarLayout layout =
                SequoiaSidebarNavigation.sidebarLayout(160, 9, 22, 6);

        assertTrue(layout.rowStep() >= layout.buttonHeight());
        assertTrue(layout.buttonHeight() >= 10);
        assertTrue(layout.bottom() <= 152);
        assertFalse(SettingsScreen.princessPromptFits(160, layout.bottom()));

        SequoiaSidebarNavigation.SidebarLayout normal =
                SequoiaSidebarNavigation.sidebarLayout(320, 9, 22, 6);
        assertFalse(SettingsScreen.princessPromptFits(320, normal.bottom()));

        SequoiaSidebarNavigation.SidebarLayout tall =
                SequoiaSidebarNavigation.sidebarLayout(400, 9, 22, 6);
        assertTrue(SettingsScreen.princessPromptFits(400, tall.bottom()));
    }
}
