package com.seqwawa.seq.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.LeaderboardBadgeAssignment;
import com.seqwawa.seq.model.LeaderboardBadgeResponse;
import com.seqwawa.seq.model.SeqBadge;
import com.seqwawa.seq.model.SeqBadgeEvent;
import com.seqwawa.seq.model.SeqBadgeTier;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.auth.StoredAuthSession;
import com.seqwawa.seq.utils.PlayerNameCache;

public final class LeaderboardBadgeService {
    private static final long REFRESH_INTERVAL_MS = 5 * 60 * 1000L;
    private static final Pattern USERNAME_TOKEN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Path CACHE_PATH = FabricLoader.getInstance()
            .getGameDir()
            .resolve("config")
            .resolve("sequoia")
            .resolve("cache")
            .resolve("leaderboard-badges.json");

    private static LeaderboardBadgeService instance;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private volatile Map<String, Map<SeqBadgeEvent, SeqBadgeTier>> cachedBadges = Map.of();
    private final Map<String, Map<SeqBadgeEvent, SeqBadgeTier>> mockBadges = new ConcurrentHashMap<>();
    private final Map<String, Map<SeqBadgeEvent, SeqBadgeTier>> mockBadgesByName = new ConcurrentHashMap<>();
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

    public List<SeqBadge> badgesFor(UUID uuid, String... nameCandidates) {
        EnumMap<SeqBadgeEvent, SeqBadgeTier> merged = new EnumMap<>(SeqBadgeEvent.class);
        String uuidKey = uuid == null ? null : PlayerNameCache.formatUUID(uuid.toString());
        if (uuidKey != null) {
            mergeBadges(merged, cachedBadges.get(uuidKey));
            mergeBadges(merged, mockBadges.get(uuidKey));
        }

        if (nameCandidates != null) {
            for (String nameCandidate : nameCandidates) {
                mergeBadges(merged, mockBadgesForNameCandidate(nameCandidate));
            }
        }

        return merged.entrySet().stream()
                .map(entry -> new SeqBadge(entry.getKey(), entry.getValue()))
                .toList();
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

        return ApiClient.getInstance().getLeaderboardBadges().thenApply(response -> {
            Map<String, Map<SeqBadgeEvent, SeqBadgeTier>> parsed = parseAssignments(response);
            cachedBadges = parsed;
            writeCache(toResponse(parsed));
            lastSuccessfulRefresh = Instant.now();
            int badgeCount = badgeCount(parsed);
            status = "loaded " + badgeCount + " backend badges for " + parsed.size() + " players";
            return "Leaderboard badges refreshed: " + badgeCount + " badges for " + parsed.size() + " players.";
        }).exceptionally(throwable -> {
            status = "refresh failed";
            SeqClient.LOGGER.debug("[LeaderboardBadges] Failed to refresh badge assignments: {}", rootMessage(throwable));
            return "Leaderboard badge refresh failed; using cached badges. Cause: " + rootMessage(throwable);
        }).whenComplete((ignored, throwable) -> refreshInFlight = false);
    }

    public CompletableFuture<String> setMockBadge(
            String username, SeqBadgeEvent event, SeqBadgeTier tier) {
        if (username == null || username.isBlank() || event == null || tier == null) {
            return CompletableFuture.completedFuture(
                    "Usage: /seq badges mock set <username> <event> <tier>");
        }
        String trimmed = username.trim();
        String normalizedName = normalizeUsername(trimmed);
        if (normalizedName == null) {
            return CompletableFuture.completedFuture("Enter a valid Minecraft username.");
        }
        putMock(mockBadgesByName, normalizedName, event, tier);
        return PlayerNameCache.resolveUUID(trimmed).thenApply(uuid -> {
            String formatted = PlayerNameCache.formatUUID(uuid);
            if (formatted == null) {
                return "Mock badge set by username: "
                        + trimmed
                        + " -> "
                        + badgeName(event, tier)
                        + ". UUID was not resolved.";
            }
            putMock(mockBadges, formatted, event, tier);
            PlayerNameCache.put(formatted, trimmed);
            return "Mock badge set: " + trimmed + " -> " + badgeName(event, tier) + ".";
        });
    }

