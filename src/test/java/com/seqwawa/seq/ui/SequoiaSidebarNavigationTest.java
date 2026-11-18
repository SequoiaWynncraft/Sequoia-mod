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
                    assertEquals(destinations.get(row), SequoiaSidebarNavigation.destinationAt(70, y, height, authorized, 0));
                    assertEquals(null, SequoiaSidebarNavigation.destinationAt(150, y, height, authorized, 0));
                    assertEquals(null,
                            SequoiaSidebarNavigation.destinationAt(70, layout.buttonY(row) - 0.5f, height, authorized, 0));
                }
            }
        }
    }

    @Test
    void aButtonIsDrawnWhereItIsClickedEdgesIncluded() {
        var destinations = SequoiaSidebarNavigation.destinations(false);
        var layout = SequoiaSidebarNavigation.sidebarLayout(540, destinations.size(), 22, 6, 0);
        UiBounds first = SequoiaSidebarNavigation.buttonBounds(layout, 0);

        assertEquals(new UiBounds(10, layout.buttonY(0), 120, 22), first);
        assertEquals(PARTY_FINDER, SequoiaSidebarNavigation.destinationAt(first.right(), first.bottom(), 540, false, 0));
        assertEquals(null, SequoiaSidebarNavigation.destinationAt(first.right() + 0.5f, first.centerY(), 540, false, 0));
    }

    @Test
    void whatAScreenKeepsAtTheFootMovesTheButtonsTheyAreClickedAt() {
        var destinations = SequoiaSidebarNavigation.destinations(false);
        // On a short sidebar, keeping room at the foot packs the buttons closer together.
        var packed = SequoiaSidebarNavigation.sidebarLayout(300, destinations.size(), 22, 6, 84);
        UiBounds last = SequoiaSidebarNavigation.buttonBounds(packed, destinations.size() - 1);

        assertEquals(GITHUB,
                SequoiaSidebarNavigation.destinationAt(last.centerX(), last.centerY(), 300, false, 84));
        assertTrue(last.bottom() <= 300 - 84);
    }

    @Test
    void warPlannerIsOnlyListedForAuthorizedMembers() {
        assertEquals(
                List.of(PARTY_FINDER, SETTINGS, ACHIEVEMENTS, CONNECTION, INGREDIENTS, MAP, MEMBERS, QUESTS, GITHUB),
                SequoiaSidebarNavigation.destinations(false));
        assertEquals(
                List.of(PARTY_FINDER, SETTINGS, ACHIEVEMENTS, CONNECTION, INGREDIENTS, MAP, MEMBERS, QUESTS, WAR, GITHUB),
                SequoiaSidebarNavigation.destinations(true));
    }

    @Test
    void allMenusStartWithPartyFinderAndSettingsThenUseAlphabeticalOrderWithGithubLast() {
        Stream.of(SequoiaSidebarNavigation.mainMenuDestinations(),
                SequoiaSidebarNavigation.destinations(false),
                SequoiaSidebarNavigation.destinations(true)).forEach(destinations -> {
            assertEquals(PARTY_FINDER, destinations.getFirst());
            assertEquals(SETTINGS, destinations.get(1));
            if (destinations.contains(GITHUB)) assertEquals(GITHUB, destinations.getLast());

            List<String> labels = destinations.stream()
                    .skip(2)
                    .filter(destination -> destination != GITHUB)
                    .map(SequoiaSidebarNavigation.Destination::label)
                    .toList();

            assertEquals(labels.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList(), labels);
        });
    }

    @Test
    void everyRowFitsACompactSidebar() {
        int rows = SequoiaSidebarNavigation.destinations(true).size();
        SequoiaSidebarNavigation.SidebarLayout layout =
                SequoiaSidebarNavigation.sidebarLayout(160, rows, 22, 6);

        assertTrue(layout.rowStep() >= layout.buttonHeight());
        assertTrue(layout.buttonHeight() >= 10);
        assertTrue(layout.bottom() <= 152);
        assertFalse(SettingsScreen.princessPromptFits(160, layout.bottom()));

        // Every row at full height, with the princess prompt still under them.
        SequoiaSidebarNavigation.SidebarLayout normal =
                SequoiaSidebarNavigation.sidebarLayout(400, rows, 22, 6);
        assertEquals(22f, normal.buttonHeight());
        assertTrue(SettingsScreen.princessPromptFits(400, normal.bottom()));
    }

    @Test
    void theButtonsKeepClearOfWhatIsReservedAtTheFoot() {
        var free = SequoiaSidebarNavigation.sidebarLayout(360, 9, 22, 6, 0);
        var reserved = SequoiaSidebarNavigation.sidebarLayout(360, 9, 22, 6, 56);

        org.junit.jupiter.api.Assertions.assertTrue(free.bottom() <= 360 - 8);
        org.junit.jupiter.api.Assertions.assertTrue(reserved.bottom() <= 360 - 56 - 8,
                "the last button ends " + reserved.bottom());
    }

    @Test
    void settingsLeavesThePrincessPromptItsPlaceAboveTheProfileCard() {
        // A 720-pixel window at the usual UI size, with the card's top at 276.
        float floor = SidebarProfile.top(360);
        for (boolean authorized : new boolean[] {false, true}) {
            int rows = SequoiaSidebarNavigation.destinations(authorized).size();
            var layout = SettingsScreen.settingsSidebarLayout(360, rows, floor, true);
            assertTrue(SettingsScreen.princessPromptFits(floor, layout.bottom()),
                    "the last of " + rows + " buttons ends at " + layout.bottom());
            assertEquals(SequoiaSidebarNavigation.sidebarLayout(360, rows, 22, 6, 360 - floor),
                    SettingsScreen.settingsSidebarLayout(360, rows, floor, false),
                    "without the prompt the buttons only keep clear of the card");
        }
    }

    @Test
    void settingsKeepsThePromptsPlaceFreeOnlyWhereThePromptFits() {
        float floor = SidebarProfile.top(360);
        int rows = SequoiaSidebarNavigation.destinations(false).size();

        assertEquals(360 - floor + 10 + 24, SettingsScreen.settingsSidebarReserved(360, rows, floor, true),
                "the profile card's room, and the prompt's above it");
        assertEquals(360 - floor, SettingsScreen.settingsSidebarReserved(360, rows, floor, false));
        assertEquals(84, SettingsScreen.settingsSidebarReserved(200, 10, 200 - 84, true),
                "too short for the prompt: only the card's room");
    }

    @Test
    void settingsGivesThePromptsSpaceBackWhereThePromptCannotFit() {
        int rows = SequoiaSidebarNavigation.destinations(true).size();
        float floor = 200 - 84;
        var layout = SettingsScreen.settingsSidebarLayout(200, rows, floor, true);

        assertFalse(SettingsScreen.princessPromptFits(floor, layout.bottom()));
        assertEquals(SequoiaSidebarNavigation.sidebarLayout(200, rows, 22, 6, 84), layout);
    }

    @Test
    void settingsLeavesTheProfileCardOutWhereItWouldHideThePrincessToggle() {
        int rows = 10;
        // At 240 the card fits under the buttons, but then the prompt does not, though it would without the card.
        assertTrue(SidebarProfile.fits(240, rows));
        assertTrue(SettingsScreen.princessPromptFits(240, SettingsScreen.settingsSidebarLayout(240, rows, 240, true).bottom()));
        assertFalse(SettingsScreen.profileLeavesPrincessRoom(240, rows, SidebarProfile.top(240), true, false));
        // At 360 both fit, and with the prompt switched off the card never gives way.
        assertTrue(SettingsScreen.profileLeavesPrincessRoom(360, rows, SidebarProfile.top(360), true, false));
        assertTrue(SettingsScreen.profileLeavesPrincessRoom(240, rows, SidebarProfile.top(240), false, false));
    }

    @Test
    void settingsLeavesTheProfileCardOutWhereItWouldHideThePrincessLeaderboard() {
        int rows = 10;
        float floor = SidebarProfile.top(440);
        // At 440 the leaderboard fits under the buttons without the card, and not with it.
        float without = SettingsScreen.settingsSidebarLayout(440, rows, 440, true).bottom();
        assertTrue(SettingsScreen.princessLeaderboardHeight(440, without, true) >= PrincessLeaderboardPanel.MIN_HEIGHT);
        float with = SettingsScreen.settingsSidebarLayout(440, rows, floor, true).bottom();
        assertTrue(SettingsScreen.princessLeaderboardHeight(floor, with, true) < PrincessLeaderboardPanel.MIN_HEIGHT);

        assertFalse(SettingsScreen.profileLeavesPrincessRoom(440, rows, floor, true, true));
        assertTrue(SettingsScreen.profileLeavesPrincessRoom(440, rows, floor, true, false), "unless the mode is off");
        assertTrue(SettingsScreen.profileLeavesPrincessRoom(720, rows, SidebarProfile.top(720), true, true),
                "a tall window has room for all of it");
    }
}
