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
import com.seqwawa.seq.client.Background;
import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.Refreshable;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Owns the raid meta and every member's profile, both from the backend, plus what
 * is the player's alone.
 * <p>
 * Catalog and profiles are guild data: fetched, cached to disk so the panel is not
 * blank offline, never authored here. Friends, premades, notes and the setup
 * dismissal are personal and stay in a local file. Friends, premades and notes are
 * shared by every Minecraft account on this computer; skipping setup is each account's own.
 * <p>
 * Your own profile is read back from the same fetch as everyone else's, so you
 * cannot see a version of yourself the guild cannot.
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

    /** How long a fetched copy is treated as fresh enough to reuse. */
    private static final long FETCH_FRESHNESS_MS = 60_000L;

    /** A note is a reminder, not an essay. */
    public static final int MAX_NOTE_LENGTH = 120;

    private static RaidProfileStore instance;

    private final Path localPath;
    private final Path cachePath;
    private final Supplier<CompletableFuture<RaidProfilesResponse>> fetch;
    /** Saves the player's own profile, answering with what the backend stored. */
    private final Function<RaidTeamProfile, CompletableFuture<RaidProfilesResponse.Profile>> save;
    /** Whether raid profiles come from another backend than the socket's. */
    private final BooleanSupplier separateBackend;

    private final Map<String, String> notes = new ConcurrentHashMap<>();
    private final List<String> friends = new CopyOnWriteArrayList<>();
    private final List<PremadeParty> premades = new CopyOnWriteArrayList<>();

    /**
     * What the backend publishes: the raid catalog, every member's profile keyed by
     * Minecraft UUID (which survives a rename where a username does not), the lowercase
     * name index for lookups that have no roster row, and the response it came from.
     */
    private record Published(
            RaidCatalog catalog,
            Map<String, RaidTeamProfile> profiles,
            Map<String, String> uuidByUsername,
            RaidProfilesResponse source) {
        static final Published EMPTY = new Published(RaidCatalog.empty(), Map.of(), Map.of(), null);
    }

    /**
     * A failed fetch keeps what is loaded. Pushes and saves change it through
     * {@link Refreshable#mutate}, so a fetch answered before them can't undo them.
     */
    private final Refreshable<Published> published;
    private final Executor gameThread;
    private final Executor disk;
    /** The Minecraft accounts that skipped setup, by normalised UUID: each account decides for itself. */
    private final Set<String> setupDismissedBy = ConcurrentHashMap.newKeySet();
    /** An older file's single "skipped": the first account to ask takes it as its own. */
    private volatile boolean unclaimedSetupDismissal;
    /** The Minecraft account playing, by UUID, or null before the game knows. */
    private final Supplier<String> account;
    private volatile boolean loaded;
    /** Whether a fetch has worked since the game started. */
    private volatile boolean fetchedOnce;
    private volatile String lastError;

    RaidProfileStore(Path localPath, Path cachePath) {
        this(
                localPath,
                cachePath,
                () -> ApiClient.getInstance().getRaidProfiles(),
                ApiClient::raidProfilesOnSeparateBackend,
                ClientThread.executor(),
                Background.disk());
    }

    /**
     * Test seam, so cache and fetch behaviour can run without a network. It behaves as
     * if raid profiles share the main backend, whichever backend the build targets.
     */
    RaidProfileStore(
            Path localPath, Path cachePath, Supplier<CompletableFuture<RaidProfilesResponse>> fetch) {
        this(localPath, cachePath, fetch, () -> false);
    }

    RaidProfileStore(
            Path localPath,
            Path cachePath,
            Supplier<CompletableFuture<RaidProfilesResponse>> fetch,
            BooleanSupplier separateBackend) {
        // A test completes its fetches on its own thread, which stands in for the game's.
        this(localPath, cachePath, fetch, separateBackend, Runnable::run, Runnable::run);
    }

    RaidProfileStore(
            Path localPath,
            Path cachePath,
            Supplier<CompletableFuture<RaidProfilesResponse>> fetch,
            BooleanSupplier separateBackend,
            Executor gameThread,
            Executor disk) {
        this(localPath, cachePath, fetch, separateBackend, gameThread, disk, RaidProfileStore::localUuid);
    }

    /** Test seam: {@code account} stands in for the Minecraft account playing. */
    RaidProfileStore(
            Path localPath,
            Path cachePath,
            Supplier<CompletableFuture<RaidProfilesResponse>> fetch,
            BooleanSupplier separateBackend,
            Executor gameThread,
            Executor disk,
            Supplier<String> account) {
        this(localPath, cachePath, fetch, separateBackend, gameThread, disk, account,
                profile -> ApiClient.getInstance().putMyRaidProfile(profile));
    }

    /** Test seam: {@code save} stands in for the backend that stores your profile. */
    RaidProfileStore(
            Path localPath,
            Path cachePath,
            Supplier<CompletableFuture<RaidProfilesResponse>> fetch,
            BooleanSupplier separateBackend,
            Executor gameThread,
            Executor disk,
            Supplier<String> account,
            Function<RaidTeamProfile, CompletableFuture<RaidProfilesResponse.Profile>> save) {
        this.account = account;
        this.localPath = localPath;
        this.cachePath = cachePath;
        this.fetch = fetch;
        this.save = save;
        this.separateBackend = separateBackend;
        this.gameThread = gameThread;
        this.disk = disk;
        this.published = Refreshable.<Published>builder("raid profiles", this::fetchPublished)
                .handBack(gameThread)
                .refreshEveryMs(FETCH_FRESHNESS_MS)
                // A failure fetched nothing, so the next ask may try again at once.
                .retryAfterMs(0)
                .emptyValue(Published.EMPTY)
                .whenFetched(this::fetched)
                .whenFailed(this::fetchFailed)
                .build();
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
     * Applies pushes so a panel left open does not go stale. Optional on the
     * backend's side: the panel still refreshes on open, on Refresh and after a save.
     */
    private void subscribeToLiveUpdates() {
        // Pushes arrive on the socket's thread.
        ConnectionManager.onRaidProfileUpdate(message -> gameThread.execute(() -> onLiveUpdate(message)));
    }

    void onLiveUpdate(ConnectionManager.RaidProfileUpdateMessage message) {
        if (message == null || message.profileJson() == null) {
            return;
        }
        if (separateBackend.getAsBoolean()) {
            // The socket belongs to the main backend, so its pushes describe that
            // backend's profiles and must not mix into ones fetched elsewhere.
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
            // A removal has a profile's shape with null fields, and storing it would
            // read as "shared an empty profile" rather than "shared none".
            removeProfile(uuid);
            return;
        }
        applyProfileUpdate(profile);
    }

    // ── Backend data ──

    /** The meta as the backend publishes it, empty until the first fetch lands. */
    public RaidCatalog catalog() {
        return published.value().catalog();
    }

    /** The profile for a roster member, matched on UUID. Prefer this to the by-name lookup. */
    public RaidTeamProfile profileFor(GuildMemberPresence member) {
        return member == null ? RaidTeamProfile.empty() : profileForUuid(member.uuid());
    }

    public RaidTeamProfile profileForUuid(String uuid) {
        String key = RaidProfilesResponse.normalizeUuid(uuid);
        return key == null ? RaidTeamProfile.empty() : published.value().profiles().getOrDefault(key, RaidTeamProfile.empty());
    }

    /**
     * The profile for a bare username, for the friends list where a name is all there
     * is. Best effort: the name index can be a rename behind.
     */
    public RaidTeamProfile profileForUsername(String username) {
        String key = normalizeKey(username);
        if (key == null) {
            return RaidTeamProfile.empty();
        }
        Published current = published.value();
        String uuid = current.uuidByUsername().get(key);
        return uuid == null ? RaidTeamProfile.empty() : current.profiles().getOrDefault(uuid, RaidTeamProfile.empty());
    }

    /** Your own profile, read back from the backend like everyone else's. */
    public RaidTeamProfile selfProfile() {
        String uuid = localUuid();
        return uuid == null ? profileForUsername(localUsername()) : profileForUuid(uuid);
    }

    /** How many members other than you have shared a profile. */
    int sharedProfileCount() {
        String self = RaidProfilesResponse.normalizeUuid(localUuid());
        return (int) published.value().profiles().entrySet().stream()
                .filter(entry -> entry.getValue().isComplete())
                .filter(entry -> self == null || !entry.getKey().equals(self))
                .count();
    }

    public boolean hasLoadedProfiles() {
        return fetchedOnce || !published.value().profiles().isEmpty();
    }

    public boolean isFetching() {
        return published.isLoading();
    }

    public String lastError() {
        return lastError;
    }

    /** Fetches only when the loaded copy is old enough to be worth replacing. Game thread only. */
    public CompletableFuture<Void> refreshIfStale() {
        published.request();
        return settled();
    }

    /**
     * Fetches the catalog and every profile, then caches the response. A failure keeps
     * whatever was already loaded rather than emptying the panel. With a fetch already
     * running, waits for that one: a push or save since it began is made again to its
     * answer, so it can't win with older data. Game thread only; completes there.
     */
    public CompletableFuture<Void> refresh() {
        published.force();
        return settled();
    }

    private CompletableFuture<Void> settled() {
        return published.settled().thenApply(state -> null);
    }

    /** Reads the backend's answer into what the store shows, keeping the last catalog when it sends none. */
    private CompletableFuture<Published> fetchPublished() {
        return fetch.get().thenApply(response -> {
            if (response == null) {
                throw new IllegalStateException("The backend sent no raid profiles.");
            }
            return publish(response);
        });
    }

    private Published publish(RaidProfilesResponse response) {
        RaidCatalog fetched = response.toCatalog();
        // A response with no catalog keeps the last one that worked, rather than
        // blanking every build chip.
        RaidCatalog catalog = fetched.isEmpty() ? published.value().catalog() : fetched;
        return new Published(catalog, response.toDomain(catalog), response.uuidByUsername(), response);
    }

    private void fetched(Published fetched) {
        fetchedOnce = true;
        lastError = null;
        if (fetched.source() != null) {
            disk.execute(() -> writeCache(fetched.source()));
        }
        SeqClient.LOGGER.info(
                "[RaidProfiles] Loaded {} builds, {} raids, {} profiles",
                fetched.catalog().builds().size(),
                fetched.catalog().raids().size(),
                fetched.profiles().size());
    }

    private void fetchFailed(Throwable failure) {
        lastError = RaidProfileApiError.describe(failure, "Could not load the guild's raid profiles.");
        SeqClient.LOGGER.warn(
                "[RaidProfiles] GET {}/raid-profiles failed: {} | shown: {}",
                BuildConfig.RAID_PROFILES_API_URL,
                RaidProfileApiError.describeForLog(failure),
                lastError);
    }

    /** Shows a whole response, as a fetch would. */
    void apply(RaidProfilesResponse response) {
        published.mutate(current -> publish(response));
    }

    /**
     * Applies one member's profile from a {@code raid_profile_update} push. Only for
     * the {@code updated} action: a removal carries nulls, which would store as empty.
     * Game thread only.
     */
    public void applyProfileUpdate(RaidProfilesResponse.Profile profile) {
        String uuid = RaidProfilesResponse.identityKey(profile);
        if (uuid == null) {
            return;
        }
        published.mutate(current -> withProfile(current, uuid, profile));
        SeqClient.LOGGER.debug("[RaidProfiles] Applied live update for {}", profile.minecraft().username());
    }

    private static Published withProfile(Published current, String uuid, RaidProfilesResponse.Profile profile) {
        RaidProfilesResponse wrapper = new RaidProfilesResponse(
                RaidProfilesResponse.CURRENT_SCHEMA_VERSION, null, List.of(profile));
        RaidTeamProfile updated = wrapper.toDomain(current.catalog()).get(uuid);
        if (updated == null) {
            return current;
        }
        Map<String, RaidTeamProfile> merged = new LinkedHashMap<>(current.profiles());
        merged.put(uuid, updated);
        RaidProfilesResponse source = current.source() == null ? null : sourceWithout(current.source(), uuid, profile);
        Map<String, String> names = current.uuidByUsername();
        String username = profile.minecraft().username();
        if (username != null && !username.isBlank()) {
            Map<String, String> renamed = new LinkedHashMap<>(names);
            renamed.put(username.trim().toLowerCase(Locale.ROOT), uuid);
            names = Map.copyOf(renamed);
        }
        return new Published(current.catalog(), Map.copyOf(merged), names, source);
    }

    /**
     * The fetched payload with {@code uuid}'s profile replaced by {@code replacement}, or
     * removed when that is null, so the cache written from it matches what is shown.
     */
    private static RaidProfilesResponse sourceWithout(
            RaidProfilesResponse source, String uuid, RaidProfilesResponse.Profile replacement) {
        List<RaidProfilesResponse.Profile> profiles = new ArrayList<>();
        if (source.profiles() != null) {
            for (RaidProfilesResponse.Profile profile : source.profiles()) {
                if (!uuid.equals(RaidProfilesResponse.identityKey(profile))) {
                    profiles.add(profile);
                }
            }
        }
        if (replacement != null) {
            profiles.add(replacement);
        }
        return new RaidProfilesResponse(source.schemaVersion(), source.catalog(), List.copyOf(profiles));
    }

    /** Drops one member's profile, matched on UUID so a rename cannot orphan it. */
    public void removeProfile(String uuid) {
        String key = RaidProfilesResponse.normalizeUuid(uuid);
        if (key == null) {
            return;
        }
        published.mutate(current -> {
            if (!current.profiles().containsKey(key)) {
                return current;
            }
            Map<String, RaidTeamProfile> merged = new LinkedHashMap<>(current.profiles());
            merged.remove(key);
            Map<String, String> names = new LinkedHashMap<>(current.uuidByUsername());
            names.values().removeIf(key::equals);
            RaidProfilesResponse source = current.source() == null ? null : sourceWithout(current.source(), key, null);
            return new Published(current.catalog(), Map.copyOf(merged), Map.copyOf(names), source);
        });
    }

    /**
     * Saves your profile and applies what the backend stored, so what you see is what
     * the guild sees, including any normalising the server did.
     */
    public CompletableFuture<Boolean> saveSelfProfile(RaidTeamProfile profile) {
        RaidTeamProfile toSave = profile == null ? RaidTeamProfile.empty() : profile;
        // Whose setup it answers: the account playing now, not whoever plays when the answer lands.
        String savedBy = accountKey();
        // The answer is handled on the game thread, where the store's state lives.
        return save.apply(toSave)
                .thenApplyAsync(saved -> {
                    forgetSetupDismissal(savedBy);
                    persist();
                    // The response is authoritative, including a username that may be a
                    // rename ahead of the token.
                    applyProfileUpdate(saved);
                    // Pull the authoritative copy in the background; the entry above keeps
                    // the panel responsive meanwhile.
                    refresh();
                    lastError = null;
                    return true;
                }, gameThread)
                .exceptionallyAsync(throwable -> {
                    lastError = RaidProfileApiError.describe(throwable, "Could not save your profile.");
                    SeqClient.LOGGER.warn(
                            "[RaidProfiles] PUT {}/raid-profiles/me failed: {} | shown: {}",
                            BuildConfig.RAID_PROFILES_API_URL,
                            RaidProfileApiError.describeForLog(throwable),
                            lastError);
                    return false;
                }, gameThread);
    }

    // ── Setup gating ──

    /** Whether setup should open instead of the list: until you have a profile, or skip it. */
    public boolean needsSetup() {
        return !selfProfile().isComplete() && !setupDismissedHere();
    }

    /** Records that this account closed setup without filling anything in. */
    public void dismissSetup() {
        String self = accountKey();
        if (self == null) {
            unclaimedSetupDismissal = true;
        } else {
            setupDismissedBy.add(self);
        }
        persist();
    }

    /** Whether the account playing skipped setup, claiming an older version's single answer if nobody has. */
    private boolean setupDismissedHere() {
        String self = accountKey();
        if (self == null) {
            return unclaimedSetupDismissal;
        }
        if (unclaimedSetupDismissal) {
            unclaimedSetupDismissal = false;
            setupDismissedBy.add(self);
            persist();
        }
        return setupDismissedBy.contains(self);
    }

    /** A saved profile answers setup for the account that saved it, so its skip no longer applies. */
    private void forgetSetupDismissal(String savedBy) {
        if (savedBy == null) {
            unclaimedSetupDismissal = false;
        } else {
            setupDismissedBy.remove(savedBy);
        }
    }

    private String accountKey() {
        return RaidProfilesResponse.normalizeUuid(account.get());
    }

    // ── Friends ──

    /** Friends, in the order they were added. */
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

    /** Your own note about a member, or null. Never leaves this client. */
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
        String value = trimmed.length() <= MAX_NOTE_LENGTH ? trimmed : trimmed.substring(0, MAX_NOTE_LENGTH);
        // The card saves on every close, and most closes change nothing.
        if (value.equals(notes.getOrDefault(key, ""))) {
            return;
        }
        if (value.isEmpty()) {
            notes.remove(key);
        } else {
            notes.put(key, value);
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
            // A corrupt file can be filled in again; refusing to open the panel cannot.
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
            if (cached != null && published.seed(publish(cached))) {
                SeqClient.LOGGER.info("[RaidProfiles] Loaded {} profiles from cache", published.value().profiles().size());
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
        unclaimedSetupDismissal = document.has("setupDismissed")
                && document.get("setupDismissed").isJsonPrimitive()
                && document.get("setupDismissed").getAsBoolean();
        setupDismissedBy.clear();
        if (document.has("setupDismissedBy") && document.get("setupDismissedBy").isJsonArray()) {
            for (JsonElement element : document.getAsJsonArray("setupDismissedBy")) {
                String uuid = element.isJsonPrimitive() ? RaidProfilesResponse.normalizeUuid(element.getAsString()) : null;
                if (uuid != null) {
                    setupDismissedBy.add(uuid);
                }
            }
        }

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
        JsonArray dismissedBy = new JsonArray();
        setupDismissedBy.forEach(dismissedBy::add);
        document.add("setupDismissedBy", dismissedBy);
        if (unclaimedSetupDismissal) {
            // Kept until an account claims it, as an older version wrote it.
            document.addProperty("setupDismissed", true);
        }

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
