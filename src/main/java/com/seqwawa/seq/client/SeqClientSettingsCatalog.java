package com.seqwawa.seq.client;

import com.seqwawa.seq.config.ConfigManager;
import com.seqwawa.seq.config.Setting;
import java.util.List;
import java.util.function.Consumer;

final class SeqClientSettingsCatalog {
    private final Setting.BooleanSetting autoConnect =
            new Setting.BooleanSetting("auto_connect", "network", true);
    private final Setting.BooleanSetting showDiscordChat =
            new Setting.BooleanSetting("show_discord_bridge", "chat", true);
    private final Setting.BooleanSetting raidAutoAnnounce =
            new Setting.BooleanSetting("auto_announce", "raids", true);
    private final Setting.BooleanSetting radianceChecker =
            new Setting.BooleanSetting("enable_radiance_visualiser", "raids", true);
    private final Setting.ColorSetting radianceMarkerColor =
            new Setting.ColorSetting("radiance_marker_color", "raids", 0xFF0000);
    private final Setting.BooleanSetting halcyonRangeVisualiser =
            new Setting.BooleanSetting("enable_halcyon_range_visualiser", "raids", true);
    private final Setting.ColorSetting halcyonRingColor =
            new Setting.ColorSetting("halcyon_ring_color", "raids", 0x00FFFF);
    private final Setting.BooleanSetting lightRoomVisualiser =
            new Setting.BooleanSetting("enable_light_room_visualiser", "raids", true);
    private final Setting.ColorSetting lightRoomRingColor =
            new Setting.ColorSetting("light_room_ring_color", "raids", 0x00FFFF);
    private final Setting.BooleanSetting trackGuildWars =
            new Setting.BooleanSetting("track_guild_wars", "guild_wars", true);
    private final Setting.BooleanSetting checkUpdates =
            new Setting.BooleanSetting("check_updates", "updates", true);
    private final Setting.BooleanSetting trackGuildStorage =
            new Setting.BooleanSetting("track_guild_storage", "guild_storage", true);
    private final Setting.IntSetting guildStorageEmeraldNotifyValue = new Setting.IntSetting(
            "guild_storage_emerald_threshold_percent", "guild_storage", 100, 0, 100);
    private final Setting.IntSetting guildStorageAspectNotifyValue = new Setting.IntSetting(
            "guild_storage_aspect_threshold_percent", "guild_storage", 100, 0, 100);
    private final Setting.BooleanSetting easterEggs =
            new Setting.BooleanSetting("enable_easter_eggs", "ui", true);
    private final Setting.BooleanSetting startupVideo =
            new Setting.BooleanSetting("startup_video", "ui", false);
    private final Setting.IntSetting uiSizePercent =
            new Setting.IntSetting("ui_size_percent", "ui", 100, 75, 150, 5)
                    .allowOutOfRangeManualInput();
    private final Setting.ChoiceSetting theme;
    private final Setting.BooleanSetting announceOpenParties =
            new Setting.BooleanSetting("announce_open_parties", "party_finder", true);
    private final Setting.IntSetting announceOpenPartiesIntervalMinutes = new Setting.IntSetting(
            "announce_open_parties_interval_minutes", "party_finder", 5, 1, 60);
    private final Setting.BooleanSetting syncWynnParty =
            new Setting.BooleanSetting("sync_with_wynn_party", "party_finder", true);
    private final Setting.BooleanSetting receiveBombShareRequests =
            new Setting.BooleanSetting("receive_bomb_share_requests", "network", true);
    private final Setting.BooleanSetting showRaidBadges =
            new Setting.BooleanSetting("show_raid_badges", "leaderboard_badges", true);
    private final Setting.BooleanSetting showInsigniaBadges =
            new Setting.BooleanSetting("show_insignia_badges", "leaderboard_badges", true);
    private final Setting.BooleanSetting showOwnLeaderboardBadge =
            new Setting.BooleanSetting("show_own_leaderboard_badge", "leaderboard_badges", true);
    private final Setting.BooleanSetting showPartyHealthBars =
            new Setting.BooleanSetting("show_party_healthbars", "raids", true);
    private final Setting.BooleanSetting notifyTrackedWorldEvents =
            new Setting.BooleanSetting("notify_tracked_world_events", "world_events", false);
    private final List<Setting<?>> settings;

