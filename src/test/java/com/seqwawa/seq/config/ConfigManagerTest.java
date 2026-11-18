package com.seqwawa.seq.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void gatheringSourceDefaultsToStaticAndPersistsApiOptIn() throws Exception {
        Path configPath = tempDir.resolve("sequoia.json");
        Files.writeString(configPath, "{}");
        var manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        var setting = com.seqwawa.seq.map.WorldMapSettings.createGatheringNodeSourceSetting();
        manager.register(setting);
        manager.load();
        assertEquals(com.seqwawa.seq.map.GatheringNodeSource.STATIC, setting.getValue());
        setting.setValue(com.seqwawa.seq.map.GatheringNodeSource.WYNN_API);
        manager.save();
        assertTrue(Files.readString(configPath).contains("\"world_map.gathering_node_source\": \"WYNN_API\""));
        var restored = com.seqwawa.seq.map.WorldMapSettings.createGatheringNodeSourceSetting();
        var reloaded = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        reloaded.register(restored);
        reloaded.load();
        assertEquals(com.seqwawa.seq.map.GatheringNodeSource.WYNN_API, restored.getValue());
        restored.reset();
        assertEquals(com.seqwawa.seq.map.GatheringNodeSource.STATIC, restored.getValue());
    }

    @Test
    void invalidSavedGatheringSourceUsesStaticDefault() throws Exception {
        Path configPath = tempDir.resolve("sequoia.json");
        Files.writeString(configPath, "{\"world_map.gathering_node_source\":\"unknown\"}");
        var manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        var setting = com.seqwawa.seq.map.WorldMapSettings.createGatheringNodeSourceSetting();
        manager.register(setting);
        manager.load();
        assertEquals(com.seqwawa.seq.map.GatheringNodeSource.STATIC, setting.getValue());
    }

    @Test
    void aSavedValueThatCannotBeReadKeepsItsSettingAndTheRestStillLoad() throws Exception {
        Path configPath = tempDir.resolve("sequoia.json");
        Files.writeString(configPath, "{\"ui.ui_size_percent\": 1e10000, \"ui.enabled\": false}");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        var size = new Setting.IntSetting("ui_size_percent", "ui", 100, 50, 200);
        var enabled = new Setting.BooleanSetting("enabled", "ui", true);
        manager.register(size);
        manager.register(enabled);

        manager.load();

        assertEquals(100, size.getValue());
        assertFalse(enabled.getValue());
    }

    @Test
    void queueHudDefaultsOnForConfigsWithoutTheKeyAndPersistsDisabledValue() throws Exception {
        Path configPath = tempDir.resolve("sequoia.json");
        Files.writeString(configPath, "{\"war_planner.queue_hud_only_owned_or_joined\": true}");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        var setting = com.seqwawa.seq.ui.WarTerritoryQueueHudRenderer.createEnabledSetting();
        manager.register(setting);
        manager.load();
        assertTrue(setting.getValue());
        assertEquals("Show queue timers", setting.getDisplayName());
        assertEquals("War queue HUD", setting.getSection());
        setting.setValue(false);
        manager.save();
        assertTrue(Files.readString(configPath).contains("\"war_planner.queue_hud_enabled\": false"));
        ConfigManager reloaded = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        var restored = com.seqwawa.seq.ui.WarTerritoryQueueHudRenderer.createEnabledSetting();
        reloaded.register(restored);
        reloaded.load();
        assertFalse(restored.getValue());
    }

    @Test
    void gearsharingProtectionDefaultsOnAndPersistsDisabledValue() throws Exception {
        Path configPath = tempDir.resolve("sequoia.json");
        Files.writeString(configPath, "{}");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        var setting = com.seqwawa.seq.managers.GearSharingProtectionManager.createSetting();
        manager.register(setting);
        manager.load();
        assertTrue(setting.getValue());
        assertTrue(setting.isVisible());
        assertEquals("Automatically disable Wynntils armor/accessory sharing", setting.getDisplayName());
        assertEquals("Gearsharing", setting.getSection());
        assertEquals("wynntils", setting.getCategory());
        setting.setValue(false);
        manager.save();
        assertTrue(Files.readString(configPath).contains("\"wynntils.auto_disable_wynntils_gearsharing\": false"));
        ConfigManager reloaded = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        var restored = com.seqwawa.seq.managers.GearSharingProtectionManager.createSetting();
        reloaded.register(restored);
        reloaded.load();
        assertFalse(restored.getValue());
        restored.reset();
        assertTrue(restored.getValue());
    }

    @Test
    void savingNeverWritesABackendSession() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);

        manager.setDiscordUsername(MAIN_ACCOUNT, "discord-user");

        String json = Files.readString(configPath);
        assertFalse(json.contains("_auth_token"));
        assertFalse(json.contains("_auth_token_expires_at"));
        assertFalse(json.contains("_minecraft_uuid"));
        assertFalse(json.contains("_minecraft_username"));
        assertTrue(json.contains("_discord_usernames"));
    }

    private static final java.util.UUID MAIN_ACCOUNT = java.util.UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final java.util.UUID ALT_ACCOUNT = java.util.UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void eachMinecraftAccountKeepsItsOwnDiscordName() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.setDiscordUsername(MAIN_ACCOUNT, "main-discord");
        manager.setDiscordUsername(ALT_ACCOUNT, "alt-discord");

        ConfigManager reloaded = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        reloaded.load();

        assertEquals("main-discord", reloaded.getDiscordUsername(MAIN_ACCOUNT));
        assertEquals("alt-discord", reloaded.getDiscordUsername(ALT_ACCOUNT));
    }

    @Test
    void clearingOneAccountsDiscordNameLeavesTheOthers() {
        ConfigManager manager = new ConfigManager(tempDir.resolve("sequoia.json"), tempDir.resolve(".seq_token"), false);
        manager.setDiscordUsername(MAIN_ACCOUNT, "main-discord");
        manager.setDiscordUsername(ALT_ACCOUNT, "alt-discord");

        manager.clearDiscordUsername(ALT_ACCOUNT);

        assertEquals("main-discord", manager.getDiscordUsername(MAIN_ACCOUNT));
        assertNull(manager.getDiscordUsername(ALT_ACCOUNT));
    }

    @Test
    void anOlderSingleDiscordNameGoesToTheFirstAccountThatAsks() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, "{\"_discord_username\": \"discord-user\"}");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.load();

        assertNull(manager.getDiscordUsername(null), "no account yet takes nothing");
        assertEquals("discord-user", manager.getDiscordUsername(MAIN_ACCOUNT));
        assertNull(manager.getDiscordUsername(ALT_ACCOUNT), "another account does not inherit it");

        ConfigManager restarted = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        restarted.load();
        assertNull(restarted.getDiscordUsername(ALT_ACCOUNT), "it was claimed for good");
        assertEquals("discord-user", restarted.getDiscordUsername(MAIN_ACCOUNT));
        assertFalse(Files.readString(configPath).contains("\"_discord_username\""));
    }

    @Test
    void aSignInReplyWithNoDiscordNameLeavesAnOlderNameForTheAccountItBelongsTo() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, "{\"_discord_username\": \"discord-user\"}");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.load();

        // The alt signs in first, and the backend links no Discord name to it.
        manager.setDiscordUsername(ALT_ACCOUNT, null);

        assertNull(manager.getDiscordUsername(ALT_ACCOUNT), "the alt does not take the older name either");
        ConfigManager restarted = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        restarted.load();
        assertNull(restarted.getDiscordUsername(ALT_ACCOUNT), "nor after a restart");
        assertEquals("discord-user", restarted.getDiscordUsername(MAIN_ACCOUNT));
    }

    @Test
    void aBlankDiscordNameFromTheBackendMeansNoneLikeAMissingOne() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, "{\"_discord_username\": \"discord-user\"}");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.load();
        manager.setDiscordUsername(MAIN_ACCOUNT, "old-discord");

        manager.setDiscordUsername(MAIN_ACCOUNT, "  ");
        manager.setDiscordUsername(ALT_ACCOUNT, "");

        assertNull(manager.getDiscordUsername(MAIN_ACCOUNT), "the stale name goes");
        assertNull(manager.getDiscordUsername(ALT_ACCOUNT), "and the older name is not taken");
        assertTrue(Files.readString(configPath).contains("\"_discord_username\": \"discord-user\""),
                "it waits for the account it belongs to");
    }

    @Test
    void clearSessionForgetsAnOlderNameNobodyClaimed() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, "{\"_discord_username\": \"discord-user\"}");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.load();

        manager.clearDiscordUsername(MAIN_ACCOUNT);

        assertNull(manager.getDiscordUsername(MAIN_ACCOUNT), "the player asked to forget it");
    }

    @Test
    void loadScrubsPersistedAuthSessionWithoutLoadingToken() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(
                configPath,
                """
                {
                  "_auth_token": "leaked-token",
                  "_auth_token_expires_at": "2026-07-06T12:00:00Z",
                  "_minecraft_uuid": "123e4567-e89b-12d3-a456-426614174000",
                  "_minecraft_username": "VerifiedPlayer",
                  "_discord_username": "discord-user",
                  "network.auto_connect": true
                }
                """);

        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.load();

        String json = Files.readString(configPath);
        assertFalse(json.contains("_auth_token"));
        assertFalse(json.contains("_auth_token_expires_at"));
        assertFalse(json.contains("_minecraft_uuid"));
        assertFalse(json.contains("_minecraft_username"));
        assertTrue(json.contains("_discord_username"));
        assertTrue(json.contains("network.auto_connect"));
    }

    @Test
    void persistsTrackedWorldEventIdsIncludingUnknownEvents() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);

        assertTrue(manager.setWorldEventTracked("known-event", true));
        assertTrue(manager.setWorldEventTracked("temporarily-unknown", true));

        ConfigManager reloaded = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        reloaded.load();
        assertEquals(Set.of("known-event", "temporarily-unknown"), reloaded.trackedWorldEventIds());
    }

    @Test
    void ignoresMalformedTrackedWorldEventEntries() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
                {
                  "_tracked_world_events": ["valid", "", 12, null, {"id": "bad"}]
                }
                """);

        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.load();

        assertEquals(Set.of("valid"), manager.trackedWorldEventIds());
    }

    @Test
    void persistsWarPlannerZoneVisibilityIncludingUncategorizedZones() {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);

        manager.setWarPlannerZoneHidden(11, true);
        manager.setWarPlannerZoneCategoryHidden(5L, true);
        manager.setWarPlannerZoneCategoryHidden(null, true);

        ConfigManager reloaded = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        reloaded.load();

        assertEquals(Set.of(11L), reloaded.hiddenWarPlannerZoneIds());
        assertTrue(reloaded.hiddenWarPlannerZoneCategoryIds().contains(5L));
        assertTrue(reloaded.hiddenWarPlannerZoneCategoryIds().contains(null));

        reloaded.setWarPlannerZoneHidden(11, false);
        reloaded.setWarPlannerZoneCategoryHidden(5L, false);
        reloaded.setWarPlannerZoneCategoryHidden(null, false);

        ConfigManager shownAgain = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        shownAgain.load();
        assertTrue(shownAgain.hiddenWarPlannerZoneIds().isEmpty());
        assertTrue(shownAgain.hiddenWarPlannerZoneCategoryIds().isEmpty());
    }

    @Test
    void ignoresMalformedWarPlannerVisibilityEntries() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
                {
                  "_war_planner_hidden_zone_ids": [3, -1, "4", null, {}],
                  "_war_planner_hidden_zone_category_ids": [7, 0, "8", null, []]
                }
                """);

        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.load();

        assertEquals(Set.of(3L), manager.hiddenWarPlannerZoneIds());
        assertEquals(2, manager.hiddenWarPlannerZoneCategoryIds().size());
        assertTrue(manager.hiddenWarPlannerZoneCategoryIds().contains(7L));
        assertTrue(manager.hiddenWarPlannerZoneCategoryIds().contains(null));
    }

    @Test
    void persistsUiSizeSetting() {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Setting.IntSetting setting = new Setting.IntSetting("ui_size_percent", "ui", 100, 75, 150, 5)
                .allowOutOfRangeManualInput();
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.register(setting);

        setting.setValueFromManualInput(175);
        manager.save();

        Setting.IntSetting reloadedSetting = new Setting.IntSetting("ui_size_percent", "ui", 100, 75, 150, 5)
                .allowOutOfRangeManualInput();
        ConfigManager reloaded = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        reloaded.register(reloadedSetting);
        reloaded.load();

        assertEquals(175, reloadedSetting.getValue());
    }

    @Test
    void loadsLegacySettingKeysOnlyWhenTheCurrentKeyIsAbsent() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
                {
                  "chat.show_rank_gradients": false,
                  "chat.show_rank_pill_gradients": true
                }
                """);
        Setting.BooleanSetting pillGradients =
                new Setting.BooleanSetting("show_rank_pill_gradients", "chat", false);
        Setting.BooleanSetting usernameGradients =
                new Setting.BooleanSetting("show_username_gradients", "chat", true);
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.registerWithLegacyKeys(pillGradients, "chat.show_rank_gradients");
        manager.registerWithLegacyKeys(usernameGradients, "chat.show_rank_gradients");

        manager.load();

        assertTrue(pillGradients.getValue(), "the current pill setting wins over its legacy key");
        assertFalse(usernameGradients.getValue(), "a missing username setting inherits the old shared value");

        manager.save();
        String migrated = Files.readString(configPath);
        assertTrue(migrated.contains("\"chat.show_rank_pill_gradients\": true"));
        assertTrue(migrated.contains("\"chat.show_username_gradients\": false"));
    }

    @Test
    void persistsAndAppliesChoiceSetting() {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Setting.ChoiceSetting setting = new Setting.ChoiceSetting(
                "theme", "ui", "default", List.of("default", "high_contrast"), ignored -> {});
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.register(setting);
        setting.setValue("high_contrast");
        manager.save();

        AtomicReference<String> applied = new AtomicReference<>();
        Setting.ChoiceSetting reloadedSetting = new Setting.ChoiceSetting(
                "theme", "ui", "default", List.of("default", "high_contrast"), applied::set);
        ConfigManager reloaded = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        reloaded.register(reloadedSetting);
        reloaded.load();

        assertEquals("high_contrast", reloadedSetting.getValue());
        assertEquals("high_contrast", applied.get());
    }

    @Test
    void persistsColorSettingsAsCanonicalHex() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Setting.ColorSetting lightRoom = new Setting.ColorSetting("light_room_ring_color", "raids", 0x00FFFF);
        Setting.ColorSetting halcyon = new Setting.ColorSetting("halcyon_ring_color", "raids", 0x00FFFF);
        Setting.ColorSetting radiance = new Setting.ColorSetting("radiance_marker_color", "raids", 0xFF0000);
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        manager.register(lightRoom);
        manager.register(halcyon);
        manager.register(radiance);

        lightRoom.setHexValue("#a1b2c3");
        halcyon.setHexValue("#123456");
        radiance.setHexValue("#fedcba");
        manager.save();

        String json = Files.readString(configPath);
        assertTrue(json.contains("\"raids.light_room_ring_color\": \"#A1B2C3\""));
        assertTrue(json.contains("\"raids.halcyon_ring_color\": \"#123456\""));
        assertTrue(json.contains("\"raids.radiance_marker_color\": \"#FEDCBA\""));

        Setting.ColorSetting reloadedLightRoom =
                new Setting.ColorSetting("light_room_ring_color", "raids", 0x00FFFF);
        Setting.ColorSetting reloadedHalcyon =
                new Setting.ColorSetting("halcyon_ring_color", "raids", 0x00FFFF);
        Setting.ColorSetting reloadedRadiance =
                new Setting.ColorSetting("radiance_marker_color", "raids", 0xFF0000);
        ConfigManager reloaded = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);
        reloaded.register(reloadedLightRoom);
        reloaded.register(reloadedHalcyon);
        reloaded.register(reloadedRadiance);
        reloaded.load();

        assertEquals("#A1B2C3", reloadedLightRoom.getHexValue());
        assertEquals("#123456", reloadedHalcyon.getHexValue());
        assertEquals("#FEDCBA", reloadedRadiance.getHexValue());
    }
}
