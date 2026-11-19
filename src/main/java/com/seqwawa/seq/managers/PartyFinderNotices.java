package com.seqwawa.seq.managers;

import com.seqwawa.seq.accessors.NotificationAccessor;
import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.model.PartyJoinPolicy;
import com.seqwawa.seq.model.PartyRole;
import com.seqwawa.seq.model.PartyStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * What the party finder tells the player in chat: an invite with its join and deny buttons, a
 * warning that their listing is about to be removed, who joined or left their party, new open
 * listings, and a reminder of the open ones. A line that waits for a player's name is shown only
 * while the session and the server connection it was for last.
 */
final class PartyFinderNotices {

    private static final String SEQ_INVITE_ALL_COMMAND = "/seq p invite-all";

    private final PartyFinderManager partyFinder;
    private final PartyFinderPlayer player;
    private volatile long nextOpenPartyAnnouncementAtMs;
    private volatile boolean openPartyAnnouncementRefreshInFlight;

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

    PartyFinderNotices(PartyFinderManager partyFinder) {
        this.partyFinder = partyFinder;
        this.player = partyFinder.player;
    }

    void reset() {
        nextOpenPartyAnnouncementAtMs = 0L;
    }

    /** An invite from {@code inviterUUID}, shown once their name is known, to the player it came for. */
    void invited(long listingId, String inviterUUID, String inviteToken) {
        withName(inviterUUID, inviterName -> {
            String message = "Party Finder invite from " + PartyFinderPlayer.displayName(inviterName) + ".";
            if (inviteToken == null || inviteToken.isBlank()) {
                player.notify(message);
                return;
            }
            String joinCommand = "/seq p join " + listingId + " token " + quoteForCommand(inviteToken);
            // Not the command: its token lets whoever holds it join.
            SeqClient.LOGGER.info("[PartyFinderWS] Showing invite listingId={}", listingId);
            player.show(
                    line(message),
                    Component.empty()
                            .append(pill("join", ChatFormatting.GREEN, joinCommand))
                            .append(Component.literal(" "))
                            .append(pill("deny", ChatFormatting.RED, "/seq p deny " + listingId)));
        });
    }

    void staleWarning(String reason, long listingId, Instant disbandAt, long minutesRemaining) {
        SeqClient.LOGGER.info(
                "[PartyFinderWS] handlePartyFinderStaleWarning reason={} listingId={} disbandAt={} minutesRemaining={}",
                reason,
                listingId,
                disbandAt,
                minutesRemaining);
        Listing current = partyFinder.getCurrentListing();
        String myUUID = player.uuid();
        if (!shouldDisplayStaleWarning(current, listingId, myUUID)) {
            SeqClient.LOGGER.warn(
                    "[PartyFinderWS] Ignoring stale warning for listingId={} currentListingId={} myUUID={}",
                    listingId,
                    current != null ? current.id() : null,
                    myUUID);
            return;
        }
        player.show(
                line(staleWarningMessage(minutesRemaining)),
                Component.empty().append(pill("extend 30m", ChatFormatting.GOLD, staleWarningExtendCommand(listingId))));
    }

    static boolean shouldDisplayStaleWarning(Listing currentListing, long listingId, String myUUID) {
        return currentListing != null
                && currentListing.id() == listingId
                && currentListing.isLedBy(myUUID)
                && currentListing.hasMember(myUUID);
    }

    static String staleWarningMessage(long minutesRemaining) {
        long safeMinutesRemaining = Math.max(0, minutesRemaining);
        String unit = safeMinutesRemaining == 1 ? "minute" : "minutes";
        return "Your Party Finder listing looks inactive and will be removed in "
                + safeMinutesRemaining + " " + unit + " unless activity resumes.";
    }

    static String staleWarningExtendCommand(long listingId) {
        return "/seq p extend " + listingId;
    }

