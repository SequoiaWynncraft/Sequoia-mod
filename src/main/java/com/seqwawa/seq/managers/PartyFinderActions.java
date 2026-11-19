package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.integrations.WynntilsWorldStateAccess;
import com.seqwawa.seq.managers.PartyFinderManager.CommandResult;
import com.seqwawa.seq.managers.PartyFinderManager.IssuedInvite;
import com.seqwawa.seq.managers.PartyFinderManager.Ticket;
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.PartyJoinPolicy;
import com.seqwawa.seq.model.PartyRegion;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.PartyStatus;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.utils.PlayerNameCache;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the player asks of the party finder's backend, from {@code /seq p} or the screen: load,
 * create, join, leave, invite, kick and the rest. Each request goes out with its session's own
 * client, under the {@link Ticket} read where the action started, and its answer changes the
 * listings on the game thread, only while that session lasts; a name looked up on the way is
 * handed to the game thread too. A command reports how it went; the screen shows a failure on
 * its banner, to the session and server connection it was clicked on.
 */
public final class PartyFinderActions {

    private static final String SESSION_ENDED = "Your account changed or you signed out before this finished.";

    private final PartyFinderManager partyFinder;
    private final PartyFinderPlayer player;

    private record ActivityResolution(List<Long> activityIds, List<String> unresolved, List<String> displayNames) {}

    private record MemberTarget(Listing listing, UUID uuid, String username) {}

    PartyFinderActions(PartyFinderManager partyFinder) {
        this.partyFinder = partyFinder;
        this.player = partyFinder.player;
    }

    // ══════════════════════════════════════════════════════════════
    // Loading
    // ══════════════════════════════════════════════════════════════

    /** Loads activities, then listings, showing a failure on the banner. Called when the screen opens. */
    public void refreshData() {
        Ticket ticket = partyFinder.ticket();
        CompletableFuture<List<Activity>> activities = fetchActivities(ticket);
        onScreen(ticket, activities, "Failed to load activities", "Failed to load activities");
        activities.handle((loaded, error) -> null).thenRun(() ->
                onScreen(ticket, fetchListings(ticket), "Failed to load listings", "Failed to load listings"));
    }

    /**
     * Reloads listings for a screen that only reads them, such as the guild members panel. A
     * failure is logged, not pushed to the party finder's banner, which would otherwise greet the
     * player the next time they open that screen.
     */
    public CompletableFuture<List<Listing>> refreshListingsQuietly() {
        return fetchListings(partyFinder.ticket()).exceptionally(error -> {
            SeqClient.LOGGER.debug("[PartyFinder] Quiet listings refresh failed: {}",
                    PartyFinderErrors.message(error, "Failed to load listings"), error);
            return null;
        });
    }

    CompletableFuture<List<Listing>> refreshListingsForAnnouncements(Ticket ticket) {
        return fetchListings(ticket).exceptionally(error -> {
            String message = PartyFinderErrors.message(error, "Failed to load listings");
            if (message != null) {
                SeqClient.LOGGER.warn("[PartyFinderWS] Failed to refresh listings for open-party reminders: {}",
                        message, error);
            }
            return null;
        });
    }

    public CompletableFuture<CommandResult<List<Listing>>> refreshListingsForCommand() {
        return refreshListingsForCommand(partyFinder.ticket());
    }

    private CompletableFuture<CommandResult<List<Listing>>> refreshListingsForCommand(Ticket ticket) {
        return fetchListings(ticket)
                .thenApply(listings -> CommandResult.success("Loaded " + listings.size() + " listings.", listings))
                .exceptionally(error -> commandFailure(error, "Failed to load listings", "Failed to load listings"));
    }

    private CompletableFuture<List<Activity>> fetchActivities(Ticket ticket) {
        return partyFinder.send(ticket, ApiClient::getActivities, applying(partyFinder::replaceActivities));
    }

    private CompletableFuture<List<Listing>> fetchListings(Ticket ticket) {
        return fetchListings(ticket, Function.identity());
    }

    /** Every listing there is, taken as the listings, then {@code then} with them, on the game thread. */
    private <R> CompletableFuture<R> fetchListings(Ticket ticket, Function<List<Listing>, R> then) {
        return partyFinder.send(ticket, api -> api.getListings(null, null),
                fetched -> then.apply(partyFinder.replaceListings(fetched)));
    }

    private CompletableFuture<CommandResult<List<Activity>>> ensureActivitiesLoaded(Ticket ticket) {
        if (!partyFinder.getActivities().isEmpty()) {
            return CompletableFuture.completedFuture(
                    CommandResult.success("Activities ready.", List.copyOf(partyFinder.getActivities())));
        }
        return fetchActivities(ticket)
                .thenApply(activities ->
                        CommandResult.success("Loaded " + activities.size() + " activities.", List.copyOf(activities)))
                .exceptionally(error -> commandFailure(error, "Failed to load activities", "Failed to load activities"));
    }

