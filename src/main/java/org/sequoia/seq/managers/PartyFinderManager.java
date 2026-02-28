package org.sequoia.seq.managers;

import com.google.gson.*;
import lombok.Getter;
import org.sequoia.seq.client.SeqClient;
import org.sequoia.seq.events.PartyFinderUpdateEvent;
import org.sequoia.seq.model.*;
import org.sequoia.seq.network.ApiClient;
import org.sequoia.seq.network.ConnectionManager;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

public class PartyFinderManager {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(Instant.class, (JsonDeserializer<Instant>) (json, type, ctx) ->
                    Instant.parse(json.getAsString()))
            .create();

    @Getter
    private final List<Activity> activities = new CopyOnWriteArrayList<>();
    @Getter
    private final List<Listing> listings = new CopyOnWriteArrayList<>();

    /** The listing the local player is currently a member of, or null. */
    @Getter
    private Listing currentListing;

    public PartyFinderManager() {
        // Register for real-time WS updates
        ConnectionManager.onPartyFinderUpdate(update -> {
            Listing listing = GSON.fromJson(update.listingJson(), Listing.class);
            handlePartyFinderUpdate(update.action(), listing);
        });
    }

    // ── Data loading ──

    public CompletableFuture<List<Activity>> loadActivities() {
        return ApiClient.getInstance().getActivities()
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

    public CompletableFuture<List<Listing>> loadListings(Long activityId, PartyRegion region) {
        return ApiClient.getInstance().getListings(activityId, region)
                .thenApply(result -> {
                    listings.clear();
                    listings.addAll(result);
                    refreshCurrentListing();
                    return result;
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to load listings", e);
                    return List.of();
                });
    }

    // ── Party actions ──

    public CompletableFuture<Listing> createParty(long activityId, PartyMode mode, PartyRegion region, PartyRole role, String note) {
        return ApiClient.getInstance().createListing(activityId, mode, region, role, note)
                .thenApply(listing -> {
                    listings.add(0, listing);
                    currentListing = listing;
                    return listing;
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to create party", e);
                    return null;
                });
    }

    public CompletableFuture<Listing> joinParty(long listingId, PartyRole role) {
        return ApiClient.getInstance().joinListing(listingId, role)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    return listing;
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to join party", e);
                    return null;
                });
    }

    public CompletableFuture<Listing> leaveParty(long listingId) {
        return ApiClient.getInstance().leaveListing(listingId)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = null;
                    return listing;
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to leave party", e);
                    return null;
                });
    }

    public CompletableFuture<Listing> closeParty(long listingId) {
        return ApiClient.getInstance().closeListing(listingId)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    return listing;
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to close party", e);
                    return null;
                });
    }

    public CompletableFuture<Listing> reopenParty(long listingId) {
        return ApiClient.getInstance().reopenListing(listingId)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    return listing;
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to reopen party", e);
                    return null;
                });
    }

    public CompletableFuture<Void> disbandParty(long listingId) {
        return ApiClient.getInstance().disbandListing(listingId)
                .thenRun(() -> {
                    listings.removeIf(l -> l.id() == listingId);
                    if (currentListing != null && currentListing.id() == listingId) {
                        currentListing = null;
                    }
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to disband party", e);
                    return null;
                });
    }

    public CompletableFuture<Void> kickMember(long listingId, UUID targetUUID) {
        return ApiClient.getInstance().kickMember(listingId, targetUUID)
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to kick member", e);
                    return null;
                });
    }

    public CompletableFuture<Listing> changeMyRole(PartyRole role) {
        return ApiClient.getInstance().changeMyRole(role)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    return listing;
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to change role", e);
                    return null;
                });
    }

    public CompletableFuture<Listing> reassignRole(long listingId, UUID targetUUID, PartyRole role) {
        return ApiClient.getInstance().reassignRole(listingId, targetUUID, role)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    return listing;
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to reassign role", e);
                    return null;
                });
    }

    public CompletableFuture<Listing> transferLeadership(long listingId, UUID targetUUID) {
        return ApiClient.getInstance().transferLeadership(listingId, targetUUID)
                .thenApply(listing -> {
                    replaceListing(listing);
                    currentListing = listing;
                    return listing;
                })
                .exceptionally(e -> {
                    SeqClient.LOGGER.error("Failed to transfer leadership", e);
                    return null;
                });
    }

    // ── Convenience queries ──

    public boolean isInParty() {
        return currentListing != null;
    }

    public boolean isPartyLeader() {
        if (currentListing == null) return false;
        String myUUID = getLocalPlayerUUID();
        return myUUID != null && myUUID.equals(currentListing.leaderUUID());
    }

    public String getLocalPlayerUUID() {
        var player = SeqClient.mc.player;
        return player != null ? player.getUUID().toString() : null;
    }

    // ── Real-time update handler ──

    public void handlePartyFinderUpdate(String action, Listing listing) {
        switch (action) {
            case "CREATED" -> {
                // Add to the top of the list if not already present
                if (listings.stream().noneMatch(l -> l.id() == listing.id())) {
                    listings.add(0, listing);
                }
            }
            case "UPDATED" -> replaceListing(listing);
            case "DISBANDED" -> {
                listings.removeIf(l -> l.id() == listing.id());
                if (currentListing != null && currentListing.id() == listing.id()) {
                    currentListing = null;
                }
            }
        }
        refreshCurrentListing();

        // Fire event for the UI
        if (SeqClient.getEventBus() != null) {
            SeqClient.getEventBus().fire(new PartyFinderUpdateEvent(action, listing));
        }
    }

    // ── Internal helpers ──

    private void replaceListing(Listing updated) {
        for (int i = 0; i < listings.size(); i++) {
            if (listings.get(i).id() == updated.id()) {
                listings.set(i, updated);
                return;
            }
        }
        // Not found — add it
        listings.add(updated);
    }

    private void refreshCurrentListing() {
        String myUUID = getLocalPlayerUUID();
        if (myUUID == null) {
            currentListing = null;
            return;
        }
        currentListing = listings.stream()
                .filter(l -> l.members().stream().anyMatch(m -> m.playerUUID().equals(myUUID)))
                .findFirst()
                .orElse(null);
    }
}