    public String setMockBadgeForCurrentPlayer(SeqBadgeEvent event, SeqBadgeTier tier) {
        if (event == null || tier == null) {
            return "Usage: /seq badges mock me <event> <tier>";
        }

        int uuidCount = 0;
        int nameCount = 0;

        StoredAuthSession session = SeqClient.getConfigManager().getStoredAuthSession();
        if (session != null) {
            if (putMockUuid(session.minecraftUuid(), event, tier)) {
                uuidCount++;
            }
            if (putMockName(session.minecraftUsername(), event, tier)) {
                nameCount++;
            }
        }

        if (SeqClient.mc != null && SeqClient.mc.getUser() != null) {
            if (putMockUuid(SeqClient.mc.getUser().getProfileId(), event, tier)) {
                uuidCount++;
            }
            if (putMockName(SeqClient.mc.getUser().getName(), event, tier)) {
                nameCount++;
            }
        }

        if (uuidCount == 0 && nameCount == 0) {
            return "No authenticated or launcher Minecraft identity is available.";
        }
        return "Mock badge set for current player: "
                + uuidCount
                + " UUID"
                + (uuidCount == 1 ? "" : "s")
                + ", "
                + nameCount
                + " name"
                + (nameCount == 1 ? "" : "s")
                + " -> "
                + badgeName(event, tier)
                + ".";
    }

    public String clearMockBadges() {
        int count = badgeCount(mockBadges);
        mockBadges.clear();
        int nameCount = badgeCount(mockBadgesByName);
        mockBadgesByName.clear();
        return "Cleared "
                + count
                + " UUID mock badge"
                + (count == 1 ? "" : "s")
                + " and "
                + nameCount
                + " username mock badge"
                + (nameCount == 1 ? "." : "s.");
    }

    public String status() {
        String refreshed = lastSuccessfulRefresh == null ? "never" : lastSuccessfulRefresh.toString();
        return "Leaderboard badges: backend="
                + badgeCount(cachedBadges)
                + " badges for "
                + cachedBadges.size()
                + " players"
                + " | mocks="
                + (badgeCount(mockBadges) + badgeCount(mockBadgesByName))
                + " across "
                + (mockBadges.size() + mockBadgesByName.size())
                + " identities"
                + " | status="
                + status
                + " | last refresh="
                + refreshed;
    }

