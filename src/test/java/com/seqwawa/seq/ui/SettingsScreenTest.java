package com.seqwawa.seq.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seqwawa.seq.config.Setting;
import com.seqwawa.seq.ui.widget.BooleanWidget;
import com.seqwawa.seq.ui.widget.SettingWidget;
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
    @Test
    void interleavedRankPillControlsBecomeOneSectionWithoutDroppingOrReorderingControls() {
        SettingWidget<?> ranks = widget("ranks", "Discord ranks");
        SettingWidget<?> showPills = widget("show_pills", "Rank pills");
        SettingWidget<?> insignias = widget("insignias", "Discord ranks");
        SettingWidget<?> perUserColors = widget("per_user_colors", "Discord ranks");
        SettingWidget<?> pillColors = widget("pill_colors", "Rank pills");
        SettingWidget<?> pillGradients = widget("pill_gradients", "Rank pills");
        SettingWidget<?> usernames = widget("usernames", "Usernames");

        assertEquals(
                List.of(ranks, insignias, perUserColors, showPills, pillColors, pillGradients, usernames),
                SettingsScreen.groupWidgetsBySection(
                        List.of(ranks, showPills, insignias, perUserColors, pillColors, pillGradients, usernames)));
    }

    @Test
    void unsectionedControlsAreRetainedAndGroupingIsStable() {
        SettingWidget<?> first = widget("first", null);
        SettingWidget<?> grouped = widget("grouped", "Options");
        SettingWidget<?> last = widget("last", null);
        List<SettingWidget<?>> result = SettingsScreen.groupWidgetsBySection(List.of(first, grouped, last));

        assertEquals(List.of(first, last, grouped), result);
        assertEquals(result, SettingsScreen.groupWidgetsBySection(result));
        assertEquals(List.of(), SettingsScreen.groupWidgetsBySection(List.of()));
    }

    private static SettingWidget<?> widget(String name, String section) {
        Setting.BooleanSetting setting = new Setting.BooleanSetting(name, "chat", true);
        setting.setPresentation(null, null, section);
        return new BooleanWidget(setting);
    }

}
