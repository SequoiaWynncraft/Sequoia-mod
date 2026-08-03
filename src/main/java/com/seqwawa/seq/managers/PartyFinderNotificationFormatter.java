package com.seqwawa.seq.managers;

import com.seqwawa.seq.model.Activity;
import com.seqwawa.seq.model.Listing;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

final class PartyFinderNotificationFormatter {
    private PartyFinderNotificationFormatter() {}

    static String staleWarningMessage(long minutesRemaining) {
        long safeMinutesRemaining = Math.max(0, minutesRemaining);
        String unit = safeMinutesRemaining == 1 ? "minute" : "minutes";
        return "Your Party Finder listing looks inactive and will be removed in "
                + safeMinutesRemaining
                + " "
                + unit
                + " unless activity resumes.";
    }

    static String staleWarningExtendCommand(long listingId) {
        return "/seq p extend " + listingId;
    }

    static String quoteForCommand(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    static String inviterName(String inviterName) {
        if (inviterName == null
                || inviterName.isBlank()
                || "Loading...".equalsIgnoreCase(inviterName)
                || "Unknown".equalsIgnoreCase(inviterName)) {
            return "a player";
        }
        return inviterName;
    }

    static String activitySummary(Listing listing) {
        if (listing == null) {
            return "";
        }
        return listing.resolvedActivities().stream()
                .map(Activity::name)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .map(PartyFinderNotificationFormatter::abbreviateActivityName)
                .distinct()
                .collect(Collectors.joining("/"));
    }

    static String abbreviateActivityName(String activityName) {
        String normalizedName = PartyListing.backendNameToDisplayName(activityName);
        return switch (normalizedName) {
            case "Nest of the Grootslangs" -> "NOG";
            case "The Nameless Anomaly" -> "TNA";
            case "The Canyon Colossus" -> "TCC";
            case "Nexus of Light" -> "NOL";
            case "The Wartorn Palace" -> "TWP";
            case "Prelude to Annihilation" -> "ANNI";
            default -> normalizedName;
        };
    }

    static PartyFinderManager.OpenPartyAnnouncementSummary openPartySummary(
            List<Listing> candidates, Function<String, String> leaderNameResolver) {
        if (candidates == null || candidates.isEmpty()) {
            return new PartyFinderManager.OpenPartyAnnouncementSummary(List.of());
        }
        List<PartyFinderManager.OpenPartyAnnouncementEntry> entries = candidates.stream()
                .filter(Objects::nonNull)
                .map(listing -> new PartyFinderManager.OpenPartyAnnouncementEntry(
                        listing.id(),
                        defaultActivitySummary(activitySummary(listing)),
                        listing.occupiedSlotCount(),
                        listing.maxPartySize(),
                        leaderNameResolver.apply(listing.leaderUUID()),
                        "/seq p join " + listing.id()))
                .toList();
        return new PartyFinderManager.OpenPartyAnnouncementSummary(entries);
    }

    static String inviteAllMessage(int sentCount, int skippedCount) {
        if (sentCount <= 0) {
            return skippedCount > 0
                    ? "no valid party members to invite. Skipped " + skippedCount + "."
                    : "no valid party members to invite.";
        }
        String inviteWord = sentCount == 1 ? "party invite" : "party invites";
        return skippedCount > 0
                ? "sent " + sentCount + " " + inviteWord + ". Skipped " + skippedCount + "."
                : "sent " + sentCount + " " + inviteWord + ".";
    }

    private static String defaultActivitySummary(String summary) {
        return summary == null || summary.isBlank() ? "Party" : summary;
    }
}