    /** The player's listing, loading the listings first when none is known yet. */
    CompletableFuture<CommandResult<Listing>> ensureCurrentListing(Ticket ticket) {
        Listing current = partyFinder.getCurrentListing();
        if (current != null) {
            return CompletableFuture.completedFuture(CommandResult.success("Current listing ready.", current));
        }
        return fetchListings(ticket, listings -> {
            Listing led = partyFinder.adoptLedListing();
            if (led != null) {
                return CommandResult.success("Resolved your led listing.", led);
            }
            Listing loaded = partyFinder.getCurrentListing();
            return loaded == null
                    ? CommandResult.<Listing>failure("You are not currently in a Sequoia party.")
                    : CommandResult.success("Current listing loaded.", loaded);
        }).exceptionally(error -> commandFailure(error, "Failed to load listings", "Failed to load listings"));
    }

    // ══════════════════════════════════════════════════════════════
    // From the screen
    // ══════════════════════════════════════════════════════════════

    /** Creates a party from the modal's raids and role. */
    public void createParty(
            List<String> activityNames, String role, int reservedSlots, PartyRegion region, PartyJoinPolicy joinPolicy) {
        if (activityNames == null || activityNames.isEmpty()) {
            SeqClient.LOGGER.error("Cannot create party without a selected activity");
            partyFinder.pushUiError("Select at least one raid before creating a party.");
            return;
        }
        ActivityResolution resolution = resolveActivities(activityNames);
        if (resolution.activityIds().isEmpty()) {
            SeqClient.LOGGER.error("No matching activities found for selected raids {}; loaded backend activities: {}",
                    activityNames, partyFinder.getActivities().stream().map(Activity::name).toList());
            partyFinder.pushUiError("Could not map selected raids to backend activities.");
            return;
        }
        if (!resolution.unresolved().isEmpty()) {
            SeqClient.LOGGER.warn("Some selected raids could not be mapped to backend activities: {}", resolution.unresolved());
        }

        PartyRole createRole = Objects.requireNonNullElse(mapDisplayRole(role), PartyRole.DPS);
        PartyRegion listingRegion = region != null ? region : PartyRegion.NA;
        PartyJoinPolicy policy = joinPolicy != null ? joinPolicy : PartyJoinPolicy.DEFAULT_CREATE_POLICY;
        listingAction(api -> api.createListing(resolution.activityIds(), listingRegion, createRole, null, currentWorld(),
                policy, reservedSlots), partyFinder::applyJoined, "Unable to create party", "Failed to create party");
    }

    /** Updates the player's listing from the modal, then sets its reserved slots. */
    public void updateParty(
            List<String> activityNames, String role, int reservedSlots, PartyRegion region, PartyJoinPolicy joinPolicy) {
        Listing current = partyFinder.getCurrentListing();
        if (current == null) {
            partyFinder.pushUiError("Unable to update party: no active listing.");
            return;
        }
        if (activityNames == null || activityNames.isEmpty()) {
            partyFinder.pushUiError("Select at least one raid before updating your party.");
            return;
        }
        List<Long> activityIds = resolveActivities(activityNames).activityIds();
        if (activityIds.isEmpty()) {
            partyFinder.pushUiError("Could not map selected raids to backend activities.");
            return;
        }

        PartyRegion listingRegion = region != null ? region : (current.region() != null ? current.region() : PartyRegion.NA);
        PartyJoinPolicy policy = joinPolicy != null ? joinPolicy : current.resolvedJoinPolicy();
        Ticket ticket = partyFinder.ticket();
        onScreen(ticket, partyFinder.send(ticket, api -> api.updateListing(current.id(), activityIds, listingRegion,
                        current.note(), currentWorld(), policy), applying(listing -> {
                    if (listing != null) {
                        partyFinder.applyUpdated(listing);
                        onScreen(ticket, partyFinder.send(ticket, api -> api.reserveSlots(listing.id(), reservedSlots),
                                        applying(updated -> {
                                            if (updated != null) {
                                                partyFinder.applyUpdated(updated);
                                            }
                                        })),
                                "Unable to set reserved slots", "Failed to set reserved slots on update");
                    }
                })),
                "Unable to update party", "Failed to update party");
    }

    /** Joins {@code listingId} with a display-name role, if it is still open and the player is in no party. */
    public void joinPartyById(long listingId, String roleString) {
        if (listingToJoin(partyFinder.getListings(), listingId, partyFinder.getCurrentListing()) == null) {
            return;
        }
        PartyRole role = Objects.requireNonNullElse(mapDisplayRole(roleString), PartyRole.DPS);
        listingAction(api -> api.joinListing(listingId, role), partyFinder::applyJoined,
                "Unable to join party", "Failed to join party");
    }

