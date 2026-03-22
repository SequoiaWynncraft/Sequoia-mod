package org.sequoia.seq.managers;

import com.google.gson.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
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
import org.sequoia.seq.network.WynncraftServerPolicy;
import org.sequoia.seq.network.auth.StoredAuthSession;
import org.sequoia.seq.utils.PlayerNameCache;

public class PartyFinderManager implements NotificationAccessor {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(
                    Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) -> Instant.parse(json.getAsString()))
            .create();
    private static final long INVITE_NAME_LOOKUP_TIMEOUT_SECONDS = 3L;
    private static final String GAME_PARTY_CREATE_COMMAND = "party create";
    private static final String GAME_PARTY_INVITE_PREFIX = "party ";
    private static final String GAME_PARTY_KICK_PREFIX = "party kick ";
    private static final String GAME_PARTY_PROMOTE_PREFIX = "party promote ";
    private static final String SEQ_INVITE_ALL_COMMAND = "/seq p invite-all";
    private static final ChatFormatting OPEN_PARTY_REMINDER_TEXT_COLOR = ChatFormatting.GRAY;

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
    private volatile long nextOpenPartyAnnouncementAtMs;
    private volatile boolean openPartyAnnouncementRefreshInFlight;

    public record InviteAllResult(boolean success, boolean sentAny, int sentCount, int skippedCount, String message) {}

    public record CommandResult<T>(boolean success, String message, T data) {
        public static <T> CommandResult<T> success(String message, T data) {
            return new CommandResult<>(true, message, data);
        }

        public static <T> CommandResult<T> failure(String message) {
            return new CommandResult<>(false, message, null);
        }
    }

    record OpenPartyAnnouncementEntry(
            long listingId,
            String activitySummary,
            int occupiedSlots,
            int maxPartySize,
            String leaderName,
            String joinCommand) {}

    record OpenPartyAnnouncementSummary(List<OpenPartyAnnouncementEntry> entries) {
        boolean isEmpty() {
            return entries == null || entries.isEmpty();
        }
    }

    private record ActivityResolution(List<Long> activityIds, List<String> unresolved, List<String> displayNames) {}

    private record ListingMemberTarget(Listing listing, UUID targetUUID, String username) {}

    private record InviteAllCandidate(String memberUUID, CompletableFuture<String> usernameFuture) {}

    private record ResolvedInviteTarget(String memberUUID, String username) {}

    public PartyFinderManager() {
        SeqClient.LOGGER.info("[PartyFinderWS] Registering party finder websocket handlers");
        // Register for real-time WS updates
        ConnectionManager.onPartyFinderUpdate(update -> {
            SeqClient.LOGGER.info(
                    "[PartyFinderWS] Received update callback action={} hasListingJson={}",
                    update.action(),
                    update.listingJson() != null);
            Listing listing = GSON.fromJson(update.listingJson(), Listing.class);
            handlePartyFinderUpdate(update.action(), listing);
        });

        ConnectionManager.onPartyFinderInvite(invite -> {
            SeqClient.LOGGER.info(
                    "[PartyFinderWS] Received invite callback listingId={} inviterUUID={} tokenPresent={} hasListingJson={}",
                    invite.listingId(),
                    invite.inviterUUID(),
                    invite.inviteToken() != null && !invite.inviteToken().isBlank(),
                    invite.listingJson() != null);
            Listing listing = null;
            if (invite.listingJson() != null) {
                listing = GSON.fromJson(invite.listingJson(), Listing.class);
            }
            handlePartyFinderInvite(invite.listingId(), invite.inviterUUID(), invite.inviteToken(), listing);
        });

        ConnectionManager.onPartyFinderStaleWarning(warning -> {
            SeqClient.LOGGER.info(
                    "[PartyFinderWS] Received stale warning callback reason={} listingId={} disbandAt={} minutesRemaining={}",
                    warning.reason(),
                    warning.listingId(),
                    warning.disbandAt(),
                    warning.minutesRemaining());
            handlePartyFinderStaleWarning(
                    warning.reason(), warning.listingId(), warning.disbandAt(), warning.minutesRemaining());
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
                    String message = extractUserFriendlyApiError(e, "Failed to load activities");
                    SeqClient.LOGGER.error("Failed to load activities: {}", message, e);
                    pushUiError(message);
                    return List.of();
                });
    }

    public CompletableFuture<List<Listing>> loadListings(Long activityId, PartyRegion region) {
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
                    String message = extractUserFriendlyApiError(e, "Failed to load listings");
                    SeqClient.LOGGER.error("Failed to load listings: {}", message, e);
                    pushUiError(message);
                    return List.of();
                });
    }

    public CompletableFuture<CommandResult<List<Activity>>> ensureActivitiesLoadedForCommand() {
        if (!activities.isEmpty()) {
            return CompletableFuture.completedFuture(
                    CommandResult.success("Activities ready.", List.copyOf(activities)));
        }

        return ApiClient.getInstance()
                .getActivities()
                .thenApply(result -> {
                    activities.clear();
                    activities.addAll(result);
                    return CommandResult.success("Loaded " + result.size() + " activities.", List.copyOf(result));
                })
                .exceptionally(e -> commandFailure(e, "Failed to load activities", "Failed to load activities"));
    }

    public CompletableFuture<CommandResult<List<Listing>>> refreshListingsForCommand() {
        return ApiClient.getInstance()
                .getListings(null, null)
                .thenApply(result -> {
                    List<Listing> deduped = deduplicateById(result);
                    synchronized (listingsLock) {
                        listings.clear();
                        listings.addAll(deduped);
                    }
                    refreshCurrentListing();
                    listingsVersion++;
                    return CommandResult.success("Loaded " + deduped.size() + " listings.", List.copyOf(deduped));
                })
                .exceptionally(e -> commandFailure(e, "Failed to load listings", "Failed to load listings"));
    }

    public CompletableFuture<CommandResult<Listing>> createPartyFromCommand(List<String> activityInputs) {
        return refreshListingsForCommand().thenCompose(listingsResult -> {
            if (!listingsResult.success()) {
                return completedCommandFailure(listingsResult.message());
            }
            if (currentListing != null) {
                return completedCommandFailure("You are already in party #" + currentListing.id()
                        + ". Use /seq p update or leave/disband it first.");
            }
            return ensureActivitiesLoadedForCommand().thenCompose(activitiesResult -> {
                if (!activitiesResult.success()) {
                    return completedCommandFailure(activitiesResult.message());
                }

                CommandResult<ActivityResolution> selectionResult = resolveActivitiesForCommand(activityInputs, true);
                if (!selectionResult.success()) {
                    return completedCommandFailure(selectionResult.message());
                }

                ActivityResolution resolution = selectionResult.data();
                return executeListingCommand(
                        ApiClient.getInstance()
                                .createListing(
                                        resolution.activityIds(),
                                        PartyMode.CHILL,
                                        false,
                                        PartyRegion.NA,
                                        PartyRole.DPS,
                                        null),
                        listing -> applyCreatedListingState(listing),
                        "Unable to create party",
                        "Failed to create party",
                        listing -> "Created party #" + listing.id() + " for "
                                + formatActivityNames(resolution.displayNames()) + ".");
            });
        });
    }

    public CompletableFuture<CommandResult<Listing>> updatePartyFromCommand(List<String> activityInputs) {
        return ensureCurrentListingForCommand().thenCompose(currentResult -> {
            if (!currentResult.success()) {
                return completedCommandFailure(currentResult.message());
            }
            if (!isPartyLeader()) {
                return completedCommandFailure("Only the party leader can update the Sequoia listing.");
            }

            Listing listing = currentResult.data();
            return ensureActivitiesLoadedForCommand().thenCompose(activitiesResult -> {
                if (!activitiesResult.success()) {
                    return completedCommandFailure(activitiesResult.message());
                }

                CommandResult<ActivityResolution> selectionResult = resolveActivitiesForCommand(activityInputs, true);
                if (!selectionResult.success()) {
                    return completedCommandFailure(selectionResult.message());
                }

                ActivityResolution resolution = selectionResult.data();
                PartyRegion region = listing.region() != null ? listing.region() : PartyRegion.NA;
                return executeListingCommand(
                        ApiClient.getInstance()
                                .updateListing(
                                        listing.id(),
                                        resolution.activityIds(),
                                        listing.mode(),
                                        listing.strict(),
                                        region,
                                        listing.note()),
                        this::applyUpdatedCurrentListingState,
                        "Unable to update party",
                        "Failed to update party",
                        updatedListing -> "Updated party #" + updatedListing.id() + " to "
                                + formatActivityNames(resolution.displayNames()) + ".");
            });
        });
    }

    public CompletableFuture<CommandResult<Listing>> joinPartyFromCommand(long listingId, PartyRole role) {
        return joinPartyFromCommand(listingId, role, null);
    }

    public CompletableFuture<CommandResult<Listing>> joinPartyFromCommand(
            long listingId, PartyRole role, String inviteToken) {
        PartyRole resolvedRole = role != null ? role : PartyRole.DPS;
        String normalizedInviteToken = inviteToken;
        if (normalizedInviteToken != null && normalizedInviteToken.isBlank()) {
            return completedCommandFailure("Invite token missing.");
        }

        return refreshListingsForCommand().thenCompose(listingsResult -> {
            if (!listingsResult.success()) {
                return completedCommandFailure(listingsResult.message());
            }
            if (currentListing != null) {
                return completedCommandFailure("You are already in party #" + currentListing.id()
                        + ". Leave it before joining another listing.");
            }

            CompletableFuture<Listing> joinFuture = normalizedInviteToken == null
                    ? ApiClient.getInstance().joinListing(listingId, resolvedRole)
                    : ApiClient.getInstance().joinListing(listingId, resolvedRole, normalizedInviteToken);

            return executeListingCommand(
                    joinFuture,
                    this::applyJoinedListingState,
                    "Unable to join party",
                    "Failed to join party",
                    listing -> "Joined party #" + listing.id() + " as " + formatRoleName(resolvedRole) + ".");
        });
    }

    public CompletableFuture<CommandResult<Listing>> leavePartyFromCommand() {
        return ensureCurrentListingForCommand().thenCompose(currentResult -> {
            if (!currentResult.success()) {
                return completedCommandFailure(currentResult.message());
            }

            Listing listing = currentResult.data();
            return executeListingCommand(
                    ApiClient.getInstance().leaveListing(listing.id()),
                    this::applyLeftListingState,
                    "Unable to leave party",
                    "Failed to leave party",
                    ignored -> "Left party #" + listing.id() + ".");
        });
    }

    public CompletableFuture<CommandResult<Void>> createInviteFromCommand(String username) {
        return ensureCurrentListingForCommand().thenCompose(currentResult -> {
            if (!currentResult.success()) {
                return completedCommandFailureVoid(currentResult.message());
            }
            if (!isPartyLeader()) {
                return completedCommandFailureVoid("Only the party leader can invite players.");
            }

            String validationMessage = validateUsername(username, false);
            if (validationMessage != null) {
                return completedCommandFailureVoid(validationMessage);
            }

            String normalizedUsername = username.trim();
            Listing listing = currentResult.data();
            String reservedSlotError = validateReservedSlotsForInvite(listing);
            if (reservedSlotError != null) {
                return completedCommandFailureVoid(reservedSlotError);
            }
            return resolveUuidForCommand(normalizedUsername).thenCompose(uuidResult -> {
                if (!uuidResult.success()) {
                    return completedCommandFailureVoid(uuidResult.message());
                }

                UUID targetUUID = uuidResult.data();
                if (uuidEquals(getLocalPlayerUUID(), targetUUID.toString())) {
                    return completedCommandFailureVoid("You cannot invite yourself.");
                }

                return executeVoidCommand(
                        ApiClient.getInstance().createInvite(listing.id(), targetUUID),
                        "Unable to create party invite",
                        "Failed to create invite",
                        "Created Sequoia invite for " + normalizedUsername + " on party #" + listing.id() + ".");
            });
        });
    }

    public CompletableFuture<CommandResult<Listing>> setReservedSlotTargetFromCommand(int requestedReservedSlots) {
        if (requestedReservedSlots < 0) {
            return completedCommandFailure("Reserved slot target cannot be negative.");
        }

        return ensureCurrentListingForCommand().thenCompose(currentResult -> {
            if (!currentResult.success()) {
                return completedCommandFailure(currentResult.message());
            }
            if (!isPartyLeader()) {
                return completedCommandFailure("Only the party leader can reserve slots.");
            }

            Listing listing = currentResult.data();
            String verb = requestedReservedSlots == 1 ? "slot" : "slots";
            return executeListingCommand(
                    ApiClient.getInstance().reserveSlots(listing.id(), requestedReservedSlots),
                    this::applyUpdatedCurrentListingState,
                    "Unable to set reserved slots",
                    "Failed to set reserved slots",
                    updatedListing -> "Set reserved slots to " + requestedReservedSlots + " " + verb + " on party #"
                            + updatedListing.id() + ".");
        });
    }

    public CompletableFuture<CommandResult<Listing>> reopenPartyFromCommand() {
        return runLeaderListingCommand(
                "Only the party leader can open the Sequoia listing.",
                listing -> executeListingCommand(
                        ApiClient.getInstance().reopenListing(listing.id()),
                        this::applyUpdatedCurrentListingState,
                        "Unable to reopen party",
                        "Failed to reopen party",
                        updatedListing -> "Opened party #" + updatedListing.id() + "."));
    }

    public CompletableFuture<CommandResult<Listing>> closePartyFromCommand() {
        return runLeaderListingCommand(
                "Only the party leader can close the Sequoia listing.",
                listing -> executeListingCommand(
                        ApiClient.getInstance().closeListing(listing.id()),
                        this::applyUpdatedCurrentListingState,
                        "Unable to close party",
                        "Failed to close party",
                        updatedListing -> "Closed party #" + updatedListing.id() + "."));
    }

    public CompletableFuture<CommandResult<Listing>> disbandPartyFromCommand() {
        return runLeaderListingCommand(
                "Only the party leader can disband the Sequoia listing.",
                listing -> executeListingCommand(
                        ApiClient.getInstance().disbandListing(listing.id()),
                        ignored -> applyDisbandedListingState(listing.id()),
                        "Unable to disband party",
                        "Failed to disband party",
                        ignored -> "Disbanded party #" + listing.id() + "."));
    }

    public CompletableFuture<CommandResult<Listing>> changeRoleFromCommand(PartyRole role) {
        PartyRole resolvedRole = role != null ? role : PartyRole.DPS;
        return ensureCurrentListingForCommand().thenCompose(currentResult -> {
            if (!currentResult.success()) {
                return completedCommandFailure(currentResult.message());
            }

            return executeListingCommand(
                    ApiClient.getInstance().changeMyRole(resolvedRole),
                    this::applyUpdatedCurrentListingState,
                    "Unable to change role",
                    "Failed to change role",
                    updatedListing -> "Changed your party role to " + formatRoleName(resolvedRole) + ".");
        });
    }

    public CompletableFuture<CommandResult<Listing>> kickMemberFromCommand(String username) {
        return resolveCurrentMemberTargetForCommand(username, true).thenCompose(targetResult -> {
            if (!targetResult.success()) {
                return completedCommandFailure(targetResult.message());
            }

            ListingMemberTarget target = targetResult.data();
            if (uuidEquals(target.listing().leaderUUID(), target.targetUUID().toString())) {
                return completedCommandFailure("You cannot kick the party leader.");
            }

            return executeListingCommand(
                    ApiClient.getInstance().kickMember(target.listing().id(), target.targetUUID()),
                    this::applyUpdatedCurrentListingState,
                    "Unable to kick member",
                    "Failed to kick member",
                    updatedListing -> "Kicked " + target.username() + " from party #" + updatedListing.id() + ".");
        });
    }

    public CompletableFuture<CommandResult<Listing>> promoteMemberFromCommand(String username) {
        return resolveCurrentMemberTargetForCommand(username, true).thenCompose(targetResult -> {
            if (!targetResult.success()) {
                return completedCommandFailure(targetResult.message());
            }

            ListingMemberTarget target = targetResult.data();
            if (uuidEquals(target.listing().leaderUUID(), target.targetUUID().toString())) {
                return completedCommandFailure(target.username() + " is already the party leader.");
            }

            return executeListingCommand(
                    ApiClient.getInstance().transferLeadership(target.listing().id(), target.targetUUID()),
                    this::applyUpdatedCurrentListingState,
                    "Unable to promote member",
                    "Failed to transfer leadership",
                    updatedListing ->
                            "Transferred party #" + updatedListing.id() + " leadership to " + target.username() + ".");
        });
    }

    public CompletableFuture<CommandResult<Void>> inviteAllCurrentMembersFromCommand() {
        return ensureCurrentListingForCommand().thenCompose(currentResult -> {
            if (!currentResult.success()) {
                return completedCommandFailureVoid(currentResult.message());
            }

            return inviteAllCurrentMembersInternal(false)
                    .thenApply(inviteAllResult -> inviteAllResult.success()
                            ? CommandResult.success("Invite all: " + inviteAllResult.message(), null)
                            : CommandResult.failure("Invite all: " + inviteAllResult.message()));
        });
    }

    // ══════════════════════════════════════════════════════════════
    // Party actions (existing — with listingsVersion increments)
    // ══════════════════════════════════════════════════════════════

    public CompletableFuture<Listing> createParty(
            long activityId, PartyMode mode, PartyRegion region, PartyRole role, String note) {
        return createParty(List.of(activityId), mode, false, region, role, note);
    }

    public CompletableFuture<Listing> createParty(
            List<Long> activityIds, PartyMode mode, boolean strict, PartyRegion region, PartyRole role, String note) {
        return ApiClient.getInstance()
                .createListing(activityIds, mode, strict, region, role, note)
                .thenApply(listing -> {
                    applyCreatedListingState(listing);
                    return listing;
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to create party", "Failed to create party");
                    return null;
                });
    }

    public CompletableFuture<Listing> joinParty(long listingId, PartyRole role) {
        return ApiClient.getInstance()
                .joinListing(listingId, role)
                .thenApply(listing -> {
                    applyJoinedListingState(listing);
                    return listing;
                })
                .exceptionally(e -> {
                    String errorMessage = extractUserFriendlyApiError(e, "Unable to join party");
                    SeqClient.LOGGER.warn("Failed to join party: {}", errorMessage);
                    pushUiError(errorMessage);
                    return null;
                });
    }

    public CompletableFuture<Listing> joinPartyWithInviteToken(long listingId, PartyRole role, String inviteToken) {
        return ApiClient.getInstance()
                .joinListing(listingId, role, inviteToken)
                .thenApply(listing -> {
                    applyJoinedListingState(listing);
                    return listing;
                })
                .exceptionally(e -> {
                    String errorMessage = extractUserFriendlyApiError(e, "Unable to join party");
                    SeqClient.LOGGER.warn("Failed to join party with invite token: {}", errorMessage);
                    pushUiError(errorMessage);
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
                    applyLeftListingState(listing);
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
                    applyUpdatedCurrentListingState(listing);
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
                    applyUpdatedCurrentListingState(listing);
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

    public CompletableFuture<Listing> kickMember(long listingId, UUID targetUUID) {
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
                    applyUpdatedCurrentListingState(listing);
                    return listing;
                })
                .exceptionally(e -> {
                    String errorMessage = extractUserFriendlyApiError(e, "Unable to change role");
                    SeqClient.LOGGER.warn("Failed to change role: {}", errorMessage);
                    pushUiError(errorMessage);
                    return null;
                });
    }

    public CompletableFuture<Listing> reassignRole(long listingId, UUID targetUUID, PartyRole role) {
        return ApiClient.getInstance()
                .reassignRole(listingId, targetUUID, role)
                .thenApply(listing -> {
                    applyUpdatedCurrentListingState(listing);
                    return listing;
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to reassign role", "Failed to reassign role");
                    return null;
                });
    }

    public CompletableFuture<Listing> transferLeadership(long listingId, UUID targetUUID) {
        return ApiClient.getInstance()
                .transferLeadership(listingId, targetUUID)
                .thenApply(listing -> {
                    applyUpdatedCurrentListingState(listing);
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

    public void tickOpenPartyAnnouncements() {
        if (!shouldRunOpenPartyAnnouncements()) {
            nextOpenPartyAnnouncementAtMs = 0L;
            return;
        }

        long intervalMs = resolveOpenPartyAnnouncementIntervalMs();
        long now = System.currentTimeMillis();
        if (nextOpenPartyAnnouncementAtMs <= 0L) {
            nextOpenPartyAnnouncementAtMs = now + intervalMs;
            return;
        }

        if (now < nextOpenPartyAnnouncementAtMs || openPartyAnnouncementRefreshInFlight) {
            return;
        }

        nextOpenPartyAnnouncementAtMs = now + intervalMs;
        openPartyAnnouncementRefreshInFlight = true;
        refreshListingsForAnnouncements().whenComplete((refreshedListings, error) -> {
            openPartyAnnouncementRefreshInFlight = false;
            if (error != null) {
                SeqClient.LOGGER.warn("[PartyFinderWS] Open party reminder refresh failed", error);
                return;
            }
            if (refreshedListings == null || refreshedListings.isEmpty()) {
                return;
            }

            SeqClient.mc.execute(() -> announceOpenPartySummary(refreshedListings));
        });
    }

    public boolean isPartyLeader() {
        if (currentListing == null) return false;
        String myUUID = getLocalPlayerUUID();
        return uuidEquals(myUUID, currentListing.leaderUUID());
    }

    public String getLocalPlayerUUID() {
        String currentPlayerUuid = null;
        var player = SeqClient.mc.player;
        if (player != null) {
            currentPlayerUuid = player.getUUID().toString();
        }
        return resolveIdentityUuid(
                currentPlayerUuid,
                SeqClient.getConfigManager() != null ? SeqClient.getConfigManager().getStoredAuthSession() : null);
    }

    // ══════════════════════════════════════════════════════════════
    // Real-time update handler
    // ══════════════════════════════════════════════════════════════

    public void handlePartyFinderUpdate(String action, Listing listing) {
        if (listing == null) {
            SeqClient.LOGGER.warn("[PartyFinderWS] Ignoring party update action={} because listing is null", action);
            return;
        }

        String myUUID = getLocalPlayerUUID();
        Listing previousListing = findListingById(listing.id());
        SeqClient.LOGGER.info(
                "[PartyFinderWS] handlePartyFinderUpdate action={} listingId={} myUUID={} containsMe={} reservedForMe={} reservedSlotCount={}",
                action,
                listing.id(),
                myUUID,
                listingContainsPlayer(listing, myUUID),
                listingHasReservedSlotForPlayer(listing, myUUID),
                listing.reservedSlotCount());

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
                if (currentListing != null && currentListing.id() == listing.id()) {
                    currentListing = null;
                }
                listingsVersion++;
            }
        }
        refreshCurrentListing();
        notifyPartyActionUx(action, previousListing, listing, myUUID);

        // Fire event for the UI
        if (SeqClient.getEventBus() != null) {
            SeqClient.getEventBus().dispatch(new PartyFinderUpdateEvent(action, listing));
        }
    }

    public void handlePartyFinderInvite(long listingId, String inviterUUID, String inviteToken, Listing listing) {
        SeqClient.LOGGER.info(
                "[PartyFinderWS] handlePartyFinderInvite listingId={} inviterUUID={} tokenPresent={} hasListing={}",
                listingId,
                inviterUUID,
                inviteToken != null && !inviteToken.isBlank(),
                listing != null);
        if (listing != null) {
            SeqClient.LOGGER.info("[PartyFinderWS] Upserting invite listing {} from websocket payload", listing.id());
            upsertListing(listing, true);
            listingsVersion++;
            refreshCurrentListing();
        }

        PlayerNameCache.resolveAsync(inviterUUID)
                .completeOnTimeout(null, INVITE_NAME_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((resolvedName, error) -> {
                    String inviterName = formatInviterName(resolvedName);
                    SeqClient.LOGGER.info(
                            "[PartyFinderWS] Resolved inviter name='{}' for uuid={}", inviterName, inviterUUID);

                    if (inviteToken == null || inviteToken.isBlank()) {
                        SeqClient.LOGGER.info(
                                "[PartyFinderWS] Invite token missing; sending plain notification for listing {}",
                                listingId);
                        notify("Party Finder invite from " + inviterName + ".");
                    } else {
                        SeqClient.LOGGER.info(
                                "[PartyFinderWS] Invite token present; sending clickable invite notification for listing {}",
                                listingId);
                        notifyInviteWithJoinAction(
                                "Party Finder invite from " + inviterName + ".", listingId, inviteToken);
                    }
                });

        SeqClient.LOGGER.info(
                "Received party_finder_invite listingId={} inviterUUID={} tokenPresent={}",
                listingId,
                inviterUUID,
                inviteToken != null && !inviteToken.isBlank());
    }

    public void handlePartyFinderStaleWarning(String reason, long listingId, Instant disbandAt, long minutesRemaining) {
        SeqClient.LOGGER.info(
                "[PartyFinderWS] handlePartyFinderStaleWarning reason={} listingId={} disbandAt={} minutesRemaining={}",
                reason,
                listingId,
                disbandAt,
                minutesRemaining);

        long safeMinutesRemaining = Math.max(0, minutesRemaining);
        if ("heartbeat_lost".equals(reason)) {
            notify("Your Party Finder listing will auto-disband in " + safeMinutesRemaining + " minutes.");
            return;
        }
        notify("Your Party Finder listing is still solo and has not changed in a while.");
    }

    private void notifyInviteWithJoinAction(String message, long listingId, String inviteToken) {
        SeqClient.LOGGER.info(
                "[PartyFinderWS] Queueing clickable invite chat message listingId={} tokenLength={}",
                listingId,
                inviteToken != null ? inviteToken.length() : 0);
        SeqClient.mc.execute(() -> {
            var player = SeqClient.mc.player;
            if (player == null) {
                SeqClient.LOGGER.warn(
                        "[PartyFinderWS] Skipped invite chat message listingId={} because mc.player is null",
                        listingId);
                return;
            }

            String joinCommand = "/seq p join " + listingId + " token " + quoteForCommand(inviteToken);
            String denyCommand = "/seq p deny " + listingId;

            ClickEvent joinClickEvent = new ClickEvent.RunCommand(joinCommand);
            ClickEvent denyClickEvent = new ClickEvent.RunCommand(denyCommand);

            MutableComponent inviteMessage = NotificationAccessor.prefixComponent()
                    .append(Component.literal(String.valueOf(message)).withStyle(ChatFormatting.GRAY));
            MutableComponent actionMessage = Component.empty()
                    .append(NotificationAccessor.wynnPill(
                            "join", ChatFormatting.GREEN, ChatFormatting.WHITE, joinClickEvent))
                    .append(Component.literal(" "))
                    .append(NotificationAccessor.wynnPill(
                            "deny", ChatFormatting.RED, ChatFormatting.WHITE, denyClickEvent));

            SeqClient.LOGGER.info(
                    "[PartyFinderWS] Displaying invite chat message listingId={} player={} joinCommand={} denyCommand={}",
                    listingId,
                    player.getName().getString(),
                    joinCommand,
                    denyCommand);
            player.displayClientMessage(inviteMessage, false);
            player.displayClientMessage(actionMessage, false);
        });
    }

    private void notifyPartyActionUx(String action, Listing previousListing, Listing updatedListing, String myUUID) {
        if (updatedListing == null || myUUID == null || myUUID.isBlank()) {
            return;
        }

        if ("CREATED".equals(action)) {
            String announcementLeaderUUID = createdListingNotificationTarget(updatedListing, myUUID);
            if (announcementLeaderUUID != null) {
                notifyNewPartyListing(announcementLeaderUUID, updatedListing);
            }
            return;
        }

        if ("DISBANDED".equals(action)) {
            boolean wasInParty = listingContainsPlayer(previousListing, myUUID)
                    || listingContainsPlayer(updatedListing, myUUID)
                    || uuidEquals(myUUID, updatedListing.leaderUUID());
            if (wasInParty) {
                notify("Your party was disbanded.");
            }
            return;
        }

        if (!"UPDATED".equals(action) || previousListing == null) {
            return;
        }

        notifyLocalPartyUpdateUx(previousListing, updatedListing, myUUID);
        notifyLeaderPartyUpdateUx(previousListing, updatedListing, myUUID);
    }

    private String createdListingNotificationTarget(Listing listing, String myUUID) {
        if (listing == null || myUUID == null || myUUID.isBlank()) {
            return null;
        }

        if (uuidEquals(myUUID, listing.leaderUUID()) || listingContainsPlayer(listing, myUUID)) {
            return null;
        }

        return listing.leaderUUID();
    }

    private void notifyNewPartyListing(String leaderUUID, Listing listing) {
        if (leaderUUID == null || leaderUUID.isBlank() || listing == null) {
            return;
        }

        PlayerNameCache.resolveAsync(leaderUUID)
                .completeOnTimeout(null, INVITE_NAME_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((resolvedName, error) -> {
                    String leaderName = formatInviterName(resolvedName);
                    String activitiesLabel = formatActivitySummary(listing);
                    String message = leaderName + " started "
                            + (activitiesLabel.isBlank() ? "a party!" : "a " + activitiesLabel + " party!");
                    notifyNewPartyListingWithJoinAction(message, listing.id());
                });
    }

    private void notifyNewPartyListingWithJoinAction(String message, long listingId) {
        SeqClient.mc.execute(() -> {
            var player = SeqClient.mc.player;
            if (player == null) {
                return;
            }

            ClickEvent joinClickEvent = new ClickEvent.RunCommand("/seq p join " + listingId);
            MutableComponent announcement = NotificationAccessor.prefixComponent()
                    .append(Component.literal(String.valueOf(message)).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(" "))
                    .append(NotificationAccessor.wynnPill(
                            "JOIN", ChatFormatting.GREEN, ChatFormatting.WHITE, joinClickEvent));

            player.displayClientMessage(announcement, false);
        });
    }

    private void announceOpenPartySummary(List<Listing> refreshedListings) {
        var player = SeqClient.mc.player;
        String myUUID = getLocalPlayerUUID();
        if (player == null || myUUID == null || !shouldRunOpenPartyAnnouncements()) {
            return;
        }

        List<Listing> candidates = selectOpenPartyAnnouncementCandidates(refreshedListings, myUUID);
        if (candidates.isEmpty()) {
            return;
        }

        OpenPartyAnnouncementSummary summary =
                buildOpenPartyAnnouncementSummary(candidates, PartyFinderManager::resolveReminderLeaderName);
        if (summary.isEmpty()) {
            return;
        }

        MutableComponent message = NotificationAccessor.prefixComponent().append(
                Component.literal("Open Sequoia parties (" + summary.entries().size() + "):")
                        .withStyle(OPEN_PARTY_REMINDER_TEXT_COLOR));

        for (OpenPartyAnnouncementEntry entry : summary.entries()) {
            message.append(Component.literal("\n"));
            message.append(Component.literal(entry.activitySummary()
                            + " "
                            + entry.occupiedSlots()
                            + "/"
                            + entry.maxPartySize()
                            + " | "
                            + entry.leaderName()
                            + " ")
                    .withStyle(OPEN_PARTY_REMINDER_TEXT_COLOR));
            message.append(NotificationAccessor.wynnPill(
                    "JOIN #" + entry.listingId(),
                    ChatFormatting.GREEN,
                    ChatFormatting.WHITE,
                    new ClickEvent.RunCommand(entry.joinCommand())));
        }

        player.displayClientMessage(message, false);
    }

    private void notifyLocalPartyUpdateUx(Listing previousListing, Listing updatedListing, String myUUID) {
        boolean wasInParty = listingContainsPlayer(previousListing, myUUID);
        boolean isInParty = listingContainsPlayer(updatedListing, myUUID);

        if (wasInParty && !isInParty) {
            notify("You are no longer in party #" + updatedListing.id() + ".");
            return;
        }

        if (!wasInParty && isInParty) {
            notify("You joined party #" + updatedListing.id() + ".");
        }

        if (!isInParty) {
            return;
        }

        if (!uuidEquals(previousListing.leaderUUID(), updatedListing.leaderUUID())) {
            if (uuidEquals(myUUID, updatedListing.leaderUUID())) {
                notify("You are now party leader.");
            } else if (uuidEquals(myUUID, previousListing.leaderUUID())) {
                notifyResolvedPlayerMessage(
                        updatedListing.leaderUUID(), "A player is now party leader.", " is now party leader.");
            }
        }

        PartyRole previousRole = resolveMemberRole(previousListing, myUUID);
        PartyRole updatedRole = resolveMemberRole(updatedListing, myUUID);
        if (previousRole != null && updatedRole != null && previousRole != updatedRole) {
            notify("Your role is now " + formatRoleName(updatedRole) + ".");
        }

        if (previousListing.status() != updatedListing.status()) {
            switch (updatedListing.status()) {
                case OPEN -> notify("Party is now open.");
                case CLOSED -> notify("Party is now closed.");
                case FULL -> {
                    if (!uuidEquals(myUUID, updatedListing.leaderUUID())) {
                        notify("Party is now full.");
                    }
                }
                default -> {}
            }
        }
    }

    private void notifyLeaderPartyUpdateUx(Listing previousListing, Listing updatedListing, String myUUID) {
        if (!uuidEquals(myUUID, updatedListing.leaderUUID())) {
            return;
        }

        maybeQueueLeaderStatusBanner(previousListing, updatedListing);

        List<String> joinedMembers = collectJoinedMemberUUIDs(previousListing, updatedListing, myUUID);
        if (!joinedMembers.isEmpty()) {
            notifyLeaderAboutJoinedMembers(joinedMembers);
        }

        List<String> departedMembers = collectDepartedMemberUUIDs(previousListing, updatedListing, myUUID);
        if (!departedMembers.isEmpty()) {
            notifyLeaderAboutDepartedMembers(departedMembers);
        }

        if (!isListingFull(previousListing) && isListingFull(updatedListing)) {
            notifyPartyFullWithInviteAllAction();
        }
    }

    private void maybeQueueLeaderStatusBanner(Listing previousListing, Listing updatedListing) {
        boolean wasAutoCapacityClosed = isAutoCapacityClosed(previousListing);
        boolean isAutoCapacityClosed = isAutoCapacityClosed(updatedListing);

        if (!wasAutoCapacityClosed && isAutoCapacityClosed) {
            pushUiStatus("Party auto-closed at capacity. It will reopen when a slot frees up.");
            return;
        }

        if (wasAutoCapacityClosed && updatedListing.status() == PartyStatus.OPEN) {
            pushUiStatus("Party auto-opened after a slot freed up.");
        }
    }

    private List<String> collectJoinedMemberUUIDs(Listing previousListing, Listing updatedListing, String localUUID) {
        if (previousListing == null
                || updatedListing == null
                || updatedListing.members() == null
                || updatedListing.members().isEmpty()) {
            return List.of();
        }

        Set<String> previousMembers = collectListingMemberKeys(previousListing);
        List<String> joinedMembers = new ArrayList<>();
        for (Member member : updatedListing.members()) {
            if (member == null) {
                continue;
            }

            String memberUUID = member.playerUUID();
            if (memberUUID == null || memberUUID.isBlank() || uuidEquals(localUUID, memberUUID)) {
                continue;
            }

            String memberKey = normalizeUuidLike(memberUUID);
            if (memberKey == null || previousMembers.contains(memberKey)) {
                continue;
            }
            joinedMembers.add(memberUUID);
        }
        return joinedMembers;
    }

    private List<String> collectDepartedMemberUUIDs(Listing previousListing, Listing updatedListing, String localUUID) {
        if (previousListing == null
                || previousListing.members() == null
                || previousListing.members().isEmpty()) {
            return List.of();
        }

        Set<String> updatedMembers = collectListingMemberKeys(updatedListing);
        List<String> departedMembers = new ArrayList<>();
        for (Member member : previousListing.members()) {
            if (member == null) {
                continue;
            }

            String memberUUID = member.playerUUID();
            if (memberUUID == null || memberUUID.isBlank() || uuidEquals(localUUID, memberUUID)) {
                continue;
            }

            String memberKey = normalizeUuidLike(memberUUID);
            if (memberKey == null || updatedMembers.contains(memberKey)) {
                continue;
            }
            departedMembers.add(memberUUID);
        }
        return departedMembers;
    }

    private void notifyLeaderAboutJoinedMembers(List<String> joinedMemberUUIDs) {
        if (joinedMemberUUIDs == null || joinedMemberUUIDs.isEmpty()) {
            return;
        }

        if (joinedMemberUUIDs.size() > 1) {
            notify(joinedMemberUUIDs.size() + " players joined your party.");
            return;
        }

        String joinedMemberUUID = joinedMemberUUIDs.get(0);
        notifyResolvedPlayerMessage(joinedMemberUUID, "Player joined your party.", " joined your party.");
    }

    private void notifyLeaderAboutDepartedMembers(List<String> departedMemberUUIDs) {
        if (departedMemberUUIDs == null || departedMemberUUIDs.isEmpty()) {
            return;
        }

        if (departedMemberUUIDs.size() > 1) {
            notify(departedMemberUUIDs.size() + " players left your party.");
            return;
        }

        String departedMemberUUID = departedMemberUUIDs.get(0);
        notifyResolvedPlayerMessage(departedMemberUUID, "Player left your party.", " left your party.");
    }

    private void notifyResolvedPlayerMessage(String playerUUID, String fallbackMessage, String suffixMessage) {
        if (playerUUID == null || playerUUID.isBlank()) {
            notify(fallbackMessage);
            return;
        }

        PlayerNameCache.resolveAsync(playerUUID)
                .completeOnTimeout(null, INVITE_NAME_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .whenComplete((resolvedName, error) -> {
                    String memberName = formatInviterName(resolvedName);
                    if ("a player".equals(memberName)) {
                        notify(fallbackMessage);
                        return;
                    }
                    notify(memberName + suffixMessage);
                });
    }

    private static PartyRole resolveMemberRole(Listing listing, String playerUUID) {
        if (listing == null || playerUUID == null || playerUUID.isBlank() || listing.members() == null) {
            return null;
        }

        for (Member member : listing.members()) {
            if (member == null) {
                continue;
            }

            if (uuidEquals(playerUUID, member.playerUUID())) {
                return member.role();
            }
        }
        return null;
    }

    private void notifyPartyFullWithInviteAllAction() {
        SeqClient.mc.execute(() -> {
            var player = SeqClient.mc.player;
            if (player == null) {
                return;
            }

            MutableComponent fullMessage = NotificationAccessor.prefixComponent()
                    .append(Component.literal("Party is now full.").withStyle(ChatFormatting.GRAY));

            ClickEvent inviteAllClickEvent = new ClickEvent.RunCommand(SEQ_INVITE_ALL_COMMAND);
            MutableComponent actionMessage = Component.empty()
                    .append(NotificationAccessor.wynnPill(
                            "invite all", ChatFormatting.GREEN, ChatFormatting.WHITE, inviteAllClickEvent));

            player.displayClientMessage(fullMessage, false);
            player.displayClientMessage(actionMessage, false);
        });
    }

    private static String quoteForCommand(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static String formatInviterName(String inviterName) {
        if (inviterName == null
                || inviterName.isBlank()
                || "Loading...".equalsIgnoreCase(inviterName)
                || "Unknown".equalsIgnoreCase(inviterName)) {
            return "a player";
        }
        return inviterName;
    }

    private static String formatActivitySummary(Listing listing) {
        if (listing == null) {
            return "";
        }

        return listing.resolvedActivities().stream()
                .map(Activity::name)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .map(PartyFinderManager::abbreviateActivityName)
                .distinct()
                .collect(Collectors.joining("/"));
    }

    private static String abbreviateActivityName(String activityName) {
        return switch (activityName) {
            case "Nest of the Grootslangs" -> "NOG";
            case "The Nameless Anomaly" -> "TNA";
            case "The Canyon Colossus" -> "TCC";
            case "Nexus of Light" -> "NOL";
            case "Prelude to Annihilation" -> "ANNI";
            default -> activityName;
        };
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
            cachedParties = listings.stream()
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
        if (currentListing == null) return -1;
        List<PartyListing> parties = getParties();
        for (int i = 0; i < parties.size(); i++) {
            if (parties.get(i).id == currentListing.id()) return i;
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
            if (pr != null) changeMyRole(pr);
        }
    }

    /** Transfers leadership by party/member index. */
    public void promoteMember(int partyIndex, int memberIndex) {
        List<PartyListing> parties = getParties();
        if (partyIndex < 0 || partyIndex >= parties.size()) return;
        PartyListing party = parties.get(partyIndex);
        if (memberIndex < 0 || memberIndex >= party.members.size()) return;
        PartyMember member = party.members.get(memberIndex);
        try {
            String formattedTargetUuid = PlayerNameCache.formatUUID(member.playerUUID);
            if (formattedTargetUuid == null) {
                throw new IllegalArgumentException("Unparseable member UUID");
            }
            UUID targetUUID = UUID.fromString(formattedTargetUuid);
            transferLeadership(party.id, targetUUID).thenAccept(listing -> {
                if (listing != null) {
                    sendGamePartyCommandForUuid(targetUUID, GAME_PARTY_PROMOTE_PREFIX);
                    sendGameDirectMessage(targetUUID, "You were promoted to party leader.");
                }
            });
        } catch (IllegalArgumentException e) {
            SeqClient.LOGGER.error("Invalid UUID for promote: {}", member.playerUUID, e);
        }
    }

    /**
     * Kicks a member by party/member index (overload of kickMember(long, UUID)).
     */
    public void kickMember(int partyIndex, int memberIndex) {
        List<PartyListing> parties = getParties();
        if (partyIndex < 0 || partyIndex >= parties.size()) return;
        PartyListing party = parties.get(partyIndex);
        if (memberIndex < 0 || memberIndex >= party.members.size()) return;
        PartyMember member = party.members.get(memberIndex);
        try {
            String formattedTargetUuid = PlayerNameCache.formatUUID(member.playerUUID);
            if (formattedTargetUuid == null) {
                throw new IllegalArgumentException("Unparseable member UUID");
            }
            UUID targetUUID = UUID.fromString(formattedTargetUuid);
            kickMember(party.id, targetUUID).thenAccept(listing -> {
                if (listing != null) {
                    sendGamePartyCommandForUuid(targetUUID, GAME_PARTY_KICK_PREFIX);
                    sendGameDirectMessage(targetUUID, "You were kicked from the party.");
                }
            });
        } catch (IllegalArgumentException e) {
            SeqClient.LOGGER.error("Invalid UUID for kick: {}", member.playerUUID, e);
        }
    }

    /** Joins a party by index with a display-name role string. */
    public void joinParty(int partyIndex, String roleString) {
        List<PartyListing> parties = getParties();
        if (partyIndex < 0 || partyIndex >= parties.size()) return;
        PartyListing party = parties.get(partyIndex);
        PartyRole role = mapDisplayRole(roleString);
        if (role == null) role = PartyRole.DPS;
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
        Listing requestedListing = currentListing;
        long requestedListingId = requestedListing.id();
        String reservedSlotError = validateReservedSlotsForInvite(requestedListing);
        if (reservedSlotError != null) {
            pushUiError(reservedSlotError);
            return;
        }
        SeqClient.LOGGER.info(
                "[PartyFinderWS] createInvite requested listingId={} username='{}'",
                requestedListingId,
                normalizedUsername);
        if (!normalizedUsername.matches("[A-Za-z0-9_]{3,16}")) {
            pushUiError("Enter a valid Minecraft username.");
            return;
        }

        var player = SeqClient.mc.player;
        String myUsername =
                player != null && player.getName() != null ? player.getName().getString() : null;
        if (myUsername != null && myUsername.equalsIgnoreCase(normalizedUsername)) {
            pushUiError("You cannot invite yourself.");
            return;
        }

        PlayerNameCache.resolveUUID(normalizedUsername)
                .thenCompose(resolvedUUID -> {
                    SeqClient.LOGGER.info(
                            "[PartyFinderWS] createInvite resolved username='{}' rawUUID='{}'",
                            normalizedUsername,
                            resolvedUUID);
                    if (resolvedUUID == null || resolvedUUID.isBlank()) {
                        pushUiError("Unable to find that player UUID.");
                        return CompletableFuture.completedFuture(false);
                    }

                    String formattedResolvedUuid = PlayerNameCache.formatUUID(resolvedUUID);
                    if (formattedResolvedUuid == null) {
                        SeqClient.LOGGER.warn("Unable to normalize resolved invite UUID: {}", resolvedUUID);
                        pushUiError("Unable to resolve a valid UUID for that player.");
                        return CompletableFuture.completedFuture(false);
                    }

                    UUID targetUUID;
                    try {
                        targetUUID = UUID.fromString(formattedResolvedUuid);
                    } catch (IllegalArgumentException e) {
                        SeqClient.LOGGER.warn("Unable to parse resolved invite UUID: {}", resolvedUUID, e);
                        pushUiError("Unable to resolve a valid UUID for that player.");
                        return CompletableFuture.completedFuture(false);
                    }

                    String myUUID = getLocalPlayerUUID();
                    SeqClient.LOGGER.info(
                            "[PartyFinderWS] createInvite normalized targetUUID={} myUUID={} listingId={}",
                            targetUUID,
                            myUUID,
                            requestedListingId);
                    if (myUUID != null && myUUID.equalsIgnoreCase(targetUUID.toString())) {
                        pushUiError("You cannot invite yourself.");
                        return CompletableFuture.completedFuture(false);
                    }

                    if (currentListing == null || currentListing.id() != requestedListingId) {
                        pushUiError("Your active party changed before the invite was created. Try again.");
                        return CompletableFuture.completedFuture(false);
                    }
                    if (!isPartyLeader()) {
                        pushUiError("Only the party leader can invite players.");
                        return CompletableFuture.completedFuture(false);
                    }

                    String latestReservedSlotError = validateReservedSlotsForInvite(currentListing);
                    if (latestReservedSlotError != null) {
                        pushUiError(latestReservedSlotError);
                        return CompletableFuture.completedFuture(false);
                    }

                    SeqClient.LOGGER.info(
                            "[PartyFinderWS] createInvite calling API listingId={} targetUUID={}",
                            requestedListingId,
                            targetUUID);
                    return ApiClient.getInstance()
                            .createInvite(requestedListingId, targetUUID)
                            .thenApply(ignored -> true);
                })
                .thenAccept(inviteCreated -> {
                    if (Boolean.TRUE.equals(inviteCreated)) {
                        SeqClient.LOGGER.info("[PartyFinderWS] createInvite API success; reloading listings");
                        notify("Party Finder invite created.");
                        loadListings(null, null);
                    } else {
                        SeqClient.LOGGER.warn("[PartyFinderWS] createInvite did not complete successfully");
                    }
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to create party invite", "Failed to create invite");
                    return null;
                });
    }

    public CompletableFuture<InviteAllResult> inviteAllCurrentMembers() {
        return inviteAllCurrentMembersInternal(true);
    }

    private CompletableFuture<InviteAllResult> inviteAllCurrentMembersInternal(boolean notifyPlayer) {
        if (currentListing == null) {
            return CompletableFuture.completedFuture(
                    finishInviteAll(false, false, 0, 0, "no active party found.", notifyPlayer));
        }

        if (!isPartyLeader()) {
            return CompletableFuture.completedFuture(
                    finishInviteAll(false, false, 0, 0, "only the party leader can use this.", notifyPlayer));
        }

        var player = SeqClient.mc.player;
        if (player == null || player.connection == null) {
            return CompletableFuture.completedFuture(
                    finishInviteAll(false, false, 0, 0, "client connection unavailable.", notifyPlayer));
        }

        Listing listingSnapshot = currentListing;
        long listingId = listingSnapshot.id();
        List<Member> members = listingSnapshot.members();
        if (members == null || members.isEmpty()) {
            return CompletableFuture.completedFuture(
                    finishInviteAll(true, false, 0, 0, "no valid party members to invite.", notifyPlayer));
        }

        String myUUID = getLocalPlayerUUID();
        List<InviteAllCandidate> inviteCandidates = new ArrayList<>();
        int skippedCount = 0;

        for (Member member : members) {
            if (member == null) {
                skippedCount++;
                continue;
            }

            String memberUUID = member.playerUUID();
            if (memberUUID == null || memberUUID.isBlank()) {
                skippedCount++;
                continue;
            }

            if (uuidEquals(myUUID, memberUUID)) {
                continue;
            }

            inviteCandidates.add(new InviteAllCandidate(
                    memberUUID, PlayerNameCache.resolveAsync(memberUUID).exceptionally(ignored -> null)));
        }

        if (inviteCandidates.isEmpty()) {
            return CompletableFuture.completedFuture(finishInviteAll(
                    true, false, 0, skippedCount, formatInviteAllMessage(0, skippedCount), notifyPlayer));
        }

        int baseSkippedCount = skippedCount;
        return CompletableFuture.allOf(inviteCandidates.stream()
                        .map(InviteAllCandidate::usernameFuture)
                        .toArray(CompletableFuture[]::new))
                .thenCompose(ignored -> {
                    if (currentListing == null || currentListing.id() != listingId) {
                        return CompletableFuture.completedFuture(finishInviteAll(
                                false,
                                false,
                                0,
                                baseSkippedCount,
                                "your active party changed before invites could be sent.",
                                notifyPlayer));
                    }

                    List<ResolvedInviteTarget> targets = new ArrayList<>();
                    Set<String> seenTargets = new LinkedHashSet<>();
                    int resolvedSkippedCount = baseSkippedCount;

                    for (InviteAllCandidate inviteCandidate : inviteCandidates) {
                        String username = inviteCandidate.usernameFuture().getNow(null);
                        if (username == null
                                || username.isBlank()
                                || "Loading...".equalsIgnoreCase(username)
                                || "Unknown".equalsIgnoreCase(username)
                                || !username.matches("[A-Za-z0-9_]{3,16}")) {
                            resolvedSkippedCount++;
                            continue;
                        }

                        String normalizedUsername = username.toLowerCase(Locale.ROOT);
                        if (!seenTargets.add(normalizedUsername)) {
                            resolvedSkippedCount++;
                            continue;
                        }

                        targets.add(new ResolvedInviteTarget(inviteCandidate.memberUUID(), username));
                    }

                    if (targets.isEmpty()) {
                        return CompletableFuture.completedFuture(finishInviteAll(
                                true,
                                false,
                                0,
                                resolvedSkippedCount,
                                formatInviteAllMessage(0, resolvedSkippedCount),
                                notifyPlayer));
                    }

                    CompletableFuture<InviteAllResult> dispatchFuture = new CompletableFuture<>();
                    int finalSkippedCount = resolvedSkippedCount;
                    SeqClient.mc.execute(() -> {
                        var currentPlayer = SeqClient.mc.player;
                        if (currentPlayer == null || currentPlayer.connection == null) {
                            dispatchFuture.complete(finishInviteAll(
                                    false,
                                    false,
                                    0,
                                    finalSkippedCount,
                                    "client connection unavailable.",
                                    notifyPlayer));
                            return;
                        }

                        if (currentListing == null || currentListing.id() != listingId) {
                            dispatchFuture.complete(finishInviteAll(
                                    false,
                                    false,
                                    0,
                                    finalSkippedCount,
                                    "your active party changed before invites could be sent.",
                                    notifyPlayer));
                            return;
                        }
                        if (!isPartyLeader()) {
                            dispatchFuture.complete(finishInviteAll(
                                    false,
                                    false,
                                    0,
                                    finalSkippedCount,
                                    "only the party leader can use this.",
                                    notifyPlayer));
                            return;
                        }

                        Set<String> activeMemberKeys = collectListingMemberKeys(currentListing);
                        int sentCount = 0;
                        int skippedAtDispatch = finalSkippedCount;
                        for (ResolvedInviteTarget target : targets) {
                            String memberKey = normalizeUuidLike(target.memberUUID());
                            if (memberKey == null || !activeMemberKeys.contains(memberKey)) {
                                skippedAtDispatch++;
                                continue;
                            }
                            currentPlayer.connection.sendCommand(GAME_PARTY_INVITE_PREFIX + target.username());
                            sentCount++;
                        }

                        dispatchFuture.complete(finishInviteAll(
                                true,
                                sentCount > 0,
                                sentCount,
                                skippedAtDispatch,
                                formatInviteAllMessage(sentCount, skippedAtDispatch),
                                notifyPlayer));
                    });
                    return dispatchFuture;
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
    public void createParty(
            List<String> tags, String role, int reservedSlots, boolean strictRoles, PartyRegion region) {
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
            Activity activity = activities.stream()
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
        final boolean strict = mode == PartyMode.GRIND && strictRoles;
        final PartyRegion selectedRegion = region != null ? region : PartyRegion.NA;

        int requestedReservedSlots = reservedSlots;
        createParty(activityIds, mode, strict, selectedRegion, createRole, null).thenAccept(listing -> {
            if (listing == null) {
                return;
            }
            applyReservedSlotTarget(listing.id(), requestedReservedSlots, "create");
        });
    }

    public void updateParty(
            List<String> tags, String role, int reservedSlots, boolean strictRoles, PartyRegion region) {
        if (currentListing == null) {
            pushUiError("Unable to update party: no active listing.");
            return;
        }

        int requestedReservedSlots = reservedSlots;

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
            Activity activity = activities.stream()
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

        PartyRegion selectedRegion =
                region != null ? region : (currentListing.region() != null ? currentListing.region() : PartyRegion.NA);
        boolean strict = mode == PartyMode.GRIND && strictRoles;
        ApiClient.getInstance()
                .updateListing(currentListing.id(), activityIds, mode, strict, selectedRegion, currentListing.note())
                .thenAccept(listing -> {
                    if (listing != null) {
                        applyUpdatedCurrentListingState(listing);
                        applyReservedSlotTarget(listing.id(), requestedReservedSlots, "update");
                    }
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to update party", "Failed to update party");
                    return null;
                });
    }

    private void pushUiBanner(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        latestPartyError = message;
    }

    public void pushUiError(String message) {
        pushUiBanner(message);
    }

    public void pushUiStatus(String message) {
        pushUiBanner(message);
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

    CompletableFuture<List<Listing>> refreshListingsForAnnouncements() {
        return refreshListingsSnapshot(null, null, (message, error) ->
                SeqClient.LOGGER.warn("[PartyFinderWS] Failed to refresh listings for open-party reminders: {}", message, error));
    }

    // ══════════════════════════════════════════════════════════════
    // Internal helpers
    // ══════════════════════════════════════════════════════════════

    private boolean shouldRunOpenPartyAnnouncements() {
        if (SeqClient.getAnnounceOpenPartiesSetting() == null
                || SeqClient.getAnnounceOpenPartiesIntervalMinutesSetting() == null) {
            return false;
        }

        if (!SeqClient.getAnnounceOpenPartiesSetting().getValue()) {
            return false;
        }

        return ConnectionManager.isConnected() && !isInParty();
    }

    private long resolveOpenPartyAnnouncementIntervalMs() {
        int intervalMinutes = 5;
        if (SeqClient.getAnnounceOpenPartiesIntervalMinutesSetting() != null) {
            intervalMinutes = SeqClient.getAnnounceOpenPartiesIntervalMinutesSetting().getValue();
        }
        return Math.max(1L, intervalMinutes) * 60_000L;
    }

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

    private CompletableFuture<List<Listing>> refreshListingsSnapshot(
            Long activityId, PartyRegion region, BiConsumer<String, Throwable> failureHandler) {
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
                    return List.copyOf(deduped);
                })
                .exceptionally(e -> {
                    String message = extractUserFriendlyApiError(e, "Failed to load listings");
                    if (failureHandler != null) {
                        failureHandler.accept(message, e);
                    }
                    return null;
                });
    }

    static List<Listing> selectOpenPartyAnnouncementCandidates(List<Listing> source, String myUUID) {
        if (source == null || source.isEmpty() || myUUID == null || myUUID.isBlank()) {
            return List.of();
        }

        return source.stream()
                .filter(Objects::nonNull)
                .filter(listing -> listing.status() == PartyStatus.OPEN)
                .filter(listing -> listing.occupiedSlotCount() < listing.maxPartySize())
                .filter(listing -> !uuidEquals(myUUID, listing.leaderUUID()))
                .filter(listing -> !listingContainsPlayer(listing, myUUID))
                .filter(listing -> !listingHasReservedSlotForPlayer(listing, myUUID))
                .sorted(Comparator.comparing(Listing::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    static OpenPartyAnnouncementSummary buildOpenPartyAnnouncementSummary(
            List<Listing> candidates, Function<String, String> leaderNameResolver) {
        if (candidates == null || candidates.isEmpty()) {
            return new OpenPartyAnnouncementSummary(List.of());
        }

        Function<String, String> resolver = leaderNameResolver != null
                ? leaderNameResolver
                : PartyFinderManager::resolveReminderLeaderName;

        List<OpenPartyAnnouncementEntry> entries = candidates.stream()
                .filter(Objects::nonNull)
                .map(listing -> new OpenPartyAnnouncementEntry(
                        listing.id(),
                        defaultReminderActivitySummary(formatActivitySummary(listing)),
                        listing.occupiedSlotCount(),
                        listing.maxPartySize(),
                        resolver.apply(listing.leaderUUID()),
                        "/seq p join " + listing.id()))
                .toList();

        return new OpenPartyAnnouncementSummary(entries);
    }

    private static String defaultReminderActivitySummary(String summary) {
        if (summary == null || summary.isBlank()) {
            return "Party";
        }
        return summary;
    }

    private static String resolveReminderLeaderName(String leaderUUID) {
        String resolved = PlayerNameCache.resolve(leaderUUID);
        if (resolved == null || resolved.isBlank()) {
            return "Unknown";
        }
        return resolved;
    }

    private static String extractUserFriendlyApiError(Throwable throwable, String fallbackMessage) {
        ApiClient.ApiException apiException = findApiException(throwable);
        if (apiException == null) {
            return fallbackMessage;
        }

        int statusCode = apiException.getStatusCode();
        String responseBody = apiException.getResponseBody();
        String backendError = extractApiErrorMessage(responseBody);
        String statusMapped = mapStatusError(statusCode, responseBody, backendError);
        if (statusMapped != null) {
            return statusMapped;
        }

        String translatedBackendError = translatePartyFinderBackendError(backendError);
        if (translatedBackendError != null) {
            return translatedBackendError;
        }

        return fallbackMessage;
    }

    private static String mapStatusError(int statusCode, String responseBody, String backendError) {
        String body = responseBody == null ? "" : responseBody.toLowerCase(Locale.ROOT);
        if (body.contains("main_server_only")) {
            return WynncraftServerPolicy.MAIN_SERVER_ONLY_MESSAGE;
        }
        if (statusCode == 426 || body.contains("mod_version_unsupported")) {
            if (backendError != null) {
                return backendError;
            }
            String minimumSafeVersion = extractApiField(responseBody, "minimum_safe_version");
            if (minimumSafeVersion != null && !minimumSafeVersion.isBlank()) {
                return "Update Sequoia to at least " + minimumSafeVersion + ".";
            }
            return "Update Sequoia to a newer version.";
        }
        if (statusCode == 400 || statusCode == 422) {
            String translatedBackendError = translatePartyFinderBackendError(backendError);
            if (translatedBackendError != null) {
                return translatedBackendError;
            }
            return "Request rejected by backend validation. Check your inputs.";
        }
        if (statusCode == 401) {
            return "Authentication required. Please relink/login with /seq link.";
        }
        if (statusCode == 403) {
            if (body.contains("guild") || body.contains("not in guild")) {
                return "Access denied: your account is not in the guild.";
            }
            if (backendError != null) {
                return backendError;
            }
            return "Access denied by backend authorization.";
        }
        return null;
    }

    private static String translatePartyFinderBackendError(String backendError) {
        if (backendError == null || backendError.isBlank()) {
            return null;
        }

        String normalized = backendError.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "listing is not open for new members" -> "This party is currently closed by the leader.";
            case "party is already full" -> "This party is already full.";
            case "invite has expired" -> "This invite has expired.";
            case "invite is no longer active" -> "This invite is no longer active.";
            case "this invite was not intended for your linked account" ->
                "This invite was sent to a different linked account.";
            case "role is required" -> "Choose a role before joining this party.";
            case "listing has been disbanded", "listing has already been disbanded" ->
                "This party has already been disbanded.";
            case "listing is already closed" -> "This party is already closed.";
            case "only a closed listing can be reopened" -> "This party is not currently closed.";
            case "only the party leader can close the listing" -> "Only the party leader can close the party.";
            case "only the party leader can reopen the listing" -> "Only the party leader can reopen the party.";
            case "only the party leader can disband the listing" -> "Only the party leader can disband the party.";
            case "you are not a member of this party" -> "You are not in this party.";
            case "strict grind party allows at most 1 healer" -> "This strict grind party already has a healer.";
            case "strict grind party must include at least 1 tank" -> "This strict grind party still needs a tank.";
            case "strict grind party has too many dps slots filled" ->
                "This strict grind party has no DPS slots left.";
            default -> backendError;
        };
    }

    private static String extractApiErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonElement parsed = JsonParser.parseString(responseBody);
            if (!parsed.isJsonObject()) {
                return null;
            }

            JsonObject obj = parsed.getAsJsonObject();
            String error = getJsonString(obj, "error");
            if (error != null) {
                return error;
            }

            String message = getJsonString(obj, "message");
            if (message != null) {
                return message;
            }

            return getJsonString(obj, "detail");
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String extractApiField(String responseBody, String key) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }

        try {
            JsonElement parsed = JsonParser.parseString(responseBody);
            if (!parsed.isJsonObject()) {
                return null;
            }
            return getJsonString(parsed.getAsJsonObject(), key);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getJsonString(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null
                || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        String value = element.getAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
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

    private void handleActionError(Throwable throwable, String fallbackMessage, String logMessage) {
        String errorMessage = extractUserFriendlyApiError(throwable, fallbackMessage);
        SeqClient.LOGGER.warn("{}: {}", logMessage, errorMessage);
        pushUiError(errorMessage);
    }

    private InviteAllResult finishInviteAll(
            boolean success, boolean sentAny, int sentCount, int skippedCount, String message, boolean notifyPlayer) {
        if (notifyPlayer) {
            notify(message);
        }
        return new InviteAllResult(success, sentAny, sentCount, skippedCount, message);
    }

    private static String formatInviteAllMessage(int sentCount, int skippedCount) {
        if (sentCount <= 0) {
            if (skippedCount > 0) {
                return "no valid party members to invite. Skipped " + skippedCount + ".";
            }
            return "no valid party members to invite.";
        }

        String inviteWord = sentCount == 1 ? "party invite" : "party invites";
        if (skippedCount > 0) {
            return "sent " + sentCount + " " + inviteWord + ". Skipped " + skippedCount + ".";
        }
        return "sent " + sentCount + " " + inviteWord + ".";
    }

    private static <T> CompletableFuture<CommandResult<T>> completedCommandFailure(String message) {
        return CompletableFuture.completedFuture(CommandResult.failure(message));
    }

    private static CompletableFuture<CommandResult<Void>> completedCommandFailureVoid(String message) {
        return CompletableFuture.completedFuture(CommandResult.failure(message));
    }

    private <T> CommandResult<T> commandFailure(Throwable throwable, String fallbackMessage, String logMessage) {
        String errorMessage = extractUserFriendlyApiError(throwable, fallbackMessage);
        SeqClient.LOGGER.warn("{}: {}", logMessage, errorMessage);
        return CommandResult.failure(errorMessage);
    }

    private CompletableFuture<CommandResult<Listing>> executeListingCommand(
            CompletableFuture<Listing> apiFuture,
            Consumer<Listing> stateUpdater,
            String fallbackMessage,
            String logMessage,
            Function<Listing, String> successMessageBuilder) {
        return apiFuture
                .thenApply(listing -> {
                    stateUpdater.accept(listing);
                    return CommandResult.success(successMessageBuilder.apply(listing), listing);
                })
                .exceptionally(e -> commandFailure(e, fallbackMessage, logMessage));
    }

    private CompletableFuture<CommandResult<Void>> executeVoidCommand(
            CompletableFuture<Void> apiFuture, String fallbackMessage, String logMessage, String successMessage) {
        return apiFuture
                .thenApply(ignored -> CommandResult.<Void>success(successMessage, null))
                .exceptionally(e -> commandFailure(e, fallbackMessage, logMessage));
    }

    private CompletableFuture<CommandResult<Listing>> runLeaderListingCommand(
            String notLeaderMessage, Function<Listing, CompletableFuture<CommandResult<Listing>>> action) {
        return ensureCurrentListingForCommand().thenCompose(currentResult -> {
            if (!currentResult.success()) {
                return completedCommandFailure(currentResult.message());
            }
            if (!isPartyLeader()) {
                return completedCommandFailure(notLeaderMessage);
            }
            return action.apply(currentResult.data());
        });
    }

    private CompletableFuture<CommandResult<Listing>> ensureCurrentListingForCommand() {
        if (currentListing != null) {
            return CompletableFuture.completedFuture(CommandResult.success("Current listing ready.", currentListing));
        }

        return refreshListingsForCommand().thenApply(result -> {
            if (!result.success()) {
                return CommandResult.failure(result.message());
            }
            Listing fallbackListing = findActiveLedListingForCommand();
            if (fallbackListing != null) {
                currentListing = fallbackListing;
                return CommandResult.success("Resolved your led listing.", fallbackListing);
            }
            if (currentListing == null) {
                return CommandResult.failure("You are not currently in a Sequoia party.");
            }
            return CommandResult.success("Current listing loaded.", currentListing);
        });
    }

    private CommandResult<ActivityResolution> resolveActivitiesForCommand(
            Collection<String> activityInputs, boolean rejectUnresolved) {
        if (activityInputs == null || activityInputs.isEmpty()) {
            return CommandResult.failure("Provide at least one activity.");
        }

        LinkedHashSet<String> normalizedInputs = new LinkedHashSet<>();
        for (String rawInput : activityInputs) {
            if (rawInput == null) {
                continue;
            }
            String trimmed = rawInput.trim();
            if (!trimmed.isEmpty()) {
                normalizedInputs.add(trimmed);
            }
        }

        if (normalizedInputs.isEmpty()) {
            return CommandResult.failure("Provide at least one activity.");
        }

        List<Long> activityIds = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        LinkedHashSet<String> displayNames = new LinkedHashSet<>();

        for (String activityInput : normalizedInputs) {
            String searchName = PartyListing.displayNameToBackendName(activityInput);
            Activity activity = activities.stream()
                    .filter(candidate -> matchesActivityName(candidate, activityInput, searchName))
                    .findFirst()
                    .orElse(null);

            if (activity == null) {
                unresolved.add(activityInput);
                continue;
            }

            if (!activityIds.contains(activity.id())) {
                activityIds.add(activity.id());
            }
            displayNames.add(PartyListing.backendNameToDisplayName(activity.name()));
        }

        if (displayNames.contains("Prelude to Annihilation") && displayNames.size() > 1) {
            return CommandResult.failure("Prelude to Annihilation cannot be combined with other activities.");
        }

        if (activityIds.isEmpty()) {
            return CommandResult.failure("Unknown activities: " + String.join(", ", unresolved) + ".");
        }

        if (rejectUnresolved && !unresolved.isEmpty()) {
            return CommandResult.failure("Unknown activities: " + String.join(", ", unresolved) + ".");
        }

        return CommandResult.success(
                "Resolved " + displayNames.size() + " activities.",
                new ActivityResolution(List.copyOf(activityIds), List.copyOf(unresolved), List.copyOf(displayNames)));
    }

    private CompletableFuture<CommandResult<UUID>> resolveUuidForCommand(String username) {
        return PlayerNameCache.resolveUUID(username).thenApply(resolvedUuid -> {
            if (resolvedUuid == null || resolvedUuid.isBlank()) {
                return CommandResult.failure("Unable to find a UUID for " + username + ".");
            }

            String formattedResolvedUuid = PlayerNameCache.formatUUID(resolvedUuid);
            if (formattedResolvedUuid == null) {
                SeqClient.LOGGER.warn("Unable to normalize resolved UUID {}", resolvedUuid);
                return CommandResult.failure("Unable to resolve a valid UUID for " + username + ".");
            }

            try {
                return CommandResult.success("Resolved UUID.", UUID.fromString(formattedResolvedUuid));
            } catch (IllegalArgumentException e) {
                SeqClient.LOGGER.warn("Unable to parse resolved UUID {}", resolvedUuid, e);
                return CommandResult.failure("Unable to resolve a valid UUID for " + username + ".");
            }
        });
    }

    private CompletableFuture<CommandResult<ListingMemberTarget>> resolveCurrentMemberTargetForCommand(
            String username, boolean requireLeader) {
        String validationMessage = validateUsername(username, false);
        if (validationMessage != null) {
            return completedCommandFailure(validationMessage);
        }

        String normalizedUsername = username.trim();
        return ensureCurrentListingForCommand().thenCompose(currentResult -> {
            if (!currentResult.success()) {
                return completedCommandFailure(currentResult.message());
            }
            if (requireLeader && !isPartyLeader()) {
                return completedCommandFailure("Only the party leader can manage party members.");
            }

            Listing listing = currentResult.data();
            return resolveUuidForCommand(normalizedUsername).thenApply(uuidResult -> {
                if (!uuidResult.success()) {
                    return CommandResult.failure(uuidResult.message());
                }

                UUID targetUUID = uuidResult.data();
                Member targetMember = findMemberByUuid(listing, targetUUID);
                if (targetMember == null) {
                    return CommandResult.failure(normalizedUsername + " is not in your Sequoia party.");
                }

                return CommandResult.success(
                        "Resolved target member.", new ListingMemberTarget(listing, targetUUID, normalizedUsername));
            });
        });
    }

    private static Member findMemberByUuid(Listing listing, UUID targetUUID) {
        if (listing == null || targetUUID == null || listing.members() == null) {
            return null;
        }

        for (Member member : listing.members()) {
            if (member != null && uuidEquals(targetUUID.toString(), member.playerUUID())) {
                return member;
            }
        }

        return null;
    }

    private CommandResult<Void> sendGamePartyCreateCommand() {
        if (SeqClient.mc.player == null || SeqClient.mc.player.connection == null) {
            return CommandResult.failure("Client connection unavailable.");
        }

        SeqClient.mc.player.connection.sendCommand(GAME_PARTY_CREATE_COMMAND);
        return CommandResult.success("Sent Wynn party create command.", null);
    }

    private CommandResult<Void> sendGamePartyInviteCommand(String username) {
        String validationMessage = validateUsername(username, false);
        if (validationMessage != null) {
            return CommandResult.failure(validationMessage);
        }
        if (SeqClient.mc.player == null || SeqClient.mc.player.connection == null) {
            return CommandResult.failure("Client connection unavailable.");
        }

        String normalizedUsername = username.trim();
        if (SeqClient.mc.player.getName() != null
                && SeqClient.mc.player.getName().getString().equalsIgnoreCase(normalizedUsername)) {
            return CommandResult.failure("You cannot invite yourself.");
        }
        SeqClient.mc.player.connection.sendCommand(GAME_PARTY_INVITE_PREFIX + normalizedUsername);
        return CommandResult.success("Sent Wynn party invite to " + normalizedUsername + ".", null);
    }

    private void sendGamePartyCommandForUuid(UUID targetUUID, String commandPrefix) {
        if (targetUUID == null || commandPrefix == null || commandPrefix.isBlank()) {
            return;
        }

        PlayerNameCache.resolveAsync(targetUUID.toString())
                .completeOnTimeout(null, INVITE_NAME_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .thenAccept(resolvedName -> {
                    if (resolvedName == null
                            || resolvedName.isBlank()
                            || "Loading...".equalsIgnoreCase(resolvedName)
                            || "Unknown".equalsIgnoreCase(resolvedName)
                            || !resolvedName.matches("[A-Za-z0-9_]{3,16}")) {
                        SeqClient.LOGGER.warn(
                                "Skipping party command '{}' for unresolved UUID {}", commandPrefix, targetUUID);
                        return;
                    }

                    SeqClient.mc.execute(() -> {
                        var player = SeqClient.mc.player;
                        if (player == null || player.connection == null) {
                            return;
                        }
                        player.connection.sendCommand(commandPrefix + resolvedName);
                    });
                });
    }

    private static String validateUsername(String username, boolean rejectSelf) {
        if (username == null || username.isBlank()) {
            return "Enter a valid Minecraft username.";
        }

        String normalizedUsername = username.trim();
        if (!normalizedUsername.matches("[A-Za-z0-9_]{3,16}")) {
            return "Enter a valid Minecraft username.";
        }

        if (rejectSelf) {
            var player = SeqClient.mc.player;
            String myUsername = player != null && player.getName() != null
                    ? player.getName().getString()
                    : null;
            if (myUsername != null && myUsername.equalsIgnoreCase(normalizedUsername)) {
                return "You cannot target yourself.";
            }
        }

        return null;
    }

    private static String formatActivityNames(Collection<String> displayNames) {
        if (displayNames == null || displayNames.isEmpty()) {
            return "Unknown Activity";
        }
        return displayNames.stream().map(PartyListing::displayNameToBackendName).collect(Collectors.joining(", "));
    }

    private static String formatRoleName(PartyRole role) {
        if (role == null) {
            return "DPS";
        }
        return switch (role) {
            case DPS -> "DPS";
            case HEALER -> "Healer";
            case TANK -> "Tank";
        };
    }

    private void applyCreatedListingState(Listing listing) {
        replaceListing(listing);
        boolean shouldPublishClassUpdate = false;
        if (currentListing == null || currentListing.id() == listing.id()) {
            currentListing = listing;
            shouldPublishClassUpdate = true;
        }
        listingsVersion++;
        if (shouldPublishClassUpdate) {
            publishLocalClassUpdate();
        }
    }

    private void applyJoinedListingState(Listing listing) {
        replaceListing(listing);
        boolean shouldPublishClassUpdate = false;
        if (currentListing == null || currentListing.id() == listing.id()) {
            currentListing = listing;
            shouldPublishClassUpdate = true;
        }
        listingsVersion++;
        if (shouldPublishClassUpdate) {
            publishLocalClassUpdate();
        }
    }

    private void applyUpdatedCurrentListingState(Listing listing) {
        replaceListing(listing);
        if (currentListing != null && currentListing.id() == listing.id()) {
            currentListing = listing;
        }
        listingsVersion++;
    }

    private void applyLeftListingState(Listing listing) {
        replaceListing(listing);
        if (currentListing != null && currentListing.id() == listing.id()) {
            currentListing = null;
        }
        listingsVersion++;
    }

    private void applyDisbandedListingState(long listingId) {
        listings.removeIf(l -> l.id() == listingId);
        if (currentListing != null && currentListing.id() == listingId) {
            currentListing = null;
        }
        listingsVersion++;
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

    private void applyReservedSlotTarget(long listingId, int requestedReservedSlots, String context) {
        CompletableFuture<Listing> adjustFuture =
                ApiClient.getInstance().reserveSlots(listingId, requestedReservedSlots);

        adjustFuture
                .thenAccept(updatedListing -> {
                    if (updatedListing != null) {
                        applyUpdatedCurrentListingState(updatedListing);
                    }
                })
                .exceptionally(e -> {
                    handleActionError(e, "Unable to set reserved slots", "Failed to set reserved slots on " + context);
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
                continue;
            }

            String playerUUID = member.playerUUID();
            if (playerUUID == null || playerUUID.isBlank()) {
                continue;
            }

            String normalized = playerUUID.trim().toLowerCase(Locale.ROOT);
            if ("reserved".equals(normalized)) {
                count++;
            }
        }
        return count;
    }

    private static String validateReservedSlotsForInvite(Listing listing) {
        if (inferReservedSlotCount(listing) > 0) {
            return null;
        }
        return "Add at least one reserved slot before creating a party finder invite.";
    }

    private static boolean isListingFull(Listing listing) {
        if (listing == null) {
            return false;
        }
        if (listing.status() == PartyStatus.FULL) {
            return true;
        }
        int maxPartySize = listing.maxPartySize();
        return maxPartySize > 0 && listing.occupiedSlotCount() >= maxPartySize;
    }

    private static boolean isAutoCapacityClosed(Listing listing) {
        return listing != null
                && listing.status() == PartyStatus.CLOSED
                && listing.closeReason() == PartyCloseReason.AUTO_CAPACITY;
    }

    private Listing findListingById(long listingId) {
        synchronized (listingsLock) {
            for (Listing existing : listings) {
                if (existing != null && existing.id() == listingId) {
                    return existing;
                }
            }
        }
        return null;
    }

    private Listing findActiveLedListingForCommand() {
        String myUUID = getLocalPlayerUUID();
        if (myUUID == null || myUUID.isBlank()) {
            return null;
        }

        synchronized (listingsLock) {
            for (Listing listing : listings) {
                if (listing == null) {
                    continue;
                }
                if (listing.status() == PartyStatus.DISBANDED) {
                    continue;
                }
                if (uuidEquals(myUUID, listing.leaderUUID())) {
                    return listing;
                }
            }
        }
        return null;
    }

    private void refreshCurrentListing() {
        String myUUID = getLocalPlayerUUID();
        if (myUUID == null) {
            currentListing = null;
            return;
        }

        currentListing = listings.stream()
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

    private static boolean listingHasReservedSlotForPlayer(Listing listing, String myUUID) {
        if (listing == null || myUUID == null || myUUID.isBlank()) {
            return false;
        }

        List<Member> reservedSlots = listing.reservedSlots();
        if (reservedSlots == null || reservedSlots.isEmpty()) {
            return false;
        }

        for (Member reserved : reservedSlots) {
            if (reserved == null) {
                continue;
            }
            if (uuidEquals(myUUID, reserved.playerUUID())) {
                return true;
            }
        }

        return false;
    }

    private static Set<String> collectListingMemberKeys(Listing listing) {
        LinkedHashSet<String> memberKeys = new LinkedHashSet<>();
        if (listing == null || listing.members() == null) {
            return memberKeys;
        }

        for (Member member : listing.members()) {
            if (member == null) {
                continue;
            }

            String memberKey = normalizeUuidLike(member.playerUUID());
            if (memberKey != null) {
                memberKeys.add(memberKey);
            }
        }
        return memberKeys;
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
        if (formatted == null) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
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

    static String resolveIdentityUuid(String currentPlayerUuid, StoredAuthSession storedAuthSession) {
        if (storedAuthSession != null) {
            String authenticatedUuid = storedAuthSession.minecraftUuid();
            if (authenticatedUuid != null && !authenticatedUuid.isBlank()) {
                return authenticatedUuid;
            }
        }
        return currentPlayerUuid;
    }

    /**
     * Maps display role strings (from ROLES constant / dropdown) to PartyRole enum
     * values.
     */
    private static PartyRole mapDisplayRole(String displayRole) {
        if (displayRole == null) return null;
        return switch (displayRole.toUpperCase()) {
            case "DPS" -> PartyRole.DPS;
            case "HEALER" -> PartyRole.HEALER;
            case "TANK" -> PartyRole.TANK;
            default -> null;
        };
    }

    private static boolean matchesActivityName(Activity activity, String displayName, String backendSearchName) {
        if (activity == null || activity.name() == null) {
            return false;
        }

        String backendName = activity.name().trim();
        String mappedDisplay = PartyListing.backendNameToDisplayName(backendName);
        String candidateCode = PartyListing.displayNameToBackendName(mappedDisplay);

        String inputDisplay = PartyListing.backendNameToDisplayName(displayName);
        String inputCode = PartyListing.displayNameToBackendName(inputDisplay);

        String searchDisplay = PartyListing.backendNameToDisplayName(backendSearchName);
        String searchCode = PartyListing.displayNameToBackendName(searchDisplay);

        if (backendName.equalsIgnoreCase(backendSearchName)
                || backendName.equalsIgnoreCase(displayName)
                || mappedDisplay.equalsIgnoreCase(displayName)
                || mappedDisplay.equalsIgnoreCase(inputDisplay)
                || candidateCode.equalsIgnoreCase(backendSearchName)
                || candidateCode.equalsIgnoreCase(displayName)
                || candidateCode.equalsIgnoreCase(inputCode)
                || candidateCode.equalsIgnoreCase(searchCode)) {
            return true;
        }

        String normalizedBackend = normalizeActivityKey(backendName);
        String normalizedDisplay = normalizeActivityKey(displayName);
        String normalizedMappedDisplay = normalizeActivityKey(mappedDisplay);
        String normalizedBackendSearch = normalizeActivityKey(backendSearchName);
        String normalizedCandidateCode = normalizeActivityKey(candidateCode);
        String normalizedInputDisplay = normalizeActivityKey(inputDisplay);
        String normalizedInputCode = normalizeActivityKey(inputCode);
        String normalizedSearchDisplay = normalizeActivityKey(searchDisplay);
        String normalizedSearchCode = normalizeActivityKey(searchCode);

        return normalizedBackend.equals(normalizedDisplay)
                || normalizedMappedDisplay.equals(normalizedDisplay)
                || normalizedMappedDisplay.equals(normalizedInputDisplay)
                || normalizedBackend.equals(normalizedBackendSearch)
                || normalizedMappedDisplay.equals(normalizedSearchDisplay)
                || normalizedCandidateCode.equals(normalizedDisplay)
                || normalizedCandidateCode.equals(normalizedBackendSearch)
                || normalizedCandidateCode.equals(normalizedInputCode)
                || normalizedCandidateCode.equals(normalizedSearchCode);
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
