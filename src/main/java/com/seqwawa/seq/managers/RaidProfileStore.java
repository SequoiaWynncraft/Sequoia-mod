package com.seqwawa.seq.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializer;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.PremadeParty;
import com.seqwawa.seq.model.RaidCatalog;
import com.seqwawa.seq.model.RaidProfilesResponse;
import com.seqwawa.seq.model.RaidTeamProfile;
import com.seqwawa.seq.model.GuildMemberPresence;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.BuildConfig;
import com.seqwawa.seq.network.ConnectionManager;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Owns the raid meta and every member's profile, both of which come from the
 * backend, plus the handful of things that are the player's alone.
 * <p>
 * The split is deliberate. The catalog of meta builds and the profiles are guild
 * data: they are fetched, cached to disk so the panel is not blank while offline,
 * and never authored here. Friends, premade parties, private notes and whether
 * the setup screen has been dismissed are personal, so they live in a local file
 * and are never sent anywhere.
 * <p>
 * Your own profile is read back from the same fetch as everyone else's rather
 * than kept locally, which is what stops your client from showing a version of
 * yourself that the rest of the guild cannot see.
 */
public final class RaidProfileStore {

    /** Personal, never uploaded: friends, premades, notes, setup dismissal. */
    private static final Path LOCAL_PATH = Path.of("config", "sequoia", "raid-profile.json");

    /** Last backend response, so the panel has something to draw while offline. */
    private static final Path CACHE_PATH = Path.of("config", "sequoia", "cache", "raid-profiles.json");

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .registerTypeAdapter(
                    Instant.class,
                    (JsonDeserializer<Instant>) (json, type, context) -> Instant.parse(json.getAsString()))
            .registerTypeAdapter(
                    Instant.class,
                    (JsonSerializer<Instant>) (src, type, context) -> new JsonPrimitive(src.toString()))
            .create();

    private static final int SCHEMA_VERSION = 1;

    /** Notes are a reminder to yourself, not an essay. */
    public static final int MAX_NOTE_LENGTH = 120;

    private static RaidProfileStore instance;

    private final Path localPath;
    private final Path cachePath;
    private final Supplier<CompletableFuture<RaidProfilesResponse>> fetch;

    private final Map<String, String> notes = new ConcurrentHashMap<>();
    private final List<String> friends = new CopyOnWriteArrayList<>();
    private final List<PremadeParty> premades = new CopyOnWriteArrayList<>();

    private volatile RaidCatalog catalog = RaidCatalog.empty();
    /** Keyed by Minecraft UUID, which survives a rename where a username does not. */
    private volatile Map<String, RaidTeamProfile> profiles = Map.of();
    /** Lowercase username to UUID, only for lookups that have no roster row. */
    private volatile Map<String, String> uuidByUsername = Map.of();
    private volatile boolean setupDismissed;
    private volatile boolean loaded;
    private volatile boolean fetching;
    private volatile boolean refreshQueued;
    private volatile CompletableFuture<Void> inFlightRefresh = CompletableFuture.completedFuture(null);
    private volatile String lastError;
    private volatile long lastFetchAtMs;

    RaidProfileStore(Path localPath, Path cachePath) {
        this(localPath, cachePath, () -> ApiClient.getInstance().getRaidProfiles());
    }

    /** Test seam, so cache and fetch behaviour can be exercised without a network. */
    RaidProfileStore(
            Path localPath, Path cachePath, Supplier<CompletableFuture<RaidProfilesResponse>> fetch) {
        this.localPath = localPath;
        this.cachePath = cachePath;
        this.fetch = fetch;
    }

    public static synchronized RaidProfileStore getInstance() {
        if (instance == null) {
            instance = new RaidProfileStore(LOCAL_PATH, CACHE_PATH);
            instance.load();
            instance.subscribeToLiveUpdates();
        }
        return instance;
    }

    /**
     * Applies pushes so a panel left open does not go stale.
     * <p>
     * Optional on the backend's side: without the push the panel still refreshes
     * when it opens, when Refresh is pressed, and after a save.
     */
    private void subscribeToLiveUpdates() {
        ConnectionManager.onRaidProfileUpdate(this::onLiveUpdate);
    }

