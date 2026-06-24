package org.sequoia.seq.config;

import com.google.gson.*;
import lombok.AccessLevel;
import lombok.Getter;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.network.auth.StoredAuthSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Getter
public class ConfigManager {
    private static final Path DEFAULT_CONFIG_PATH = Path.of("config", "sequoia.json");
    private static final Path DEFAULT_LEGACY_TOKEN_FILE = Path.of(System.getProperty("user.home"), ".seq_token");
    private static final String TOKEN_KEY = "_auth_token";
    private static final String TOKEN_EXPIRES_AT_KEY = "_auth_token_expires_at";
    private static final String MINECRAFT_UUID_KEY = "_minecraft_uuid";
    private static final String MINECRAFT_USERNAME_KEY = "_minecraft_username";
    private static final String DISCORD_USERNAME_KEY = "_discord_username";
    private static final String IGNORED_BRIDGE_USERS_KEY = "_ignored_bridge_users";
    private static final String BOMB_SHARE_PROMPT_SEEN_KEY = "_bomb_share_prompt_seen";
    private static final String STARTUP_VIDEO_X_KEY = "_startup_video_x";
    private static final String STARTUP_VIDEO_Y_KEY = "_startup_video_y";
    private static final String STARTUP_VIDEO_WIDTH_KEY = "_startup_video_width";
    private static final String STARTUP_VIDEO_HEIGHT_KEY = "_startup_video_height";
    private static final Pattern MINECRAFT_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    @Getter(AccessLevel.NONE)
    private final Path configPath;

    @Getter(AccessLevel.NONE)
    private final Path legacyTokenFile;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final Set<String> ignoredBridgeUsers = new LinkedHashSet<>();
    private String authToken;
    private Instant authTokenExpiresAt;
    private String minecraftUuid;
    private String minecraftUsername;
    private String discordUsername;
    private boolean bombSharePromptSeen;
    private Double startupVideoX;
    private Double startupVideoY;
    private Double startupVideoWidth;
    private Double startupVideoHeight;

    public ConfigManager() {
        this(DEFAULT_CONFIG_PATH, DEFAULT_LEGACY_TOKEN_FILE, true);
    }

    ConfigManager(Path configPath, Path legacyTokenFile) {
        this(configPath, legacyTokenFile, false);
    }

