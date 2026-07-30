package com.seqwawa.seq.managers;

import com.seqwawa.seq.client.SeqClient;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Maintains a recent canonical party snapshot for raid-completion name resolution. */
public final class RaidPartySnapshotTracker {
    private static final long SNAPSHOT_INTERVAL_MS = 2_000;
    private static final long SNAPSHOT_MAX_AGE_MS = 15_000;
    private static final int DEFAULT_MAX_RAID_PARTY_MEMBERS = 4;
    private static final int ABSOLUTE_MAX_RAID_PARTY_MEMBERS = 10;
    private static final Pattern MC_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private static volatile PartySnapshot latestSnapshot = PartySnapshot.empty();
    private static long lastPollAtMs;

    private RaidPartySnapshotTracker() {}

    public static synchronized void tick() {
        long now = System.currentTimeMillis();
        if (now - lastPollAtMs < SNAPSHOT_INTERVAL_MS) {
            return;
        }
        captureSnapshot(now);
    }

    public static synchronized void refreshNow() {
        captureSnapshot(System.currentTimeMillis());
    }

    private static void captureSnapshot(long now) {
        lastPollAtMs = now;

        WynnPartyScoreboardReader.PartyObservation partyObservation =
                WynnPartyScoreboardReader.readPartyObservation();
        PartySnapshot observedSnapshot = collectCurrentPartySnapshot(
                partyObservation.members(),
                partyObservation.canonicalRosterUsernames(),
                partyObservation.raidSidebarActive(),
                now);
        latestSnapshot = updateSnapshot(latestSnapshot, observedSnapshot, partyObservation.raidSidebarActive(), now);
    }

    public static List<String> resolvePartyMembers(List<String> parsedPartyMembers, int displayedPartySize) {
        return resolvePartyMembers(parsedPartyMembers, displayedPartySize, DEFAULT_MAX_RAID_PARTY_MEMBERS);
    }

    public static List<String> resolvePartyMembers(
            List<String> parsedPartyMembers, int displayedPartySize, int maximumPartySize) {
        long now = System.currentTimeMillis();
        PartySnapshot currentSnapshot = latestSnapshot;
        long snapshotAgeMs = now - currentSnapshot.capturedAtMs();
        boolean fresh = snapshotAgeMs <= SNAPSHOT_MAX_AGE_MS;
        PartySnapshot snapshot = fresh ? currentSnapshot : PartySnapshot.empty();
        List<String> resolved =
                choosePartyMembers(parsedPartyMembers, snapshot, displayedPartySize, maximumPartySize);
        SeqClient.LOGGER.info(
                "[RaidPartySnapshot] resolution={} displayedCount={} maxPartySize={} snapshotAgeMs={} raidContext={} snapshotMembers={} parsedMembers={} resolvedMembers={}",
                resolutionSource(parsedPartyMembers, resolved, snapshot, fresh, maximumPartySize),
                displayedPartySize,
                maximumPartySize,
                currentSnapshot.capturedAtMs() == 0 ? -1 : snapshotAgeMs,
                snapshot.raidContext(),
                snapshot.usernames(),
                parsedPartyMembers,
                resolved);
        return resolved;
    }

    static List<String> choosePartyMembers(
            List<String> parsedPartyMembers,
            List<SnapshotMember> snapshotPartyMembers,
            int displayedPartySize) {
        return choosePartyMembers(
                parsedPartyMembers,
                PartySnapshot.from(snapshotPartyMembers, true, 0),
                displayedPartySize,
                DEFAULT_MAX_RAID_PARTY_MEMBERS);
    }

    static List<String> choosePartyMembers(
            List<String> parsedPartyMembers, PartySnapshot snapshot, int displayedPartySize) {
        return choosePartyMembers(
                parsedPartyMembers, snapshot, displayedPartySize, DEFAULT_MAX_RAID_PARTY_MEMBERS);
    }

