package com.seqwawa.seq.managers;

import com.seqwawa.seq.utils.MinecraftUsername;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NicknameResolverCache {
    private static final Duration ENTRY_TTL = Duration.ofMinutes(10);
    private static final Map<String, Entry> usernameByNickname = new ConcurrentHashMap<>();

    private NicknameResolverCache() {}

    public static void remember(String nickname, String username) {
        String nicknameKey = normalizeNickname(nickname);
        String safeUsername = normalizeUsername(username);
        if (nicknameKey == null || safeUsername == null) {
            return;
        }

        usernameByNickname.put(nicknameKey, new Entry(safeUsername, Instant.now()));
    }

    public static String resolveUsername(String nickname) {
        String nicknameKey = normalizeNickname(nickname);
        if (nicknameKey == null) {
            return null;
        }

        Entry entry = usernameByNickname.get(nicknameKey);
        if (entry != null) {
            return liveUsername(nicknameKey, entry);
        }

        String matchedUsername = null;
        for (Map.Entry<String, Entry> cached : usernameByNickname.entrySet()) {
            String cachedNickname = cached.getKey();
            if (!cachedNickname.startsWith(nicknameKey)) {
                continue;
            }

            String username = liveUsername(cachedNickname, cached.getValue());
            if (username == null) {
                continue;
            }

            if (matchedUsername != null && !matchedUsername.equalsIgnoreCase(username)) {
                return null;
            }

            matchedUsername = username;
        }

        if (matchedUsername != null) {
            usernameByNickname.put(nicknameKey, new Entry(matchedUsername, Instant.now()));
        }
        return matchedUsername;
    }

    private static String liveUsername(String nicknameKey, Entry entry) {
        if (Duration.between(entry.lastSeen(), Instant.now()).compareTo(ENTRY_TTL) > 0) {
            usernameByNickname.remove(nicknameKey, entry);
            return null;
        }

        return entry.username();
    }

    private static String normalizeNickname(String nickname) {
        if (nickname == null) {
            return null;
        }
        String normalized = nickname.trim().replaceAll("\\s+", " ");
        return normalized.isBlank() ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeUsername(String username) {
        return MinecraftUsername.normalize(username);
    }

    private record Entry(String username, Instant lastSeen) {}
}
