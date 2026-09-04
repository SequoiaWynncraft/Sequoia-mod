package com.seqwawa.seq.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.model.GuildMemberPresence;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads the guild roster from Wynncraft's own public API.
 * <p>
 * The Sequoia backend only knows who has the mod connected, which is a fraction
 * of the guild. Wynncraft publishes every member with an {@code online} flag and
 * the world they are on, so the panel can list the whole guild rather than the
 * subset running Sequoia.
 * <p>
 * The guild endpoint is served with a two-minute cache and a bucket limit of 50
 * requests per minute, so {@link #MINIMUM_REFRESH_INTERVAL} is what the upstream
 * cache makes worth asking for; polling faster returns the same bytes.
 */
public final class WynncraftGuildClient {

    private static final String API_BASE = "https://api.wynncraft.com/v3";
    private static final String USER_AGENT = "Sequoia-mod/" + ClientVersion.resolveInstalledVersion();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    /** Matches the upstream {@code Cache-Control: max-age=120} on the guild endpoint. */
    public static final Duration MINIMUM_REFRESH_INTERVAL = Duration.ofSeconds(60);

    private static WynncraftGuildClient instance;

    private final HttpClient httpClient;

    public static synchronized WynncraftGuildClient getInstance() {
        if (instance == null) {
            instance = new WynncraftGuildClient();
        }
        return instance;
    }

    private WynncraftGuildClient() {
        ExecutorService executor = Executors.newFixedThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "seq-wynncraft-api");
            thread.setDaemon(true);
            return thread;
        });
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .executor(executor)
                .build();
    }

    /** The guild prefix the given player belongs to, or null when they are guildless. */
    public CompletableFuture<String> resolveGuildPrefix(String username) {
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return get("/player/" + encode(username)).thenApply(WynncraftGuildClient::parseGuildPrefix);
    }

    /** Every member of the guild, online and offline, keyed by nothing and ordered by rank. */
    public CompletableFuture<GuildRoster> fetchRoster(String guildPrefix) {
        if (guildPrefix == null || guildPrefix.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("guildPrefix must not be blank"));
        }
        return get("/guild/prefix/" + encode(guildPrefix)).thenApply(WynncraftGuildClient::parseRoster);
    }

    private CompletableFuture<JsonObject> get(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .GET()
                .build();

        return httpClient
                .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() == 404) {
                        throw new WynncraftApiException("Wynncraft does not know that guild.");
                    }
                    if (response.statusCode() == 429) {
                        throw new WynncraftApiException("Wynncraft is rate limiting us. Try again shortly.");
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new WynncraftApiException("Wynncraft API returned " + response.statusCode() + ".");
                    }
                    JsonElement parsed = JsonParser.parseString(response.body());
                    if (!parsed.isJsonObject()) {
                        throw new WynncraftApiException("Wynncraft API returned an unexpected payload.");
                    }
                    return parsed.getAsJsonObject();
                });
    }

    // ── Parsing (pure, no I/O) ──

    static String parseGuildPrefix(JsonObject player) {
        if (player == null || !player.has("guild") || !player.get("guild").isJsonObject()) {
            return null;
        }
        JsonObject guild = player.getAsJsonObject("guild");
        return optionalString(guild, "prefix");
    }

    /**
     * Flattens the guild payload, whose members arrive grouped under one object per
     * rank, into a single list. Offline members are dropped here: the panel exists
     * to answer "who can I play with right now", and a 150-member guild is mostly
     * offline at any hour.
     * <p>
     * A member who has hidden their online status through Wynncraft's privacy
     * settings reports {@code online: false} with no world, and is indistinguishable
     * from someone genuinely offline. They are left out rather than guessed at.
     */
    static GuildRoster parseRoster(JsonObject guild) {
        if (guild == null) {
            return GuildRoster.empty();
        }

        String guildName = optionalString(guild, "name");
        String guildPrefix = optionalString(guild, "prefix");
        List<GuildMemberPresence> online = new ArrayList<>();

        if (guild.has("members") && guild.get("members").isJsonObject()) {
            JsonObject members = guild.getAsJsonObject("members");
            for (Map.Entry<String, JsonElement> rankEntry : members.entrySet()) {
                // "total" is a member count sitting alongside the rank groups.
                if (!rankEntry.getValue().isJsonObject()) {
                    continue;
                }
                GuildMemberPresence.GuildRank rank =
                        GuildMemberPresence.GuildRank.fromApiKey(rankEntry.getKey());
                JsonObject byUsername = rankEntry.getValue().getAsJsonObject();
                for (Map.Entry<String, JsonElement> memberEntry : byUsername.entrySet()) {
                    if (!memberEntry.getValue().isJsonObject()) {
                        continue;
                    }
                    JsonObject member = memberEntry.getValue().getAsJsonObject();
                    if (!optionalBoolean(member, "online")) {
                        continue;
                    }
                    String username = memberEntry.getKey();
                    if (username == null || username.isBlank()) {
                        continue;
                    }
                    online.add(new GuildMemberPresence(
                            username, optionalString(member, "uuid"), rank, optionalString(member, "server"), false));
                }
            }
        }

        int total = guild.has("members")
                        && guild.get("members").isJsonObject()
                        && guild.getAsJsonObject("members").has("total")
                ? optionalInt(guild.getAsJsonObject("members"), "total")
                : online.size();

        return new GuildRoster(guildName, guildPrefix, total, List.copyOf(online));
    }

    private static String optionalString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        String value = object.get(key).getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean optionalBoolean(JsonObject object, String key) {
        return object != null
                && object.has(key)
                && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isBoolean()
                && object.get(key).getAsBoolean();
    }

    private static int optionalInt(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return 0;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value.trim(), StandardCharsets.UTF_8);
    }

    /** The online half of a guild, plus the guild's own identity for the panel header. */
    public record GuildRoster(String guildName, String guildPrefix, int totalMembers, List<GuildMemberPresence> online) {

        public GuildRoster {
            online = online == null ? List.of() : List.copyOf(online);
        }

        public static GuildRoster empty() {
            return new GuildRoster(null, null, 0, List.of());
        }

        public String displayName() {
            if (guildName != null) {
                return guildName;
            }
            return guildPrefix != null ? guildPrefix.toUpperCase(Locale.ROOT) : "Guild";
        }
    }

    /** A Wynncraft API failure worth showing to the player rather than logging alone. */
    public static class WynncraftApiException extends RuntimeException {
        public WynncraftApiException(String message) {
            super(message);
        }
    }
}