    /** What changed for the player when the socket reported {@code action} on {@code updated}. */
    void partyUpdated(String action, Listing previous, Listing updated, String myUUID) {
        if (updated == null || myUUID == null || myUUID.isBlank()) {
            return;
        }
        switch (action) {
            case "CREATED" -> {
                if (updated.resolvedJoinPolicy() == PartyJoinPolicy.OPEN && !updated.includes(myUUID)) {
                    announceNewListing(updated);
                }
            }
            case "DISBANDED" -> {
                boolean wasInParty = (previous != null && previous.includes(myUUID)) || updated.includes(myUUID);
                if (wasInParty) {
                    player.notify("Your party was disbanded.");
                }
            }
            case "UPDATED" -> {
                if (previous != null) {
                    localPartyUpdated(previous, updated, myUUID);
                    if (updated.isLedBy(myUUID)) {
                        leaderPartyUpdated(previous, updated, myUUID);
                    }
                }
            }
            default -> {}
        }
    }

    private void announceNewListing(Listing listing) {
        if (listing.leaderUUID() == null || listing.leaderUUID().isBlank()) {
            return;
        }
        withName(listing.leaderUUID(), resolvedName -> {
            String activities = formatActivitySummary(listing);
            String message = PartyFinderPlayer.displayName(resolvedName) + " started "
                    + (activities.isBlank() ? "a party!" : "a " + activities + " party!");
            player.show(line(message)
                    .append(Component.literal(" "))
                    .append(pill("JOIN", ChatFormatting.GREEN, "/seq p join " + listing.id())));
        });
    }

    private void localPartyUpdated(Listing previous, Listing updated, String myUUID) {
        boolean wasInParty = previous.includes(myUUID);
        boolean isInParty = updated.includes(myUUID);
        if (wasInParty && !isInParty) {
            player.notify("You are no longer in party #" + updated.id() + ".");
            return;
        }
        if (!wasInParty && isInParty) {
            player.notify("You joined party #" + updated.id() + ".");
        }
        if (!isInParty) {
            return;
        }

        if (!Listing.samePlayer(previous.leaderUUID(), updated.leaderUUID())) {
            if (updated.isLedBy(myUUID)) {
                player.notify("You are now party leader.");
            } else if (previous.isLedBy(myUUID)) {
                notifyNamed(updated.leaderUUID(), "A player is now party leader.", " is now party leader.");
            }
        }

        Member previousMember = previous.member(myUUID);
        Member updatedMember = updated.member(myUUID);
        PartyRole previousRole = previousMember == null ? null : previousMember.role();
        PartyRole updatedRole = updatedMember == null ? null : updatedMember.role();
        if (previousRole != null && updatedRole != null && previousRole != updatedRole) {
            player.notify("Your role is now " + PartyMember.formatRole(updatedRole) + ".");
        }

        if (previous.status() != updated.status()) {
            switch (updated.status()) {
                case OPEN -> player.notify("Party is now open.");
                case CLOSED -> player.notify("Party is now closed.");
                case FULL -> {
                    if (!updated.isLedBy(myUUID)) {
                        player.notify("Party is now full.");
                    }
                }
                default -> {}
            }
        }
    }

    private void leaderPartyUpdated(Listing previous, Listing updated, String myUUID) {
        if (!previous.isAutoCapacityClosed() && updated.isAutoCapacityClosed()) {
            partyFinder.pushUiError("Party auto-closed at capacity. It will reopen when a slot frees up.");
        } else if (previous.isAutoCapacityClosed() && updated.status() == PartyStatus.OPEN) {
            partyFinder.pushUiError("Party auto-opened after a slot freed up.");
        }

        notifyMembers(membersMissingFrom(updated, previous, myUUID), " joined your party.");
        notifyMembers(membersMissingFrom(previous, updated, myUUID), " left your party.");

        if (!previous.isFull() && updated.isFull()) {
            player.show(
                    line("Party is now full."),
                    Component.empty().append(pill("invite all", ChatFormatting.GREEN, SEQ_INVITE_ALL_COMMAND)));
        }
    }