    /** Leaves {@code listingId}, if it is still the party the player is in. */
    public void leavePartyById(long listingId) {
        Listing current = partyFinder.getCurrentListing();
        if (current != null && current.id() == listingId) {
            listingAction(api -> api.leaveListing(listingId), partyFinder::applyLeft,
                    "Unable to leave party", "Failed to leave party");
        }
    }

    public void closeParty(long listingId) {
        listingAction(api -> api.closeListing(listingId), partyFinder::applyUpdated,
                "Unable to close party", "Failed to close party");
    }

    public void reopenParty(long listingId) {
        listingAction(api -> api.reopenListing(listingId), partyFinder::applyUpdated,
                "Unable to reopen party", "Failed to reopen party");
    }

    /** Disbands the player's listing. */
    public void delistParty() {
        Listing current = partyFinder.getCurrentListing();
        if (current != null) {
            listingAction(api -> api.disbandListing(current.id()), ignored -> partyFinder.applyDisbanded(current.id()),
                    "Unable to delist party", "Failed to disband party");
        }
    }

    /** Changes the player's role to a display-name role. */
    public void setRole(String role) {
        PartyRole partyRole = mapDisplayRole(role);
        if (partyRole != null) {
            listingAction(api -> api.changeMyRole(partyRole), partyFinder::applyUpdated,
                    "Unable to change role", "Failed to change role");
        }
    }

    /*
     * The screen's buttons name their party by listing id and their player by UUID, and these
     * look both up when the click lands: a websocket update can reorder or rebuild the list
     * between the frame that drew a button and the click on it. A party or player that has gone
     * by then gets nothing.
     */

    /** Kicks the member {@code memberUuid} from {@code listingId}, in the listing and then in the game. */
    public void kickMemberById(long listingId, String memberUuid) {
        memberAction(listingId, memberUuid, "Kick", (api, target) -> api.kickMember(listingId, target),
                PartyGameCommands.KICK, "You were kicked from the party.", "Unable to kick member", "Failed to kick member");
    }

    /** Transfers leadership of {@code listingId} to the member {@code memberUuid}, in the listing and then in the game. */
    public void promoteMemberById(long listingId, String memberUuid) {
        memberAction(listingId, memberUuid, "Promote", (api, target) -> api.transferLeadership(listingId, target),
                PartyGameCommands.PROMOTE, "You were promoted to party leader.",
                "Unable to promote member", "Failed to transfer leadership");
    }

    private void memberAction(long listingId, String memberUuid, String name,
            BiFunction<ApiClient, UUID, CompletableFuture<Listing>> call,
            String gameCommand, String message, String fallbackMessage, String logMessage) {
        // Read as the click lands: the reply may come back after the player reconnected or signed out.
        Ticket ticket = partyFinder.ticket();
        UUID target = memberActionTarget(partyFinder.getListings(), listingId, memberUuid);
        if (target == null) {
            SeqClient.LOGGER.info("[PartyFinder] {} skipped: listing {} no longer has that member", name, listingId);
            return;
        }
        onScreen(ticket, partyFinder.send(ticket, api -> call.apply(api, target), applying(listing -> {
            partyFinder.applyUpdated(listing);
            partyFinder.game().partyCommandFor(ticket, target, gameCommand);
            partyFinder.game().directMessage(ticket, target, message);
        })), fallbackMessage, logMessage);
    }

    /** Creates a backend invite for {@code username} to the player's listing. */
    public void createInvite(String username) {
        Ticket ticket = partyFinder.ticket();
        if (!partyFinder.isPartyLeader()) {
            partyFinder.pushUiError("Only the party leader can invite players.");
            return;
        }
        Listing requested = partyFinder.getCurrentListing();
        if (requested == null) {
            partyFinder.pushUiError("Unable to invite player: no active listing.");
            return;
        }
        if (username == null || username.isBlank()) {
            partyFinder.pushUiError("Enter a username to invite.");
            return;
        }
        String normalizedUsername = username.trim();
        String reservedSlotError = validateReservedSlotsForInvite(requested);
        if (reservedSlotError != null) {
            partyFinder.pushUiError(reservedSlotError);
            return;
        }
        SeqClient.LOGGER.info("[PartyFinderWS] createInvite requested listingId={} username='{}'",
                requested.id(), normalizedUsername);
        if (!PartyFinderPlayer.isUsername(normalizedUsername)) {
            partyFinder.pushUiError("Enter a valid Minecraft username.");
            return;
        }
        if (normalizedUsername.equalsIgnoreCase(player.name())) {
            partyFinder.pushUiError("You cannot invite yourself.");
            return;
        }

        // Null once the invite is created, else why it could not be.
        CompletableFuture<String> invited = player.uuidOf(normalizedUsername).thenComposeAsync(resolvedUUID -> {
            String problem = inviteProblem(resolvedUUID, requested.id());
            if (problem != null) {
                return CompletableFuture.completedFuture(problem);
            }
            UUID targetUUID = parseUuid(resolvedUUID);
            SeqClient.LOGGER.info("[PartyFinderWS] createInvite calling API listingId={} targetUUID={}",
                    requested.id(), targetUUID);
            return partyFinder.send(ticket, api -> api.createInvite(requested.id(), targetUUID), response -> {
                partyFinder.applyCreatedInvite(response, requested.id(), targetUUID, normalizedUsername);
                return null;
            });
        }, partyFinder.gameThread);
        partyFinder.reply(ticket, invited, (problem, error) -> {
            if (error != null) {
                screenFailure(error, "Unable to create party invite", "Failed to create invite");
            } else if (problem != null) {
                partyFinder.pushUiError(problem);
            } else {
                player.notify("Party Finder invite created.");
            }
        });
    }

