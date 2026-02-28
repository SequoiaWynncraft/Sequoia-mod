package org.sequoia.seq.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves player UUIDs to usernames.
 * Uses the Minecraft tab list (PlayerInfo) when available,
 * with a persistent cache for players seen previously.
 */
public class PlayerNameCache {

    private static final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * Resolve a UUID string to a username.
     * Returns the UUID itself (truncated) as a fallback if not resolvable.
     */
    public static String resolve(String uuid) {
        if (uuid == null) return "Unknown";

        // Check local player
        var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.getUUID().toString().equals(uuid)) {
            String name = mc.player.getName().getString();
            cache.put(uuid, name);
            return name;
        }

        // Check cache
        String cached = cache.get(uuid);
        if (cached != null) return cached;

        // Check tab list
        if (mc.getConnection() != null) {
            try {
                UUID uid = UUID.fromString(uuid);
                PlayerInfo info = mc.getConnection().getPlayerInfo(uid);
                if (info != null && info.getProfile().name() != null) {
                    String name = info.getProfile().name();
                    cache.put(uuid, name);
                    return name;
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // Fallback: truncated UUID
        return uuid.length() > 8 ? uuid.substring(0, 8) + "..." : uuid;
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
