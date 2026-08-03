package com.seqwawa.seq.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.DiscordRank;
import com.seqwawa.seq.model.RankProfilesResponse;
import com.seqwawa.seq.model.SeqBadgeTier;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.utils.PlayerNameCache;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Keeps a local index of the Sequoia Discord progression rank of every member
 * who linked their Discord and Minecraft accounts.
 * <p>
 * Backed by {@code /v1/rank-profiles?scope=linked}. The index is keyed by every
 * identity the client may realistically observe: the Minecraft UUID and
 * username for in-game guild chat, and the Discord id, username and display
 * name for messages arriving over the guild bridge.
 */
public final class DiscordRankService {
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;

    private static DiscordRankService instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private volatile Index index = Index.empty();
    private volatile boolean cacheLoaded;
    private volatile boolean refreshInFlight;
    private volatile long lastRefreshAttemptMs;
    private volatile Instant lastSuccessfulRefresh;
    private volatile String status = "not loaded";

    private DiscordRankService() {
        loadCache();
    }

    public static synchronized DiscordRankService getInstance() {
        if (instance == null) {
            instance = new DiscordRankService();
        }
        return instance;
    }

    /** True when at least one rank is known, i.e. decoration can do something useful. */
    public boolean hasRanks() {
        return !index.byMinecraftUuid().isEmpty() || !index.byDiscordIdentity().isEmpty();
    }

