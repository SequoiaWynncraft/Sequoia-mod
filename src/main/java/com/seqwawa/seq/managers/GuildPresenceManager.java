package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.integrations.WynntilsWorldStateAccess;
import com.seqwawa.seq.model.GuildMemberPresence;
import com.seqwawa.seq.model.KnownGuildMember;
import com.seqwawa.seq.model.MemberFilter;
import com.seqwawa.seq.model.MemberSort;
import com.seqwawa.seq.model.PartyFinderSpot;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.RaidCatalog;
import com.seqwawa.seq.model.RaidProfilesResponse;
import com.seqwawa.seq.model.RaidTeamProfile;
import com.seqwawa.seq.model.RaidType;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.WynncraftGuildClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * One view of who in the guild is online, where they are and whether they are
 * mid-raid.
 * <p>
 * Wynncraft's guild API supplies the roster and the world, the backend's
 * connected-user list marks who runs the mod, and {@link GuildRaidActivityTracker}
 * supplies the busy window. Only the roster needs a request, so it is what the
 * refresh throttle protects.
 */
public final class GuildPresenceManager {

    /**
     * How long a failed fetch blocks the next one. Short, because a failure fetched
     * nothing: it is Wynncraft's cache that the full interval protects, not us.
     */
    static final long RETRY_AFTER_FAILURE_MS = 10_000L;

    /** How long a first roster fetch waits for a raid catalog it does not have yet. */
    private static final long CATALOG_WAIT_SECONDS = 8L;

    private static GuildPresenceManager instance;

    private final Map<String, Boolean> sequoiaConnected = new ConcurrentHashMap<>();

    private volatile WynncraftGuildClient.GuildRoster roster = WynncraftGuildClient.GuildRoster.empty();
    private volatile String guildPrefix;
    private volatile String lastError;
    private volatile boolean refreshing;
    private volatile long lastRefreshAtMs;
    /**
     * Set when the roster was read with no catalog, so every clear count came out as
     * zero. Reading it again with one gives the right numbers, so the throttle is waived.
     */
    private volatile boolean readWithoutCatalog;
    /**
     * Bumped by {@link #reset()}, so a fetch started for the previous account cannot
     * land its guild's roster in the new session.
     */
    private volatile long generation;

    private GuildPresenceManager() {}

    public static synchronized GuildPresenceManager getInstance() {
        if (instance == null) {
            instance = new GuildPresenceManager();
        }
        return instance;
    }

    // ── State reads ──

    public boolean isRefreshing() {
        return refreshing;
    }

    public String lastError() {
        return lastError;
    }

    /** True once a refresh has completed, whether or not it found anyone online. */
    public boolean hasLoaded() {
        return lastRefreshAtMs > 0L;
    }

    /** Whether enough time has passed that another roster fetch would return new data. */
    public boolean canRefresh(long nowMs) {
        if (refreshing) {
            return false;
        }
        if (readWithoutCatalog && !RaidProfileStore.getInstance().catalog().isEmpty()) {
            return true;
        }
        return nowMs - lastRefreshAtMs >= refreshIntervalMs(lastError != null);
    }

    /** The wait before the next fetch is worth making. */
    static long refreshIntervalMs(boolean lastAttemptFailed) {
        return lastAttemptFailed
                ? RETRY_AFTER_FAILURE_MS
                : WynncraftGuildClient.MINIMUM_REFRESH_INTERVAL.toMillis();
    }

    /** The online guild members, each carrying whichever extra facts are known. */
    public List<GuildMemberPresence> onlineMembers() {
        return roster.online().stream()
                .map(member -> member.withSequoiaConnected(
                        Boolean.TRUE.equals(sequoiaConnected.get(member.key()))))
                .toList();
    }

    /**
     * The members in the order the panel draws them: narrowed by {@code filter} and
     * ordered by the column the player clicked. Takes the roster the caller already
     * read, since a frame uses it more than once and each read allocates a record per
     * member.
     */
    public List<GuildMemberPresence> membersForDisplay(
            List<GuildMemberPresence> members, MemberFilter filter, MemberSort sort, boolean descending) {
        List<GuildMemberPresence> matching = filteredMembers(members, filter);
        if (sort == null || sort == MemberSort.NAME && !descending) {
            return sortByName(matching);
        }
        return MemberSort.sort(matching, sort, descending, this::lastLogin);
    }