    /** The members of {@code listing}, the player aside, that {@code other} does not have. */
    private static List<String> membersMissingFrom(Listing listing, Listing other, String myUUID) {
        if (listing.members() == null) {
            return List.of();
        }
        Set<String> otherMembers = other.memberKeys();
        List<String> missing = new ArrayList<>();
        for (Member member : listing.members()) {
            String key = member == null ? null : Listing.playerKey(member.playerUUID());
            if (key != null && !Listing.samePlayer(myUUID, member.playerUUID()) && !otherMembers.contains(key)) {
                missing.add(member.playerUUID());
            }
        }
        return missing;
    }

    /** "Name joined your party.", or how many when more than one did. */
    private void notifyMembers(List<String> memberUUIDs, String suffix) {
        if (memberUUIDs.size() > 1) {
            player.notify(memberUUIDs.size() + " players" + suffix);
        } else if (memberUUIDs.size() == 1) {
            notifyNamed(memberUUIDs.getFirst(), "Player" + suffix, suffix);
        }
    }

    private void notifyNamed(String playerUUID, String fallbackMessage, String suffix) {
        if (playerUUID == null || playerUUID.isBlank()) {
            player.notify(fallbackMessage);
            return;
        }
        withName(playerUUID, resolvedName -> {
            String name = PartyFinderPlayer.displayName(resolvedName);
            player.notify("a player".equals(name) ? fallbackMessage : name + suffix);
        });
    }

    /**
     * Looks up {@code uuid}'s name, for three seconds at most, then runs {@code show} with it
     * (null when it was not found) on the game thread, unless the session or the server
     * connection the player was on has changed since this was called.
     */
    private void withName(String uuid, Consumer<String> show) {
        PartyFinderManager.Ticket ticket = partyFinder.ticket();
        partyFinder.nameSoon(uuid).whenComplete((name, error) ->
                partyFinder.forSamePlayer(ticket, () -> show.accept(error == null ? name : null)));
    }

    // ══════════════════════════════════════════════════════════════
    // Open party reminders
    // ══════════════════════════════════════════════════════════════

    void tickOpenPartyReminders() {
        if (!shouldRunOpenPartyAnnouncements()) {
            nextOpenPartyAnnouncementAtMs = 0L;
            return;
        }

        long intervalMs = Math.max(1L, SeqClient.getAnnounceOpenPartiesIntervalMinutesSetting().getValue()) * 60_000L;
        long now = System.currentTimeMillis();
        if (nextOpenPartyAnnouncementAtMs <= 0L) {
            nextOpenPartyAnnouncementAtMs = now + intervalMs;
            return;
        }
        if (now < nextOpenPartyAnnouncementAtMs || openPartyAnnouncementRefreshInFlight) {
            return;
        }

        nextOpenPartyAnnouncementAtMs = now + intervalMs;
        remindOfOpenParties();
    }

    /** Refreshes the listings, then reminds the player of the open ones as they are when the reminder shows. */
    void remindOfOpenParties() {
        openPartyAnnouncementRefreshInFlight = true;
        PartyFinderManager.Ticket ticket = partyFinder.ticket();
        partyFinder.actions().refreshListingsForAnnouncements(ticket).whenComplete((refreshedListings, error) -> {
            openPartyAnnouncementRefreshInFlight = false;
            if (error != null) {
                SeqClient.LOGGER.warn("[PartyFinderWS] Open party reminder refresh failed", error);
            } else if (refreshedListings != null && !refreshedListings.isEmpty()) {
                // A listing may close, or fill, before this runs: what was fetched is not read again.
                partyFinder.forSamePlayer(ticket, this::announceOpenParties);
            }
        });
    }