    private ConfigManager(Path configPath, Path legacyTokenFile, boolean registerShutdownHook) {
        this.configPath = configPath;
        this.legacyTokenFile = legacyTokenFile;
        if (registerShutdownHook) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::save));
        }
    }

    public synchronized void register(Setting<?> setting) {
        settings.add(setting);
    }

    // ── Token management ──

    public synchronized String getToken() {
        return authToken;
    }

    public synchronized Instant getTokenExpiresAt() {
        return authTokenExpiresAt;
    }

    public synchronized String getMinecraftUuid() {
        return minecraftUuid;
    }

    public synchronized String getMinecraftUsername() {
        return minecraftUsername;
    }

    public synchronized String getDiscordUsername() {
        return discordUsername;
    }

    public synchronized boolean isBombSharePromptSeen() {
        return bombSharePromptSeen;
    }

    public synchronized StoredAuthSession getStoredAuthSession() {
        if (authToken == null || authToken.isBlank()) {
            return null;
        }
        return new StoredAuthSession(authToken, authTokenExpiresAt, minecraftUuid, minecraftUsername);
    }

    public synchronized void setToken(String token) {
        this.authToken = token;
        this.authTokenExpiresAt = null;
        save();
    }

    public synchronized void setAuthSession(StoredAuthSession session) {
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

    public synchronized void clearToken() {
        clearAuthSession();
    }

    public synchronized void clearAuthSession() {
        this.authToken = null;
        this.authTokenExpiresAt = null;
        this.minecraftUuid = null;
        this.minecraftUsername = null;
        save();
    }

    public synchronized void setDiscordUsername(String discordUsername) {
        this.discordUsername = discordUsername;
        save();
    }

    public synchronized void clearDiscordUsername() {
        this.discordUsername = null;
        save();
    }

    public static boolean isValidBridgeUsername(String username) {
        return normalizeBridgeUsername(username) != null;
    }

    public static String normalizeBridgeUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        if (!MINECRAFT_USERNAME_PATTERN.matcher(trimmed).matches()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    public synchronized boolean addIgnoredBridgeUser(String username) {
        String normalized = normalizeBridgeUsername(username);
        if (normalized == null) {
            return false;
        }
        boolean added = ignoredBridgeUsers.add(normalized);
        if (added) {
            save();
        }
        return added;
    }

    public synchronized boolean removeIgnoredBridgeUser(String username) {
        String normalized = normalizeBridgeUsername(username);
        if (normalized == null) {
            return false;
        }
        boolean removed = ignoredBridgeUsers.remove(normalized);
        if (removed) {
            save();
        }
        return removed;
    }

    public synchronized boolean isIgnoredBridgeUser(String username) {
        String normalized = normalizeBridgeUsername(username);
        return normalized != null && ignoredBridgeUsers.contains(normalized);
    }

    public synchronized List<String> ignoredBridgeUsers() {
        return List.copyOf(ignoredBridgeUsers);
    }

    public synchronized void setBombSharePromptSeen(boolean bombSharePromptSeen) {
        this.bombSharePromptSeen = bombSharePromptSeen;
        save();
    }

    public synchronized StartupVideoBounds getStartupVideoBounds() {
        if (startupVideoX == null || startupVideoY == null || startupVideoWidth == null || startupVideoHeight == null) {
            return null;
        }
        return new StartupVideoBounds(startupVideoX, startupVideoY, startupVideoWidth, startupVideoHeight);
    }

    public synchronized void setStartupVideoBounds(double x, double y, double width, double height) {
        startupVideoX = clamp01(x);
        startupVideoY = clamp01(y);
        startupVideoWidth = clamp01(width);
        startupVideoHeight = clamp01(height);
        save();
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    /** Migrate token from legacy ~/.seq_token into sequoia.json on first run. */
    public synchronized void migrateToken() {
        if (authToken != null) return;
        try {
            if (Files.exists(legacyTokenFile)) {
                String legacy = Files.readString(legacyTokenFile).trim();
                if (!legacy.isEmpty()) {
                    authToken = legacy;
                    save();
                    SeqClient.LOGGER.info("Migrated auth token from ~/.seq_token into sequoia.json");
                }
                Files.deleteIfExists(legacyTokenFile);
            }
        } catch (Exception e) {
            SeqClient.LOGGER.warn("Failed to migrate legacy token", e);
        }
    }

    // ── Save / Load ──

    public synchronized void save() {
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
            if (discordUsername != null) {
                root.addProperty(DISCORD_USERNAME_KEY, discordUsername);
            }
            if (!ignoredBridgeUsers.isEmpty()) {
                JsonArray ignoredUsers = new JsonArray();
                for (String username : ignoredBridgeUsers) {
                    ignoredUsers.add(username);
                }
                root.add(IGNORED_BRIDGE_USERS_KEY, ignoredUsers);
            }
            root.addProperty(BOMB_SHARE_PROMPT_SEEN_KEY, bombSharePromptSeen);
            if (startupVideoX != null
                    && startupVideoY != null
                    && startupVideoWidth != null
                    && startupVideoHeight != null) {
                root.addProperty(STARTUP_VIDEO_X_KEY, startupVideoX);
                root.addProperty(STARTUP_VIDEO_Y_KEY, startupVideoY);
                root.addProperty(STARTUP_VIDEO_WIDTH_KEY, startupVideoWidth);
                root.addProperty(STARTUP_VIDEO_HEIGHT_KEY, startupVideoHeight);
            }
            for (Setting<?> setting : settings) {
                String key = setting.getCategory() + "." + setting.getName();
                root.add(key, setting.serialize());
            }
            writeConfigAtomically(root);
        } catch (IOException e) {
            SeqClient.LOGGER.error("Failed to save config", e);
        }
    }

    private void writeConfigAtomically(JsonObject root) throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempPath = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(tempPath, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
        try {
            Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(tempPath, configPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized void load() {
        if (!Files.exists(configPath)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
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
            if (root != null && root.has(DISCORD_USERNAME_KEY) && root.get(DISCORD_USERNAME_KEY).isJsonPrimitive()) {
                discordUsername = root.get(DISCORD_USERNAME_KEY).getAsString();
            }
            ignoredBridgeUsers.clear();
            if (root != null && root.has(IGNORED_BRIDGE_USERS_KEY) && root.get(IGNORED_BRIDGE_USERS_KEY).isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray(IGNORED_BRIDGE_USERS_KEY)) {
                    if (element == null || !element.isJsonPrimitive()) {
                        continue;
                    }
                    String normalized = normalizeBridgeUsername(element.getAsString());
                    if (normalized != null) {
                        ignoredBridgeUsers.add(normalized);
                    }
                }
            }
            if (root != null
                    && root.has(BOMB_SHARE_PROMPT_SEEN_KEY)
                    && root.get(BOMB_SHARE_PROMPT_SEEN_KEY).isJsonPrimitive()) {
                try {
                    bombSharePromptSeen = root.get(BOMB_SHARE_PROMPT_SEEN_KEY).getAsBoolean();
                } catch (Exception ignored) {
                    bombSharePromptSeen = false;
                }
            }
            if (root != null) {
                startupVideoX = readDouble(root, STARTUP_VIDEO_X_KEY);
                startupVideoY = readDouble(root, STARTUP_VIDEO_Y_KEY);
                startupVideoWidth = readDouble(root, STARTUP_VIDEO_WIDTH_KEY);
                startupVideoHeight = readDouble(root, STARTUP_VIDEO_HEIGHT_KEY);
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

    private static Double readDouble(JsonObject root, String key) {
        if (!root.has(key) || !root.get(key).isJsonPrimitive()) {
            return null;
        }
        try {
            return clamp01(root.get(key).getAsDouble());
        } catch (Exception ignored) {
            return null;
        }
    }

    public record StartupVideoBounds(double x, double y, double width, double height) {}
}
