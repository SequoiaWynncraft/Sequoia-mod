package org.sequoia.seq.config;

import com.google.gson.*;
import lombok.Getter;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.auth.StoredAuthSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
public class ConfigManager {
    private static final Path CONFIG_PATH = Path.of("config", "sequoia.json");
    private static final Path LEGACY_TOKEN_FILE = Path.of(System.getProperty("user.home"), ".seq_token");
    private static final String TOKEN_KEY = "_auth_token";
    private static final String TOKEN_EXPIRES_AT_KEY = "_auth_token_expires_at";
    private static final String MINECRAFT_UUID_KEY = "_minecraft_uuid";
    private static final String MINECRAFT_USERNAME_KEY = "_minecraft_username";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final List<Setting<?>> settings = new ArrayList<>();
    private String authToken;
    private Instant authTokenExpiresAt;
    private String minecraftUuid;
    private String minecraftUsername;

    public ConfigManager() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::save));
    }

    public void register(Setting<?> setting) {
        settings.add(setting);
    }

    // ── Token management ──

    public String getToken() {
        return authToken;
    }

    public Instant getTokenExpiresAt() {
        return authTokenExpiresAt;
    }

    public String getMinecraftUuid() {
        return minecraftUuid;
    }

    public String getMinecraftUsername() {
        return minecraftUsername;
    }

    public StoredAuthSession getStoredAuthSession() {
        if (authToken == null || authToken.isBlank()) {
            return null;
        }
        return new StoredAuthSession(authToken, authTokenExpiresAt, minecraftUuid, minecraftUsername);
    }

    public void setToken(String token) {
        this.authToken = token;
        this.authTokenExpiresAt = null;
        save();
    }

    public void setAuthSession(StoredAuthSession session) {
        if (session == null || !session.hasToken()) {
            clearAuthSession();
            return;
        }

        this.authToken = session.token();
        this.authTokenExpiresAt = session.expiresAt();
        this.minecraftUuid = session.minecraftUuid();
        this.minecraftUsername = session.minecraftUsername();
        save();
    }

    public void clearToken() {
        clearAuthSession();
    }

    public void clearAuthSession() {
        this.authToken = null;
        this.authTokenExpiresAt = null;
        this.minecraftUuid = null;
        this.minecraftUsername = null;
        save();
    }

    /** Migrate token from legacy ~/.seq_token into sequoia.json on first run. */
    public void migrateToken() {
        if (authToken != null) return;
        try {
            if (Files.exists(LEGACY_TOKEN_FILE)) {
                String legacy = Files.readString(LEGACY_TOKEN_FILE).trim();
                if (!legacy.isEmpty()) {
                    authToken = legacy;
                    save();
                    SeqClient.LOGGER.info("Migrated auth token from ~/.seq_token into sequoia.json");
                }
                Files.deleteIfExists(LEGACY_TOKEN_FILE);
            }
        } catch (Exception e) {
            SeqClient.LOGGER.warn("Failed to migrate legacy token", e);
        }
    }

    // ── Save / Load ──

    public void save() {
        try {
            JsonObject root = new JsonObject();
            if (authToken != null) {
                root.addProperty(TOKEN_KEY, authToken);
            }
            if (authTokenExpiresAt != null) {
                root.addProperty(TOKEN_EXPIRES_AT_KEY, authTokenExpiresAt.toString());
            }
            if (minecraftUuid != null) {
                root.addProperty(MINECRAFT_UUID_KEY, minecraftUuid);
            }
            if (minecraftUsername != null) {
                root.addProperty(MINECRAFT_USERNAME_KEY, minecraftUsername);
            }
            for (Setting<?> setting : settings) {
                String key = setting.getCategory() + "." + setting.getName();
                root.add(key, setting.serialize());
            }
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer =
                    new OutputStreamWriter(new FileOutputStream(CONFIG_PATH.toFile()), StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            SeqClient.LOGGER.error("Failed to save config", e);
        }
    }

    public void load() {
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(CONFIG_PATH.toFile()), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            // Load token
            if (root != null && root.has(TOKEN_KEY) && root.get(TOKEN_KEY).isJsonPrimitive()) {
                authToken = root.get(TOKEN_KEY).getAsString();
            }
            if (root != null
                    && root.has(TOKEN_EXPIRES_AT_KEY)
                    && root.get(TOKEN_EXPIRES_AT_KEY).isJsonPrimitive()) {
                try {
                    authTokenExpiresAt =
                            Instant.parse(root.get(TOKEN_EXPIRES_AT_KEY).getAsString());
                } catch (Exception ignored) {
                    authTokenExpiresAt = null;
                }
            }
            if (root != null
                    && root.has(MINECRAFT_UUID_KEY)
                    && root.get(MINECRAFT_UUID_KEY).isJsonPrimitive()) {
                minecraftUuid = root.get(MINECRAFT_UUID_KEY).getAsString();
            }
            if (root != null
                    && root.has(MINECRAFT_USERNAME_KEY)
                    && root.get(MINECRAFT_USERNAME_KEY).isJsonPrimitive()) {
                minecraftUsername = root.get(MINECRAFT_USERNAME_KEY).getAsString();
            }

            for (Setting<?> setting : settings) {
                if (root == null) {
                    break;
                }
                String key = setting.getCategory() + "." + setting.getName();
                JsonElement element = root.get(key);
                if (element != null) {
                    setting.deserialize(element);
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            SeqClient.LOGGER.error("Failed to load config", e);
        }
    }
}
