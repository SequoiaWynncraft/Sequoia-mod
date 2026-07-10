package com.seqwawa.seq.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.RankProfilesResponse;
import com.seqwawa.seq.model.SeqBadge;
import com.seqwawa.seq.model.SeqBadgeType;
import com.seqwawa.seq.model.SeqBadgeTier;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.utils.PlayerNameCache;

public final class LeaderboardBadgeService {
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;

    private static LeaderboardBadgeService instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private volatile Map<String, Map<SeqBadgeType, SeqBadgeTier>> cachedBadges = Map.of();
    private volatile boolean cacheLoaded;
    private volatile boolean refreshInFlight;
    private volatile long lastRefreshAttemptMs;
    private volatile Instant lastSuccessfulRefresh;
    private volatile String status = "not loaded";

    private LeaderboardBadgeService() {
        loadCache();
    }

    public static synchronized LeaderboardBadgeService getInstance() {
        if (instance == null) {
            instance = new LeaderboardBadgeService();
        }
        return instance;
    }

    public List<SeqBadge> badgesFor(UUID uuid) {
        EnumMap<SeqBadgeType, SeqBadgeTier> merged = new EnumMap<>(SeqBadgeType.class);
        String uuidKey = uuid == null ? null : PlayerNameCache.formatUUID(uuid.toString());
        if (uuidKey != null) {
            mergeBadges(merged, cachedBadges.get(uuidKey));
        }

        return SeqBadge.sortForRender(merged.entrySet().stream()
                .map(entry -> new SeqBadge(entry.getKey(), entry.getValue()))
                .toList());
    }

    public void tick() {
        long now = System.currentTimeMillis();
        if (!cacheLoaded || now - lastRefreshAttemptMs >= REFRESH_INTERVAL_MS) {
            refreshAsync();
        }
    }

    public CompletableFuture<String> refreshAsync() {
        if (refreshInFlight) {
            return CompletableFuture.completedFuture("Leaderboard badge refresh already running.");
        }
        refreshInFlight = true;
        lastRefreshAttemptMs = System.currentTimeMillis();
        status = "refreshing";

        return ApiClient.getInstance().getRecognizedRankProfiles().thenApply(response -> {
            Map<String, Map<SeqBadgeType, SeqBadgeTier>> parsed = parseProfiles(response);
            cachedBadges = parsed;
            RankProfileBadgeAssetCache.refresh(response.catalog());
            writeCache(response);
            lastSuccessfulRefresh = Instant.now();
            int badgeCount = badgeCount(parsed);
            status = "loaded " + badgeCount + " backend badges for " + parsed.size() + " players";
            return "Leaderboard badges refreshed: " + badgeCount + " badges for " + parsed.size() + " players.";
        }).exceptionally(throwable -> {
            status = "refresh failed";
            SeqClient.LOGGER.debug("[LeaderboardBadges] Failed to refresh badge assignments: {}", rootMessage(throwable));
            return "Leaderboard badge refresh failed; using cached badges. Cause: " + rootMessage(throwable);
        }).whenComplete((ignored, throwable) -> {
            refreshInFlight = false;
        });
    }