    public DiscordRank rankForUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return index.byMinecraftUuid().get(PlayerNameCache.formatUUID(uuid.toString()));
    }

    /** Looks up a rank by Minecraft username (case-insensitive). */
    public DiscordRank rankForMinecraftUsername(String username) {
        return index.byMinecraftUsername().get(normalizeKey(username));
    }

    /**
     * Looks up a rank by any Discord-side identity: user id, username, or display
     * name. Sequoia display names follow a {@code "<Rank> <McName>"} convention, so
     * the trailing segment is also indexed to match bridge senders such as
     * {@code dix} coming from {@code Sapling dix}.
     */
    public DiscordRank rankForDiscordIdentity(String identity) {
        return index.byDiscordIdentity().get(normalizeKey(identity));
    }

    /** Resolves a bridge sender name against Discord identities, then Minecraft names. */
    public DiscordRank rankForBridgeSender(String senderName) {
        DiscordRank rank = rankForDiscordIdentity(senderName);
        return rank != null ? rank : rankForMinecraftUsername(senderName);
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (!cacheLoaded || now - lastRefreshAttemptMs >= REFRESH_INTERVAL_MS) {
            refreshAsync();
        }
    }

    public CompletableFuture<String> refreshAsync() {
        if (refreshInFlight) {
            return CompletableFuture.completedFuture("Discord rank refresh already running.");
        }
        refreshInFlight = true;
        lastRefreshAttemptMs = System.currentTimeMillis();
        status = "refreshing";

        return ApiClient.getInstance()
                .getLinkedRankProfiles()
                .thenApply(response -> {
                    Index parsed = parseProfiles(response);
                    index = parsed;
                    writeCache(response);
                    lastSuccessfulRefresh = Instant.now();
                    status = "loaded " + parsed.rankedProfiles() + " ranked profiles";
                    return "Discord ranks refreshed: " + parsed.rankedProfiles() + " ranked members.";
                })
                .exceptionally(throwable -> {
                    status = "refresh failed";
                    SeqClient.LOGGER.debug("[DiscordRanks] Failed to refresh linked profiles: {}", rootMessage(throwable));
                    return "Discord rank refresh failed; using cached ranks. Cause: " + rootMessage(throwable);
                })
                .whenComplete((ignored, throwable) -> refreshInFlight = false);
    }

    public String status() {
        String refreshed = lastSuccessfulRefresh == null ? "never" : lastSuccessfulRefresh.toString();
        return "Discord ranks: "
                + index.rankedProfiles()
                + " ranked members ("
                + index.byMinecraftUsername().size()
                + " game names, "
                + index.byDiscordIdentity().size()
                + " discord aliases)"
                + " | status="
                + status
                + " | last refresh="
                + refreshed;
    }

    private void loadCache() {
        cacheLoaded = true;
        Path cachePath = cachePath();
        if (!Files.isRegularFile(cachePath)) {
            status = "no cache";
            return;
        }
        try {
            RankProfilesResponse response = gson.fromJson(Files.readString(cachePath), RankProfilesResponse.class);
            index = parseProfiles(response);
            status = "loaded " + index.rankedProfiles() + " cached ranked profiles";
        } catch (IOException | RuntimeException exception) {
            status = "cache load failed";
            SeqClient.LOGGER.warn("[DiscordRanks] Failed to load cached linked profiles.", exception);
        }
    }

    // ── Parsing ──

    static Index parseProfiles(RankProfilesResponse response) {
        if (response == null || response.schemaVersion() != 1 || response.catalog() == null) {
            throw new IllegalArgumentException("Unsupported or incomplete rank-profile response");
        }
        if (response.profiles() == null) {
            return Index.empty();
        }

        Map<String, DiscordRank> ranksByKey = progressionRanks(response.catalog());
        Map<String, DiscordRank> byUuid = new HashMap<>();
        Map<String, DiscordRank> byMinecraftUsername = new HashMap<>();
        Map<String, DiscordRank> byDiscordIdentity = new HashMap<>();
        Map<String, SeqBadgeTier> insignia = new HashMap<>();
        Map<String, SeqBadgeTier> insigniaByDiscord = new HashMap<>();
        Map<String, SeqBadgeTier> insigniaTiers = insigniaTiers(response.catalog());
        int rankedProfiles = 0;

        for (RankProfilesResponse.Profile profile : response.profiles()) {
            recordInsignia(insignia, insigniaByDiscord, insigniaTiers, profile);

            DiscordRank rank = resolveRank(profile, ranksByKey);
            if (rank == null) {
                continue;
            }
            rankedProfiles++;

            RankProfilesResponse.MinecraftIdentity minecraft = profile.minecraft();
            if (minecraft != null) {
                String uuid = PlayerNameCache.formatUUID(minecraft.uuid());
                if (uuid != null) {
                    byUuid.put(uuid, rank);
                }
                putIdentity(byMinecraftUsername, minecraft.username(), rank);
            }

            RankProfilesResponse.DiscordIdentity discord = profile.discord();
            if (discord != null) {
                putIdentity(byDiscordIdentity, discord.id(), rank);
                putIdentity(byDiscordIdentity, discord.username(), rank);
                putIdentity(byDiscordIdentity, discord.displayName(), rank);
                putIdentity(byDiscordIdentity, stripRankPrefix(discord.displayName(), ranksByKey), rank);
            }
        }

        return new Index(
                Map.copyOf(byUuid),
                Map.copyOf(byMinecraftUsername),
                Map.copyOf(byDiscordIdentity),
                Map.copyOf(insignia),
                Map.copyOf(insigniaByDiscord),
                rankedProfiles);
    }

    /** Builds {@code roleKey -> rank} for every progression rank in the catalog. */
    static Map<String, DiscordRank> progressionRanks(RankProfilesResponse.Catalog catalog) {
        Map<String, DiscordRank> ranks = new HashMap<>();
        if (catalog.roles() == null) {
            return ranks;
        }
        for (RankProfilesResponse.RoleDefinition role : catalog.roles()) {
            if (role == null
                    || !DiscordRank.PROGRESSION_CATEGORY.equalsIgnoreCase(role.category())
                    || role.key() == null
                    || role.key().isBlank()
                    || role.label() == null
                    || role.label().isBlank()) {
                continue;
            }
            ranks.put(
                    role.key(),
                    new DiscordRank(role.key(), role.label().trim(), role.position(), primaryColor(role.colors())));
        }
        return ranks;
    }

    /** Insignia tier of a member, or {@code null} when they hold none. */
    public SeqBadgeTier insigniaForMinecraftUsername(String username) {
        return index.insigniaByMinecraftUsername().get(normalizeKey(username));
    }

    /** Insignia of a bridge sender, matched on Discord identity then Minecraft name. */
    public SeqBadgeTier insigniaForBridgeSender(String senderName) {
        SeqBadgeTier tier = index.insigniaByDiscordIdentity().get(normalizeKey(senderName));
        return tier != null ? tier : insigniaForMinecraftUsername(senderName);
    }

    /** Builds {@code roleKey -> tier} for the catalog's insignia roles. */
    static Map<String, SeqBadgeTier> insigniaTiers(RankProfilesResponse.Catalog catalog) {
        Map<String, SeqBadgeTier> tiers = new HashMap<>();
        if (catalog.roles() == null) {
            return tiers;
        }
        for (RankProfilesResponse.RoleDefinition role : catalog.roles()) {
            if (role == null || !"insignia".equalsIgnoreCase(role.category()) || role.key() == null) {
                continue;
            }
            SeqBadgeTier tier = SeqBadgeTier.parse(role.tier());
            if (tier != null) {
                tiers.put(role.key(), tier);
            }
        }
        return tiers;
    }

    /**
     * Records a member's insignia, preferring the backend-resolved summary and
     * falling back to the highest tier among their roles.
     */
    private static void recordInsignia(
            Map<String, SeqBadgeTier> byMinecraft,
            Map<String, SeqBadgeTier> byDiscord,
            Map<String, SeqBadgeTier> tiers,
            RankProfilesResponse.Profile profile) {
        if (profile == null) {
            return;
        }
        SeqBadgeTier tier = profile.summary() == null ? null : tiers.get(profile.summary().insignia());
        if (tier == null && profile.roleKeys() != null) {
            for (String roleKey : profile.roleKeys()) {
                SeqBadgeTier candidate = tiers.get(roleKey);
                if (candidate != null) {
                    tier = tier == null ? candidate : SeqBadgeTier.highest(tier, candidate);
                }
            }
        }
        if (tier == null) {
            return;
        }
        if (profile.minecraft() != null) {
            putIdentity(byMinecraft, profile.minecraft().username(), tier);
        }
        RankProfilesResponse.DiscordIdentity discord = profile.discord();
        if (discord != null) {
            putIdentity(byDiscord, discord.id(), tier);
            putIdentity(byDiscord, discord.username(), tier);
            putIdentity(byDiscord, discord.displayName(), tier);
            putIdentity(byDiscord, stripRankPrefix(discord.displayName(), Map.of()), tier);
        }
    }

    /** Parses the role's {@code #RRGGBB} colour, or {@code null} when it has none. */
    static Integer primaryColor(RankProfilesResponse.RoleColors colors) {
        String primary = colors == null ? null : colors.primary();
        if (primary == null) {
            return null;
        }
        String hex = primary.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (!hex.matches("(?i)[0-9a-f]{6}")) {
            return null;
        }
        return Integer.parseInt(hex, 16);
    }

    /**
     * Prefers the backend-resolved summary; falls back to the highest positioned
     * progression role in {@code role_keys} when the summary is absent.
     */
    private static DiscordRank resolveRank(RankProfilesResponse.Profile profile, Map<String, DiscordRank> ranksByKey) {
        if (profile == null) {
            return null;
        }

        if (profile.summary() != null && profile.summary().progressionRank() != null) {
            DiscordRank summarised = ranksByKey.get(profile.summary().progressionRank());
            if (summarised != null) {
                return summarised;
            }
        }

        List<String> roleKeys = profile.roleKeys();
        if (roleKeys == null) {
            return null;
        }
        DiscordRank best = null;
        for (String roleKey : roleKeys) {
            DiscordRank candidate = ranksByKey.get(roleKey);
            if (candidate != null && (best == null || candidate.position() > best.position())) {
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Sequoia members are nicknamed {@code "<Rank> <McName>"} on Discord. The bridge
     * forwards the bare name, so index that too.
     */
    static String stripRankPrefix(String displayName, Map<String, DiscordRank> ranksByKey) {
        if (displayName == null) {
            return null;
        }
        String trimmed = displayName.trim();
        for (DiscordRank rank : ranksByKey.values()) {
            String prefix = rank.label() + " ";
            if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
                String remainder = trimmed.substring(prefix.length()).trim();
                return remainder.isEmpty() ? null : remainder;
            }
        }
        return null;
    }

    private static <T> void putIdentity(Map<String, T> target, String identity, T value) {
        String key = normalizeKey(identity);
        if (key != null) {
            target.put(key, value);
        }
    }

    private static String normalizeKey(String identity) {
        if (identity == null) {
            return null;
        }
        String normalized = identity.trim().replaceAll("\\s+", " ");
        return normalized.isEmpty() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    // ── Cache ──

    private void writeCache(RankProfilesResponse response) {
        try {
            Path cachePath = cachePath();
            Files.createDirectories(cachePath.getParent());
            Path temp = cachePath.resolveSibling(cachePath.getFileName() + ".tmp");
            Files.writeString(temp, gson.toJson(response));
            try {
                Files.move(temp, cachePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, cachePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[DiscordRanks] Failed to write linked profile cache.", exception);
        }
    }

    private static Path cachePath() {
        return FabricLoader.getInstance()
                .getGameDir()
                .resolve("config")
                .resolve("sequoia")
                .resolve("cache")
                .resolve("rank-profile-linked.json");
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        String message = current == null ? null : current.getMessage();
        if (message == null || message.isBlank()) {
            return current == null ? "unknown" : current.getClass().getSimpleName();
        }
        return message.replace('\n', ' ').replace('\r', ' ').toLowerCase(Locale.ROOT);
    }

    record Index(
            Map<String, DiscordRank> byMinecraftUuid,
            Map<String, DiscordRank> byMinecraftUsername,
            Map<String, DiscordRank> byDiscordIdentity,
            Map<String, SeqBadgeTier> insigniaByMinecraftUsername,
            Map<String, SeqBadgeTier> insigniaByDiscordIdentity,
            int rankedProfiles) {

        static Index empty() {
            return new Index(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), 0);
        }
    }
}
