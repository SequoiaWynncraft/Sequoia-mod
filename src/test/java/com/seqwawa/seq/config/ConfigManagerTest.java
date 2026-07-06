package com.seqwawa.seq.config;

import com.seqwawa.seq.network.auth.StoredAuthSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    @TempDir
    Path tempDir;

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
}
