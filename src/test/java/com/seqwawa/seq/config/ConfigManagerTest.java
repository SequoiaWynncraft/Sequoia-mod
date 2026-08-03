package com.seqwawa.seq.config;

import com.seqwawa.seq.network.auth.StoredAuthSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void normalizesBridgeUsernamesForCaseInsensitiveStorage() {
        assertTrue(ConfigManager.isValidBridgeUsername("  Player_Name  "));
        assertEquals("player_name", ConfigManager.normalizeBridgeUsername("  Player_Name  "));
        assertNull(ConfigManager.normalizeBridgeUsername("ab"));
        assertNull(ConfigManager.normalizeBridgeUsername("Player-Name"));
    }

    @Test
    void doesNotPersistAuthSessionInConfig() throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"), false);

        manager.setAuthSession(new StoredAuthSession(
                "backend-token",
                Instant.parse("2026-07-06T12:00:00Z"),
                "123e4567-e89b-12d3-a456-426614174000",
                "VerifiedPlayer"));
        manager.setDiscordUsername("discord-user");

        StoredAuthSession inMemorySession = manager.getStoredAuthSession();
        assertNotNull(inMemorySession);
        assertTrue(inMemorySession.hasToken());

        String json = Files.readString(configPath);
        assertFalse(json.contains("_auth_token"));
        assertFalse(json.contains("_auth_token_expires_at"));
        assertFalse(json.contains("_minecraft_uuid"));
        assertFalse(json.contains("_minecraft_username"));
        assertTrue(json.contains("_discord_username"));
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

        assertNull(manager.getStoredAuthSession());
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
