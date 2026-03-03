package org.sequoia.seq.utils;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
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
    private static final Set<String> pending = ConcurrentHashMap.newKeySet();
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
        if (uuid == null)
            return "Unknown";

        // 1. Check local player
        var mc = Minecraft.getInstance();
        var localPlayer = mc.player;
        if (localPlayer != null && localPlayer.getUUID().toString().equals(uuid)) {
            String name = localPlayer.getName() != null
                    ? localPlayer.getName().getString()
                    : "Unknown";
            cache.put(uuid, name);
            usernameToUuid.put(name.toLowerCase(), uuid);
            return name;
        }

        // 2. Check cache
        String cached = cache.get(uuid);
        if (cached != null)
            return cached;

        // 3. Check tab list
        var connection = mc.getConnection();
        if (connection != null) {
            try {
                String formattedUuid = formatUUID(uuid);
                if (formattedUuid == null) {
                    return "Loading...";
                }
                UUID uid = UUID.fromString(formattedUuid);
                PlayerInfo info = connection.getPlayerInfo(uid);
                if (info != null && info.getProfile().name() != null) {
                    String name = info.getProfile().name();
                    cache.put(uuid, name);
                    usernameToUuid.put(name.toLowerCase(), formatUUID(uuid));
                    return name;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        // 4. Start async Mojang API lookup if not already pending
        if (pending.add(uuid)) {
            CompletableFuture.runAsync(() -> {
                try {
                    String cleanUUID = uuid.replace("-", "");
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(
                                    URI.create(
                                            "https://sessionserver.mojang.com/session/minecraft/profile/" +
                                                    cleanUUID))
                            .timeout(Duration.ofSeconds(10))
                            .GET()
                            .build();
                    HttpResponse<String> resp = httpClient.send(
                            req,
                            HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        JsonObject json = GSON.fromJson(
                                resp.body(),
                                JsonObject.class);
                        if (json.has("name")) {
                            String name = json.get("name").getAsString();
                            String formatted = formatUUID(uuid);
                            cache.put(formatted, name);
                            usernameToUuid.put(name.toLowerCase(), formatted);
                        }
                    }
                } catch (Exception e) {
                    // Lookup failed — will retry next time resolve() is called
                } finally {
                    pending.remove(uuid);
                }
            });
        }

        // 5. Fallback while async resolution is in progress
        return "Loading...";
    }

    /**
     * Ensures a UUID string has dashes (standard Minecraft format).
     * If the input already contains dashes, it is returned as-is.
     */
    public static String formatUUID(String uuid) {
        if (uuid == null)
            return null;
        if (uuid.contains("-"))
            return uuid;
        return uuid.replaceFirst(
                "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})",
                "$1-$2-$3-$4-$5");
    }

    /** Manually cache a UUID→username mapping. */
    public static void put(String uuid, String username) {
        if (uuid != null && username != null) {
            String formatted = formatUUID(uuid);
            cache.put(formatted, username);
            usernameToUuid.put(username.toLowerCase(), formatted);
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
        String key = normalized.toLowerCase();

        String cachedUuid = usernameToUuid.get(key);
        if (cachedUuid != null && !cachedUuid.isBlank()) {
            return CompletableFuture.completedFuture(formatUUID(cachedUuid));
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
                if (name.equalsIgnoreCase(normalized)) {
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

    public static void clear() {
        cache.clear();
        usernameToUuid.clear();
    }
}
