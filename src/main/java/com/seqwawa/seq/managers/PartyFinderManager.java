package com.seqwawa.seq.managers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializer;
import com.seqwawa.seq.client.ClientThread;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.client.TickQueue;
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.CreateInviteResponse;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.PartyStatus;
import com.seqwawa.seq.model.ReservedSlot;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.network.ConnectionManager;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * One session's party finder: the listings and the party the player sees, kept up to date by the
 * backend's socket, and the banner the screen shows. What the player does with it is split by
 * where it goes: {@link #actions()} to the backend, {@link #game()} to the game, and the notices
 * it shows in chat. Each is made by this one and ends with it.
 *
 * <p>Every action reads a {@link Ticket} where it starts, before it waits on anything, and checks
 * it again before it changes anything: a request never goes out with the next account's token, and
 * a command never reaches the account, or the server connection, after the one it was for.
 */
public class PartyFinderManager {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    @Getter
    private final List<Activity> activities = new CopyOnWriteArrayList<>();

    @Getter
    private final List<Listing> listings = new CopyOnWriteArrayList<>();

    private final Object listingsLock = new Object();

    /** The listing the local player is currently a member of, or null. */
    @Getter
    private volatile Listing currentListing;

    /** Whether each card is open, kept across reloads. */
    private final Map<Long, Boolean> expandedStates = new ConcurrentHashMap<>();
    /** Invite tokens issued by this client, retained so leaders can revoke them. */
    private final Map<IssuedInviteKey, IssuedInvite> issuedInvites = new ConcurrentHashMap<>();
    /** The cards, rebuilt when the listings change. */
    private List<PartyListing> cachedParties;
    private int cachedVersion = -1;
    private volatile int listingsVersion;
    /** Moves on at each {@link #reset()}: the session's end. A {@link Ticket} remembers it. */
    private volatile long resetGeneration;
    private volatile String latestPartyError;

    /** Where what the player sees or sends is handed to the game thread; a test passes its own queue. */
    final TickQueue gameThread;
    /** The backend client carrying this session's token, and no other. */
    final ApiClient api;
    final PartyFinderPlayer player;
    private final PartyFinderActions actions;
    private final PartyGameCommands game;
    final PartyFinderNotices notices;

    public record CommandResult<T>(boolean success, String message, T data) {
        public static <T> CommandResult<T> success(String message, T data) {
            return new CommandResult<>(true, message, data);
        }

        public static <T> CommandResult<T> failure(String message) {
            return new CommandResult<>(false, message, null);
        }
    }

    /** What an action began under: this party finder's session, and the server connection the player was on. */
    record Ticket(long generation, Object connection) {}

    record IssuedInviteKey(long listingId, String normalizedUsername) {}

    record IssuedInvite(UUID targetUUID, String username, String inviteToken) {}

    /** The party finder of the session whose backend client is {@code api}. */
    public PartyFinderManager(ApiClient api) {
        this(api, ClientThread.queue(), new PartyFinderPlayer());
    }

    PartyFinderManager(ApiClient api, TickQueue gameThread, PartyFinderPlayer player) {
        this.api = api;
        this.gameThread = gameThread;
        this.player = player;
        this.actions = new PartyFinderActions(this);
        this.game = new PartyGameCommands(this);
        this.notices = new PartyFinderNotices(this);
    }

    /** What the player asks of the backend: create, join, invite and the rest, from a command or the screen. */
    public PartyFinderActions actions() {
        return actions;
    }

    /** The party commands the party finder sends in the game. */
    public PartyGameCommands game() {
        return game;
    }

    /** Hands the backend's party finder messages to the current session's party finder, from now on. */
    public static void listen() {
        ConnectionManager.listen(ConnectionManager.Inbound.PARTY_FINDER_UPDATE, update -> SeqClient.getPartyFinderManager()
                .handlePartyFinderUpdate(update.action(), GSON.fromJson(update.listingJson(), Listing.class)));
        ConnectionManager.listen(ConnectionManager.Inbound.PARTY_FINDER_INVITE, invite -> SeqClient.getPartyFinderManager()
                .handlePartyFinderInvite(invite.listingId(), invite.inviterUUID(), invite.inviteToken(),
                        invite.listingJson() == null ? null : GSON.fromJson(invite.listingJson(), Listing.class)));
        ConnectionManager.listen(ConnectionManager.Inbound.PARTY_FINDER_STALE_WARNING, warning -> SeqClient
                .getPartyFinderManager().notices.staleWarning(
                        warning.reason(), warning.listingId(), warning.disbandAt(), warning.minutesRemaining()));
    }

    public void tickOpenPartyAnnouncements() {
        notices.tickOpenPartyReminders();
    }

    // ══════════════════════════════════════════════════════════════
    // The session guard
    // ══════════════════════════════════════════════════════════════

    /** The session and the server connection now, read where an action starts. */
    Ticket ticket() {
        return new Ticket(resetGeneration, player.connection());
    }

    /** Whether {@code ticket}'s session is still this party finder's. */
    boolean holds(Ticket ticket) {
        return ticket.generation() == resetGeneration;
    }

    /**
     * Runs {@code action} on the game thread, unless this party finder's session has ended, or the
     * player has moved to another server connection, since {@code ticket} was read.
     */
    void forSamePlayer(Ticket ticket, Runnable action) {
        gameThread.execute(() -> {
            if (holds(ticket) && ticket.connection() == player.connection()) {
                action.run();
            }
        });
    }

    /**
     * {@code call}, sent with this session's backend client, unless the session ended since
     * {@code ticket} was read; then, and for a reply that arrives after it ends, it fails with
     * {@link SupersededByReset}. A reply for the previous account must not restore its party,
     * listings or invites.
     */
    <T> CompletableFuture<T> send(Ticket ticket, Function<ApiClient, CompletableFuture<T>> call) {
        if (!holds(ticket)) {
            return CompletableFuture.failedFuture(new SupersededByReset());
        }
        return call.apply(api).thenApply(result -> {
            if (!holds(ticket)) {
                throw new SupersededByReset();
            }
            return result;
        });
    }

    /** A request whose session ended before it went out, or before its reply came; it is dropped without a word. */
    static final class SupersededByReset extends CancellationException {
        SupersededByReset() {
            super("The party finder's session ended while this request was in flight");
        }
    }

    static boolean isSupersededByReset(Throwable throwable) {
        for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
            if (cause instanceof SupersededByReset) {
                return true;
            }
            if (cause.getCause() == cause) {
                break;
            }
        }
        return false;
    }

    /**
     * Forgets what described this session: its party, the invites it issued and the cards, so
     * nothing (such as the class updates the client tick sends while in a party) acts on them for
     * whoever signs in next, and what it started but has not finished does nothing.
     */
    public void reset() {
        resetGeneration++;
        synchronized (listingsLock) {
            listings.clear();
        }
        currentListing = null;
        issuedInvites.clear();
        expandedStates.clear();
        cachedParties = null;
        cachedVersion = -1;
        listingsVersion++;
        latestPartyError = null;
        notices.reset();
    }

    // ══════════════════════════════════════════════════════════════
    // What the player sees
    // ══════════════════════════════════════════════════════════════

    public boolean isInParty() {
        return currentListing != null;
    }

    public boolean isPartyLeader() {
        Listing current = currentListing;
        return current != null && current.isLedBy(player.uuid());
    }

    /**
     * The cards over the current listings, the same list until they change, with each card
     * open or closed as the player left it. Called every frame by the screen.
     */
    public List<PartyListing> getParties() {
        if (cachedParties == null || cachedVersion != listingsVersion) {
            if (cachedParties != null) {
                for (PartyListing party : cachedParties) {
                    expandedStates.put(party.id, party.expanded);
                }
            }
            cachedParties = listings.stream()
                    .map(listing -> {
                        PartyListing party = new PartyListing(listing);
                        party.expanded = expandedStates.getOrDefault(listing.id(), false);
                        return party;
                    })
                    .collect(Collectors.toCollection(ArrayList::new));
            cachedVersion = listingsVersion;
        }
        return cachedParties;
    }

    /** Index of the listing the local player has joined, or -1. */
    public int getJoinedPartyIndex() {
        Listing current = currentListing;
        if (current == null) return -1;
        List<PartyListing> parties = getParties();
        for (int i = 0; i < parties.size(); i++) {
            if (parties.get(i).id == current.id()) return i;
        }
        return -1;
    }

    /** Shows {@code message} on the screen's banner. A request dropped with its session has none. */
    public void pushUiError(String message) {
        if (message != null && !message.isBlank()) {
            latestPartyError = message;
        }
    }

    public String consumeLatestPartyError() {
        String message = latestPartyError;
        latestPartyError = null;
        return message;
    }

    // ══════════════════════════════════════════════════════════════
    // The backend's socket
    // ══════════════════════════════════════════════════════════════

    public void handlePartyFinderUpdate(String action, Listing listing) {
        if (listing == null) {
            SeqClient.LOGGER.warn("[PartyFinderWS] Ignoring party update action={} because listing is null", action);
            return;
        }

        String myUUID = player.uuid();
        Listing previousListing = findListingById(listing.id());
        SeqClient.LOGGER.info(
                "[PartyFinderWS] handlePartyFinderUpdate action={} listingId={} myUUID={} containsMe={} reservedForMe={} reservedSlotCount={}",
                action,
                listing.id(),
                myUUID,
                listing.includes(myUUID),
                listing.reservesSlotFor(myUUID),
                listing.reservedSlotCount());

        switch (action) {
            case "CREATED" -> {
                upsertListing(listing, true);
                listingsVersion++;
            }
            case "UPDATED" -> {
                upsertListing(listing, false);
                pruneIssuedInvites(listing);
                listingsVersion++;
            }
            case "DISBANDED" -> applyDisbanded(listing.id());
            default -> {}
        }
        refreshCurrentListing();
        notices.partyUpdated(action, previousListing, listing, myUUID);
    }

    public void handlePartyFinderInvite(long listingId, String inviterUUID, String inviteToken, Listing listing) {
        SeqClient.LOGGER.info(
                "[PartyFinderWS] handlePartyFinderInvite listingId={} inviterUUID={} tokenPresent={} hasListing={}",
                listingId,
                inviterUUID,
                inviteToken != null && !inviteToken.isBlank(),
                listing != null);
        if (listing != null) {
            upsertListing(listing, true);
            listingsVersion++;
            refreshCurrentListing();
        }
        notices.invited(listingId, inviterUUID, inviteToken);
    }

    // ══════════════════════════════════════════════════════════════
    // Changes the backend confirmed
    // ══════════════════════════════════════════════════════════════

    /** Takes {@code fetched} as every listing there is, and returns them, once each. */
    List<Listing> replaceListings(List<Listing> fetched) {
        LinkedHashMap<Long, Listing> unique = new LinkedHashMap<>();
        for (Listing listing : fetched) {
            unique.putIfAbsent(listing.id(), listing);
        }
        List<Listing> deduped = List.copyOf(unique.values());
        synchronized (listingsLock) {
            listings.clear();
            listings.addAll(deduped);
        }
        refreshCurrentListing();
        listingsVersion++;
        return deduped;
    }

    void replaceActivities(List<Activity> fetched) {
        activities.clear();
        activities.addAll(fetched);
    }

    /** The player created or joined {@code listing}. */
    void applyJoined(Listing listing) {
        upsertListing(listing, false);
        boolean shouldPublishClassUpdate = false;
        if (currentListing == null || currentListing.id() == listing.id()) {
            currentListing = listing;
            shouldPublishClassUpdate = true;
        }
        listingsVersion++;
        if (shouldPublishClassUpdate && ConnectionManager.isConnected()) {
            ConnectionManager.getInstance().sendLocalPartyClassUpdate();
        }
    }

    void applyUpdated(Listing listing) {
        upsertListing(listing, false);
        if (currentListing != null && currentListing.id() == listing.id()) {
            currentListing = listing;
        }
        pruneIssuedInvites(listing);
        listingsVersion++;
    }

    void applyLeft(Listing listing) {
        upsertListing(listing, false);
        if (currentListing != null && currentListing.id() == listing.id()) {
            currentListing = null;
        }
        issuedInvites.keySet().removeIf(key -> key.listingId() == listing.id());
        listingsVersion++;
    }

    void applyDisbanded(long listingId) {
        forgetListing(listingId);
        listingsVersion++;
    }

    void applyCreatedInvite(CreateInviteResponse response, long listingId, UUID targetUUID, String targetUsername) {
        if (response == null || response.listing() == null) {
            throw new IllegalStateException("Party invite response did not include the updated listing.");
        }
        if (response.inviteToken() == null || response.inviteToken().isBlank()) {
            throw new IllegalStateException("Party invite response did not include an invite token.");
        }
        if (response.listing().id() != listingId) {
            throw new IllegalStateException("Party invite response referenced an unexpected listing.");
        }

        applyUpdated(response.listing());
        issuedInvites.put(
                issuedInviteKey(listingId, targetUsername),
                new IssuedInvite(targetUUID, targetUsername.trim(), response.inviteToken()));
    }

    IssuedInvite issuedInvite(long listingId, String username) {
        return issuedInvites.get(issuedInviteKey(listingId, username));
    }

    void forgetIssuedInvite(long listingId, IssuedInvite invite) {
        issuedInvites.remove(issuedInviteKey(listingId, invite.username()), invite);
    }

    boolean hasIssuedInvite(long listingId, String username) {
        return issuedInvite(listingId, username) != null;
    }

    /** With no party known, takes a listing the player leads as theirs, as a command that needs one does. */
    Listing adoptLedListing() {
        String myUUID = player.uuid();
        if (myUUID == null || myUUID.isBlank()) {
            return null;
        }
        for (Listing listing : listings) {
            if (listing != null && listing.status() != PartyStatus.DISBANDED && listing.isLedBy(myUUID)) {
                currentListing = listing;
                return listing;
            }
        }
        return null;
    }

    private void upsertListing(Listing updated, boolean moveToTop) {
        synchronized (listingsLock) {
            int firstMatchIndex = -1;
            for (int i = 0; i < listings.size(); i++) {
                if (listings.get(i).id() != updated.id()) {
                    continue;
                }
                if (firstMatchIndex < 0) {
                    firstMatchIndex = i;
                } else {
                    listings.remove(i);
                    i--;
                }
            }

            if (firstMatchIndex < 0) {
                listings.add(moveToTop ? 0 : listings.size(), updated);
                return;
            }

            listings.set(firstMatchIndex, updated);
            if (moveToTop && firstMatchIndex > 0) {
                listings.remove(firstMatchIndex);
                listings.add(0, updated);
            }
        }
    }

    private void forgetListing(long listingId) {
        synchronized (listingsLock) {
            listings.removeIf(listing -> listing.id() == listingId);
        }
        if (currentListing != null && currentListing.id() == listingId) {
            currentListing = null;
        }
        issuedInvites.keySet().removeIf(key -> key.listingId() == listingId);
    }

    private void pruneIssuedInvites(Listing listing) {
        Set<String> reservedPlayerKeys = listing.reservedSlots() == null
                ? Set.of()
                : listing.reservedSlots().stream()
                        .filter(Objects::nonNull)
                        .map(ReservedSlot::playerUUID)
                        .map(Listing::playerKey)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
        issuedInvites.entrySet().removeIf(entry -> entry.getKey().listingId() == listing.id()
                && !reservedPlayerKeys.contains(Listing.playerKey(entry.getValue().targetUUID().toString())));
    }

    private static IssuedInviteKey issuedInviteKey(long listingId, String username) {
        return new IssuedInviteKey(listingId, username.trim().toLowerCase(Locale.ROOT));
    }

    private Listing findListingById(long listingId) {
        for (Listing existing : listings) {
            if (existing != null && existing.id() == listingId) {
                return existing;
            }
        }
        return null;
    }

    private void refreshCurrentListing() {
        currentListing = selectCurrentListing(listings, player.uuid(), currentListing);
    }

    static Listing selectCurrentListing(List<Listing> listings, String myUUID, Listing previousCurrentListing) {
        if (listings == null || myUUID == null || myUUID.isBlank()) {
            return null;
        }

        if (previousCurrentListing != null) {
            for (Listing listing : listings) {
                if (listing != null && listing.id() == previousCurrentListing.id() && listing.hasMember(myUUID)) {
                    return listing;
                }
            }
        }
        for (Listing listing : listings) {
            if (listing != null && listing.hasMember(myUUID)) {
                return listing;
            }
        }
        for (Listing listing : listings) {
            if (listing != null && listing.isLedBy(myUUID)) {
                return listing;
            }
        }
        return null;
    }
}
