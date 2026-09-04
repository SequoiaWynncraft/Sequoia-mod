package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.integrations.WynntilsWorldStateAccess;
import com.seqwawa.seq.model.GuildMemberPresence;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.WynncraftGuildClient;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Keeps one view of who in the guild is online, where they are, and whether they
 * are mid-raid, assembled from three sources that each know a different part.
 * <p>
 * Wynncraft's guild API supplies the roster and the world; the Sequoia backend's
 * connected-user list marks who is running the mod; {@link GuildRaidActivityTracker}
 * supplies the busy window. Only the first needs a network round trip, so the
 * roster is what refresh throttling protects — the other two are read live.
 */
public final class GuildPresenceManager {

    /** Worlds are grouped under this heading when Wynncraft reports no server. */
    public static final String UNKNOWN_WORLD = "Unknown";

    private static GuildPresenceManager instance;

    private final Map<String, Boolean> sequoiaConnected = new ConcurrentHashMap<>();

    private volatile WynncraftGuildClient.GuildRoster roster = WynncraftGuildClient.GuildRoster.empty();
    private volatile String guildPrefix;
    private volatile String lastError;
    private volatile boolean refreshing;
    private volatile long lastRefreshAtMs;

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

    public long lastRefreshAtMs() {
        return lastRefreshAtMs;
    }

    public String guildDisplayName() {
        return roster.displayName();
    }

    /** True once a refresh has completed, whether or not it found anyone online. */
    public boolean hasLoaded() {
        return lastRefreshAtMs > 0L;
    }

    /** Whether enough time has passed that another roster fetch would return new data. */
    public boolean canRefresh(long nowMs) {
        return !refreshing && nowMs - lastRefreshAtMs >= WynncraftGuildClient.MINIMUM_REFRESH_INTERVAL.toMillis();
    }

    /** The online guild members, each carrying whichever extra facts are known. */
    public List<GuildMemberPresence> onlineMembers() {
        return roster.online().stream()
                .map(member -> member.withSequoiaConnected(
                        Boolean.TRUE.equals(sequoiaConnected.get(member.key()))))
                .toList();
    }

    /** The online members grouped by world, in the order the panel should draw them. */
    public List<WorldGroup> groupedByWorld() {
        return groupByWorld(onlineMembers(), currentWorld(), GuildRaidActivityTracker::isBusy);
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

        requestSequoiaConnectedUsers();

        return resolveGuildPrefix()
                .thenCompose(prefix -> {
                    if (prefix == null || prefix.isBlank()) {
                        throw new IllegalStateException("Wynncraft does not list you in a guild.");
                    }
                    return WynncraftGuildClient.getInstance().fetchRoster(prefix);
                })
                .thenAccept(fetched -> {
                    roster = fetched;
                    lastRefreshAtMs = System.currentTimeMillis();
                    SeqClient.LOGGER.info(
                            "[GuildPresence] Roster refreshed guild='{}' online={} total={}",
                            fetched.displayName(),
                            fetched.online().size(),
                            fetched.totalMembers());
                })
                .exceptionally(throwable -> {
                    Throwable cause = throwable.getCause() != null ? throwable.getCause() : throwable;
                    lastError = cause.getMessage() == null ? "Could not reach Wynncraft." : cause.getMessage();
                    // A failed refresh still counts as an attempt, otherwise a broken
                    // connection would be retried on every frame the panel is open.
                    lastRefreshAtMs = System.currentTimeMillis();
                    SeqClient.LOGGER.warn("[GuildPresence] Roster refresh failed: {}", lastError);
                    return null;
                })
                .whenComplete((ignored, throwable) -> refreshing = false);
    }

    private CompletableFuture<String> resolveGuildPrefix() {
        if (guildPrefix != null && !guildPrefix.isBlank()) {
            return CompletableFuture.completedFuture(guildPrefix);
        }
        String username = localUsername();
        if (username == null || username.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }
        return WynncraftGuildClient.getInstance().resolveGuildPrefix(username).thenApply(prefix -> {
            guildPrefix = prefix;
            return prefix;
        });
    }

    /**
     * Marks which members are running Sequoia. This is best effort and deliberately
     * off the refresh's critical path: the roster is what the panel is for, and a
     * missing badge is a smaller loss than a panel that fails to load.
     */
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
        roster = WynncraftGuildClient.GuildRoster.empty();
        sequoiaConnected.clear();
        guildPrefix = null;
        lastError = null;
        lastRefreshAtMs = 0L;
        refreshing = false;
    }

    // ── Actions ──

    /** Switches to the world a member is on. */
    public void switchToWorld(String world) {
        if (world == null || world.isBlank()) {
            return;
        }
        if (SeqClient.mc == null || SeqClient.mc.player == null || SeqClient.mc.player.connection == null) {
            return;
        }
        SeqClient.mc.player.connection.sendCommand("switch " + world.trim());
    }

    /**
     * Invites a member to the local player's Wynncraft party, creating the party
     * first when there is not one yet.
     * <p>
     * The two commands cannot be sent back to back: Wynncraft has to acknowledge the
     * party before it will accept an invite into it, so the invite is delayed by one
     * short beat, the same way the party finder paces its bulk invites.
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

    /**
     * Orders worlds so the panel answers "where is the guild right now" at a glance:
     * the world you are already on first, then the busiest worlds, then the rest by
     * name. A group with no world sinks to the bottom — it is the least actionable,
     * since there is nothing to switch to.
     */
    static List<WorldGroup> groupByWorld(
            List<GuildMemberPresence> members, String localWorld, Predicate<String> busy) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }

        Map<String, List<GuildMemberPresence>> byWorld = new LinkedHashMap<>();
        for (GuildMemberPresence member : members) {
            byWorld.computeIfAbsent(member.hasWorld() ? member.world() : UNKNOWN_WORLD, key -> new ArrayList<>())
                    .add(member);
        }

        String normalizedLocalWorld = localWorld == null ? null : localWorld.trim().toUpperCase(Locale.ROOT);
        Predicate<String> busyCheck = busy == null ? name -> false : busy;

        List<WorldGroup> groups = new ArrayList<>(byWorld.size());
        for (Map.Entry<String, List<GuildMemberPresence>> entry : byWorld.entrySet()) {
            List<GuildMemberPresence> sorted = entry.getValue().stream()
                    // Available members come first: the panel exists to find someone to
                    // pull into a group, and a busy member is not that.
                    .sorted(Comparator.comparing((GuildMemberPresence member) -> busyCheck.test(member.username()))
                            .thenComparing(member -> member.username().toLowerCase(Locale.ROOT)))
                    .toList();
            groups.add(new WorldGroup(entry.getKey(), sorted));
        }

        groups.sort(Comparator.comparing((WorldGroup group) -> !group.world().equals(normalizedLocalWorld))
                .thenComparing(group -> group.world().equals(UNKNOWN_WORLD))
                .thenComparing(Comparator.comparingInt((WorldGroup group) -> group.members().size()).reversed())
                .thenComparing(WorldGroup::world));

        return List.copyOf(groups);
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

    /** One world's worth of online guild members. */
    public record WorldGroup(String world, List<GuildMemberPresence> members) {
        public WorldGroup {
            members = members == null ? List.of() : List.copyOf(members);
        }

        public boolean hasSwitchTarget() {
            return !UNKNOWN_WORLD.equals(world);
        }
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
