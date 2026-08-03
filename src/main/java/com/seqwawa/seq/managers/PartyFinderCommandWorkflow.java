package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.network.ApiClient;
import com.seqwawa.seq.utils.PlayerNameCache;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

final class PartyFinderCommandWorkflow {
    private final PartyFinderManager manager;

    PartyFinderCommandWorkflow(PartyFinderManager manager) {
        this.manager = manager;
    }

    CompletableFuture<PartyFinderManager.CommandResult<List<Activity>>> ensureActivitiesLoaded() {
        List<Activity> activities = manager.getActivities();
        if (!activities.isEmpty()) {
            return CompletableFuture.completedFuture(
                    PartyFinderManager.CommandResult.success("Activities ready.", List.copyOf(activities)));
        }
        return ApiClient.getInstance()
                .getActivities()
                .thenApply(result -> {
                    activities.clear();
                    activities.addAll(result);
                    return PartyFinderManager.CommandResult.success(
                            "Loaded " + result.size() + " activities.", List.copyOf(result));
                })
                .exceptionally(error -> failure(error, "Failed to load activities", "Failed to load activities"));
    }

    CompletableFuture<PartyFinderManager.CommandResult<List<Listing>>> refreshListings() {
        return ApiClient.getInstance()
                .getListings(null, null)
                .thenApply(result -> {
                    List<Listing> listings = manager.replaceListingsForCommand(result);
                    return PartyFinderManager.CommandResult.success(
                            "Loaded " + listings.size() + " listings.", List.copyOf(listings));
                })
                .exceptionally(error -> failure(error, "Failed to load listings", "Failed to load listings"));
    }

    <T> CompletableFuture<PartyFinderManager.CommandResult<T>> completedFailure(String message) {
        return CompletableFuture.completedFuture(PartyFinderManager.CommandResult.failure(message));
    }

    CompletableFuture<PartyFinderManager.CommandResult<Void>> completedVoidFailure(String message) {
        return CompletableFuture.completedFuture(PartyFinderManager.CommandResult.failure(message));
    }

    <T> PartyFinderManager.CommandResult<T> failure(
            Throwable throwable, String fallbackMessage, String logMessage) {
        String errorMessage = PartyFinderManager.extractUserFriendlyApiError(throwable, fallbackMessage);
        SeqClient.LOGGER.warn("{}: {}", logMessage, errorMessage);
        return PartyFinderManager.CommandResult.failure(errorMessage);
    }

    CompletableFuture<PartyFinderManager.CommandResult<Listing>> executeListing(
            CompletableFuture<Listing> apiFuture,
            Consumer<Listing> stateUpdater,
            String fallbackMessage,
            String logMessage,
            Function<Listing, String> successMessageBuilder) {
        return apiFuture
                .thenApply(listing -> {
                    stateUpdater.accept(listing);
                    return PartyFinderManager.CommandResult.success(successMessageBuilder.apply(listing), listing);
                })
                .exceptionally(error -> failure(error, fallbackMessage, logMessage));
    }

    CompletableFuture<PartyFinderManager.CommandResult<Void>> executeVoid(
            CompletableFuture<Void> apiFuture,
            String fallbackMessage,
            String logMessage,
            String successMessage) {
        return apiFuture
                .thenApply(ignored -> PartyFinderManager.CommandResult.<Void>success(successMessage, null))
                .exceptionally(error -> failure(error, fallbackMessage, logMessage));
    }

    CompletableFuture<PartyFinderManager.CommandResult<Listing>> runLeaderListingCommand(
            String notLeaderMessage,
            Function<Listing, CompletableFuture<PartyFinderManager.CommandResult<Listing>>> action) {
        return ensureCurrentListing().thenCompose(currentResult -> {
            if (!currentResult.success()) {
                return completedFailure(currentResult.message());
            }
            if (!manager.isPartyLeader()) {
                return completedFailure(notLeaderMessage);
            }
            return action.apply(currentResult.data());
        });
    }

    CompletableFuture<PartyFinderManager.CommandResult<Listing>> ensureCurrentListing() {
        if (manager.getCurrentListing() != null) {
            return CompletableFuture.completedFuture(PartyFinderManager.CommandResult.success(
                    "Current listing ready.", manager.getCurrentListing()));
        }
        return refreshListings().thenApply(result -> {
            if (!result.success()) {
                return PartyFinderManager.CommandResult.failure(result.message());
            }
            Listing fallbackListing = manager.findActiveLedListingForCommand();
            if (fallbackListing != null) {
                manager.setCurrentListingForCommand(fallbackListing);
                return PartyFinderManager.CommandResult.success("Resolved your led listing.", fallbackListing);
            }
            if (manager.getCurrentListing() == null) {
                return PartyFinderManager.CommandResult.failure("You are not currently in a Sequoia party.");
            }
            return PartyFinderManager.CommandResult.success("Current listing loaded.", manager.getCurrentListing());
        });
    }