    void onLiveUpdate(ConnectionManager.RaidProfileUpdateMessage message) {
        if (message == null || message.profileJson() == null) {
            return;
        }
        if (ApiClient.raidProfilesOnSeparateBackend()) {
            // The socket belongs to the main backend, so its pushes describe that
            // backend's profiles. Mixing them into ones fetched from elsewhere would
            // show members who never saved a profile where this panel reads from.
            return;
        }
        RaidProfilesResponse.Profile profile;
        try {
            profile = GSON.fromJson(message.profileJson(), RaidProfilesResponse.Profile.class);
        } catch (RuntimeException e) {
            SeqClient.LOGGER.warn("[RaidProfiles] Could not read a live update: {}", e.toString());
            return;
        }
        String uuid = RaidProfilesResponse.identityKey(profile);
        if (uuid == null) {
            SeqClient.LOGGER.warn("[RaidProfiles] Live update carried no uuid, ignoring it");
            return;
        }

        if (message.isRemoval()) {
            // A removal payload has a profile's shape with null fields. Storing it
            // would read as "shared an empty profile" instead of "shared none".
            removeProfile(uuid);
            return;
        }
        applyProfileUpdate(profile);
    }

    // ── Backend data ──

    /** The meta as the backend publishes it, empty until the first fetch lands. */
    public RaidCatalog catalog() {
        return catalog;
    }

    /**
     * The profile for a roster member, matched on their UUID.
     * <p>
     * Always prefer this over the by-name lookup: a member who renamed keeps the
     * same UUID, so this cannot return a stale entry or miss a fresh one.
     */
    public RaidTeamProfile profileFor(GuildMemberPresence member) {
        return member == null ? RaidTeamProfile.empty() : profileForUuid(member.uuid());
    }

    public RaidTeamProfile profileForUuid(String uuid) {
        String key = RaidProfilesResponse.normalizeUuid(uuid);
        return key == null ? RaidTeamProfile.empty() : profiles.getOrDefault(key, RaidTeamProfile.empty());
    }

    /**
     * The profile for a bare username, for the friends list where the member may
     * be offline and a name is all there is. Best effort: the name index can be a
     * rename behind, which is exactly why nothing with a roster row uses it.
     */
    public RaidTeamProfile profileForUsername(String username) {
        String key = normalizeKey(username);
        if (key == null) {
            return RaidTeamProfile.empty();
        }
        String uuid = uuidByUsername.get(key);
        return uuid == null ? RaidTeamProfile.empty() : profiles.getOrDefault(uuid, RaidTeamProfile.empty());
    }

    public boolean hasProfileFor(GuildMemberPresence member) {
        return profileFor(member).isComplete();
    }

    /** Your own profile, read back from the backend like everybody else's. */
    public RaidTeamProfile selfProfile() {
        String uuid = localUuid();
        return uuid == null ? profileForUsername(localUsername()) : profileForUuid(uuid);
    }

    /** How many members other than you have shared a profile. */
    public int sharedProfileCount() {
        String self = RaidProfilesResponse.normalizeUuid(localUuid());
        return (int) profiles.entrySet().stream()
                .filter(entry -> entry.getValue().isComplete())
                .filter(entry -> self == null || !entry.getKey().equals(self))
                .count();
    }

    public boolean hasLoadedProfiles() {
        return lastFetchAtMs > 0L || !profiles.isEmpty();
    }

    public boolean isFetching() {
        return fetching;
    }

    public String lastError() {
        return lastError;
    }

    /**
     * Fetches the catalog and every profile, then caches the response.
     * <p>
     * A failure keeps whatever was already loaded: a panel showing a slightly old
     * roster is worth more than one that empties itself because a request timed out.
     */
    public synchronized CompletableFuture<Void> refresh() {
        if (fetching) {
            // A fetch already in flight may have started before a save, and would then
            // overwrite it with pre-save data. Run once more after it lands.
            refreshQueued = true;
            return inFlightRefresh;
        }
        fetching = true;
        refreshQueued = false;
        CompletableFuture<Void> attempt = fetch.get()
                .thenAccept(response -> {
                    if (response == null) {
                        return;
                    }
                    apply(response);
                    writeCache(response);
                    lastError = null;
                    lastFetchAtMs = System.currentTimeMillis();
                    SeqClient.LOGGER.info(
                            "[RaidProfiles] Loaded {} builds, {} raids, {} profiles",
                            catalog.builds().size(),
                            catalog.raids().size(),
                            profiles.size());
                })
                .exceptionally(throwable -> {
                    lastError = RaidProfileApiError.describe(throwable, "Could not load the guild's raid profiles.");
                    SeqClient.LOGGER.warn(
                            "[RaidProfiles] GET {}/raid-profiles failed: {} | shown: {}",
                            BuildConfig.RAID_PROFILES_API_URL,
                            RaidProfileApiError.describeForLog(throwable),
                            lastError);
                    return null;
                })
                .whenComplete((ignored, throwable) -> onRefreshFinished());
        inFlightRefresh = attempt;
        return attempt;
    }