    static List<String> choosePartyMembers(
            List<String> parsedPartyMembers,
            PartySnapshot snapshot,
            int displayedPartySize,
            int maximumPartySize) {
        List<String> parsed = sanitizeParty(parsedPartyMembers);
        int safeMaximumPartySize =
                Math.max(1, Math.min(maximumPartySize, ABSOLUTE_MAX_RAID_PARTY_MEMBERS));

        if (displayedPartySize < 1
                || displayedPartySize > safeMaximumPartySize
                || !snapshot.raidContext()
                || snapshot.usernames().isEmpty()
                || snapshot.usernames().size() > safeMaximumPartySize) {
            return parsed;
        }

        List<String> resolved = new ArrayList<>(parsed.size());
        for (String parsedMember : parsed) {
            resolved.add(snapshot.resolveAlias(parsedMember));
        }
        if (overlapCount(sanitizeParty(resolved), snapshot.usernames()) < 1) {
            return parsed;
        }
        if (snapshot.usernames().size() >= displayedPartySize) {
            return snapshot.usernames();
        }
        return sanitizeParty(resolved);
    }

    public static void reset() {
        invalidate();
    }

    public static synchronized void invalidate() {
        latestSnapshot = PartySnapshot.empty();
        lastPollAtMs = 0;
    }

    private static PartySnapshot collectCurrentPartySnapshot(
            List<WynnPartyScoreboardReader.PartyHealth> partyHealth,
            List<String> canonicalRosterUsernames,
            boolean raidContext,
            long capturedAtMs) {
        List<SnapshotMember> members = new ArrayList<>();
        for (WynnPartyScoreboardReader.PartyHealth member : partyHealth) {
            members.add(new SnapshotMember(member.nickname(), member.username()));
        }

        List<String> supplementalRoster = sanitizeParty(canonicalRosterUsernames);
        if (supplementalRoster.isEmpty()) {
            WynnPartySyncManager syncManager = SeqClient.getWynnPartySyncManager();
            List<String> syncUsernames =
                    syncManager != null ? syncManager.getObservedMemberUsernames() : List.of();
            supplementalRoster = compatibleSupplementalRoster(members, partyHealth.size(), syncUsernames);
        }
        for (String username : supplementalRoster) {
            members.add(new SnapshotMember(username, username));
        }
        return PartySnapshot.from(members, raidContext, capturedAtMs);
    }

    static List<String> compatibleSupplementalRoster(
            List<SnapshotMember> primaryMembers, int observedRowCount, List<String> supplementalUsernames) {
        List<String> supplemental = sanitizeParty(supplementalUsernames);
        if (observedRowCount < 1 || supplemental.size() != observedRowCount) {
            return List.of();
        }

        for (SnapshotMember primaryMember : primaryMembers) {
            String username = sanitizeUsername(primaryMember.username());
            if (username != null
                    && supplemental.stream().noneMatch(username::equalsIgnoreCase)) {
                return List.of();
            }
        }
        return supplemental;
    }