    private SeqClientSettingsCatalog(
            String currentTheme, List<String> loadedThemeNames, Consumer<String> themeChangeListener) {
        theme = new Setting.ChoiceSetting(
                "theme", "ui", currentTheme, loadedThemeNames, themeChangeListener);

        radianceMarkerColor.setVisibilityCondition(radianceChecker::getValue);
        halcyonRingColor.setVisibilityCondition(halcyonRangeVisualiser::getValue);
        lightRoomRingColor.setVisibilityCondition(lightRoomVisualiser::getValue);

        settings = List.of(
                autoConnect,
                showDiscordChat,
                raidAutoAnnounce,
                trackGuildWars,
                checkUpdates,
                trackGuildStorage,
                guildStorageEmeraldNotifyValue,
                guildStorageAspectNotifyValue,
                easterEggs,
                startupVideo,
                uiSizePercent,
                theme,
                announceOpenParties,
                announceOpenPartiesIntervalMinutes,
                syncWynnParty,
                receiveBombShareRequests,
                radianceChecker,
                radianceMarkerColor,
                halcyonRangeVisualiser,
                halcyonRingColor,
                lightRoomVisualiser,
                lightRoomRingColor,
                showRaidBadges,
                showInsigniaBadges,
                showOwnLeaderboardBadge,
                showPartyHealthBars,
                notifyTrackedWorldEvents);
    }

    static SeqClientSettingsCatalog create(
            String currentTheme, List<String> loadedThemeNames, Consumer<String> themeChangeListener) {
        return new SeqClientSettingsCatalog(currentTheme, loadedThemeNames, themeChangeListener);
    }

    List<Setting<?>> settings() {
        return settings;
    }

    void install(ConfigManager configManager) {
        SeqClient.autoConnectSetting = autoConnect;
        SeqClient.showDiscordChatSetting = showDiscordChat;
        SeqClient.raidAutoAnnounceSetting = raidAutoAnnounce;
        SeqClient.trackGuildWarsSetting = trackGuildWars;
        SeqClient.checkUpdatesSetting = checkUpdates;
        SeqClient.trackGuildStorageSetting = trackGuildStorage;
        SeqClient.guildStorageEmeraldNotifyValueSetting = guildStorageEmeraldNotifyValue;
        SeqClient.guildStorageAspectNotifyValueSetting = guildStorageAspectNotifyValue;
        SeqClient.easterEggsSetting = easterEggs;
        SeqClient.startupVideoSetting = startupVideo;
        SeqClient.uiSizePercentSetting = uiSizePercent;
        SeqClient.themeSetting = theme;
        SeqClient.announceOpenPartiesSetting = announceOpenParties;
        SeqClient.announceOpenPartiesIntervalMinutesSetting = announceOpenPartiesIntervalMinutes;
        SeqClient.syncWynnPartySetting = syncWynnParty;
        SeqClient.receiveBombShareRequestsSetting = receiveBombShareRequests;
        SeqClient.radianceCheckerSetting = radianceChecker;
        SeqClient.radianceMarkerColorSetting = radianceMarkerColor;
        SeqClient.halcyonRangeVisualiserSetting = halcyonRangeVisualiser;
        SeqClient.halcyonRingColorSetting = halcyonRingColor;
        SeqClient.lightRoomVisualiserSetting = lightRoomVisualiser;
        SeqClient.lightRoomRingColorSetting = lightRoomRingColor;
        SeqClient.showRaidBadgesSetting = showRaidBadges;
        SeqClient.showInsigniaBadgesSetting = showInsigniaBadges;
        SeqClient.showOwnLeaderboardBadgeSetting = showOwnLeaderboardBadge;
        SeqClient.showPartyHealthBarsSetting = showPartyHealthBars;
        SeqClient.notifyTrackedWorldEventsSetting = notifyTrackedWorldEvents;

        settings.forEach(configManager::register);
    }
}