    private void onRefreshFinished() {
        boolean again;
        synchronized (this) {
            fetching = false;
            again = refreshQueued;
            refreshQueued = false;
        }
        if (again) {
            refresh();
        }
    }

    void apply(RaidProfilesResponse response) {
        RaidCatalog fetched = response.toCatalog();
        // An empty catalog would blank every build chip in the panel, so a response
        // that carries none keeps the last one that worked.
        catalog = fetched.isEmpty() ? catalog : fetched;
        profiles = response.toDomain(catalog);
        uuidByUsername = response.uuidByUsername();
    }

    /**
     * Applies one member's profile from a {@code raid_profile_update} push.
     * <p>
     * Only ever called for the {@code updated} action. A removal carries a payload
     * shaped like a profile but filled with nulls, and storing that would show the
     * member as having shared an empty profile instead of none.
     */
    public void applyProfileUpdate(RaidProfilesResponse.Profile profile) {
        String uuid = RaidProfilesResponse.identityKey(profile);
        if (uuid == null) {
            return;
        }
        RaidProfilesResponse wrapper = new RaidProfilesResponse(
                RaidProfilesResponse.CURRENT_SCHEMA_VERSION, null, List.of(profile));
        RaidTeamProfile updated = wrapper.toDomain(catalog).get(uuid);
        if (updated == null) {
            return;
        }

        Map<String, RaidTeamProfile> merged = new LinkedHashMap<>(profiles);
        merged.put(uuid, updated);
        profiles = Map.copyOf(merged);

        String username = profile.minecraft().username();
        if (username != null && !username.isBlank()) {
            Map<String, String> names = new LinkedHashMap<>(uuidByUsername);
            names.put(username.trim().toLowerCase(Locale.ROOT), uuid);
            uuidByUsername = Map.copyOf(names);
        }
        SeqClient.LOGGER.debug("[RaidProfiles] Applied live update for {}", username);
    }

    /** Drops one member's profile, matched on UUID so a rename cannot orphan it. */
    public void removeProfile(String uuid) {
        String key = RaidProfilesResponse.normalizeUuid(uuid);
        if (key == null || !profiles.containsKey(key)) {
            return;
        }
        Map<String, RaidTeamProfile> merged = new LinkedHashMap<>(profiles);
        merged.remove(key);
        profiles = Map.copyOf(merged);

        Map<String, String> names = new LinkedHashMap<>(uuidByUsername);
        names.values().removeIf(key::equals);
        uuidByUsername = Map.copyOf(names);
    }

    /**
     * Saves your profile to the backend and applies what it stored.
     * <p>
     * The stored profile is read back rather than assumed, so what you see is what
     * the rest of the guild will see, including any normalising the server did.
     */
    public CompletableFuture<Boolean> saveSelfProfile(RaidTeamProfile profile) {
        RaidTeamProfile toSave = profile == null ? RaidTeamProfile.empty() : profile;
        return ApiClient.getInstance()
                .putMyRaidProfile(toSave)
                .thenApply(saved -> {
                    setupDismissed = false;
                    persist();
                    // The response is authoritative, including the username, which can be
                    // a rename ahead of what the token says.
                    applyProfileUpdate(saved);
                    // Pull the authoritative copy in the background; the optimistic entry
                    // above is what keeps the panel responsive in the meantime.
                    refresh();
                    lastError = null;
                    return true;
                })
                .exceptionally(throwable -> {
                    lastError = RaidProfileApiError.describe(throwable, "Could not save your profile.");
                    SeqClient.LOGGER.warn(
                            "[RaidProfiles] PUT {}/raid-profiles/me failed: {} | shown: {}",
                            BuildConfig.RAID_PROFILES_API_URL,
                            RaidProfileApiError.describeForLog(throwable),
                            lastError);
                    return false;
                });
    }

    // ── Setup gating ──

