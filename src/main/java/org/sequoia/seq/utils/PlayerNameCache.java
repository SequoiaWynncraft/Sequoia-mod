package org.sequoia.seq.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Resolves player UUIDs to usernames.
 * Uses the Minecraft tab list (PlayerInfo) when available,
 * with a persistent cache for players seen previously.
 * Falls back to async Mojang sessionserver API lookup.
 */
public class PlayerNameCache {

    private static final Map<String, String> cache = new ConcurrentHashMap<>();
    private static final Map<String, String> usernameToUuid = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final Gson GSON = new Gson();

    /**
     * Resolve a UUID string to a username.
     * Returns "Loading..." as a fallback if not resolvable yet.
     * Starts an async Mojang API lookup if the name isn't cached or in the tab
     * list.
     */
    public static String resolve(String uuid) {
        String formattedUuid = formatUUID(uuid);
        if (formattedUuid == null)
            return "Unknown";

        String resolved = resolveImmediate(formattedUuid);
        if (resolved != null) {
            return resolved;
        }

        resolveAsync(formattedUuid);
        return "Loading...";
    }

    public static CompletableFuture<String> resolveAsync(String uuid) {
        String formattedUuid = formatUUID(uuid);
        if (formattedUuid == null) {
            return CompletableFuture.completedFuture("Unknown");
        }

        String resolved = resolveImmediate(formattedUuid);
        if (resolved != null) {
            return CompletableFuture.completedFuture(resolved);
        }

        return pending.computeIfAbsent(formattedUuid, ignored -> CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(
                                "https://sessionserver.mojang.com/session/minecraft/profile/"
                                        + formattedUuid.replace("-", "")))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
                    if (json != null && json.has("name")) {
                        String name = json.get("name").getAsString();
                        put(formattedUuid, name);
                        return name;
                    }
                }
            } catch (Exception ignoredException) {
            }

            return null;
        }).whenComplete((resolvedName, throwable) -> pending.remove(formattedUuid)));
    }

    private static String resolveImmediate(String formattedUuid) {
        var mc = Minecraft.getInstance();
        var localPlayer = mc.player;
        if (localPlayer != null && localPlayer.getUUID().toString().equals(formattedUuid)) {
            String name = localPlayer.getName() != null
                    ? localPlayer.getName().getString()
                    : "Unknown";
            put(formattedUuid, name);
            return name;
        }

        String cached = cache.get(formattedUuid);
        if (cached != null) {
            return cached;
        }

        var connection = mc.getConnection();
        if (connection != null) {
            try {
                UUID uid = UUID.fromString(formattedUuid);
                PlayerInfo info = connection.getPlayerInfo(uid);
                if (info != null && info.getProfile().name() != null) {
                    String name = info.getProfile().name();
                    put(formattedUuid, name);
                    return name;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        return null;
    }

    /**
     * Ensures a UUID string has dashes (standard Minecraft format).
     * If the input already contains dashes, it is returned as-is.
     */
    public static String formatUUID(String uuid) {
        if (uuid == null)
            return null;

        String trimmed = uuid.trim();
        if (trimmed.isBlank()) {
            return null;
        }

        if (trimmed.contains("-")) {
            try {
                return UUID.fromString(trimmed).toString();
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        if (!trimmed.matches("[0-9a-fA-F]{32}")) {
            return null;
        }

        return trimmed.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5");
    }

    /** Manually cache a UUID→username mapping. */
    public static void put(String uuid, String username) {
        if (uuid != null && username != null) {
            String formatted = formatUUID(uuid);
            if (formatted == null) {
                return;
            }
            cache.put(formatted, username);
            if (isCanonicalOnlinePlayerUuid(formatted)) {
                usernameToUuid.put(username.toLowerCase(Locale.ROOT), formatted);
            }
        }
    }

    /**
     * Resolve a username to UUID asynchronously.
     * Returns a dashed UUID string, or null when not found.
     */
    public static CompletableFuture<String> resolveUUID(String username) {
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        String normalized = username.trim();
        String key = normalized.toLowerCase(Locale.ROOT);

        String cachedUuid = usernameToUuid.get(key);
        if (cachedUuid != null && !cachedUuid.isBlank()) {
            String formattedCached = formatUUID(cachedUuid);
            if (isCanonicalOnlinePlayerUuid(formattedCached)) {
                return CompletableFuture.completedFuture(formattedCached);
            }
            usernameToUuid.remove(key);
        }

        var mc = Minecraft.getInstance();
        var localPlayer = mc.player;
        if (localPlayer != null) {
            String localName = localPlayer.getName() != null
                    ? localPlayer.getName().getString()
                    : null;
            if (localName != null && localName.equalsIgnoreCase(normalized)) {
                String localUuid = localPlayer.getUUID().toString();
                put(localUuid, localName);
                return CompletableFuture.completedFuture(localUuid);
            }
        }

        var connection = mc.getConnection();
        if (connection != null) {
            for (PlayerInfo info : connection.getOnlinePlayers()) {
                if (info == null || info.getProfile() == null) {
                    continue;
                }

                String name = info.getProfile().name();
                if (name != null && name.equalsIgnoreCase(normalized)) {
                    String resolved = info.getProfile().id() != null
                            ? info.getProfile().id().toString()
                            : null;
                    if (resolved != null) {
                        put(resolved, name);
                    }
                    return CompletableFuture.completedFuture(resolved);
                }
            }
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.mojang.com/users/profiles/minecraft/" + normalized))
                        .timeout(Duration.ofSeconds(10))
                        .GET()
                        .build();
                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() != 200 || resp.body() == null || resp.body().isBlank()) {
                    return null;
                }

                JsonObject json = GSON.fromJson(resp.body(), JsonObject.class);
                if (json == null || !json.has("id")) {
                    return null;
                }

                String resolved = formatUUID(json.get("id").getAsString());
                String resolvedName = json.has("name") && !json.get("name").isJsonNull()
                        ? json.get("name").getAsString()
                        : normalized;
                put(resolved, resolvedName);
                return resolved;
            } catch (Exception ignored) {
                return null;
            }
        });
    }

    private static boolean isCanonicalOnlinePlayerUuid(String uuid) {
        String formatted = formatUUID(uuid);
        if (formatted == null || formatted.isBlank()) {
            return false;
        }

        try {
            UUID parsed = UUID.fromString(formatted);
            return parsed.version() == 4 && parsed.variant() == 2;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    public static void clear() {
        cache.clear();
        usernameToUuid.clear();
    }
}
