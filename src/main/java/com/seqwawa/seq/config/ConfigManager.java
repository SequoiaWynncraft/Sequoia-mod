package com.seqwawa.seq.config;

import com.google.gson.*;
import lombok.AccessLevel;
import lombok.Getter;
import com.seqwawa.seq.client.SeqClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Getter
public class ConfigManager {
    private static final Path DEFAULT_CONFIG_PATH = Path.of("config", "sequoia.json");
    private static final Path DEFAULT_LEGACY_TOKEN_FILE = Path.of(System.getProperty("user.home"), ".seq_token");
    private static final String TOKEN_KEY = "_auth_token";
    private static final String TOKEN_EXPIRES_AT_KEY = "_auth_token_expires_at";
    private static final String MINECRAFT_UUID_KEY = "_minecraft_uuid";
    private static final String MINECRAFT_USERNAME_KEY = "_minecraft_username";
    /** Before Discord names were kept per Minecraft account: one name, for whoever played. */
    private static final String LEGACY_DISCORD_USERNAME_KEY = "_discord_username";
    private static final String DISCORD_USERNAMES_KEY = "_discord_usernames";
    private static final String IGNORED_BRIDGE_USERS_KEY = "_ignored_bridge_users";
    private static final String BOMB_SHARE_PROMPT_SEEN_KEY = "_bomb_share_prompt_seen";
    private static final String STARTUP_VIDEO_X_KEY = "_startup_video_x";
    private static final String STARTUP_VIDEO_Y_KEY = "_startup_video_y";
    private static final String STARTUP_VIDEO_WIDTH_KEY = "_startup_video_width";
    private static final String STARTUP_VIDEO_HEIGHT_KEY = "_startup_video_height";
    private static final String TRACKED_WORLD_EVENTS_KEY = "_tracked_world_events";
    private static final String WAR_PLANNER_HIDDEN_ZONE_IDS_KEY = "_war_planner_hidden_zone_ids";
    private static final String WAR_PLANNER_HIDDEN_ZONE_CATEGORY_IDS_KEY =
            "_war_planner_hidden_zone_category_ids";
    private static final Pattern MINECRAFT_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path configPath;
    private final Path legacyTokenFile;
    private final List<Setting<?>> settings = new ArrayList<>();
    private final Map<Setting<?>, List<String>> legacySettingKeys = new IdentityHashMap<>();
    private final Set<String> ignoredBridgeUsers = new LinkedHashSet<>();
    private final Set<String> trackedWorldEventIds = new LinkedHashSet<>();
    private final Set<Long> hiddenWarPlannerZoneIds = new LinkedHashSet<>();
    private final Set<Long> hiddenWarPlannerZoneCategoryIds = new LinkedHashSet<>();
    /** The Discord name linked to each Minecraft account, by its UUID. Guarded by this. */
    @Getter(AccessLevel.NONE)
    private final Map<String, String> discordUsernames = new LinkedHashMap<>();
    /** A name an older version saved for no account in particular; the first account to ask takes it. */
    @Getter(AccessLevel.NONE)
    private String unclaimedDiscordUsername;
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

    /**
     * Registers a setting with old fully-qualified config keys to consult only when
     * its current key is absent. A later save writes only the current key.
     */
    public void registerWithLegacyKeys(Setting<?> setting, String... legacyKeys) {
        register(setting);
        legacySettingKeys.put(setting, List.of(legacyKeys));
    }

    /**
     * The Discord name linked to {@code account}, or null. An older version kept one name for
     * everyone; the first account to ask after the update takes it as its own.
     */
    public String getDiscordUsername(UUID account) {
        if (account == null) {
            return null;
        }
        boolean claimed;
        String name;
        synchronized (this) {
            String key = account.toString();
            claimed = unclaimedDiscordUsername != null && !discordUsernames.containsKey(key);
            if (claimed) {
                discordUsernames.put(key, unclaimedDiscordUsername);
                unclaimedDiscordUsername = null;
            }
            name = discordUsernames.get(key);
        }
        if (claimed) {
            save();
        }
        return name;
    }

    public boolean isBombSharePromptSeen() {
        return bombSharePromptSeen;
    }

    public void setDiscordUsername(UUID account, String discordUsername) {
        if (account == null) {
            return;
        }
        synchronized (this) {
            discordUsernames.put(account.toString(), discordUsername);
        }
        save();
    }