    /**
     * Whether the setup screen should open instead of the members list. True until
     * the backend holds a profile for you, or you explicitly skip it.
     */
    public boolean needsSetup() {
        return !selfProfile().isComplete() && !setupDismissed;
    }

    /** Records that the player closed setup without filling anything in. */
    public void dismissSetup() {
        setupDismissed = true;
        persist();
    }

    /** Reopens the setup screen on the next visit, for "edit my profile". */
    public void requestSetup() {
        setupDismissed = false;
    }

    // ── Friends ──

    /** People the player likes raiding with, in the order they added them. */
    public List<String> friends() {
        return List.copyOf(friends);
    }

    public boolean isFriend(String username) {
        String key = normalizeKey(username);
        return key != null && friends.stream().anyMatch(friend -> key.equals(normalizeKey(friend)));
    }

    /** Adds a friend, or removes them when they are already on the list. */
    public void toggleFriend(String username) {
        String key = normalizeKey(username);
        if (key == null) {
            return;
        }
        if (!friends.removeIf(friend -> key.equals(normalizeKey(friend)))) {
            friends.add(username.trim());
        }
        persist();
    }

    // ── Premade parties ──

    public List<PremadeParty> premades() {
        return List.copyOf(premades);
    }

    public PremadeParty premade(String name) {
        String key = normalizeKey(name);
        if (key == null) {
            return null;
        }
        return premades.stream().filter(party -> party.key().equals(key)).findFirst().orElse(null);
    }

    /**
     * Saves a composition, replacing any existing one with the same name.
     * {@code previousName} lets an edit rename without leaving the old entry behind.
     */
    public void savePremade(PremadeParty party, String previousName) {
        if (party == null || !party.isUsable()) {
            return;
        }
        String previousKey = normalizeKey(previousName);
        if (previousKey != null) {
            premades.removeIf(existing -> existing.key().equals(previousKey));
        }
        premades.removeIf(existing -> existing.key().equals(party.key()));
        premades.add(party.savedAt(System.currentTimeMillis()));
        persist();
    }

    public void deletePremade(String name) {
        String key = normalizeKey(name);
        if (key != null && premades.removeIf(party -> party.key().equals(key))) {
            persist();
        }
    }

    // ── Private notes ──

    /** The player's own note about a member, or null. Never leaves this client. */
    public String noteFor(String username) {
        String key = normalizeKey(username);
        return key == null ? null : notes.get(key);
    }

    public void setNote(String username, String note) {
        String key = normalizeKey(username);
        if (key == null) {
            return;
        }
        String trimmed = note == null ? "" : note.trim();
        if (trimmed.isEmpty()) {
            notes.remove(key);
        } else {
            notes.put(key, trimmed.length() <= MAX_NOTE_LENGTH ? trimmed : trimmed.substring(0, MAX_NOTE_LENGTH));
        }
        persist();
    }

    // ── Persistence ──

    void load() {
        loaded = true;
        loadLocal();
        loadCache();
    }

    private void loadLocal() {
        if (!Files.exists(localPath)) {
            return;
        }
        try {
            JsonElement parsed = JsonParser.parseString(Files.readString(localPath, StandardCharsets.UTF_8));
            if (parsed.isJsonObject()) {
                applyLocalDocument(parsed.getAsJsonObject());
            }
        } catch (IOException | RuntimeException e) {
            // A corrupt file is recoverable by filling things in again; refusing to open
            // the panel over it would not be.
            SeqClient.LOGGER.warn("[RaidProfiles] Could not read {}: {}", localPath, e.toString());
        }
    }

    private void loadCache() {
        if (!Files.exists(cachePath)) {
            return;
        }
        try {
            RaidProfilesResponse cached = parseProfilesResponse(
                            Files.readString(cachePath, StandardCharsets.UTF_8))
                    .orElse(null);
            if (cached != null) {
                apply(cached);
                SeqClient.LOGGER.info("[RaidProfiles] Loaded {} profiles from cache", profiles.size());
            }
        } catch (IOException | RuntimeException e) {
            SeqClient.LOGGER.warn("[RaidProfiles] Could not read cache {}: {}", cachePath, e.toString());
        }
    }

