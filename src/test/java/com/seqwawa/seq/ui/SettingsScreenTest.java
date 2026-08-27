package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class SettingsScreenTest {
    @Test
    void categoriesAreSortedByDisplayName() {
        List<String> registrationOrder = List.of(
                "chat_filters",
                "network",
                "chat",
                "raids",
                "guild_wars",
                "updates",
                "guild_storage",
                "ui",
                "party_finder",
                "leaderboard_badges",
                "world_events");

        assertEquals(
                List.of(
                        "leaderboard_badges",
                        "chat",
                        "chat_filters",
                        "guild_storage",
                        "network",
                        "party_finder",
                        "raids",
                        "ui",
                        "updates",
                        "guild_wars",
                        "world_events"),
                SettingsScreen.sortedCategoryNames(registrationOrder));
    }
}
