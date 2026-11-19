package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import com.seqwawa.seq.managers.PartyFinderManager.CommandResult;
import com.seqwawa.seq.managers.PartyFinderManager.Ticket;
import com.seqwawa.seq.model.Listing;
import com.seqwawa.seq.model.Member;
import com.seqwawa.seq.network.ConnectionManager;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * The party commands the party finder sends in the game, as the player: kick, promote and invite,
 * a message to the member it acted on, and an invite for every member of the listing. Each goes
 * only to the session and the server connection its {@link Ticket} was read on; a player who
 * reconnects while one waits never sends it on the new connection.
 */
public final class PartyGameCommands {

    static final String KICK = "party kick ";
    static final String PROMOTE = "party promote ";
    private static final long NAME_LOOKUP_TIMEOUT_SECONDS = 3L;
    private static final long INVITE_ALL_COMMAND_DELAY_MS = 500L;
    private static final String PARTY_CHANGED = "your active party changed before invites could be sent.";

    private final PartyFinderManager partyFinder;
    private final PartyFinderPlayer player;

    public record InviteAllResult(boolean success, boolean sentAny, int sentCount, int skippedCount, String message) {}

    private record InviteTarget(String memberUUID, String username) {}

    PartyGameCommands(PartyFinderManager partyFinder) {
        this.partyFinder = partyFinder;
        this.player = partyFinder.player;
    }

    static String gamePartyInviteCommand(String username) {
        return "party invite " + username;
    }