    private void writeCache(RaidProfilesResponse response) {
        try {
            Path parent = cachePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(cachePath, GSON.toJson(response), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            SeqClient.LOGGER.warn("[RaidProfiles] Could not write cache {}: {}", cachePath, e.toString());
        }
    }

    void applyLocalDocument(JsonObject document) {
        setupDismissed = document.has("setupDismissed")
                && document.get("setupDismissed").isJsonPrimitive()
                && document.get("setupDismissed").getAsBoolean();

        notes.clear();
        if (document.has("notes") && document.get("notes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : document.getAsJsonObject("notes").entrySet()) {
                String key = normalizeKey(entry.getKey());
                if (key != null && entry.getValue().isJsonPrimitive()) {
                    notes.put(key, entry.getValue().getAsString());
                }
            }
        }

        friends.clear();
        if (document.has("friends") && document.get("friends").isJsonArray()) {
            for (JsonElement element : document.getAsJsonArray("friends")) {
                if (element.isJsonPrimitive() && !element.getAsString().isBlank()) {
                    friends.add(element.getAsString().trim());
                }
            }
        }

        premades.clear();
        if (document.has("premades") && document.get("premades").isJsonArray()) {
            for (JsonElement element : document.getAsJsonArray("premades")) {
                if (!element.isJsonObject()) {
                    continue;
                }
                PremadeParty party = parsePremade(element.getAsJsonObject());
                if (party != null && party.isUsable()) {
                    premades.add(party);
                }
            }
        }
    }

    static PremadeParty parsePremade(JsonObject object) {
        if (object == null || !object.has("name") || !object.get("name").isJsonPrimitive()) {
            return null;
        }
        List<String> members = new ArrayList<>();
        if (object.has("members") && object.get("members").isJsonArray()) {
            for (JsonElement element : object.getAsJsonArray("members")) {
                if (element.isJsonPrimitive()) {
                    members.add(element.getAsString());
                }
            }
        }
        long updatedAt = 0L;
        if (object.has("updatedAt") && object.get("updatedAt").isJsonPrimitive()) {
            try {
                updatedAt = object.get("updatedAt").getAsLong();
            } catch (RuntimeException ignored) {
                updatedAt = 0L;
            }
        }
        return new PremadeParty(object.get("name").getAsString(), members, updatedAt);
    }

    static JsonObject serializePremade(PremadeParty party) {
        JsonObject object = new JsonObject();
        object.addProperty("name", party.name());
        JsonArray members = new JsonArray();
        party.members().forEach(members::add);
        object.add("members", members);
        object.addProperty("updatedAt", party.updatedAtEpochMs());
        return object;
    }

    private void persist() {
        try {
            Path parent = localPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(localPath, GSON.toJson(toLocalDocument()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            SeqClient.LOGGER.warn("[RaidProfiles] Could not write {}: {}", localPath, e.toString());
        }
    }

    JsonObject toLocalDocument() {
        JsonObject document = new JsonObject();
        document.addProperty("version", SCHEMA_VERSION);
        document.addProperty("setupDismissed", setupDismissed);

        JsonObject noteObject = new JsonObject();
        new LinkedHashMap<>(notes).forEach(noteObject::addProperty);
        document.add("notes", noteObject);

        JsonArray friendArray = new JsonArray();
        friends.forEach(friendArray::add);
        document.add("friends", friendArray);

        JsonArray premadeArray = new JsonArray();
        premades.forEach(party -> premadeArray.add(serializePremade(party)));
        document.add("premades", premadeArray);
        return document;
    }

    /** Parses the backend payload. Shared by the live fetch and the on-disk cache. */
    static Optional<RaidProfilesResponse> parseProfilesResponse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(GSON.fromJson(raw, RaidProfilesResponse.class));
    }

    /** The local player's UUID, which is what their own profile is stored under. */
    private static String localUuid() {
        if (SeqClient.mc == null || SeqClient.mc.getUser() == null) {
            return null;
        }
        return SeqClient.mc.getUser().getProfileId() == null
                ? null
                : SeqClient.mc.getUser().getProfileId().toString();
    }

    private static String localUsername() {
        if (SeqClient.mc == null) {
            return null;
        }
        if (SeqClient.mc.getUser() != null) {
            return SeqClient.mc.getUser().getName();
        }
        return SeqClient.mc.player != null ? SeqClient.mc.player.getName().getString() : null;
    }

    private static String normalizeKey(String username) {
        if (username == null) {
            return null;
        }
        String trimmed = username.trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    boolean isLoaded() {
        return loaded;
    }
}