    PartyFinderManager.CommandResult<PartyFinderManager.ActivityResolution> resolveActivities(
            Collection<String> activityInputs, boolean rejectUnresolved) {
        if (activityInputs == null || activityInputs.isEmpty()) {
            return PartyFinderManager.CommandResult.failure("Provide at least one activity.");
        }
        LinkedHashSet<String> normalizedInputs = new LinkedHashSet<>();
        for (String rawInput : activityInputs) {
            if (rawInput != null && !rawInput.trim().isEmpty()) {
                normalizedInputs.add(rawInput.trim());
            }
        }
        if (normalizedInputs.isEmpty()) {
            return PartyFinderManager.CommandResult.failure("Provide at least one activity.");
        }

        List<Long> activityIds = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        LinkedHashSet<String> displayNames = new LinkedHashSet<>();
        for (String activityInput : normalizedInputs) {
            String searchName = PartyListing.displayNameToBackendName(activityInput);
            Activity activity = manager.getActivities().stream()
                    .filter(candidate -> PartyFinderManager.matchesActivityName(candidate, activityInput, searchName))
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
            return PartyFinderManager.CommandResult.failure(
                    "Prelude to Annihilation cannot be combined with other activities.");
        }
        if (activityIds.isEmpty() || (rejectUnresolved && !unresolved.isEmpty())) {
            return PartyFinderManager.CommandResult.failure(
                    "Unknown activities: " + String.join(", ", unresolved) + ".");
        }
        return PartyFinderManager.CommandResult.success(
                "Resolved " + displayNames.size() + " activities.",
                new PartyFinderManager.ActivityResolution(
                        List.copyOf(activityIds), List.copyOf(unresolved), List.copyOf(displayNames)));
    }

    CompletableFuture<PartyFinderManager.CommandResult<UUID>> resolveUuid(String username) {
        return PlayerNameCache.resolveUUID(username).thenApply(resolvedUuid -> {
            if (resolvedUuid == null || resolvedUuid.isBlank()) {
                return PartyFinderManager.CommandResult.failure("Unable to find a UUID for " + username + ".");
            }
            String formatted = PlayerNameCache.formatUUID(resolvedUuid);
            if (formatted == null) {
                SeqClient.LOGGER.warn("Unable to normalize resolved UUID {}", resolvedUuid);
                return PartyFinderManager.CommandResult.failure("Unable to resolve a valid UUID for " + username + ".");
            }
            try {
                return PartyFinderManager.CommandResult.success("Resolved UUID.", UUID.fromString(formatted));
            } catch (IllegalArgumentException error) {
                SeqClient.LOGGER.warn("Unable to parse resolved UUID {}", resolvedUuid, error);
                return PartyFinderManager.CommandResult.failure("Unable to resolve a valid UUID for " + username + ".");
            }
        });
    }

    CompletableFuture<PartyFinderManager.CommandResult<PartyFinderManager.ListingMemberTarget>>
            resolveCurrentMemberTarget(
            String username, boolean requireLeader) {
        String validationMessage = PartyFinderManager.validateUsername(username, false);
        if (validationMessage != null) {
            return completedFailure(validationMessage);
        }
        String normalizedUsername = username.trim();
        return ensureCurrentListing().thenCompose(currentResult -> {
            if (!currentResult.success()) {
                return completedFailure(currentResult.message());
            }
            if (requireLeader && !manager.isPartyLeader()) {
                return completedFailure("Only the party leader can manage party members.");
            }
            Listing listing = currentResult.data();
            return resolveUuid(normalizedUsername).thenApply(uuidResult -> {
                if (!uuidResult.success()) {
                    return PartyFinderManager.CommandResult.failure(uuidResult.message());
                }
                UUID targetUuid = uuidResult.data();
                if (findMemberByUuid(listing, targetUuid) == null) {
                    return PartyFinderManager.CommandResult.failure(
                            normalizedUsername + " is not in your Sequoia party.");
                }
                return PartyFinderManager.CommandResult.success(
                        "Resolved target member.",
                        new PartyFinderManager.ListingMemberTarget(listing, targetUuid, normalizedUsername));
            });
        });
    }

    private static Member findMemberByUuid(Listing listing, UUID targetUuid) {
        if (listing == null || targetUuid == null || listing.members() == null) {
            return null;
        }
        return listing.members().stream()
                .filter(member -> member != null
                        && PartyFinderManager.uuidEquals(targetUuid.toString(), member.playerUUID()))
                .findFirst()
                .orElse(null);
    }

}