    /** When this member's Wynncraft session started, or null when they hide it. */
    public Instant lastLogin(GuildMemberPresence member) {
        KnownGuildMember known = member == null ? null : knownMember(member.username());
        return known == null ? null : known.lastJoin();
    }

    /**
     * The members that pass {@code filter}. "Free" means what the status column says:
     * someone sitting in a party finder listing is spoken for, like someone mid-raid.
     */
    private List<GuildMemberPresence> filteredMembers(List<GuildMemberPresence> members, MemberFilter filter) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        if (filter == null || !filter.isActive()) {
            return members;
        }
        RaidProfileStore profiles = RaidProfileStore.getInstance();
        return members.stream()
                .filter(member -> filter.matches(
                        member,
                        profiles.profileFor(member),
                        GuildRaidActivityTracker.isBusy(member.username()) || partyFinderSpotFor(member) != null,
                        profiles.catalog()))
                .toList();
    }

    /** The raid profile known for a member, empty when nobody has shared one. */
    public RaidTeamProfile profileFor(GuildMemberPresence member) {
        return RaidProfileStore.getInstance().profileFor(member);
    }

    /** Anyone in the guild by name, online or not, or null when unknown. */
    public KnownGuildMember knownMember(String username) {
        return roster.member(username);
    }

    // ── Party finder ──

    private List<PartyListing> indexedListings;
    private Map<String, PartyFinderSpot> partyFinderIndex = Map.of();

    /**
     * The party finder listing a member sits in, or null. Rebuilt only when the party
     * finder hands back a new list, and called from the render thread like its own screen.
     */
    public PartyFinderSpot partyFinderSpotFor(GuildMemberPresence member) {
        String uuid = member == null ? null : RaidProfilesResponse.normalizeUuid(member.uuid());
        if (uuid == null) {
            return null;
        }
        return partyFinderIndex().get(uuid);
    }

    private Map<String, PartyFinderSpot> partyFinderIndex() {
        PartyFinderManager manager = SeqClient.partyFinderManager;
        if (manager == null) {
            return Map.of();
        }
        List<PartyListing> listings = manager.getParties();
        if (listings != indexedListings) {
            List<PartyFinderSpot> spots = new ArrayList<>();
            for (PartyListing listing : listings) {
                spots.add(toSpot(listing));
            }
            partyFinderIndex = PartyFinderSpot.indexByMember(spots);
            indexedListings = listings;
        }
        return partyFinderIndex;
    }

    private static PartyFinderSpot toSpot(PartyListing listing) {
        List<String> raids = listing.getRaidTags().stream()
                .filter(name -> !"Unknown Activity".equals(name))
                .map(PartyListing::displayNameToBackendName)
                .toList();
        List<String> uuids = listing.members.stream()
                .filter(member -> !member.isReserved && member.playerUUID != null)
                .map(member -> member.playerUUID)
                .toList();
        return new PartyFinderSpot(listing.id, raids, listing.occupiedSlots, listing.maxSize, listing.isJoinable(), uuids);
    }

    /** Whether the local player already sits in a listing, which rules out joining one. */
    public boolean isInPartyFinderListing() {
        PartyFinderManager manager = SeqClient.partyFinderManager;
        return manager != null && manager.isInParty();
    }

    /** The listing the local player sits in, or null when they are in none. */
    public PartyFinderSpot myPartyFinderSpot() {
        PartyFinderManager manager = SeqClient.partyFinderManager;
        if (manager == null) {
            return null;
        }
        List<PartyListing> listings = manager.getParties();
        int index = manager.getJoinedPartyIndex();
        return index >= 0 && index < listings.size() ? toSpot(listings.get(index)) : null;
    }

    /** Set while a listing is being created, so two quick invites cannot open two. */
    private final java.util.concurrent.atomic.AtomicBoolean listingCreationInFlight =
            new java.util.concurrent.atomic.AtomicBoolean();

    /**
     * Opens a party finder listing for the group being put together, unless you already
     * sit in one. {@code raid} picks the activity; with none, the listing covers every
     * raid the catalog knows. The invitee is attached to it by the Wynn party sync once
     * they accept the in-game invite.
     *
     * @return the party finder's own line, or null when nothing was attempted
     */
    public CompletableFuture<String> openPartyFinderListing(RaidType raid) {
        PartyFinderManager manager = SeqClient.partyFinderManager;
        if (manager == null || !ConnectionManager.isConnected()) {
            return CompletableFuture.completedFuture(null);
        }
        long startedFor = generation;
        if (manager.isInParty()) {
            enableLinkedPartySync(manager.getCurrentListing().id(), startedFor);
            return CompletableFuture.completedFuture(null);
        }
        List<String> activities = partyFinderActivities(raid, RaidProfileStore.getInstance().catalog());
        if (activities.isEmpty() || !listingCreationInFlight.compareAndSet(false, true)) {
            return CompletableFuture.completedFuture(null);
        }
        PartyRegion region = listingRegion(currentWorld(), RaidProfileStore.getInstance().selfProfile().region());
        return manager.createPartyFromCommand(activities, region)
                .thenApply(result -> {
                    if (result.success() && result.data() != null) {
                        enableLinkedPartySync(result.data().id(), startedFor);
                    }
                    return result.message();
                })
                .exceptionally(throwable -> "Could not open a party finder listing.")
                .whenComplete((ignored, throwable) -> listingCreationInFlight.set(false));
    }

    private void enableLinkedPartySync(long listingId, long startedFor) {
        SeqClient.mc.execute(() -> {
            PartyFinderManager manager = SeqClient.getPartyFinderManager();
            WynnPartySyncManager sync = SeqClient.getWynnPartySyncManager();
            if (startedFor != generation || manager == null || sync == null
                    || manager.getCurrentListing() == null || manager.getCurrentListing().id() != listingId) {
                return;
            }
            sync.enableAutomaticSync();
            if (manager.isPartyLeader() && !sync.hasActiveParty()) {
                sync.requestCurrentPartySnapshot();
            }
        });
    }

    /**
     * The region a listing opens in: the world you are on first, since that is where the
     * group will play, then the region on your profile when the world is not a regional
     * one (a hub, a lobby), and NA as the party finder's own default.
     */
    static PartyRegion listingRegion(String currentWorld, PartyRegion profileRegion) {
        PartyRegion fromWorld = regionForWorld(currentWorld);
        if (fromWorld != null) {
            return fromWorld;
        }
        return profileRegion != null ? profileRegion : PartyRegion.NA;
    }

    /** {@code EU3} is EU, {@code NA12} is NA, {@code AS2} is AS; anything else is null. */
    static PartyRegion regionForWorld(String world) {
        if (world == null) {
            return null;
        }
        String trimmed = world.trim().toUpperCase(Locale.ROOT);
        for (PartyRegion region : PartyRegion.values()) {
            String prefix = region.name();
            if (trimmed.length() > prefix.length()
                    && trimmed.startsWith(prefix)
                    && trimmed.substring(prefix.length()).chars().allMatch(Character::isDigit)) {
                return region;
            }
        }
        return null;
    }

    /** The activities a listing for {@code raid} covers: that raid, or every raid when none. */
    static List<String> partyFinderActivities(RaidType raid, RaidCatalog catalog) {
        if (raid != null) {
            return List.of(partyFinderActivityFor(raid));
        }
        if (catalog == null) {
            return List.of();
        }
        return catalog.raids().stream()
                .map(GuildPresenceManager::partyFinderActivityFor)
                .distinct()
                .toList();
    }

    /**
     * The name the party finder knows a raid by. The catalog and the party finder mostly
     * share codes, but not always: the catalog's WTP is the party finder's TWP, which is
     * why the raid's full name is the fallback.
     */
    static String partyFinderActivityFor(RaidType raid) {
        String key = raid.key();
        if (!PartyListing.backendNameToDisplayName(key).equalsIgnoreCase(key)) {
            return key;
        }
        String fromName = PartyListing.displayNameToBackendName(raid.apiName());
        if (fromName != null && !fromName.equalsIgnoreCase(raid.apiName())) {
            return fromName;
        }
        return raid.shortName();
    }

    /** Loads listings for a session where the party finder screen was never opened. */
    public void refreshPartyFinder() {
        PartyFinderManager manager = SeqClient.partyFinderManager;
        if (manager == null || !ConnectionManager.isConnected()) {
            return;
        }
        try {
            manager.refreshListingsQuietly();
        } catch (RuntimeException e) {
            SeqClient.LOGGER.debug("[GuildPresence] Could not refresh party finder listings", e);
        }
    }

    /** Joins a member's listing as DPS, the party finder's default, and reports its message. */
    public CompletableFuture<String> joinPartyFinder(PartyFinderSpot spot) {
        PartyFinderManager manager = SeqClient.partyFinderManager;
        if (manager == null || spot == null) {
            return CompletableFuture.completedFuture("The party finder is not ready yet.");
        }
        return manager.joinPartyFromCommand(spot.listingId(), PartyRole.DPS)
                .thenApply(result -> result.message() == null || result.message().isBlank()
                        ? (result.success() ? "Joined the party." : "Could not join that party.")
                        : result.message())
                .exceptionally(throwable -> "Could not join that party.");
    }

    public String currentWorld() {
        return WynntilsWorldStateAccess.currentWorldName().orElse(null);
    }

    public String localUsername() {
        if (SeqClient.mc == null) {
            return null;
        }
        if (SeqClient.mc.getUser() != null) {
            return SeqClient.mc.getUser().getName();
        }
        return SeqClient.mc.player != null ? SeqClient.mc.player.getName().getString() : null;
    }

    // ── Refresh ──

    /** Fetches the roster when the throttle allows it, or immediately when forced. */
    public CompletableFuture<Void> refresh(boolean force) {
        long now = System.currentTimeMillis();
        if (refreshing || (!force && !canRefresh(now))) {
            return CompletableFuture.completedFuture(null);
        }
        refreshing = true;
        lastError = null;
        long startedFor = generation;

        requestSequoiaConnectedUsers();
        // Clear counts are keyed by the raid names the catalog carries, so it has to be
        // in hand before the roster is parsed. With one cached there is nothing to wait
        // for; without one the wait is bounded so a slow sign-in cannot keep the list empty.
        RaidProfileStore profileStore = RaidProfileStore.getInstance();
        boolean needsCatalog = profileStore.catalog().isEmpty();
        CompletableFuture<Void> refreshed = profileStore.refresh();
        CompletableFuture<Void> catalogReady = needsCatalog
                // copy(), so timing out here does not complete the shared in-flight refresh
                ? refreshed.copy().completeOnTimeout(null, CATALOG_WAIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS)
                : CompletableFuture.completedFuture(null);

        return resolveGuildPrefix(startedFor)
                .thenCombine(catalogReady, (prefix, ignored) -> prefix)
                .thenCompose(prefix -> {
                    if (prefix == null || prefix.isBlank()) {
                        throw new IllegalStateException("Wynncraft does not list you in a guild.");
                    }
                    return WynncraftGuildClient.getInstance()
                            .fetchRoster(prefix, RaidProfileStore.getInstance().catalog());
                })
                .thenAccept(fetched -> {
                    if (startedFor != generation) {
                        return;
                    }
                    roster = fetched;
                    readWithoutCatalog = RaidProfileStore.getInstance().catalog().isEmpty();
                    lastRefreshAtMs = System.currentTimeMillis();
                    SeqClient.LOGGER.info(
                            "[GuildPresence] Roster refreshed guild='{}' online={} total={}",
                            fetched.displayName(),
                            fetched.online().size(),
                            fetched.totalMembers());
                })
                .exceptionally(throwable -> {
                    if (startedFor != generation) {
                        return null;
                    }
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    lastError = cause.getMessage() == null ? "Could not reach Wynncraft." : cause.getMessage();
                    // A failed refresh still counts as an attempt, otherwise a broken
                    // connection would be retried on every frame the panel is open.
                    lastRefreshAtMs = System.currentTimeMillis();
                    SeqClient.LOGGER.warn("[GuildPresence] Roster refresh failed: {}", lastError);
                    return null;
                })
                .whenComplete((ignored, throwable) -> {
                    if (startedFor == generation) {
                        refreshing = false;
                    }
                });
    }

    private CompletableFuture<String> resolveGuildPrefix(long startedFor) {
        if (guildPrefix != null && !guildPrefix.isBlank()) {
            return CompletableFuture.completedFuture(guildPrefix);
        }
        String username = localUsername();
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return WynncraftGuildClient.getInstance().resolveGuildPrefix(username).thenApply(prefix -> {
            if (startedFor == generation) {
                guildPrefix = prefix;
            }
            return prefix;
        });
    }

    /** Best effort, and off the refresh's critical path: a missing badge beats a panel that fails. */
    private void requestSequoiaConnectedUsers() {
        if (!ConnectionManager.isConnected()) {
            sequoiaConnected.clear();
            return;
        }
        try {
            ConnectionManager.getInstance().requestConnectedUsers(this::applyConnectedUsers);
        } catch (RuntimeException e) {
            SeqClient.LOGGER.debug("[GuildPresence] Could not request connected users", e);
        }
    }

    /** Whether this member's client is currently connected to the Sequoia backend. */
    boolean isSequoiaConnected(String username) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return Boolean.TRUE.equals(sequoiaConnected.get(username.trim().toLowerCase(Locale.ROOT)));
    }

    void applyConnectedUsers(Collection<String> usernames) {
        sequoiaConnected.clear();
        if (usernames == null) {
            return;
        }
        for (String username : usernames) {
            if (username != null && !username.isBlank()) {
                sequoiaConnected.put(username.trim().toLowerCase(Locale.ROOT), Boolean.TRUE);
            }
        }
    }

    /** Clears cached state so a fresh session does not show the previous one's roster. */
    public void reset() {
        generation++;
        roster = WynncraftGuildClient.GuildRoster.empty();
        sequoiaConnected.clear();
        guildPrefix = null;
        lastError = null;
        lastRefreshAtMs = 0L;
        refreshing = false;
        readWithoutCatalog = false;
    }

    // ── Actions ──

    /**
     * Invites a member, creating the party first when there is not one. The two
     * commands cannot go back to back: Wynncraft has to acknowledge the party first.
     */
    public InviteOutcome inviteToParty(String username) {
        InviteAction action = decideInviteAction(
                username, localUsername(), hasActiveWynnParty(), observedPartyMembers());

        return switch (action) {
            case SELF -> new InviteOutcome(false, "You cannot invite yourself.");
            case ALREADY_IN_PARTY -> new InviteOutcome(false, username + " is already in your party.");
            case INVITE -> {
                sendPartyCommand("party " + username);
                yield new InviteOutcome(true, "Invited " + username + " to your party.");
            }
            case CREATE_THEN_INVITE -> {
                sendPartyCommand("party create");
                scheduleDelayed(() -> sendPartyCommand("party " + username), PARTY_CREATE_SETTLE_MS);
                yield new InviteOutcome(true, "Created a party and invited " + username + ".");
            }
        };
    }

    /** Invites a whole composition, spacing the commands so Wynncraft does not drop them. */
    public InviteOutcome inviteAllToParty(List<String> usernames) {
        List<String> targets = inviteTargets(usernames, localUsername(), observedPartyMembers());
        if (targets.isEmpty()) {
            return new InviteOutcome(false, "Nobody left to invite, they are already with you.");
        }

        boolean needsParty = !hasActiveWynnParty();
        long delay = 0L;
        if (needsParty) {
            sendPartyCommand("party create");
            delay = PARTY_CREATE_SETTLE_MS;
        }

        for (String target : targets) {
            String command = "party " + target;
            if (delay == 0L) {
                sendPartyCommand(command);
            } else {
                scheduleDelayed(() -> sendPartyCommand(command), delay);
            }
            delay += INVITE_SPACING_MS;
        }

        String who = targets.size() == 1 ? targets.get(0) : targets.size() + " members";
        return new InviteOutcome(
                true, needsParty ? "Created a party and invited " + who + "." : "Invited " + who + ".");
    }

    /** Sends {@code /msg username message}. */
    public boolean whisper(String username, String message) {
        if (username == null || message == null || message.isBlank()) {
            return false;
        }
        String target = username.trim();
        if (!target.matches("[A-Za-z0-9_]{3,16}")) {
            return false;
        }
        sendPartyCommand("msg " + target + " " + message.trim());
        return true;
    }

    /** Wynncraft drops commands sent in the same tick, so bulk invites are spaced out. */
    private static final long INVITE_SPACING_MS = 500L;

    /** Wynncraft needs a beat between creating a party and accepting an invite into it. */
    private static final long PARTY_CREATE_SETTLE_MS = 400L;

    private void sendPartyCommand(String command) {
        if (SeqClient.mc == null) {
            return;
        }
        SeqClient.mc.execute(() -> {
            if (SeqClient.mc.player != null && SeqClient.mc.player.connection != null) {
                SeqClient.mc.player.connection.sendCommand(command);
            }
        });
    }

    private void scheduleDelayed(Runnable runnable, long delayMs) {
        CompletableFuture.delayedExecutor(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .execute(runnable);
    }

    private boolean hasActiveWynnParty() {
        WynnPartySyncManager manager = SeqClient.wynnPartySyncManager;
        return manager != null && manager.hasActiveParty();
    }

    private List<String> observedPartyMembers() {
        WynnPartySyncManager manager = SeqClient.wynnPartySyncManager;
        return manager == null ? List.of() : manager.getObservedMemberUsernames();
    }

    // ── Pure logic ──

    /** Sorts members by name, ignoring case. */
    static List<GuildMemberPresence> sortByName(List<GuildMemberPresence> members) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream()
                .sorted(Comparator.comparing(member -> member.username().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /** Who still needs an invite: everyone but you and the party, duplicates collapsed. */
    static List<String> inviteTargets(
            List<String> usernames, String localUsername, Collection<String> partyMembers) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }

        Set<String> skip = new java.util.HashSet<>();
        if (localUsername != null && !localUsername.isBlank()) {
            skip.add(localUsername.trim().toLowerCase(Locale.ROOT));
        }
        if (partyMembers != null) {
            partyMembers.stream()
                    .filter(name -> name != null && !name.isBlank())
                    .map(name -> name.trim().toLowerCase(Locale.ROOT))
                    .forEach(skip::add);
        }

        List<String> targets = new ArrayList<>();
        for (String username : usernames) {
            if (username == null || username.isBlank()) {
                continue;
            }
            String trimmed = username.trim();
            if (skip.add(trimmed.toLowerCase(Locale.ROOT))) {
                targets.add(trimmed);
            }
        }
        return List.copyOf(targets);
    }

    /** What inviting {@code target} should actually do, given the local party state. */
    static InviteAction decideInviteAction(
            String target, String localUsername, boolean hasActiveParty, Collection<String> partyMembers) {
        if (target == null || target.isBlank()) {
            return InviteAction.SELF;
        }
        String normalizedTarget = target.trim();
        if (localUsername != null && normalizedTarget.equalsIgnoreCase(localUsername.trim())) {
            return InviteAction.SELF;
        }

        Set<String> members = partyMembers == null
                ? Set.of()
                : partyMembers.stream()
                        .filter(name -> name != null && !name.isBlank())
                        .map(name -> name.trim().toLowerCase(Locale.ROOT))
                        .collect(Collectors.toSet());

        if (members.contains(normalizedTarget.toLowerCase(Locale.ROOT))) {
            return InviteAction.ALREADY_IN_PARTY;
        }

        // A party that exists but currently holds only the local player still counts:
        // Wynncraft rejects "party create" when one is already open.
        return hasActiveParty ? InviteAction.INVITE : InviteAction.CREATE_THEN_INVITE;
    }

    public enum InviteAction {
        SELF,
        ALREADY_IN_PARTY,
        INVITE,
        CREATE_THEN_INVITE
    }

    /** Whether an invite was actually sent, and the line to show the player. */
    public record InviteOutcome(boolean sent, String message) {}
}
