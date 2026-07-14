package com.seqwawa.seq.config;

import com.google.gson.*;
import lombok.Getter;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.network.auth.StoredAuthSession;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private static final String TRACKED_WORLD_EVENTS_KEY = "_tracked_world_events";
    private static final Pattern MINECRAFT_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private final Path legacyTokenFile;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final Set<String> ignoredBridgeUsers = new LinkedHashSet<>();
    private final Set<String> trackedWorldEventIds = new LinkedHashSet<>();
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

    ConfigManager(Path configPath, Path legacyTokenFile, boolean installShutdownHook) {
        this.configPath = configPath;
        this.legacyTokenFile = legacyTokenFile;
        if (installShutdownHook) {
            Runtime.getRuntime().addShutdownHook(new Thread(this::save));
        }
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

    public String getDiscordUsername() {
        return discordUsername;
    }

    public boolean isBombSharePromptSeen() {
        return bombSharePromptSeen;
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
    }

    public void clearToken() {
        clearAuthSession();
    }

    public void clearAuthSession() {
        this.authToken = null;
        this.authTokenExpiresAt = null;
        this.minecraftUuid = null;
        this.minecraftUsername = null;
    }

    public void setDiscordUsername(String discordUsername) {
        this.discordUsername = discordUsername;
        save();
    }

    public void clearDiscordUsername() {
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

    public boolean addIgnoredBridgeUser(String username) {
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

    public boolean removeIgnoredBridgeUser(String username) {
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

    public boolean isIgnoredBridgeUser(String username) {
        String normalized = normalizeBridgeUsername(username);
        return normalized != null && ignoredBridgeUsers.contains(normalized);
    }

    public List<String> ignoredBridgeUsers() {
        return List.copyOf(ignoredBridgeUsers);
    }

    public Set<String> trackedWorldEventIds() {
        return Set.copyOf(trackedWorldEventIds);
    }

    public boolean setWorldEventTracked(String internalName, boolean tracked) {
        String normalized = normalizeWorldEventId(internalName);
        if (normalized == null) {
            return false;
        }
        boolean changed = tracked ? trackedWorldEventIds.add(normalized) : trackedWorldEventIds.remove(normalized);
        if (changed) {
            save();
        }
        return changed;
    }

    public void setBombSharePromptSeen(boolean bombSharePromptSeen) {
        this.bombSharePromptSeen = bombSharePromptSeen;
        save();
    }

    public StartupVideoBounds getStartupVideoBounds() {
        if (startupVideoX == null || startupVideoY == null || startupVideoWidth == null || startupVideoHeight == null) {
            return null;
        }
        return new StartupVideoBounds(startupVideoX, startupVideoY, startupVideoWidth, startupVideoHeight);
    }

    public void setStartupVideoBounds(double x, double y, double width, double height) {
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

    /** Retire the legacy persistent token file. Backend sessions are kept in memory only. */
    public void migrateToken() {
        try {
            if (Files.exists(legacyTokenFile)) {
                Files.deleteIfExists(legacyTokenFile);
                SeqClient.LOGGER.info("Deleted legacy ~/.seq_token auth token file");
            }
        } catch (Exception e) {
            SeqClient.LOGGER.warn("Failed to delete legacy token", e);
        }
    }

    // ── Save / Load ──

    public void save() {
        try {
            JsonObject root = new JsonObject();
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
            if (!trackedWorldEventIds.isEmpty()) {
                JsonArray trackedEvents = new JsonArray();
                for (String internalName : trackedWorldEventIds) {
                    trackedEvents.add(internalName);
                }
                root.add(TRACKED_WORLD_EVENTS_KEY, trackedEvents);
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
            writeConfig(root);
        } catch (IOException e) {
            SeqClient.LOGGER.error("Failed to save config", e);
        }
    }

    public void load() {
        if (!Files.exists(configPath)) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(configPath.toFile()), StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root != null && removePersistedAuthSession(root)) {
                writeConfig(root);
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
            trackedWorldEventIds.clear();
            if (root != null && root.has(TRACKED_WORLD_EVENTS_KEY) && root.get(TRACKED_WORLD_EVENTS_KEY).isJsonArray()) {
                for (JsonElement element : root.getAsJsonArray(TRACKED_WORLD_EVENTS_KEY)) {
                    if (element == null
                            || !element.isJsonPrimitive()
                            || !element.getAsJsonPrimitive().isString()) {
                        continue;
                    }
                    String normalized = normalizeWorldEventId(element.getAsString());
                    if (normalized != null) {
                        trackedWorldEventIds.add(normalized);
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

    private void writeConfig(JsonObject root) throws IOException {
        Path parent = configPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(configPath.toFile()), StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    private static String normalizeWorldEventId(String internalName) {
        if (internalName == null) {
            return null;
        }
        String normalized = internalName.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static boolean removePersistedAuthSession(JsonObject root) {
        boolean removed = root.remove(TOKEN_KEY) != null;
        removed |= root.remove(TOKEN_EXPIRES_AT_KEY) != null;
        removed |= root.remove(MINECRAFT_UUID_KEY) != null;
        removed |= root.remove(MINECRAFT_USERNAME_KEY) != null;
        return removed;
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