    /** Why an invite to {@code resolvedUUID} cannot go out now, for the listing it was asked for; null when it can. */
    private String inviteProblem(String resolvedUUID, long requestedListingId) {
        if (resolvedUUID == null || resolvedUUID.isBlank()) {
            return "Unable to find that player UUID.";
        }
        UUID targetUUID = parseUuid(resolvedUUID);
        if (targetUUID == null) {
            return "Unable to resolve a valid UUID for that player.";
        }
        String myUUID = player.uuid();
        if (myUUID != null && myUUID.equalsIgnoreCase(targetUUID.toString())) {
            return "You cannot invite yourself.";
        }
        Listing current = partyFinder.getCurrentListing();
        if (current == null || current.id() != requestedListingId) {
            return "Your active party changed before the invite was created. Try again.";
        }
        if (!partyFinder.isPartyLeader()) {
            return "Only the party leader can invite players.";
        }
        return validateReservedSlotsForInvite(current);
    }

    private void listingAction(Function<ApiClient, CompletableFuture<Listing>> call, Consumer<Listing> apply,
            String fallbackMessage, String logMessage) {
        Ticket ticket = partyFinder.ticket();
        onScreen(ticket, partyFinder.send(ticket, call, applying(apply)), fallbackMessage, logMessage);
    }

    /** Shows {@code action}'s failure on the banner, to the session and server connection it was clicked on. */
    private void onScreen(Ticket ticket, CompletableFuture<?> action, String fallbackMessage, String logMessage) {
        partyFinder.reply(ticket, action, (ignored, error) -> {
            if (error != null) {
                screenFailure(error, fallbackMessage, logMessage);
            }
        });
    }