    public static List<String> eventSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (SeqBadgeEvent event : SeqBadgeEvent.values()) {
            suggestions.add(event.commandName());
        }
        return suggestions;
    }

    public static List<String> tierSuggestions() {
        List<String> suggestions = new ArrayList<>();
        for (SeqBadgeTier tier : SeqBadgeTier.values()) {
            suggestions.add(tier.commandName());
        }
        return suggestions;
    }

    public static List<String> eventOrTierSuggestions() {
        List<String> suggestions = eventSuggestions();
        suggestions.addAll(tierSuggestions());
        return suggestions;
    }

    private static String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = stripFormatting(username).trim();
        if (!trimmed.matches("[A-Za-z0-9_]{3,16}")) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private boolean putMockUuid(String uuid, SeqBadgeEvent event, SeqBadgeTier tier) {
        String formatted = PlayerNameCache.formatUUID(uuid);
        if (formatted == null || event == null || tier == null) {
            return false;
        }
        putMock(mockBadges, formatted, event, tier);
        return true;
    }

    private boolean putMockUuid(UUID uuid, SeqBadgeEvent event, SeqBadgeTier tier) {
        return uuid != null && putMockUuid(uuid.toString(), event, tier);
    }

    private boolean putMockName(String username, SeqBadgeEvent event, SeqBadgeTier tier) {
        String normalized = normalizeUsername(username);
        if (normalized == null || event == null || tier == null) {
            return false;
        }
        putMock(mockBadgesByName, normalized, event, tier);
        return true;
    }

    private Map<SeqBadgeEvent, SeqBadgeTier> mockBadgesForNameCandidate(String nameCandidate) {
        if (nameCandidate == null || nameCandidate.isBlank()) {
            return Map.of();
        }
        String cleaned = stripFormatting(nameCandidate);
        String exact = normalizeUsername(cleaned);
        if (exact != null) {
            Map<SeqBadgeEvent, SeqBadgeTier> badges = mockBadgesByName.get(exact);
            if (badges != null) {
                return badges;
            }
        }

        Matcher matcher = USERNAME_TOKEN.matcher(cleaned);
        while (matcher.find()) {
            Map<SeqBadgeEvent, SeqBadgeTier> badges =
                    mockBadgesByName.get(matcher.group().toLowerCase(Locale.ROOT));
            if (badges != null) {
                return badges;
            }
        }
        return Map.of();
    }

    private static String stripFormatting(String value) {
        return value.replaceAll("(?i)§[0-9A-FK-OR]", "");
    }

    private void loadCache() {
        cacheLoaded = true;
        if (!Files.isRegularFile(CACHE_PATH)) {
            status = "no cache";
            return;
        }
        try {
            LeaderboardBadgeResponse response = gson.fromJson(Files.readString(CACHE_PATH), LeaderboardBadgeResponse.class);
            cachedBadges = parseAssignments(response);
            status = "loaded " + badgeCount(cachedBadges) + " cached badges";
        } catch (IOException | RuntimeException exception) {
            status = "cache load failed";
            SeqClient.LOGGER.warn("[LeaderboardBadges] Failed to load cached badges.", exception);
        }
    }

    private Map<String, Map<SeqBadgeEvent, SeqBadgeTier>> parseAssignments(LeaderboardBadgeResponse response) {
        Map<String, EnumMap<SeqBadgeEvent, SeqBadgeTier>> parsed = new HashMap<>();
        if (response == null || response.badges() == null) {
            return Map.of();
        }
        for (LeaderboardBadgeAssignment assignment : response.badges()) {
            if (assignment == null) {
                continue;
            }
            String uuid = PlayerNameCache.formatUUID(assignment.playerUuid());
            SeqBadge badge = parseAssignment(assignment);
            if (uuid == null || badge == null) {
                continue;
            }
            parsed.computeIfAbsent(uuid, ignored -> new EnumMap<>(SeqBadgeEvent.class))
                    .merge(badge.event(), badge.tier(), SeqBadgeTier::highest);
        }

        Map<String, Map<SeqBadgeEvent, SeqBadgeTier>> immutable = new HashMap<>();
        parsed.forEach((uuid, badges) -> immutable.put(uuid, Map.copyOf(badges)));
        return Map.copyOf(immutable);
    }

    private static SeqBadge parseAssignment(LeaderboardBadgeAssignment assignment) {
        SeqBadgeEvent event = SeqBadgeEvent.parse(assignment.event());
        SeqBadgeTier tier = SeqBadgeTier.parse(assignment.tier());
        if (event != null && tier != null) {
            return new SeqBadge(event, tier);
        }
        return SeqBadge.parseLegacy(assignment.legacyBadge());
    }

    private LeaderboardBadgeResponse toResponse(Map<String, Map<SeqBadgeEvent, SeqBadgeTier>> badges) {
        List<LeaderboardBadgeAssignment> assignments = badges.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .flatMap(player -> player.getValue().entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(badge -> new LeaderboardBadgeAssignment(
                                player.getKey(), new SeqBadge(badge.getKey(), badge.getValue()))))
                .toList();
        return new LeaderboardBadgeResponse(assignments);
    }

    private void writeCache(LeaderboardBadgeResponse response) {
        try {
            Files.createDirectories(CACHE_PATH.getParent());
            Path temp = CACHE_PATH.resolveSibling(CACHE_PATH.getFileName() + ".tmp");
            Files.writeString(temp, gson.toJson(response));
            try {
                Files.move(temp, CACHE_PATH, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temp, CACHE_PATH, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            SeqClient.LOGGER.warn("[LeaderboardBadges] Failed to write badge cache.", exception);
        }
    }

    private static void putMock(
            Map<String, Map<SeqBadgeEvent, SeqBadgeTier>> target,
            String key,
            SeqBadgeEvent event,
            SeqBadgeTier tier) {
        target.compute(key, (ignored, existing) -> {
            EnumMap<SeqBadgeEvent, SeqBadgeTier> updated = new EnumMap<>(SeqBadgeEvent.class);
            if (existing != null) {
                updated.putAll(existing);
            }
            updated.put(event, tier);
            return Map.copyOf(updated);
        });
    }

    private static void mergeBadges(
            EnumMap<SeqBadgeEvent, SeqBadgeTier> target,
            Map<SeqBadgeEvent, SeqBadgeTier> source) {
        if (source != null) {
            target.putAll(source);
        }
    }

    private static int badgeCount(Map<String, Map<SeqBadgeEvent, SeqBadgeTier>> badges) {
        return badges.values().stream().mapToInt(Map::size).sum();
    }

    private static String badgeName(SeqBadgeEvent event, SeqBadgeTier tier) {
        return event.commandName() + ":" + tier.commandName();
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
