package org.sequoia.seq.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sequoia.seq.network.auth.StoredAuthSession;

class ConfigManagerTest {
    @Test
    void saveWritesReadableConfigAndLoadRestoresValues(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        Path legacyTokenPath = tempDir.resolve(".seq_token");

        ConfigManager manager = new ConfigManager(configPath, legacyTokenPath);
        Setting.BooleanSetting enabled = new Setting.BooleanSetting("enabled", "test", false);
        manager.register(enabled);
        enabled.setValue(true);

        Instant expiresAt = Instant.parse("2026-06-24T20:00:00Z");
        manager.setAuthSession(new StoredAuthSession("token-value", expiresAt, "uuid-value", "PlayerName"));
        manager.setDiscordUsername("discord-name");
        manager.addIgnoredBridgeUser("Bridge_User");
        manager.setBombSharePromptSeen(true);
        manager.setStartupVideoBounds(0.1, 0.2, 0.3, 0.4);

        JsonObject saved = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
        assertEquals("token-value", saved.get("_auth_token").getAsString());
        assertEquals("discord-name", saved.get("_discord_username").getAsString());
        assertTrue(saved.get("_ignored_bridge_users").isJsonArray());
        assertFalse(Files.exists(configPath.resolveSibling("sequoia.json.tmp")));

        ConfigManager loaded = new ConfigManager(configPath, legacyTokenPath);
        Setting.BooleanSetting loadedEnabled = new Setting.BooleanSetting("enabled", "test", false);
        loaded.register(loadedEnabled);
        loaded.load();

        assertEquals("token-value", loaded.getToken());
        assertEquals(expiresAt, loaded.getTokenExpiresAt());
        assertEquals("uuid-value", loaded.getMinecraftUuid());
        assertEquals("PlayerName", loaded.getMinecraftUsername());
        assertEquals("discord-name", loaded.getDiscordUsername());
        assertEquals(List.of("bridge_user"), loaded.ignoredBridgeUsers());
        assertTrue(loaded.isBombSharePromptSeen());
        assertEquals(new ConfigManager.StartupVideoBounds(0.1, 0.2, 0.3, 0.4), loaded.getStartupVideoBounds());
        assertTrue(loadedEnabled.getValue());
    }

    @Test
    void concurrentSavesLeaveReadableLoadableConfig(@TempDir Path tempDir) throws Exception {
        Path configPath = tempDir.resolve("config").resolve("sequoia.json");
        ConfigManager manager = new ConfigManager(configPath, tempDir.resolve(".seq_token"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 40; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                start.await();
                manager.setDiscordUsername("discord-" + index);
                manager.setBombSharePromptSeen(index % 2 == 0);
                manager.setStartupVideoBounds(index / 100.0, index / 120.0, 0.5, 0.6);
                manager.addIgnoredBridgeUser("User_" + (index % 10));
                return null;
            }));
        }

        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        JsonObject saved = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
        assertTrue(saved.has("_discord_username"));
        assertTrue(saved.has("_bomb_share_prompt_seen"));

        ConfigManager loaded = new ConfigManager(configPath, tempDir.resolve(".seq_token"));
        loaded.load();
        assertNotNull(loaded.getDiscordUsername());
        assertFalse(loaded.ignoredBridgeUsers().isEmpty());
    }
}