    private void screenFailure(Throwable error, String fallbackMessage, String logMessage) {
        String message = PartyFinderErrors.message(error, fallbackMessage);
        if (message != null) {
            SeqClient.LOGGER.warn("{}: {}", logMessage, message);
            partyFinder.pushUiError(message);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // From /seq p
    // ══════════════════════════════════════════════════════════════

    public CompletableFuture<CommandResult<Listing>> createPartyFromCommand(List<String> activityInputs) {
        return createPartyFromCommand(activityInputs, PartyRegion.NA);
    }

    /** Creates a listing in {@code region}, for callers that know where the leader plays. */
    public CompletableFuture<CommandResult<Listing>> createPartyFromCommand(List<String> activityInputs, PartyRegion region) {
        PartyRegion listingRegion = region == null ? PartyRegion.NA : region;
        Ticket ticket = partyFinder.ticket();
        return refreshListingsForCommand(ticket).thenCompose(listingsResult -> {
            if (!listingsResult.success()) {
                return fail(listingsResult.message());
            }
            Listing current = partyFinder.getCurrentListing();
            if (current != null) {
                return fail("You are already in party #" + current.id() + ". Use /seq p update or leave/disband it first.");
            }
            return withActivities(ticket, activityInputs, resolution -> executeListingCommand(
                    ticket, api -> api.createListing(resolution.activityIds(), listingRegion,
                            PartyRole.DPS, null, currentWorld(), PartyJoinPolicy.DEFAULT_CREATE_POLICY, 0),
                    partyFinder::applyJoined,
                    "Unable to create party",
                    "Failed to create party",
                    listing -> "Created party #" + listing.id() + " for "
                            + formatActivityNames(resolution.displayNames()) + "."));
        });
    }

    public CompletableFuture<CommandResult<Listing>> updatePartyFromCommand(List<String> activityInputs) {
        Ticket ticket = partyFinder.ticket();
        return runLeaderListingCommand(ticket, "Only the party leader can update the Sequoia listing.",
                listing -> withActivities(ticket, activityInputs, resolution -> executeListingCommand(
                        ticket, api -> api.updateListing(listing.id(), resolution.activityIds(),
                                listing.region() != null ? listing.region() : PartyRegion.NA, listing.note(),
                                currentWorld(), listing.resolvedJoinPolicy()),
                        partyFinder::applyUpdated,
                        "Unable to update party",
                        "Failed to update party",
                        updated -> "Updated party #" + updated.id() + " to "
                                + formatActivityNames(resolution.displayNames()) + ".")));
    }

    public CompletableFuture<CommandResult<Listing>> joinPartyFromCommand(long listingId, PartyRole role) {
        return joinPartyFromCommand(listingId, role, null);
    }

    public CompletableFuture<CommandResult<Listing>> joinPartyFromCommand(long listingId, PartyRole role, String inviteToken) {
        PartyRole resolvedRole = role != null ? role : PartyRole.DPS;
        if (inviteToken != null && inviteToken.isBlank()) {
            return fail("Invite token missing.");
        }
        Ticket ticket = partyFinder.ticket();
        return refreshListingsForCommand(ticket).thenCompose(listingsResult -> {
            if (!listingsResult.success()) {
                return fail(listingsResult.message());
            }
            Listing current = partyFinder.getCurrentListing();
            if (current != null) {
                return fail("You are already in party #" + current.id() + ". Leave it before joining another listing.");
            }
            return executeListingCommand(
                    ticket, api -> api.joinListing(listingId, resolvedRole, inviteToken),
                    partyFinder::applyJoined,
                    "Unable to join party",
                    "Failed to join party",
                    listing -> "Joined party #" + listing.id() + " as " + PartyMember.formatRole(resolvedRole) + ".");
        });
    }

    public CompletableFuture<CommandResult<Listing>> leavePartyFromCommand() {
        Ticket ticket = partyFinder.ticket();
        return withCurrentListing(ticket, listing -> executeListingCommand(
                ticket, api -> api.leaveListing(listing.id()),
                partyFinder::applyLeft,
                "Unable to leave party",
                "Failed to leave party",
                ignored -> "Left party #" + listing.id() + "."));
    }

    public CompletableFuture<CommandResult<Void>> createInviteFromCommand(String username) {
        Ticket ticket = partyFinder.ticket();
        return withCurrentListing(ticket, listing -> {
            if (!partyFinder.isPartyLeader()) {
                return fail("Only the party leader can invite players.");
            }
            String problem = usernameProblem(username);
            if (problem == null) {
                problem = validateReservedSlotsForInvite(listing);
            }
            if (problem != null) {
                return fail(problem);
            }
            String normalizedUsername = username.trim();
            return resolveUuidForCommand(normalizedUsername).thenComposeAsync(uuidResult -> {
                if (!uuidResult.success()) {
                    return fail(uuidResult.message());
                }
                UUID targetUUID = uuidResult.data();
                if (Listing.samePlayer(player.uuid(), targetUUID.toString())) {
                    return fail("You cannot invite yourself.");
                }
                return partyFinder.send(ticket, api -> api.createInvite(listing.id(), targetUUID), response -> {
                            partyFinder.applyCreatedInvite(response, listing.id(), targetUUID, normalizedUsername);
                            return CommandResult.<Void>success("Created Sequoia invite for " + normalizedUsername
                                    + " on party #" + listing.id() + ".", null);
                        })
                        .exceptionally(error -> commandFailure(error, "Unable to create party invite", "Failed to create invite"));
            }, partyFinder.gameThread);
        });
    }

    public CompletableFuture<CommandResult<Listing>> revokeInviteFromCommand(String username) {
        Ticket ticket = partyFinder.ticket();
        return runLeaderListingCommand(ticket, "Only the party leader can revoke invites.", listing -> {
            String problem = usernameProblem(username);
            if (problem != null) {
                return fail(problem);
            }
            IssuedInvite invite = partyFinder.issuedInvite(listing.id(), username);
            if (invite == null) {
                return fail("No revocable invite for " + username.trim() + " was created during this session.");
            }
            return executeListingCommand(
                    ticket, api -> api.revokeInvite(listing.id(), invite.inviteToken()),
                    updated -> {
                        partyFinder.forgetIssuedInvite(listing.id(), invite);
                        partyFinder.applyUpdated(updated);
                    },
                    "Unable to revoke party invite",
                    "Failed to revoke invite",
                    updated -> "Revoked Sequoia invite for " + invite.username() + " on party #" + updated.id() + ".");
        });
    }

    public CompletableFuture<CommandResult<Listing>> setReservedSlotTargetFromCommand(int requestedReservedSlots) {
        if (requestedReservedSlots < 0) {
            return fail("Reserved slot target cannot be negative.");
        }
        Ticket ticket = partyFinder.ticket();
        String verb = requestedReservedSlots == 1 ? "slot" : "slots";
        return runLeaderListingCommand(ticket, "Only the party leader can reserve slots.", listing -> executeListingCommand(
                ticket, api -> api.reserveSlots(listing.id(), requestedReservedSlots),
                partyFinder::applyUpdated,
                "Unable to set reserved slots",
                "Failed to set reserved slots",
                updated -> "Set reserved slots to " + requestedReservedSlots + " " + verb + " on party #"
                        + updated.id() + "."));
    }

    public CompletableFuture<CommandResult<Listing>> reopenPartyFromCommand() {
        return leaderListingCall("Only the party leader can open the Sequoia listing.", ApiClient::reopenListing,
                "Unable to reopen party", "Failed to reopen party", updated -> "Opened party #" + updated.id() + ".");
    }

    public CompletableFuture<CommandResult<Listing>> closePartyFromCommand() {
        return leaderListingCall("Only the party leader can close the Sequoia listing.", ApiClient::closeListing,
                "Unable to close party", "Failed to close party", updated -> "Closed party #" + updated.id() + ".");
    }

    public CompletableFuture<CommandResult<Listing>> extendPartyFromCommand() {
        return extendPartyFromCommand(null);
    }

    public CompletableFuture<CommandResult<Listing>> extendPartyFromCommand(long listingId) {
        return extendPartyFromCommand(Long.valueOf(listingId));
    }

    private CompletableFuture<CommandResult<Listing>> extendPartyFromCommand(Long expectedListingId) {
        Ticket ticket = partyFinder.ticket();
        return runLeaderListingCommand(ticket, "Only the party leader can extend the Sequoia listing.", listing -> {
            if (expectedListingId != null && listing.id() != expectedListingId) {
                return fail("That warning belongs to an older party listing.");
            }
            return executeListingCommand(
                    ticket, api -> api.extendListing(listing.id()),
                    partyFinder::applyUpdated,
                    "Unable to extend party",
                    "Failed to extend party",
                    updated -> "Kept party #" + updated.id() + " open for 30 more minutes.");
        });
    }

    public CompletableFuture<CommandResult<Listing>> disbandPartyFromCommand() {
        Ticket ticket = partyFinder.ticket();
        return runLeaderListingCommand(ticket, "Only the party leader can disband the Sequoia listing.",
                listing -> executeListingCommand(
                        ticket, api -> api.disbandListing(listing.id()),
                        ignored -> partyFinder.applyDisbanded(listing.id()),
                        "Unable to disband party",
                        "Failed to disband party",
                        ignored -> "Disbanded party #" + listing.id() + "."));
    }

    public CompletableFuture<CommandResult<Listing>> changeRoleFromCommand(PartyRole role) {
        PartyRole resolvedRole = role != null ? role : PartyRole.DPS;
        Ticket ticket = partyFinder.ticket();
        return withCurrentListing(ticket, listing -> executeListingCommand(
                ticket, api -> api.changeMyRole(resolvedRole),
                partyFinder::applyUpdated,
                "Unable to change role",
                "Failed to change role",
                updated -> "Changed your party role to " + PartyMember.formatRole(resolvedRole) + "."));
    }

    public CompletableFuture<CommandResult<Listing>> kickMemberFromCommand(String username) {
        Ticket ticket = partyFinder.ticket();
        return withMemberTarget(ticket, username, target -> {
            if (target.listing().isLedBy(target.uuid().toString())) {
                return fail("You cannot kick the party leader.");
            }
            return executeListingCommand(
                    ticket, api -> api.kickMember(target.listing().id(), target.uuid()),
                    partyFinder::applyUpdated,
                    "Unable to kick member",
                    "Failed to kick member",
                    updated -> "Kicked " + target.username() + " from party #" + updated.id() + ".");
        });
    }

    public CompletableFuture<CommandResult<Listing>> promoteMemberFromCommand(String username) {
        Ticket ticket = partyFinder.ticket();
        return withMemberTarget(ticket, username, target -> {
            if (target.listing().isLedBy(target.uuid().toString())) {
                return fail(target.username() + " is already the party leader.");
            }
            return executeListingCommand(
                    ticket, api -> api.transferLeadership(target.listing().id(), target.uuid()),
                    listing -> {
                        partyFinder.applyUpdated(listing);
                        partyFinder.game().partyCommandFor(ticket, target.uuid(), PartyGameCommands.PROMOTE);
                    },
                    "Unable to promote member",
                    "Failed to transfer leadership",
                    updated -> "Transferred party #" + updated.id() + " leadership to " + target.username() + ".");
        });
    }

    // ══════════════════════════════════════════════════════════════
    // Command steps
    // ══════════════════════════════════════════════════════════════

    private <T> CompletableFuture<CommandResult<T>> withCurrentListing(
            Ticket ticket, Function<Listing, CompletableFuture<CommandResult<T>>> action) {
        return ensureCurrentListing(ticket).thenCompose(current ->
                current.success() ? action.apply(current.data()) : fail(current.message()));
    }

    private CompletableFuture<CommandResult<Listing>> runLeaderListingCommand(
            Ticket ticket, String notLeaderMessage, Function<Listing, CompletableFuture<CommandResult<Listing>>> action) {
        return withCurrentListing(ticket, listing ->
                partyFinder.isPartyLeader() ? action.apply(listing) : fail(notLeaderMessage));
    }

    private CompletableFuture<CommandResult<Listing>> leaderListingCall(String notLeaderMessage,
            BiFunction<ApiClient, Long, CompletableFuture<Listing>> call,
            String fallbackMessage, String logMessage, Function<Listing, String> successMessage) {
        Ticket ticket = partyFinder.ticket();
        return runLeaderListingCommand(ticket, notLeaderMessage, listing -> executeListingCommand(
                ticket, api -> call.apply(api, listing.id()),
                partyFinder::applyUpdated, fallbackMessage, logMessage, successMessage));
    }

    /** The activities the player named, once the catalog is loaded, or why they can't be used. */
    private CompletableFuture<CommandResult<Listing>> withActivities(Ticket ticket, Collection<String> activityInputs,
            Function<ActivityResolution, CompletableFuture<CommandResult<Listing>>> action) {
        return ensureActivitiesLoaded(ticket).thenCompose(activitiesResult -> {
            if (!activitiesResult.success()) {
                return fail(activitiesResult.message());
            }
            ActivityResolution resolution = resolveActivities(activityInputs == null ? List.of() : activityInputs);
            List<String> displayNames = resolution.displayNames();
            if (resolution.activityIds().isEmpty() && resolution.unresolved().isEmpty()) {
                return fail("Provide at least one activity.");
            }
            if (displayNames.contains("Prelude to Annihilation") && displayNames.size() > 1) {
                return fail("Prelude to Annihilation cannot be combined with other activities.");
            }
            if (!resolution.unresolved().isEmpty()) {
                return fail("Unknown activities: " + String.join(", ", resolution.unresolved()) + ".");
            }
            return action.apply(resolution);
        });
    }

    private CompletableFuture<CommandResult<Listing>> withMemberTarget(
            Ticket ticket, String username, Function<MemberTarget, CompletableFuture<CommandResult<Listing>>> action) {
        String problem = usernameProblem(username);
        if (problem != null) {
            return fail(problem);
        }
        String normalizedUsername = username.trim();
        return runLeaderListingCommand(ticket, "Only the party leader can manage party members.", listing ->
                resolveUuidForCommand(normalizedUsername).thenComposeAsync(uuidResult -> {
                    if (!uuidResult.success()) {
                        return fail(uuidResult.message());
                    }
                    if (listing.member(uuidResult.data().toString()) == null) {
                        return fail(normalizedUsername + " is not in your Sequoia party.");
                    }
                    return action.apply(new MemberTarget(listing, uuidResult.data(), normalizedUsername));
                }, partyFinder.gameThread));
    }

    private CompletableFuture<CommandResult<UUID>> resolveUuidForCommand(String username) {
        return player.uuidOf(username).thenApply(resolvedUuid -> {
            if (resolvedUuid == null || resolvedUuid.isBlank()) {
                return CommandResult.failure("Unable to find a UUID for " + username + ".");
            }
            UUID uuid = parseUuid(resolvedUuid);
            return uuid == null
                    ? CommandResult.failure("Unable to resolve a valid UUID for " + username + ".")
                    : CommandResult.success("Resolved UUID.", uuid);
        });
    }

    /** {@code call}, its reply applied as the session's, and how it went. */
    private CompletableFuture<CommandResult<Listing>> executeListingCommand(
            Ticket ticket,
            Function<ApiClient, CompletableFuture<Listing>> call,
            Consumer<Listing> apply,
            String fallbackMessage,
            String logMessage,
            Function<Listing, String> successMessage) {
        return partyFinder.send(ticket, call, listing -> {
                    apply.accept(listing);
                    return CommandResult.success(successMessage.apply(listing), listing);
                })
                .exceptionally(error -> commandFailure(error, fallbackMessage, logMessage));
    }

    /** {@code apply}, as a step that hands on the reply it applied. */
    private static <T> Function<T, T> applying(Consumer<? super T> apply) {
        return value -> {
            apply.accept(value);
            return value;
        };
    }

    private static <T> CommandResult<T> commandFailure(Throwable error, String fallbackMessage, String logMessage) {
        String message = Objects.requireNonNullElse(PartyFinderErrors.message(error, fallbackMessage), SESSION_ENDED);
        SeqClient.LOGGER.warn("{}: {}", logMessage, message);
        return CommandResult.failure(message);
    }

    private static <T> CompletableFuture<CommandResult<T>> fail(String message) {
        return CompletableFuture.completedFuture(CommandResult.failure(message));
    }

    // ══════════════════════════════════════════════════════════════
    // Checks and names
    // ══════════════════════════════════════════════════════════════

    /** The UUID {@code memberUuid} names while it is still a member of {@code listingId}, else null. */
    static UUID memberActionTarget(List<Listing> listings, long listingId, String memberUuid) {
        String formattedTargetUuid = PlayerNameCache.formatUUID(memberUuid);
        if (formattedTargetUuid == null) {
            return null;
        }
        for (Listing listing : listings) {
            if (listing != null && listing.id() == listingId && listing.hasMember(formattedTargetUuid)) {
                return UUID.fromString(formattedTargetUuid);
            }
        }
        return null;
    }

    /** {@code listingId} while it is listed and open to join, as the card's button showed, and the player is free. */
    static Listing listingToJoin(List<Listing> listings, long listingId, Listing currentListing) {
        if (currentListing != null) {
            return null;
        }
        for (Listing listing : listings) {
            if (listing != null && listing.id() == listingId) {
                boolean joinable = listing.status() == PartyStatus.OPEN
                        && listing.resolvedJoinPolicy() == PartyJoinPolicy.OPEN;
                return joinable ? listing : null;
            }
        }
        return null;
    }

    private static String usernameProblem(String username) {
        return username != null && PartyFinderPlayer.isUsername(username.trim()) ? null : "Enter a valid Minecraft username.";
    }

    private static String validateReservedSlotsForInvite(Listing listing) {
        if (listing != null && (listing.resolvedJoinPolicy() == PartyJoinPolicy.INVITE_ONLY || listing.reservedSlotCount() > 0)) {
            return null;
        }
        return "Add at least one reserved slot before creating a party finder invite.";
    }

    private static UUID parseUuid(String resolved) {
        String formatted = PlayerNameCache.formatUUID(resolved);
        try {
            return formatted == null ? null : UUID.fromString(formatted);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String currentWorld() {
        return WynntilsWorldStateAccess.currentWorldName().orElse(null);
    }

    private static String formatActivityNames(Collection<String> displayNames) {
        if (displayNames == null || displayNames.isEmpty()) {
            return "Unknown Activity";
        }
        return displayNames.stream().map(PartyListing::displayNameToBackendName).collect(Collectors.joining(", "));
    }

    /** Maps the screen's role labels (DPS, Healer, Tank, Other) to roles. */
    private static PartyRole mapDisplayRole(String displayRole) {
        if (displayRole == null) return null;
        return switch (displayRole.toUpperCase()) {
            case "DPS" -> PartyRole.DPS;
            case "HEALER" -> PartyRole.HEALER;
            case "TANK" -> PartyRole.TANK;
            case "OTHER" -> PartyRole.OTHER;
            default -> null;
        };
    }

    /** The loaded activities {@code inputs} name, by display name, backend name or short code. */
    private ActivityResolution resolveActivities(Collection<String> inputs) {
        Set<String> normalizedInputs = new LinkedHashSet<>();
        for (String input : inputs) {
            if (input != null && !input.isBlank()) {
                normalizedInputs.add(input.trim());
            }
        }
        List<Long> activityIds = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        Set<String> displayNames = new LinkedHashSet<>();
        for (String input : normalizedInputs) {
            String searchName = PartyListing.displayNameToBackendName(input);
            Activity activity = partyFinder.getActivities().stream()
                    .filter(candidate -> matchesActivityName(candidate, input, searchName))
                    .findFirst()
                    .orElse(null);
            if (activity == null) {
                unresolved.add(input);
                continue;
            }
            if (!activityIds.contains(activity.id())) {
                activityIds.add(activity.id());
            }
            displayNames.add(PartyListing.backendNameToDisplayName(activity.name()));
        }
        return new ActivityResolution(List.copyOf(activityIds), List.copyOf(unresolved), List.copyOf(displayNames));
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
        String normalizedCandidateCode = normalizeActivityKey(candidateCode);
        return normalizedBackend.equals(normalizedDisplay)
                || normalizedMappedDisplay.equals(normalizedDisplay)
                || normalizedMappedDisplay.equals(normalizeActivityKey(inputDisplay))
                || normalizedBackend.equals(normalizeActivityKey(backendSearchName))
                || normalizedMappedDisplay.equals(normalizeActivityKey(searchDisplay))
                || normalizedCandidateCode.equals(normalizedDisplay)
                || normalizedCandidateCode.equals(normalizeActivityKey(backendSearchName))
                || normalizedCandidateCode.equals(normalizeActivityKey(inputCode))
                || normalizedCandidateCode.equals(normalizeActivityKey(searchCode));
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