    private static List<String> sanitizeParty(List<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return List.of();
        }
        Map<String, String> distinct = new LinkedHashMap<>();
        for (String username : usernames) {
            String safeUsername = sanitizeUsername(username);
            if (safeUsername != null) {
                distinct.putIfAbsent(safeUsername.toLowerCase(Locale.ROOT), safeUsername);
            }
        }
        return List.copyOf(distinct.values());
    }

    private static String sanitizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return null;
        }
        String trimmed = username.trim();
        return MC_USERNAME_PATTERN.matcher(trimmed).matches() ? trimmed : null;
    }

    private static int overlapCount(List<String> left, List<String> right) {
        int matches = 0;
        for (String value : left) {
            if (right.stream().anyMatch(value::equalsIgnoreCase)) {
                matches++;
            }
        }
        return matches;
    }

    private static String resolutionSource(
            List<String> parsedPartyMembers,
            List<String> resolvedPartyMembers,
            PartySnapshot snapshot,
            boolean fresh,
            int maximumPartySize) {
        if (!fresh || snapshot.usernames().isEmpty()) {
            return "missing_or_stale_snapshot";
        }
        if (!snapshot.raidContext()) {
            return "non_raid_snapshot";
        }
        if (snapshot.usernames().size() > maximumPartySize) {
            return "oversized_snapshot";
        }
        if (resolvedPartyMembers.equals(snapshot.usernames())) {
            return "complete_snapshot";
        }
        return resolvedPartyMembers.equals(sanitizeParty(parsedPartyMembers))
                ? "completion_message"
                : "snapshot_aliases";
    }

    static PartySnapshot updateSnapshot(
            PartySnapshot current, PartySnapshot observed, boolean raidSidebarActive, long capturedAtMs) {
        if (!raidSidebarActive) {
            return current;
        }
        if (capturedAtMs - current.capturedAtMs() > SNAPSHOT_MAX_AGE_MS) {
            current = PartySnapshot.empty();
        }
        if (!observed.usernames().isEmpty()) {
            if (current.hasSameMembers(observed)) {
                return current.mergeAliases(observed, capturedAtMs);
            }
            if (current.containsAllMembers(observed)) {
                return current.mergeAliases(observed, current.capturedAtMs());
            }
            return observed;
        }
        return current;
    }

    record SnapshotMember(String displayedName, String username) {}

    record PartySnapshot(List<String> usernames, Map<String, String> aliases, boolean raidContext, long capturedAtMs) {
        PartySnapshot {
            usernames = List.copyOf(usernames);
            aliases = Map.copyOf(aliases);
        }

        static PartySnapshot from(List<SnapshotMember> members, long capturedAtMs) {
            return from(members, false, capturedAtMs);
        }

        static PartySnapshot from(List<SnapshotMember> members, boolean raidContext, long capturedAtMs) {
            Map<String, String> usernames = new LinkedHashMap<>();
            Map<String, String> aliases = new LinkedHashMap<>();
            Set<String> ambiguousAliases = new HashSet<>();

            for (SnapshotMember member : members) {
                String username = sanitizeUsername(member.username());
                if (username == null) {
                    continue;
                }

                usernames.putIfAbsent(username.toLowerCase(Locale.ROOT), username);
                addAlias(aliases, ambiguousAliases, member.displayedName(), username);
                addAlias(aliases, ambiguousAliases, username, username);
            }
            return new PartySnapshot(List.copyOf(usernames.values()), aliases, raidContext, capturedAtMs);
        }

        private static void addAlias(
                Map<String, String> aliases, Set<String> ambiguousAliases, String displayedName, String username) {
            String alias = sanitizeUsername(displayedName);
            if (alias == null) {
                return;
            }

            String key = alias.toLowerCase(Locale.ROOT);
            if (ambiguousAliases.contains(key)) {
                return;
            }
            String existing = aliases.putIfAbsent(key, username);
            if (existing != null && !existing.equalsIgnoreCase(username)) {
                aliases.remove(key);
                ambiguousAliases.add(key);
            }
        }

        private String resolveAlias(String displayedName) {
            String resolved = aliases.get(displayedName.toLowerCase(Locale.ROOT));
            return resolved != null ? resolved : displayedName;
        }

        private boolean hasSameMembers(PartySnapshot other) {
            if (usernames.size() != other.usernames.size()) {
                return false;
            }
            return usernames.stream()
                    .allMatch(username -> other.usernames.stream().anyMatch(username::equalsIgnoreCase));
        }

        private boolean containsAllMembers(PartySnapshot other) {
            return usernames.size() > other.usernames.size()
                    && other.usernames.stream()
                            .allMatch(username -> usernames.stream().anyMatch(username::equalsIgnoreCase));
        }

        private PartySnapshot mergeAliases(PartySnapshot other, long capturedAtMs) {
            Map<String, String> mergedAliases = new LinkedHashMap<>(aliases);
            for (Map.Entry<String, String> alias : other.aliases.entrySet()) {
                mergedAliases.putIfAbsent(alias.getKey(), alias.getValue());
            }
            return new PartySnapshot(usernames, mergedAliases, raidContext || other.raidContext, capturedAtMs);
        }

        private static PartySnapshot empty() {
            return new PartySnapshot(List.of(), Map.of(), false, 0);
        }
    }
}
