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
        if (mc.player != null && mc.player.getUUID().toString().equals(uuid)) {
            String name = mc.player.getName().getString();
            cache.put(uuid, name);
            return name;
        }

        // 2. Check cache
        String cached = cache.get(uuid);
        if (cached != null)
            return cached;

        // 3. Check tab list
        if (mc.getConnection() != null) {
            try {
                UUID uid = UUID.fromString(formatUUID(uuid));
                PlayerInfo info = mc.getConnection().getPlayerInfo(uid);
                if (info != null && info.getProfile().name() != null) {
                    String name = info.getProfile().name();
                    cache.put(uuid, name);
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
                            cache.put(uuid, name);
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
            cache.put(uuid, username);
        }
    }

    public static void clear() {
        cache.clear();
    }
}