    public String status() {
        String refreshed = lastSuccessfulRefresh == null ? "never" : lastSuccessfulRefresh.toString();
        return "Leaderboard badges: backend="
                + badgeCount(cachedBadges)
                + " badges for "
                + cachedBadges.size()
                + " players"
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
            cachedBadges = parseProfiles(response);
            RankProfileBadgeAssetCache.refresh(response.catalog());
            status = "loaded " + badgeCount(cachedBadges) + " cached badges";
        } catch (IOException | RuntimeException exception) {
            status = "cache load failed";
            SeqClient.LOGGER.warn("[LeaderboardBadges] Failed to load cached badges.", exception);
        }
    }

    static Map<String, Map<SeqBadgeType, SeqBadgeTier>> parseProfiles(RankProfilesResponse response) {
        Map<String, EnumMap<SeqBadgeType, SeqBadgeTier>> parsed = new HashMap<>();
        if (response == null || response.schemaVersion() != 1 || response.catalog() == null) {
            throw new IllegalArgumentException("Unsupported or incomplete rank-profile response");
        }
        if (response.profiles() == null) {
            return Map.of();
        }

        Map<String, SeqBadge> definitions = badgeDefinitions(response.catalog());
        for (RankProfilesResponse.Profile profile : response.profiles()) {
            if (profile == null || profile.minecraft() == null) {
                continue;
            }
            String uuid = PlayerNameCache.formatUUID(profile.minecraft().uuid());
            if (uuid == null) {
                continue;
            }
            EnumMap<SeqBadgeType, SeqBadgeTier> profileBadges = new EnumMap<>(SeqBadgeType.class);
            mergeDefinitionKeys(profileBadges, definitions, profile.roleKeys());
            mergeDefinitionKeys(profileBadges, definitions, profile.awardKeys());
            if (!profileBadges.isEmpty()) {
                parsed.put(uuid, profileBadges);
            }
        }

        Map<String, Map<SeqBadgeType, SeqBadgeTier>> immutable = new HashMap<>();
        parsed.forEach((uuid, badges) -> immutable.put(uuid, Map.copyOf(badges)));
        return Map.copyOf(immutable);
    }

    static Map<String, SeqBadge> badgeDefinitions(RankProfilesResponse.Catalog catalog) {
        Map<String, SeqBadge> definitions = new HashMap<>();
        HashSet<String> assets = new HashSet<>();
        if (catalog.assets() != null) {
            catalog.assets().stream()
                    .filter(asset -> asset != null && asset.key() != null)
                    .map(RankProfilesResponse.AssetDefinition::key)
                    .forEach(assets::add);
        }

        if (catalog.roles() != null) {
            for (RankProfilesResponse.RoleDefinition role : catalog.roles()) {
                if (role == null
                        || !"insignia".equalsIgnoreCase(role.category())
                        || !assets.contains(role.assetKey())) {
                    continue;
                }
                addDefinition(definitions, role.key(), SeqBadgeType.INSIGNIA, role.tier());
            }
        }
        if (catalog.awards() != null) {
            for (RankProfilesResponse.AwardDefinition award : catalog.awards()) {
                if (award == null
                        || !"raid_badge".equalsIgnoreCase(award.category())
                        || !assets.contains(award.assetKey())) {
                    continue;
                }
                SeqBadgeType type = SeqBadgeType.parse(award.series());
                if (type != SeqBadgeType.WTP && type != SeqBadgeType.NOL) {
                    continue;
                }
                addDefinition(definitions, award.key(), type, award.tier());
            }
        }
        return definitions;
    }

    private static void addDefinition(
            Map<String, SeqBadge> definitions, String key, SeqBadgeType type, String tierValue) {
        SeqBadgeTier tier = SeqBadgeTier.parse(tierValue);
        if (key != null && !key.isBlank() && type != null && tier != null) {
            definitions.put(key, new SeqBadge(type, tier));
        }
    }

    private static void mergeDefinitionKeys(
            EnumMap<SeqBadgeType, SeqBadgeTier> target,
            Map<String, SeqBadge> definitions,
            List<String> keys) {
        if (keys == null) {
            return;
        }
        for (String key : keys) {
            SeqBadge badge = definitions.get(key);
            if (badge != null) {
                target.merge(badge.type(), badge.tier(), SeqBadgeTier::highest);
            }
        }
    }

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
            SeqClient.LOGGER.warn("[LeaderboardBadges] Failed to write badge cache.", exception);
        }
    }

    private static Path cachePath() {
        return FabricLoader.getInstance()
                .getGameDir()
                .resolve("config")
                .resolve("sequoia")
                .resolve("cache")
                .resolve("rank-profile-badges.json");
    }

    private static void mergeBadges(
            EnumMap<SeqBadgeType, SeqBadgeTier> target,
            Map<SeqBadgeType, SeqBadgeTier> source) {
        if (source != null) {
            target.putAll(source);
        }
    }

    private static int badgeCount(Map<String, Map<SeqBadgeType, SeqBadgeTier>> badges) {
        return badges.values().stream().mapToInt(Map::size).sum();
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
}