    /** Sends {@code prefix} and {@code target}'s name, once it is known, for {@code ticket}'s player. */
    void partyCommandFor(Ticket ticket, UUID target, String prefix) {
        player.nameOf(target.toString())
                .completeOnTimeout(null, NAME_LOOKUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .thenAccept(resolvedName -> {
                    String name = PartyFinderPlayer.commandName(resolvedName);
                    if (name == null) {
                        SeqClient.LOGGER.warn("Skipping party command '{}' for unresolved UUID {}", prefix, target);
                        return;
                    }
                    partyFinder.forSamePlayer(ticket, () -> player.sendCommand(prefix + name));
                });
    }

    /** Messages {@code target}, when their name is already known, for {@code ticket}'s player. */
    void directMessage(Ticket ticket, UUID target, String message) {
        String name = PartyFinderPlayer.commandName(player.knownName(target.toString()));
        if (name == null) {
            SeqClient.LOGGER.warn("Skipping DM for unresolved target UUID {}", target);
            return;
        }
        partyFinder.forSamePlayer(ticket, () -> player.sendCommand("msg " + name + " " + message));
    }

    /** Asks Wynncraft for the party roster, which the Wynn party sync reports to the listing. */
    public CommandResult<Void> scanCurrentWynnParty() {
        if (partyFinder.getCurrentListing() == null) {
            return CommandResult.failure("You need an active Sequoia party before scanning your Wynn party.");
        }
        if (!partyFinder.isPartyLeader()) {
            return CommandResult.failure("Only the party leader can scan the current Wynn party.");
        }
        if (!ConnectionManager.isConnected()) {
            return CommandResult.failure("Connect to Sequoia before scanning your Wynn party.");
        }
        WynnPartySyncManager syncManager = SeqClient.getWynnPartySyncManager();
        if (syncManager == null || !syncManager.requestCurrentPartySnapshot()) {
            return CommandResult.failure("Unable to request the current Wynn party roster.");
        }
        return CommandResult.success(
                "Requested the current Wynn party roster. The Sequoia party will update when Wynncraft replies.",
                null);
    }

    /** Invites every member of the listing to the Wynn party, and tells the player how it went. */
    public CompletableFuture<InviteAllResult> inviteAllCurrentMembers() {
        return inviteAll(partyFinder.ticket(), true);
    }

    public CompletableFuture<CommandResult<Void>> inviteAllCurrentMembersFromCommand() {
        Ticket ticket = partyFinder.ticket();
        return partyFinder.actions().ensureCurrentListing(ticket).thenCompose(current -> !current.success()
                ? CompletableFuture.completedFuture(CommandResult.<Void>failure(current.message()))
                : inviteAll(ticket, false).thenApply(result -> result.success()
                        ? CommandResult.success("Invite all: " + result.message(), null)
                        : CommandResult.failure("Invite all: " + result.message())));
    }

    private CompletableFuture<InviteAllResult> inviteAll(Ticket ticket, boolean notifyPlayer) {
        Listing listing = partyFinder.getCurrentListing();
        if (listing == null) {
            return done(ticket, false, 0, 0, "no active party found.", notifyPlayer);
        }
        if (!partyFinder.isPartyLeader()) {
            return done(ticket, false, 0, 0, "only the party leader can use this.", notifyPlayer);
        }
        if (ticket.connection() == null) {
            return done(ticket, false, 0, 0, "client connection unavailable.", notifyPlayer);
        }
        List<Member> members = listing.members();
        if (members == null || members.isEmpty()) {
            return done(ticket, true, 0, 0, "no valid party members to invite.", notifyPlayer);
        }

        WynnPartySyncManager syncManager = SeqClient.getWynnPartySyncManager();
        Set<String> inWynnParty = syncManager == null
                ? Set.of()
                : syncManager.getObservedMemberUsernames().stream()
                        .map(username -> username.toLowerCase(Locale.ROOT))
                        .collect(Collectors.toUnmodifiableSet());
        String myUUID = player.uuid();
        List<String> candidates = new ArrayList<>();
        List<CompletableFuture<String>> names = new ArrayList<>();
        int skipped = 0;
        for (Member member : members) {
            if (member == null || member.playerUUID() == null || member.playerUUID().isBlank()) {
                skipped++;
            } else if (!Listing.samePlayer(myUUID, member.playerUUID())) {
                candidates.add(member.playerUUID());
                names.add(player.nameOf(member.playerUUID()).exceptionally(ignored -> null));
            }
        }
        if (candidates.isEmpty()) {
            return done(ticket, true, 0, skipped, formatInviteAllMessage(0, skipped), notifyPlayer);
        }

        int skippedBeforeLookup = skipped;
        return CompletableFuture.allOf(names.toArray(CompletableFuture[]::new)).thenCompose(ignored -> {
            Listing current = partyFinder.getCurrentListing();
            if (current == null || current.id() != listing.id()) {
                return done(ticket, false, 0, skippedBeforeLookup, PARTY_CHANGED, notifyPlayer);
            }
            List<InviteTarget> targets = new ArrayList<>();
            Set<String> seen = new LinkedHashSet<>();
            int skippedByName = skippedBeforeLookup;
            for (int i = 0; i < candidates.size(); i++) {
                String username = PartyFinderPlayer.commandName(names.get(i).getNow(null));
                String key = username == null ? null : username.toLowerCase(Locale.ROOT);
                if (key == null || !seen.add(key) || inWynnParty.contains(key)) {
                    skippedByName++;
                } else {
                    targets.add(new InviteTarget(candidates.get(i), username));
                }
            }
            if (targets.isEmpty()) {
                return done(ticket, true, 0, skippedByName, formatInviteAllMessage(0, skippedByName), notifyPlayer);
            }
            CompletableFuture<InviteAllResult> dispatched = new CompletableFuture<>();
            int skippedAtLookup = skippedByName;
            partyFinder.gameThread.execute(() -> dispatch(ticket, listing.id(), targets, skippedAtLookup, notifyPlayer)
                    .whenComplete((result, error) -> dispatched.complete(result)));
            return dispatched;
        });
    }

    /** On the game thread: the invites, one at a time, while the party and the player are the same. */
    private CompletableFuture<InviteAllResult> dispatch(
            Ticket ticket, long listingId, List<InviteTarget> targets, int skippedCount, boolean notifyPlayer) {
        String stillFor = stillFor(ticket, listingId);
        if (stillFor != null) {
            return done(ticket, false, 0, skippedCount, stillFor, notifyPlayer);
        }
        CompletableFuture<InviteAllResult> finished = new CompletableFuture<>();
        inviteNext(ticket, listingId, targets.iterator(), 0, skippedCount, notifyPlayer, finished);
        return finished;
    }

    /**
     * Invites the next target still in the party, and waits half a second from then before the
     * one after, so a late tick never sends two at once.
     */
    private void inviteNext(Ticket ticket, long listingId, Iterator<InviteTarget> targets, int sent, int skipped,
            boolean notifyPlayer, CompletableFuture<InviteAllResult> finished) {
        while (targets.hasNext()) {
            InviteTarget target = targets.next();
            if (stillFor(ticket, listingId) != null || !partyFinder.getCurrentListing().hasMember(target.memberUUID())) {
                skipped++;
                continue;
            }
            player.sendCommand(gamePartyInviteCommand(target.username()));
            sent++;
            if (targets.hasNext()) {
                int sentSoFar = sent;
                int skippedSoFar = skipped;
                partyFinder.gameThread.after(INVITE_ALL_COMMAND_DELAY_MS, () -> inviteNext(
                        ticket, listingId, targets, sentSoFar, skippedSoFar, notifyPlayer, finished));
                return;
            }
        }
        done(ticket, true, sent, skipped, formatInviteAllMessage(sent, skipped), notifyPlayer)
                .thenAccept(finished::complete);
    }

    /** Null while invites may still go out for {@code ticket}'s listing; else why they may not. */
    private String stillFor(Ticket ticket, long listingId) {
        if (!partyFinder.holds(ticket) || player.connection() == null || player.connection() != ticket.connection()) {
            return "client connection unavailable.";
        }
        Listing current = partyFinder.getCurrentListing();
        if (current == null || current.id() != listingId) {
            return PARTY_CHANGED;
        }
        return partyFinder.isPartyLeader() ? null : "only the party leader can use this.";
    }

    /** The result, told to the player when asked, unless their session or server connection has gone since. */
    private CompletableFuture<InviteAllResult> done(
            Ticket ticket, boolean success, int sentCount, int skippedCount, String message, boolean notifyPlayer) {
        if (notifyPlayer) {
            partyFinder.forSamePlayer(ticket, () -> player.notify(message));
        }
        return CompletableFuture.completedFuture(
                new InviteAllResult(success, sentCount > 0, sentCount, skippedCount, message));
    }

    private static String formatInviteAllMessage(int sentCount, int skippedCount) {
        String skipped = skippedCount > 0 ? " Skipped " + skippedCount + "." : "";
        if (sentCount <= 0) {
            return "no valid party members to invite." + skipped;
        }
        return "sent " + sentCount + " " + (sentCount == 1 ? "party invite" : "party invites") + "." + skipped;
    }
}