    /** Forgets the Discord name linked to {@code account}; other accounts keep theirs. */
    public void clearDiscordUsername(UUID account) {
        if (account == null) {
            return;
        }
        synchronized (this) {
            discordUsernames.remove(account.toString());
            // Whoever asks next would take an older version's name; clearing it is that account's call too.
            unclaimedDiscordUsername = null;
        }
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

    public Set<Long> hiddenWarPlannerZoneIds() {
        return Set.copyOf(hiddenWarPlannerZoneIds);
    }

    public Set<Long> hiddenWarPlannerZoneCategoryIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(hiddenWarPlannerZoneCategoryIds));
    }

    public void setWarPlannerZoneHidden(long zoneId, boolean hidden) {
        if (zoneId <= 0) {
            return;
        }
        boolean changed = hidden ? hiddenWarPlannerZoneIds.add(zoneId) : hiddenWarPlannerZoneIds.remove(zoneId);
        if (changed) {
            save();
        }
    }

    public void setWarPlannerZoneCategoryHidden(Long categoryId, boolean hidden) {
        if (categoryId != null && categoryId <= 0) {
            return;
        }
        boolean changed = hidden
                ? hiddenWarPlannerZoneCategoryIds.add(categoryId)
                : hiddenWarPlannerZoneCategoryIds.remove(categoryId);
        if (changed) {
            save();
        }
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
            synchronized (this) {
                if (!discordUsernames.isEmpty()) {
                    JsonObject names = new JsonObject();
                    discordUsernames.forEach(names::addProperty);
                    root.add(DISCORD_USERNAMES_KEY, names);
                }
                if (unclaimedDiscordUsername != null) {
                    root.addProperty(LEGACY_DISCORD_USERNAME_KEY, unclaimedDiscordUsername);
                }
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
            writeLongSet(root, WAR_PLANNER_HIDDEN_ZONE_IDS_KEY, hiddenWarPlannerZoneIds);
            writeLongSet(root, WAR_PLANNER_HIDDEN_ZONE_CATEGORY_IDS_KEY, hiddenWarPlannerZoneCategoryIds);
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
            readDiscordUsernames(root);
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
            readLongSet(root, WAR_PLANNER_HIDDEN_ZONE_IDS_KEY, hiddenWarPlannerZoneIds, false);
            readLongSet(
                    root,
                    WAR_PLANNER_HIDDEN_ZONE_CATEGORY_IDS_KEY,
                    hiddenWarPlannerZoneCategoryIds,
                    true);
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
                if (element == null) {
                    for (String legacyKey : legacySettingKeys.getOrDefault(setting, List.of())) {
                        element = root.get(legacyKey);
                        if (element != null) {
                            break;
                        }
                    }
                }
                if (element != null) {
                    try {
                        setting.deserialize(element);
                    } catch (RuntimeException e) {
                        // A hand-edited value, such as 1e10000 for a whole number, keeps the setting as it was.
                        SeqClient.LOGGER.warn("Ignoring the saved value of {}", key, e);
                    }
                }
            }
        } catch (IOException | JsonSyntaxException e) {
            SeqClient.LOGGER.error("Failed to load config", e);
        }
    }

    private synchronized void readDiscordUsernames(JsonObject root) {
        discordUsernames.clear();
        unclaimedDiscordUsername = null;
        if (root == null) {
            return;
        }
        if (root.has(DISCORD_USERNAMES_KEY) && root.get(DISCORD_USERNAMES_KEY).isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject(DISCORD_USERNAMES_KEY).entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    discordUsernames.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
        if (root.has(LEGACY_DISCORD_USERNAME_KEY) && root.get(LEGACY_DISCORD_USERNAME_KEY).isJsonPrimitive()) {
            unclaimedDiscordUsername = root.get(LEGACY_DISCORD_USERNAME_KEY).getAsString();
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

    private static void writeLongSet(JsonObject root, String key, Set<Long> values) {
        if (values.isEmpty()) {
            return;
        }
        JsonArray array = new JsonArray();
        for (Long value : values) {
            if (value == null) {
                array.add(JsonNull.INSTANCE);
            } else {
                array.add(value);
            }
        }
        root.add(key, array);
    }

    private static void readLongSet(
            JsonObject root, String key, Set<Long> destination, boolean allowNull) {
        destination.clear();
        if (root == null || !root.has(key) || !root.get(key).isJsonArray()) {
            return;
        }
        for (JsonElement element : root.getAsJsonArray(key)) {
            if (element == null || element.isJsonNull()) {
                if (allowNull) {
                    destination.add(null);
                }
                continue;
            }
            if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
                continue;
            }
            try {
                long value = element.getAsLong();
                if (value > 0) {
                    destination.add(value);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /** Older versions wrote the backend session here; it is kept in memory only now. */
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
