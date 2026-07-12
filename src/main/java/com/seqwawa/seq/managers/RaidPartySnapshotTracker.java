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
    private static final long SNAPSHOT_MAX_AGE_MS = 5_000;
    private static final int MAX_RAID_PARTY_MEMBERS = 4;
    private static final Pattern MC_USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

    private static volatile PartySnapshot latestSnapshot = PartySnapshot.empty();
    private static long lastPollAtMs;

    private RaidPartySnapshotTracker() {}

    public static synchronized void tick() {
        long now = System.currentTimeMillis();
        if (now - lastPollAtMs < SNAPSHOT_INTERVAL_MS) {
            return;
        }
        lastPollAtMs = now;

        WynnPartyScoreboardReader.PartyObservation partyObservation =
                WynnPartyScoreboardReader.readPartyObservation();
        PartySnapshot observedSnapshot = collectCurrentPartySnapshot(partyObservation.members(), now);
        latestSnapshot = updateSnapshot(latestSnapshot, observedSnapshot, partyObservation.raidSidebarActive(), now);
    }

    public static List<String> resolvePartyMembers(List<String> parsedPartyMembers, int displayedPartySize) {
        long now = System.currentTimeMillis();
        PartySnapshot currentSnapshot = latestSnapshot;
        PartySnapshot snapshot = now - currentSnapshot.capturedAtMs() <= SNAPSHOT_MAX_AGE_MS
                ? currentSnapshot
                : PartySnapshot.empty();
        return choosePartyMembers(parsedPartyMembers, snapshot, displayedPartySize);
    }

    static List<String> choosePartyMembers(
            List<String> parsedPartyMembers,
            List<SnapshotMember> snapshotPartyMembers,
            int displayedPartySize) {
        return choosePartyMembers(parsedPartyMembers, PartySnapshot.from(snapshotPartyMembers, 0), displayedPartySize);
    }

    private static List<String> choosePartyMembers(
            List<String> parsedPartyMembers, PartySnapshot snapshot, int displayedPartySize) {
        List<String> parsed = sanitizeParty(parsedPartyMembers);

        if (displayedPartySize < 1
                || displayedPartySize > MAX_RAID_PARTY_MEMBERS
                || snapshot.usernames().isEmpty()) {
            return parsed;
        }

        List<String> resolved = new ArrayList<>(parsed.size());
        for (String parsedMember : parsed) {
            resolved.add(snapshot.resolveAlias(parsedMember));
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
            List<WynnPartyScoreboardReader.PartyHealth> partyHealth, long capturedAtMs) {
        List<SnapshotMember> members = new ArrayList<>();
        for (WynnPartyScoreboardReader.PartyHealth member : partyHealth) {
            members.add(new SnapshotMember(member.nickname(), member.username()));
        }

        WynnPartySyncManager syncManager = SeqClient.getWynnPartySyncManager();
        if (syncManager != null) {
            for (String username : syncManager.getObservedMemberUsernames()) {
                members.add(new SnapshotMember(username, username));
            }
        }
        return PartySnapshot.from(members, capturedAtMs);
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

    static PartySnapshot updateSnapshot(
            PartySnapshot current, PartySnapshot observed, boolean raidSidebarActive, long capturedAtMs) {
        if (!observed.usernames().isEmpty()) {
            if (current.hasSameMembers(observed)) {
                return current.mergeAliases(observed, capturedAtMs);
            }
            return observed;
        }
        return raidSidebarActive && !current.usernames().isEmpty() ? current.refresh(capturedAtMs) : current;
    }

    private static String localUsername() {
        if (SeqClient.mc != null && SeqClient.mc.getUser() != null) {
            return sanitizeUsername(SeqClient.mc.getUser().getName());
        }
        if (SeqClient.mc != null && SeqClient.mc.player != null) {
            return sanitizeUsername(SeqClient.mc.player.getName().getString());
        }
        return null;
    }

    record SnapshotMember(String displayedName, String username) {}

    record PartySnapshot(List<String> usernames, Map<String, String> aliases, long capturedAtMs) {
        PartySnapshot {
            usernames = List.copyOf(usernames);
            aliases = Map.copyOf(aliases);
        }

        static PartySnapshot from(List<SnapshotMember> members, long capturedAtMs) {
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
            return new PartySnapshot(List.copyOf(usernames.values()), aliases, capturedAtMs);
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

        private PartySnapshot mergeAliases(PartySnapshot other, long capturedAtMs) {
            Map<String, String> mergedAliases = new LinkedHashMap<>(aliases);
            for (Map.Entry<String, String> alias : other.aliases.entrySet()) {
                mergedAliases.putIfAbsent(alias.getKey(), alias.getValue());
            }
            return new PartySnapshot(usernames, mergedAliases, capturedAtMs);
        }

        private PartySnapshot refresh(long capturedAtMs) {
            return new PartySnapshot(usernames, aliases, capturedAtMs);
        }

        private static PartySnapshot empty() {
            return new PartySnapshot(List.of(), Map.of(), 0);
        }
    }
}
