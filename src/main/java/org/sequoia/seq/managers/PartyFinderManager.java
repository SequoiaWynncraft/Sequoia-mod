package org.sequoia.seq.managers;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.sequoia.seq.accessors.NotificationAccessor;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.events.PartyFinderUpdateEvent;
import org.sequoia.seq.model.*;
import org.sequoia.seq.network.ApiClient;
import org.sequoia.seq.network.ConnectionManager;
import org.sequoia.seq.utils.PlayerNameCache;

public class PartyFinderManager implements NotificationAccessor {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class,
                    (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();

    @Getter
    private final List<Activity> activities = new CopyOnWriteArrayList<>();

    @Getter
    private final List<Listing> listings = new CopyOnWriteArrayList<>();

    private final Object listingsLock = new Object();

    /** The listing the local player is currently a member of, or null. */
    @Getter
    private Listing currentListing;

    // ── Bridge fields for PartyFinderScreen adapter layer ──

    /** Expanded state tracking (preserved across listing reloads). */
    private final Map<Long, Boolean> expandedStates = new ConcurrentHashMap<>();

    /** Cached adapter list (avoids recreating wrappers every frame). */
    private List<PartyListing> cachedParties;
    private volatile int listingsVersion = 0;
    private int cachedVersion = -1;
    private volatile String latestPartyError;

    public PartyFinderManager() {
        // Register for real-time WS updates
        ConnectionManager.onPartyFinderUpdate(update -> {
            Listing listing = GSON.fromJson(
                    update.listingJson(),
                    Listing.class);
            handlePartyFinderUpdate(update.action(), listing);
        });

        ConnectionManager.onPartyFinderInvite(invite -> {
            Listing listing = null;
            if (invite.listingJson() != null) {
                listing = GSON.fromJson(invite.listingJson(), Listing.class);
            }
            handlePartyFinderInvite(
                    invite.listingId(),
                    invite.inviterUUID(),
                    invite.inviteToken(),
                    invite.preferredRole(),
                    listing);
        });
    }

    // ══════════════════════════════════════════════════════════════
    // Data loading
    // ══════════════════════════════════════════════════════════════

    public CompletableFuture<List<Activity>> loadActivities() {
        return ApiClient.getInstance()
                .getActivities()
                .thenApply(result -> {
                    activities.clear();
                    activities.addAll(result);
                    return result;
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to load activities", e);
                    return List.of();
                });
    }

    public CompletableFuture<List<Listing>> loadListings(
            Long activityId,
            PartyRegion region) {
        return ApiClient.getInstance()
                .getListings(activityId, region)
                .thenApply(result -> {
                    List<Listing> deduped = deduplicateById(result);
                    synchronized (listingsLock) {
                        listings.clear();
                        listings.addAll(deduped);
                    }
                    refreshCurrentListing();
                    listingsVersion++;
                    return deduped;
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to load listings", e);
                    return List.of();
                });
    }

    // ══════════════════════════════════════════════════════════════
    // Party actions (existing — with listingsVersion increments)
    // ══════════════════════════════════════════════════════════════

    public CompletableFuture<Listing> createParty(
            long activityId,
            PartyMode mode,
            PartyRegion region,
            PartyRole role,
            String note) {
        return createParty(List.of(activityId), mode, region, role, note);
    }

    public CompletableFuture<Listing> createParty(
            List<Long> activityIds,
            PartyMode mode,
            PartyRegion region,
            PartyRole role,
            String note) {
        return ApiClient.getInstance()
                .createListing(activityIds, mode, region, role, note)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    listingsVersion++;
                    publishLocalClassUpdate();
                    return listing;
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to create party", "Failed to create party");
                    return null;
                });
    }

    public CompletableFuture<Listing> joinParty(
            long listingId,
            PartyRole role) {
        return ApiClient.getInstance()
                .joinListing(listingId, role)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    listingsVersion++;
                    publishLocalClassUpdate();
                    return listing;
                })
                .exceptionally(e -> {
                    String errorMessage = extractUserFriendlyApiError(e, "Unable to join party");
                    SeqClient.LOGGER.warn("Failed to join party: {}", errorMessage);
                    notify(errorMessage);
                    return null;
                });
    }

    public CompletableFuture<Listing> joinPartyWithInviteToken(
            long listingId,
            PartyRole role,
            String inviteToken) {
        return ApiClient.getInstance()
                .joinListing(listingId, role, inviteToken)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    listingsVersion++;
                    publishLocalClassUpdate();
                    return listing;
                })
                .exceptionally(e -> {
                    String errorMessage = extractUserFriendlyApiError(e, "Unable to join party");
                    SeqClient.LOGGER.warn("Failed to join party with invite token: {}", errorMessage);
                    notify(errorMessage);
                    return null;
                });
    }

    private void publishLocalClassUpdate() {
        if (!ConnectionManager.isConnected()) {
            return;
        }
        ConnectionManager.getInstance().sendLocalPartyClassUpdate();
    }

    public CompletableFuture<Listing> leaveParty(long listingId) {
        return ApiClient.getInstance()
                .leaveListing(listingId)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = null;
                    listingsVersion++;
                    return listing;
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to leave party", "Failed to leave party");
                    return null;
                });
    }

    public CompletableFuture<Listing> closeParty(long listingId) {
        return ApiClient.getInstance()
                .closeListing(listingId)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    listingsVersion++;
                    return listing;
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to close party", "Failed to close party");
                    return null;
                });
    }

    public CompletableFuture<Listing> reopenParty(long listingId) {
        return ApiClient.getInstance()
                .reopenListing(listingId)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    listingsVersion++;
                    return listing;
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to reopen party", "Failed to reopen party");
                    return null;
                });
    }

    public CompletableFuture<Listing> disbandParty(long listingId) {
        return ApiClient.getInstance()
                .disbandListing(listingId)
                .thenApply(listing -> {
                    listings.removeIf(l -> l.id() == listingId);
                    if (currentListing != null && currentListing.id() == listingId) {
                        currentListing = null;
                    }
                    listingsVersion++;
                    return listing;
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to delist party", "Failed to disband party");
                    return null;
                });
    }

    public CompletableFuture<Listing> kickMember(
            long listingId,
            UUID targetUUID) {
        return ApiClient.getInstance()
                .kickMember(listingId, targetUUID)
                .thenApply(listing -> {
                    replaceListing(listing);
                    if (currentListing != null && currentListing.id() == listingId) {
                        currentListing = listing;
                    }
                    listingsVersion++;
                    return listing;
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to kick member", "Failed to kick member");
                    return null;
                });
    }

    public CompletableFuture<Listing> changeMyRole(PartyRole role) {
        return ApiClient.getInstance()
                .changeMyRole(role)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    listingsVersion++;
                    return listing;
                })
                .exceptionally(e -> {
                    String errorMessage = extractUserFriendlyApiError(e, "Unable to change role");
                    SeqClient.LOGGER.warn("Failed to change role: {}", errorMessage);
                    notify(errorMessage);
                    return null;
                });
    }

    public CompletableFuture<Listing> reassignRole(
            long listingId,
            UUID targetUUID,
            PartyRole role) {
        return ApiClient.getInstance()
                .reassignRole(listingId, targetUUID, role)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    listingsVersion++;
                    return listing;
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to reassign role", "Failed to reassign role");
                    return null;
                });
    }

    public CompletableFuture<Listing> transferLeadership(
            long listingId,
            UUID targetUUID) {
        return ApiClient.getInstance()
                .transferLeadership(listingId, targetUUID)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    listingsVersion++;
                    return listing;
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to promote member", "Failed to transfer leadership");
                    return null;
                });
    }

    // ══════════════════════════════════════════════════════════════
    // Convenience queries
    // ══════════════════════════════════════════════════════════════

    public boolean isInParty() {
        return currentListing != null;
    }

    public boolean isPartyLeader() {
        if (currentListing == null)
            return false;
        String myUUID = getLocalPlayerUUID();
        return uuidEquals(myUUID, currentListing.leaderUUID());
    }

    public String getLocalPlayerUUID() {
        var player = SeqClient.mc.player;
        return player != null ? player.getUUID().toString() : null;
    }

    // ══════════════════════════════════════════════════════════════
    // Real-time update handler
    // ══════════════════════════════════════════════════════════════

    public void handlePartyFinderUpdate(String action, Listing listing) {
        switch (action) {
            case "CREATED" -> {
                upsertListing(listing, true);
                listingsVersion++;
            }
            case "UPDATED" -> {
                upsertListing(listing, false);
                listingsVersion++;
            }
            case "DISBANDED" -> {
                synchronized (listingsLock) {
                    listings.removeIf(l -> l.id() == listing.id());
                }
                if (currentListing != null &&
                        currentListing.id() == listing.id()) {
                    currentListing = null;
                }
                listingsVersion++;
            }
        }
        refreshCurrentListing();

        // Fire event for the UI
        if (SeqClient.getEventBus() != null) {
            SeqClient.getEventBus().dispatch(
                    new PartyFinderUpdateEvent(action, listing));
        }
    }

    public void handlePartyFinderInvite(
            long listingId,
            String inviterUUID,
            String inviteToken,
            String preferredRole,
            Listing listing) {
        if (listing != null) {
            upsertListing(listing, true);
            listingsVersion++;
            refreshCurrentListing();
        }

        String inviterName = PlayerNameCache.resolve(inviterUUID);
        if (inviterName == null || inviterName.isBlank() || "Loading...".equals(inviterName)) {
            inviterName = "a player";
        }

        String roleSuffix = "";
        if (preferredRole != null && !preferredRole.isBlank()) {
            String displayRole = formatRoleForInvite(preferredRole);
            roleSuffix = " (preferred role: " + displayRole + ")";
        }

        if (inviteToken == null || inviteToken.isBlank()) {
            notify("Party Finder invite from " + inviterName + roleSuffix + ".");
        } else {
            PartyRole joinRole = mapDisplayRole(preferredRole);
            if (joinRole == null) {
                joinRole = PartyRole.DPS;
            }
            notifyInviteWithJoinAction(
                    "Party Finder invite from " + inviterName + roleSuffix + ".",
                    listingId,
                    inviteToken,
                    joinRole);
        }

        SeqClient.LOGGER.info(
                "Received party_finder_invite listingId={} inviterUUID={} preferredRole={} tokenPresent={}",
                listingId,
                inviterUUID,
                preferredRole,
                inviteToken != null && !inviteToken.isBlank());
    }

    private void notifyInviteWithJoinAction(
            String message,
            long listingId,
            String inviteToken,
            PartyRole role) {
        SeqClient.mc.execute(() -> {
            if (SeqClient.mc.player == null) {
                return;
            }

                String joinCommand = "/seq internalinvite join " + listingId + " "
                    + quoteForCommand(inviteToken) + " " + role.name();
                String denyCommand = "/seq internalinvite deny " + listingId;

            MutableComponent fullMessage = Component.literal(PREFIX + message + " ")
                    .append(Component.literal("[Join]")
                            .withStyle(style -> style
                                    .withColor(ChatFormatting.GREEN)
                                    .withBold(true)
                            .withClickEvent(new ClickEvent.RunCommand(joinCommand))))
                    .append(Component.literal(" "))
                    .append(Component.literal("[Deny]")
                        .withStyle(style -> style
                            .withColor(ChatFormatting.RED)
                            .withBold(true)
                            .withClickEvent(new ClickEvent.RunCommand(denyCommand))));

            SeqClient.mc.player.displayClientMessage(fullMessage, false);
        });
    }

    private static String quoteForCommand(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"") + "\"";
    }

    // ══════════════════════════════════════════════════════════════
    // Bridge methods for PartyFinderScreen adapter layer
    // ══════════════════════════════════════════════════════════════

    /**
     * Returns cached PartyListing adapters over the current listings.
     * Preserves expanded state across reloads. Called every frame by the screen.
     */
    public List<PartyListing> getParties() {
        if (cachedParties == null || cachedVersion != listingsVersion) {
            // Sync expanded state FROM old cache before rebuilding
            if (cachedParties != null) {
                for (PartyListing pl : cachedParties) {
                    expandedStates.put(pl.id, pl.expanded);
                }
            }
            // Rebuild cache
            cachedParties = listings
                    .stream()
                    .map(l -> {
                        PartyListing pl = new PartyListing(l);
                        pl.expanded = expandedStates.getOrDefault(l.id(), false);
                        return pl;
                    })
                    .collect(Collectors.toCollection(ArrayList::new));
            cachedVersion = listingsVersion;
        }
        return cachedParties;
    }

    /** Index of the listing the local player has joined, or -1. */
    public int getJoinedPartyIndex() {
        if (currentListing == null)
            return -1;
        List<PartyListing> parties = getParties();
        for (int i = 0; i < parties.size(); i++) {
            if (parties.get(i).id == currentListing.id())
                return i;
        }
        return -1;
    }

    /** Alias for getJoinedPartyIndex(). */
    public int getMyPartyIndex() {
        return getJoinedPartyIndex();
    }

    /** Whether the local player has created (and is leader of) a listed party. */
    public boolean hasListedParty() {
        return isPartyLeader();
    }

    /** No-op — state is derived from backend. Kept for screen compatibility. */
    public void setHasListedParty(boolean managing) {
        // intentionally empty — state is derived from isPartyLeader()
    }

    /** Disbands the local player's current listing. */
    public void delistParty() {
        if (currentListing != null) {
            disbandParty(currentListing.id());
        }
    }

    /** Changes the local player's role. Maps display string to PartyRole enum. */
    public void setRole(String role) {
        if (role != null) {
            PartyRole pr = mapDisplayRole(role);
            if (pr != null)
                changeMyRole(pr);
        }
    }

    /** Transfers leadership by party/member index. */
    public void promoteMember(int partyIndex, int memberIndex) {
        List<PartyListing> parties = getParties();
        if (partyIndex < 0 || partyIndex >= parties.size())
            return;
        PartyListing party = parties.get(partyIndex);
        if (memberIndex < 0 || memberIndex >= party.members.size())
            return;
        PartyMember member = party.members.get(memberIndex);
        try {
            UUID targetUUID = UUID.fromString(
                    PlayerNameCache.formatUUID(member.playerUUID));
            transferLeadership(party.id, targetUUID)
                    .thenAccept(listing -> {
                        if (listing != null) {
                            sendGameDirectMessage(
                                    targetUUID,
                                    "You were promoted to party leader.");
                        }
                    });
        } catch (IllegalArgumentException e) {
            SeqClient.LOGGER.error(
                    "Invalid UUID for promote: {}",
                    member.playerUUID,
                    e);
        }
    }

    /**
     * Kicks a member by party/member index (overload of kickMember(long, UUID)).
     */
    public void kickMember(int partyIndex, int memberIndex) {
        List<PartyListing> parties = getParties();
        if (partyIndex < 0 || partyIndex >= parties.size())
            return;
        PartyListing party = parties.get(partyIndex);
        if (memberIndex < 0 || memberIndex >= party.members.size())
            return;
        PartyMember member = party.members.get(memberIndex);
        try {
            UUID targetUUID = UUID.fromString(
                    PlayerNameCache.formatUUID(member.playerUUID));
            kickMember(party.id, targetUUID)
                    .thenAccept(listing -> {
                        if (listing != null) {
                            sendGameDirectMessage(
                                    targetUUID,
                                    "You were kicked from the party.");
                        }
                    });
        } catch (IllegalArgumentException e) {
            SeqClient.LOGGER.error(
                    "Invalid UUID for kick: {}",
                    member.playerUUID,
                    e);
        }
    }

    /** Joins a party by index with a display-name role string. */
    public void joinParty(int partyIndex, String roleString) {
        List<PartyListing> parties = getParties();
        if (partyIndex < 0 || partyIndex >= parties.size())
            return;
        PartyListing party = parties.get(partyIndex);
        PartyRole role = mapDisplayRole(roleString);
        if (role == null)
            role = PartyRole.DPS;
        joinParty(party.id, role);
    }

    /** Creates a backend invite from the local listing using a target username. */
    public void createInvite(String username) {
        if (!isPartyLeader()) {
            pushUiError("Only the party leader can invite players.");
            return;
        }

        if (currentListing == null) {
            pushUiError("Unable to invite player: no active listing.");
            return;
        }

        if (username == null || username.isBlank()) {
            pushUiError("Enter a username to invite.");
            return;
        }

        String normalizedUsername = username.trim();
        if (!normalizedUsername.matches("[A-Za-z0-9_]{3,16}")) {
            pushUiError("Enter a valid Minecraft username.");
            return;
        }

        var player = SeqClient.mc.player;
        String myUsername = player != null && player.getName() != null
                ? player.getName().getString()
                : null;
        if (myUsername != null && myUsername.equalsIgnoreCase(normalizedUsername)) {
            pushUiError("You cannot invite yourself.");
            return;
        }

        PlayerNameCache.resolveUUID(normalizedUsername)
                .thenCompose(resolvedUUID -> {
                    if (resolvedUUID == null || resolvedUUID.isBlank()) {
                        pushUiError("Unable to find that player UUID.");
                        return CompletableFuture.completedFuture(false);
                    }

                    UUID targetUUID;
                    try {
                        targetUUID = UUID.fromString(PlayerNameCache.formatUUID(resolvedUUID));
                    } catch (IllegalArgumentException e) {
                        SeqClient.LOGGER.warn("Unable to parse resolved invite UUID: {}", resolvedUUID, e);
                        pushUiError("Unable to resolve a valid UUID for that player.");
                        return CompletableFuture.completedFuture(false);
                    }

                    String myUUID = getLocalPlayerUUID();
                    if (myUUID != null && myUUID.equalsIgnoreCase(targetUUID.toString())) {
                        pushUiError("You cannot invite yourself.");
                        return CompletableFuture.completedFuture(false);
                    }

                    return ApiClient.getInstance()
                            .createInvite(currentListing.id(), targetUUID)
                            .thenApply(ignored -> true);
                })
                .thenAccept(inviteCreated -> {
                    if (Boolean.TRUE.equals(inviteCreated)) {
                        notify("Party Finder invite created.");
                        loadListings(null, null);
                    }
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to create party invite", "Failed to create invite");
                    return null;
                });
    }

    /** Leaves the current party (no-arg overload). */
    public void leaveParty() {
        if (currentListing != null) {
            leaveParty(currentListing.id());
        }
    }

    /**
     * Creates a party from the modal's tag list + role string.
     * Extracts activityIds from raid tags, mode from Chill/Grind tag.
     */
    public void createParty(List<String> tags, String role, int reservedSlots) {
        // Separate party-mode tags from raid/activity display names
        PartyMode mode = PartyMode.CHILL;
        List<String> raidDisplayNames = new ArrayList<>();
        for (String tag : tags) {
            if ("Grind".equalsIgnoreCase(tag)) {
                mode = PartyMode.GRIND;
            } else if (!"Chill".equalsIgnoreCase(tag)) {
                raidDisplayNames.add(tag);
            }
        }

        if (raidDisplayNames.isEmpty()) {
            SeqClient.LOGGER.error("Cannot create party without a selected activity");
            pushUiError("Select at least one raid before creating a party.");
            return;
        }

        List<Long> activityIds = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (String activityDisplayName : raidDisplayNames) {
            if (activityDisplayName == null || activityDisplayName.isBlank()) {
                continue;
            }

            final String normalizedDisplayName = activityDisplayName.trim();
            final String searchName = PartyListing.displayNameToBackendName(normalizedDisplayName);
            Activity activity = activities
                    .stream()
                    .filter(a -> matchesActivityName(a, normalizedDisplayName, searchName))
                    .findFirst()
                    .orElse(null);

            if (activity == null) {
                unresolved.add(normalizedDisplayName);
            } else if (!activityIds.contains(activity.id())) {
                activityIds.add(activity.id());
            }
        }

        if (activityIds.isEmpty()) {
            List<String> availableNames = activities.stream()
                    .map(Activity::name)
                    .filter(Objects::nonNull)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            SeqClient.LOGGER.error("Selected raids (raw): {}", raidDisplayNames);
            SeqClient.LOGGER.error("Loaded backend activities ({}): {}", availableNames.size(), availableNames);
            SeqClient.LOGGER.error("No matching activities found for selected raids: {}", raidDisplayNames);
            pushUiError("Could not map selected raids to backend activities.");
            return;
        }

        if (!unresolved.isEmpty()) {
            SeqClient.LOGGER.warn("Some selected raids could not be mapped to backend activities: {}", unresolved);
        }

        PartyRole mappedCreateRole = mapDisplayRole(role);
        final PartyRole createRole = mappedCreateRole != null ? mappedCreateRole : PartyRole.DPS;

        // Default region to NA (no region selector in current UI)
        int requestedReservedSlots = Math.max(0, reservedSlots);
        createParty(activityIds, mode, PartyRegion.NA, createRole, null)
                .thenAccept(listing -> {
                    if (listing == null || requestedReservedSlots <= 0) {
                        return;
                    }
                    applyReservedSlotTarget(
                            listing.id(),
                            0,
                            requestedReservedSlots,
                            "create");
                });
    }

    public void updateParty(List<String> tags, String role, int reservedSlots) {
        if (currentListing == null) {
            pushUiError("Unable to update party: no active listing.");
            return;
        }

        int requestedReservedSlots = Math.max(0, reservedSlots);
        int currentReservedSlots = inferReservedSlotCount(currentListing);

        PartyMode mode = PartyMode.CHILL;
        List<String> raidDisplayNames = new ArrayList<>();
        for (String tag : tags) {
            if ("Grind".equalsIgnoreCase(tag)) {
                mode = PartyMode.GRIND;
            } else if (!"Chill".equalsIgnoreCase(tag)) {
                raidDisplayNames.add(tag);
            }
        }

        if (raidDisplayNames.isEmpty()) {
            pushUiError("Select at least one raid before updating your party.");
            return;
        }

        List<Long> activityIds = new ArrayList<>();
        for (String activityDisplayName : raidDisplayNames) {
            if (activityDisplayName == null || activityDisplayName.isBlank()) {
                continue;
            }

            final String normalizedDisplayName = activityDisplayName.trim();
            final String searchName = PartyListing.displayNameToBackendName(normalizedDisplayName);
            Activity activity = activities
                    .stream()
                    .filter(a -> matchesActivityName(a, normalizedDisplayName, searchName))
                    .findFirst()
                    .orElse(null);

            if (activity != null && !activityIds.contains(activity.id())) {
                activityIds.add(activity.id());
            }
        }

        if (activityIds.isEmpty()) {
            pushUiError("Could not map selected raids to backend activities.");
            return;
        }

        PartyRegion region = currentListing.region() != null ? currentListing.region() : PartyRegion.NA;
        ApiClient.getInstance()
                .updateListing(currentListing.id(), activityIds, mode, region, currentListing.note())
                .thenAccept(listing -> {
                    if (listing != null) {
                        replaceListing(listing);
                        currentListing = listing;
                        listingsVersion++;
                        applyReservedSlotTarget(
                                listing.id(),
                                currentReservedSlots,
                                requestedReservedSlots,
                                "update");
                    }
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to update party", "Failed to update party");
                    return null;
                });
    }

    public void pushUiError(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        latestPartyError = message;
    }

    public String consumeLatestPartyError() {
        String message = latestPartyError;
        latestPartyError = null;
        return message;
    }

    /** Loads activities and listings from the API. Called when the screen opens. */
    public void refreshData() {
        loadActivities().thenRun(() -> loadListings(null, null));
    }

    // ══════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════

    private void replaceListing(Listing updated) {
        upsertListing(updated, false);
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
                if (moveToTop) {
                    listings.add(0, updated);
                } else {
                    listings.add(updated);
                }
                return;
            }

            listings.set(firstMatchIndex, updated);
            if (moveToTop && firstMatchIndex > 0) {
                listings.remove(firstMatchIndex);
                listings.add(0, updated);
            }
        }
    }

    private static List<Listing> deduplicateById(List<Listing> source) {
        LinkedHashMap<Long, Listing> unique = new LinkedHashMap<>();
        for (Listing listing : source) {
            unique.putIfAbsent(listing.id(), listing);
        }
        return new ArrayList<>(unique.values());
    }

    private static String extractUserFriendlyApiError(
            Throwable throwable,
            String fallbackMessage) {
        ApiClient.ApiException apiException = findApiException(throwable);
        if (apiException == null) {
            return fallbackMessage;
        }

        String responseBody = apiException.getResponseBody();
        if (responseBody == null || responseBody.isBlank()) {
            return fallbackMessage;
        }

        try {
            JsonElement parsed = JsonParser.parseString(responseBody);
            if (parsed.isJsonObject()) {
                JsonObject obj = parsed.getAsJsonObject();
                JsonElement error = obj.get("error");
                if (error != null && error.isJsonPrimitive() && error.getAsJsonPrimitive().isString()) {
                    return error.getAsString();
                }
            }
        } catch (Exception ignored) {
        }

        return fallbackMessage;
    }

    private static ApiClient.ApiException findApiException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ApiClient.ApiException apiException) {
                return apiException;
            }
            current = current.getCause();
        }
        return null;
    }

    private void handleActionError(
            Throwable throwable,
            String fallbackMessage,
            String logMessage) {
        String errorMessage = extractUserFriendlyApiError(throwable, fallbackMessage);
        SeqClient.LOGGER.warn("{}: {}", logMessage, errorMessage);
        pushUiError(errorMessage);
    }

    private void sendGameDirectMessage(UUID targetUUID, String message) {
        if (targetUUID == null || message == null || message.isBlank()) {
            return;
        }

        String targetName = PlayerNameCache.resolve(targetUUID.toString());
        if (targetName == null || !targetName.matches("[A-Za-z0-9_]{3,16}")) {
            SeqClient.LOGGER.warn("Skipping DM for unresolved target UUID {}", targetUUID);
            return;
        }

        SeqClient.mc.execute(() -> {
            var player = SeqClient.mc.player;
            if (player == null || player.connection == null) {
                return;
            }
            player.connection.sendCommand("msg " + targetName + " " + message);
        });
    }

    private void applyReservedSlotTarget(
            long listingId,
            int currentReservedSlots,
            int requestedReservedSlots,
            String context) {
        int clampedCurrent = Math.max(0, currentReservedSlots);
        int clampedRequested = Math.max(0, requestedReservedSlots);
        int delta = clampedRequested - clampedCurrent;

        if (delta == 0) {
            return;
        }

        CompletableFuture<Listing> adjustFuture = delta > 0
                ? ApiClient.getInstance().reserveSlots(listingId, delta)
                : ApiClient.getInstance().unreserveSlots(listingId, -delta);

        adjustFuture
                .thenAccept(updatedListing -> {
                    if (updatedListing != null) {
                        replaceListing(updatedListing);
                        if (currentListing != null && currentListing.id() == listingId) {
                            currentListing = updatedListing;
                        }
                        listingsVersion++;
                    }
                })
                .exceptionally(e -> {
                    handleActionError(
                            e,
                            "Unable to adjust reserved slots",
                            "Failed to " + (delta > 0 ? "reserve" : "unreserve") + " slots on " + context);
                    return null;
                });
    }

    private static int inferReservedSlotCount(Listing listing) {
        if (listing == null) {
            return 0;
        }

        List<Member> reservedSlots = listing.reservedSlots();
        if (reservedSlots != null) {
            return reservedSlots.size();
        }

        if (listing.members() == null) {
            return 0;
        }

        int count = 0;
        for (Member member : listing.members()) {
            if (member == null) {
                count++;
                continue;
            }

            String playerUUID = member.playerUUID();
            if (playerUUID == null || playerUUID.isBlank()) {
                count++;
                continue;
            }

            String normalized = playerUUID.trim().toLowerCase(Locale.ROOT);
            if ("anonymous".equals(normalized) || "reserved".equals(normalized)) {
                count++;
            }
        }
        return count;
    }

    private void refreshCurrentListing() {
        String myUUID = getLocalPlayerUUID();
        if (myUUID == null) {
            currentListing = null;
            return;
        }

        currentListing = listings
                .stream()
                .filter(l -> listingContainsPlayer(l, myUUID))
                .findFirst()
                .orElse(null);
    }

    private static boolean listingContainsPlayer(Listing listing, String myUUID) {
        if (listing == null || myUUID == null || myUUID.isBlank()) {
            return false;
        }

        if (uuidEquals(myUUID, listing.leaderUUID())) {
            return true;
        }

        List<Member> members = listing.members();
        if (members == null) {
            return false;
        }

        for (Member member : members) {
            if (member == null) {
                continue;
            }

            if (uuidEquals(myUUID, member.playerUUID())) {
                return true;
            }
        }

        return false;
    }

    private static boolean uuidEquals(String left, String right) {
        String leftNorm = normalizeUuidLike(left);
        String rightNorm = normalizeUuidLike(right);
        if (leftNorm == null || rightNorm == null) {
            return false;
        }
        return leftNorm.equals(rightNorm);
    }

    private static String normalizeUuidLike(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String formatted = PlayerNameCache.formatUUID(trimmed);
        StringBuilder hex = new StringBuilder(32);
        for (int i = 0; i < formatted.length(); i++) {
            char ch = Character.toLowerCase(formatted.charAt(i));
            if (Character.digit(ch, 16) >= 0) {
                hex.append(ch);
            }
        }

        if (hex.length() == 32) {
            return hex.toString();
        }

        return trimmed.toLowerCase(Locale.ROOT);
    }

    /**
     * Maps display role strings (from ROLES constant / dropdown) to PartyRole enum
     * values.
     */
    private static PartyRole mapDisplayRole(String displayRole) {
        if (displayRole == null)
            return null;
        return switch (displayRole.toUpperCase()) {
            case "DPS" -> PartyRole.DPS;
            case "HEALER" -> PartyRole.HEALER;
            case "TANK" -> PartyRole.TANK;
            default -> null;
        };
    }

    private static String formatRoleForInvite(String role) {
        return switch (role.toUpperCase()) {
            case "DPS" -> "DPS";
            case "HEALER" -> "Healer";
            case "TANK" -> "Tank";
            default -> role;
        };
    }

    private static boolean matchesActivityName(
            Activity activity,
            String displayName,
            String backendSearchName) {
        if (activity == null || activity.name() == null) {
            return false;
        }

        String backendName = activity.name().trim();
        String mappedDisplay = PartyListing.backendNameToDisplayName(backendName);
        String mappedBackendFromDisplay = PartyListing.displayNameToBackendName(displayName);

        if (backendName.equalsIgnoreCase(backendSearchName)
                || backendName.equalsIgnoreCase(displayName)
                || mappedDisplay.equalsIgnoreCase(displayName)
                || backendName.equalsIgnoreCase(mappedBackendFromDisplay)) {
            return true;
        }

        String normalizedBackend = normalizeActivityKey(backendName);
        String normalizedDisplay = normalizeActivityKey(displayName);
        String normalizedMappedDisplay = normalizeActivityKey(mappedDisplay);
        String normalizedBackendSearch = normalizeActivityKey(backendSearchName);
        String normalizedMappedBackend = normalizeActivityKey(mappedBackendFromDisplay);

        return normalizedBackend.equals(normalizedDisplay)
                || normalizedMappedDisplay.equals(normalizedDisplay)
                || normalizedBackend.equals(normalizedBackendSearch)
                || normalizedBackend.equals(normalizedMappedBackend);
    }

    private static String normalizeActivityKey(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder normalized = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(ch)) {
                normalized.append(ch);
            }
        }
        return normalized.toString();
    }
}