    private void announceOpenParties() {
        String myUUID = player.uuid();
        if (myUUID == null || !shouldRunOpenPartyAnnouncements()) {
            return;
        }
        OpenPartyAnnouncementSummary summary = buildOpenPartyAnnouncementSummary(
                selectOpenPartyAnnouncementCandidates(partyFinder.getListings(), myUUID), this::reminderLeaderName);
        if (summary.isEmpty()) {
            return;
        }

        MutableComponent message = line("Open Sequoia parties (" + summary.entries().size() + "):");
        for (OpenPartyAnnouncementEntry entry : summary.entries()) {
            message.append(Component.literal("\n"));
            message.append(Component.literal(entry.activitySummary() + " " + entry.occupiedSlots() + "/"
                            + entry.maxPartySize() + " | " + entry.leaderName() + " ")
                    .withStyle(ChatFormatting.GRAY));
            message.append(pill("JOIN #" + entry.listingId(), ChatFormatting.GREEN, entry.joinCommand()));
        }
        player.show(message);
    }

    private boolean shouldRunOpenPartyAnnouncements() {
        return player.wantsOpenPartyReminders() && !partyFinder.isInParty();
    }

    static List<Listing> selectOpenPartyAnnouncementCandidates(List<Listing> source, String myUUID) {
        if (source == null || source.isEmpty() || myUUID == null || myUUID.isBlank()) {
            return List.of();
        }
        return source.stream()
                .filter(Objects::nonNull)
                .filter(listing -> listing.status() == PartyStatus.OPEN)
                .filter(listing -> listing.resolvedJoinPolicy() == PartyJoinPolicy.OPEN)
                .filter(listing -> listing.occupiedSlotCount() < listing.maxPartySize())
                .filter(listing -> !listing.includes(myUUID))
                .filter(listing -> !listing.reservesSlotFor(myUUID))
                .sorted(Comparator.comparing(Listing::createdAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    static OpenPartyAnnouncementSummary buildOpenPartyAnnouncementSummary(
            List<Listing> candidates, Function<String, String> leaderNameResolver) {
        if (candidates == null || candidates.isEmpty()) {
            return new OpenPartyAnnouncementSummary(List.of());
        }
        return new OpenPartyAnnouncementSummary(candidates.stream()
                .filter(Objects::nonNull)
                .map(listing -> {
                    String activities = formatActivitySummary(listing);
                    return new OpenPartyAnnouncementEntry(
                            listing.id(),
                            activities.isBlank() ? "Party" : activities,
                            listing.occupiedSlotCount(),
                            listing.maxPartySize(),
                            leaderNameResolver.apply(listing.leaderUUID()),
                            "/seq p join " + listing.id());
                })
                .toList());
    }

    private String reminderLeaderName(String leaderUUID) {
        String resolved = player.knownName(leaderUUID);
        return resolved == null || resolved.isBlank() ? "Unknown" : resolved;
    }

    /** The listing's activities by their short names: "NOG/TNA". */
    static String formatActivitySummary(Listing listing) {
        return listing.resolvedActivities().stream()
                .map(Activity::name)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .map(PartyListing::backendNameToDisplayName)
                .map(name -> switch (name) {
                    case "Nest of the Grootslangs" -> "NOG";
                    case "The Nameless Anomaly" -> "TNA";
                    case "The Canyon Colossus" -> "TCC";
                    case "Nexus of Light" -> "NOL";
                    case "The Wartorn Palace" -> "TWP";
                    case "Prelude to Annihilation" -> "ANNI";
                    default -> name;
                })
                .distinct()
                .collect(Collectors.joining("/"));
    }

    private static String quoteForCommand(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** A chat line: the Sequoia label, then {@code message} in grey. */
    private static MutableComponent line(String message) {
        return NotificationAccessor.prefixComponent()
                .append(Component.literal(String.valueOf(message)).withStyle(ChatFormatting.GRAY));
    }

    private static MutableComponent pill(String label, ChatFormatting color, String command) {
        return NotificationAccessor.wynnPill(label, color, ChatFormatting.WHITE, new ClickEvent.RunCommand(command));
    }
}
