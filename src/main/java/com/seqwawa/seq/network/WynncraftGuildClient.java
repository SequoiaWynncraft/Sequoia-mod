package com.seqwawa.seq.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seqwawa.seq.model.GuildMemberPresence;
import com.seqwawa.seq.model.GuildMemberStats;
import com.seqwawa.seq.model.KnownGuildMember;
import com.seqwawa.seq.model.RaidCatalog;
import com.seqwawa.seq.model.RaidPerformance;
import com.seqwawa.seq.model.RaidType;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads the guild roster from Wynncraft's public API, which lists the whole guild
 * rather than the subset running the mod.
 * <p>
 * The guild endpoint is served with a two-minute cache and a bucket limit of 50
 * requests per minute; polling faster than {@link #MINIMUM_REFRESH_INTERVAL}
 * returns the same bytes.
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

    /** The roster. The catalog is needed to read clear counts, which Wynncraft keys by raid name. */
    public CompletableFuture<GuildRoster> fetchRoster(String guildPrefix, RaidCatalog catalog) {
        if (guildPrefix == null || guildPrefix.isBlank()) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("guildPrefix must not be blank"));
        }
        return get("/guild/prefix/" + encode(guildPrefix)).thenApply(json -> parseRoster(json, catalog));
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
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new WynncraftApiException(describeFailure(response.statusCode()));
                    }
                    JsonElement parsed = JsonParser.parseString(response.body());
                    if (!parsed.isJsonObject()) {
                        throw new WynncraftApiException("Wynncraft API returned an unexpected payload.");
                    }
                    return parsed.getAsJsonObject();
                });
    }

    /**
     * What to show for a status Wynncraft answered with.
     * <p>
     * A 5xx is worth naming as theirs: the guild endpoint has served whole guilds a
     * 500 for a stretch while the rest of the API kept working, and nothing on this
     * side fixes that.
     */
    static String describeFailure(int status) {
        if (status == 404) {
            return "Wynncraft does not know that guild.";
        }
        if (status == 429) {
            return "Wynncraft is rate limiting us. Try again shortly.";
        }
        if (status >= 500) {
            return "Wynncraft cannot serve the guild roster right now (" + status + "). Their side, try again in a bit.";
        }
        return "Wynncraft API returned " + status + ".";
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
     * Flattens the members, which arrive grouped by rank. Online members get a full
     * presence; everyone else is kept as a {@link KnownGuildMember} so an offline
     * friend still has a head and a last login.
     * <p>
     * A member hiding their online status reports {@code online: false} with no
     * world, which is indistinguishable from being offline, so they are left out.
     */
    static GuildRoster parseRoster(JsonObject guild, RaidCatalog catalog) {
        if (guild == null) {
            return GuildRoster.empty();
        }

        String guildName = optionalString(guild, "name");
        String guildPrefix = optionalString(guild, "prefix");
        List<GuildMemberPresence> online = new ArrayList<>();
        Map<String, KnownGuildMember> everyone = new HashMap<>();

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
                    String username = memberEntry.getKey();
                    if (username == null || username.isBlank()) {
                        continue;
                    }
                    KnownGuildMember known = new KnownGuildMember(
                            username, optionalString(member, "uuid"), optionalInstant(member, "lastJoin"));
                    everyone.put(known.key(), known);
                    if (!optionalBoolean(member, "online")) {
                        continue;
                    }
                    online.add(new GuildMemberPresence(
                            username,
                            optionalString(member, "uuid"),
                            rank,
                            optionalString(member, "server"),
                            false,
                            parseStats(member, catalog)));
                }
            }
        }

        int total = guild.has("members")
                        && guild.get("members").isJsonObject()
                        && guild.getAsJsonObject("members").has("total")
                ? optionalInt(guild.getAsJsonObject("members"), "total")
                : online.size();

        return new GuildRoster(guildName, guildPrefix, total, List.copyOf(online), everyone);
    }

    /**
     * Everything the card shows, out of the payload the roster already carries.
     * <p>
     * Contribution and the join date sit on the member; playtime, wars, level, clears
     * and raid totals sit under {@code globalData}, which a member hiding their
     * profile does not have. Clears come from {@code currentGuildRaids} rather than
     * {@code raids}, so a previous guild's raids are not counted as ours.
     */
    static GuildMemberStats parseStats(JsonObject member, RaidCatalog catalog) {
        if (member == null) {
            return GuildMemberStats.unknown();
        }
        long contributedXp = optionalLong(member, "contributed");
        int contributionRank = optionalInt(member, "contributionRank");
        Instant joinedGuildAt = optionalInstant(member, "joined");

        if (!member.has("globalData") || !member.get("globalData").isJsonObject()) {
            return new GuildMemberStats(
                    0d, Map.of(), 0, 0, contributedXp, contributionRank, joinedGuildAt, RaidPerformance.unknown());
        }
        JsonObject globalData = member.getAsJsonObject("globalData");

        double playtimeHours = 0d;
        if (globalData.has("playtime") && globalData.get("playtime").isJsonPrimitive()) {
            try {
                playtimeHours = globalData.get("playtime").getAsDouble();
            } catch (RuntimeException ignored) {
                playtimeHours = 0d;
            }
        }

        Map<String, Integer> completions = new HashMap<>();
        if (catalog != null
                && globalData.has("currentGuildRaids")
                && globalData.get("currentGuildRaids").isJsonObject()) {
            JsonObject raids = globalData.getAsJsonObject("currentGuildRaids");
            if (raids.has("list") && raids.get("list").isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : raids.getAsJsonObject("list").entrySet()) {
                    RaidType raid = catalog.raidByApiName(entry.getKey());
                    if (raid == null || !entry.getValue().isJsonPrimitive()) {
                        continue;
                    }
                    try {
                        completions.put(raid.key(), entry.getValue().getAsInt());
                    } catch (RuntimeException ignored) {
                        // A count that will not parse is simply not shown.
                    }
                }
            }
        }

        return new GuildMemberStats(
                playtimeHours,
                completions,
                optionalInt(globalData, "wars"),
                optionalInt(globalData, "totalLevel"),
                contributedXp,
                contributionRank,
                joinedGuildAt,
                parseRaidPerformance(globalData));
    }

    /** Lifetime raid damage, healing, deaths and gambits, all optional. */
    private static RaidPerformance parseRaidPerformance(JsonObject globalData) {
        if (!globalData.has("raidStats") || !globalData.get("raidStats").isJsonObject()) {
            return RaidPerformance.unknown();
        }
        JsonObject raidStats = globalData.getAsJsonObject("raidStats");
        return new RaidPerformance(
                optionalLong(raidStats, "damageDealt"),
                optionalLong(raidStats, "healthHealed"),
                optionalLong(raidStats, "deaths"),
                optionalLong(raidStats, "gambitsUsed"));
    }

    private static String optionalString(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return null;
        }
        String value = object.get(key).getAsString();
        return value == null || value.isBlank() ? null : value;
    }

    /** An ISO timestamp such as {@code 2026-09-15T18:02:11.845000Z}, or null. */
    private static Instant optionalInstant(JsonObject object, String key) {
        String value = optionalString(object, key);
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static boolean optionalBoolean(JsonObject object, String key) {
        return object != null
                && object.has(key)
                && object.get(key).isJsonPrimitive()
                && object.get(key).getAsJsonPrimitive().isBoolean()
                && object.get(key).getAsBoolean();
    }

    private static long optionalLong(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return 0L;
        }
        try {
            return object.get(key).getAsLong();
        } catch (RuntimeException ignored) {
            return 0L;
        }
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

    /** The online members in full, everyone by lowercase username, and the guild's identity. */
    public record GuildRoster(
            String guildName,
            String guildPrefix,
            int totalMembers,
            List<GuildMemberPresence> online,
            Map<String, KnownGuildMember> everyone) {

        public GuildRoster {
            online = online == null ? List.of() : List.copyOf(online);
            everyone = everyone == null ? Map.of() : Map.copyOf(everyone);
        }

        public GuildRoster(String guildName, String guildPrefix, int totalMembers, List<GuildMemberPresence> online) {
            this(guildName, guildPrefix, totalMembers, online, Map.of());
        }

        public static GuildRoster empty() {
            return new GuildRoster(null, null, 0, List.of());
        }

        /** The member called {@code username} whatever their status, or null. */
        public KnownGuildMember member(String username) {
            if (username == null || username.isBlank()) {
                return null;
            }
            return everyone.get(username.trim().toLowerCase(Locale.ROOT));
        }

        public String displayName() {
            if (guildName != null) {
                return guildName;
            }
            return guildPrefix != null ? guildPrefix.toUpperCase(Locale.ROOT) : "Guild";
        }
    }

    /** A Wynncraft API failure worth showing to the player. */
    public static class WynncraftApiException extends RuntimeException {
        public WynncraftApiException(String message) {
            super(message);
        }
    }
}
