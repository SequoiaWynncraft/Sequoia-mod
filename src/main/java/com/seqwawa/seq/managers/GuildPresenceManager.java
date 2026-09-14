package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.integrations.WynntilsWorldStateAccess;
import com.seqwawa.seq.model.GuildMemberPresence;
import com.seqwawa.seq.model.MemberFilter;
import com.seqwawa.seq.model.RaidTeamProfile;
import com.seqwawa.seq.network.ConnectionManager;
import com.seqwawa.seq.network.WynncraftGuildClient;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Comparator;
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
 * roster is what refresh throttling protects, since the other two are read live.
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

    /** The online members in the order the panel draws them, narrowed by {@code filter}. */
    public List<GuildMemberPresence> membersForDisplay(MemberFilter filter) {
        return sortByName(filteredMembers(filter));
    }

    /** The online members that pass {@code filter}, ungrouped. */
    public List<GuildMemberPresence> filteredMembers(MemberFilter filter) {
        List<GuildMemberPresence> members = onlineMembers();
        if (filter == null || !filter.isActive()) {
            return members;
        }
        RaidProfileStore profiles = RaidProfileStore.getInstance();
        return members.stream()
                .filter(member -> filter.matches(
                        member,
                        profiles.profileFor(member),
                        GuildRaidActivityTracker.isBusy(member.username()),
                        profiles.catalog()))
                .toList();
    }

    /** The raid profile known for a member, empty when nobody has shared one. */
    public RaidTeamProfile profileFor(GuildMemberPresence member) {
        return RaidProfileStore.getInstance().profileFor(member);
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
        // The catalog has to be in hand before the roster is parsed, because reading
        // clear counts needs the raid names it carries.
        RaidProfileStore.getInstance().refresh();

        return resolveGuildPrefix()
                .thenCompose(prefix -> {
                    if (prefix == null || prefix.isBlank()) {
                        throw new IllegalStateException("Wynncraft does not list you in a guild.");
                    }
                    return WynncraftGuildClient.getInstance()
                            .fetchRoster(prefix, RaidProfileStore.getInstance().catalog());
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

    /**
     * Invites a whole composition, creating the party first when there is not one.
     * <p>
     * Invites are spaced the same way the party finder spaces its bulk invites:
     * Wynncraft drops commands sent in the same tick, so a burst of four would
     * arrive as one or two.
     */
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

    /** Sends {@code /msg username message}, the way the party finder pings people. */
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

    /**
     * Sorts members by name, ignoring case.
     * <p>
     * The list used to be grouped by world, which buried people: to find one person
     * you had to know where they were first. A flat A to Z list with the world in
     * its own column answers both questions, and the position of a name stops
     * moving every time someone switches server.
     */
    static List<GuildMemberPresence> sortByName(List<GuildMemberPresence> members) {
        if (members == null || members.isEmpty()) {
            return List.of();
        }
        return members.stream()
                .sorted(Comparator.comparing(member -> member.username().toLowerCase(Locale.ROOT)))
                .toList();
    }

    /**
     * Who out of {@code usernames} still needs an invite: everyone but the local
     * player and whoever is already in the party, with duplicates collapsed.
     */
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
